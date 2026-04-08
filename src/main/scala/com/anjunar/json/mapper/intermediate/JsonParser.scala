package com.anjunar.json.mapper.intermediate

import com.anjunar.json.mapper.intermediate.model.*

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import java.util

object JsonParser {

  def parse(json: String): JsonNode = {
    val parser = new FastParser(json)
    val node = parser.parseValue()
    parser.skipWhitespace()
    if (!parser.eof) throw new IllegalStateException(s"Unexpected trailing data at pos ${parser.pos}")
    node
  }

  private class FastParser(input: String) {
    private val chars: Array[Char] = input.toCharArray
    private val len: Int = chars.length
    var pos: Int = 0

    @inline def eof: Boolean = pos >= len
    @inline private def peek: Char = if (pos < len) chars(pos) else 0.toChar
    @inline private def nextChar(): Char = { val c = chars(pos); pos += 1; c }

    @inline def skipWhitespace(): Unit = {
      var c = 0.toChar
      while (pos < len) {
        c = chars(pos)
        // fast branch using ASCII whitespace set
        if (c == ' ' || c == '\n' || c == '\r' || c == '\t') pos += 1
        else return
      }
    }

    def parseValue(): JsonNode = {
      skipWhitespace()
      if (eof) throw new IllegalStateException("Unexpected end of input")
      peek match {
        case '{' => parseObject()
        case '[' => parseArray()
        case '"' => JsonString(parseString())
        case 't' => expectLiteral("true"); JsonBoolean(true)
        case 'f' => expectLiteral("false"); JsonBoolean(false)
        case 'n' => expectLiteral("null"); new JsonNull
        case c if (c == '-' || (c >= '0' && c <= '9')) => parseNumber()
        case other => throw new IllegalStateException(s"Unexpected char '${other}' at pos $pos")
      }
    }

    private def expectLiteral(lit: String): Unit = {
      val start = pos
      val end = start + lit.length
      if (end > len) throw new IllegalStateException(s"Unexpected end (expect '$lit')")
      var i = 0
      while (i < lit.length) {
        if (chars(pos + i) != lit.charAt(i))
          throw new IllegalStateException(s"Unexpected literal at pos $pos, expected $lit")
        i += 1
      }
      pos = end
    }

    private def parseObject(): JsonObject = {
      // consume '{'
      if (nextChar() != '{') throw new IllegalStateException("Expected '{'")
      val map = new util.LinkedHashMap[String, JsonNode]()
      skipWhitespace()
      if (!eof && peek == '}') { pos += 1; return JsonObject(map) } // empty object
      var continue = true
      while (continue) {
        skipWhitespace()
        if (eof || peek != '"') throw new IllegalStateException(s"Expected string key at pos $pos")
        val key = parseString()
        skipWhitespace()
        if (eof || nextChar() != ':') throw new IllegalStateException(s"Expected ':' after key at pos $pos")
        val value = parseValue()
        map.put(key, value)
        skipWhitespace()
        if (eof) throw new IllegalStateException("Unterminated object")
        peek match {
          case ',' => pos += 1; // continue
          case '}' => pos += 1; continue = false
          case ch  => throw new IllegalStateException(s"Expected ',' or '}' in object at pos $pos but found '$ch'")
        }
      }
      JsonObject(map)
    }

    private def parseArray(): JsonArray = {
      // consume '['
      if (nextChar() != '[') throw new IllegalStateException("Expected '['")
      val arr = new util.ArrayList[JsonNode]()
      skipWhitespace()
      if (!eof && peek == ']') { pos += 1; return JsonArray(arr) } // empty array
      var continue = true
      while (continue) {
        val v = parseValue()
        arr.add(v)
        skipWhitespace()
        if (eof) throw new IllegalStateException("Unterminated array")
        peek match {
          case ',' => pos += 1
          case ']' => pos += 1; continue = false
          case ch  => throw new IllegalStateException(s"Expected ',' or ']' in array at pos $pos but found '$ch'")
        }
      }
      JsonArray(arr)
    }

    // Efficient string parsing:
    // - If no escape sequences: create new String(chars, start, len)
    // - If escapes: build StringBuilder and handle escapes
    private def parseString(): String = {
      // assumes we are at '"'
      if (nextChar() != '"') throw new IllegalStateException("Expected '\"' at beginning of string")
      val start = pos
      var i = pos
      var hasEscape = false
      while (i < len) {
        val c = chars(i)
        if (c == '"') {
          // end found
          val end = i
          pos = i + 1
          if (!hasEscape) {
            // fast path: no escape -> create string directly from char array slice
            return new String(chars, start, end - start)
          } else {
            // slower path: had escapes, but we know end, so build
            return unescapeFromChars(start, end)
          }
        } else if (c == '\\') {
          hasEscape = true
          // skip escape char and continue scanning
          i += 2
        } else {
          i += 1
        }
      }
      throw new IllegalStateException("Unterminated string")
    }

    // Build unescaped string from char slice [start, end)
    private def unescapeFromChars(start: Int, end: Int): String = {
      val sb = new StringBuilder(end - start)
      var i = start
      while (i < end) {
        val c = chars(i)
        if (c == '\\') {
          i += 1
          if (i >= end) throw new IllegalStateException("Invalid escape at end of string")
          chars(i) match {
            case '"'  => sb.append('"'); i += 1
            case '\\' => sb.append('\\'); i += 1
            case '/'  => sb.append('/'); i += 1
            case 'b'  => sb.append('\b'); i += 1
            case 'f'  => sb.append('\f'); i += 1
            case 'n'  => sb.append('\n'); i += 1
            case 'r'  => sb.append('\r'); i += 1
            case 't'  => sb.append('\t'); i += 1
            case 'u'  =>
              // unicode escape \uXXXX
              if (i + 4 >= end) throw new IllegalStateException("Invalid unicode escape")
              var code = 0
              var j = 1
              while (j <= 4) {
                val ch = chars(i + j)
                code = (code << 4) + hexValue(ch)
                j += 1
              }
              sb.append(code.toChar)
              i += 5
            case other => throw new IllegalStateException(s"Invalid escape '\\$other' at pos $i")
          }
        } else {
          sb.append(c); i += 1
        }
      }
      sb.toString()
    }

    @inline private def hexValue(c: Char): Int = {
      if (c >= '0' && c <= '9') c - '0'
      else if (c >= 'A' && c <= 'F') 10 + (c - 'A')
      else if (c >= 'a' && c <= 'f') 10 + (c - 'a')
      else throw new IllegalStateException(s"Invalid hex char '$c' in unicode escape")
    }

    private def parseNumber(): JsonNumber = {
      val start = pos
      if (peek == '-') pos += 1

      if (eof) throw new IllegalStateException("Unexpected end in number")
      if (peek == '0') pos += 1
      else if (peek >= '1' && peek <= '9') {
        while (!eof && chars(pos) >= '0' && chars(pos) <= '9') pos += 1
      } else throw new IllegalStateException(s"Invalid number at pos $pos")

      // optional fraction
      if (!eof && chars(pos) == '.') {
        pos += 1
        if (eof || !(chars(pos) >= '0' && chars(pos) <= '9'))
          throw new IllegalStateException("Invalid fractional part in number")
        while (!eof && chars(pos) >= '0' && chars(pos) <= '9') pos += 1
      }

      // optional exponent
      if (!eof && (chars(pos) == 'e' || chars(pos) == 'E')) {
        pos += 1
        if (!eof && (chars(pos) == '+' || chars(pos) == '-')) pos += 1
        if (eof || !(chars(pos) >= '0' && chars(pos) <= '9'))
          throw new IllegalStateException("Invalid exponent in number")
        while (!eof && chars(pos) >= '0' && chars(pos) <= '9') pos += 1
      }

      JsonNumber(new String(chars, start, pos - start))
    }
  }
}