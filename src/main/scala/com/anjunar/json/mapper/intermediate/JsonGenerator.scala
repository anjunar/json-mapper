package com.anjunar.json.mapper.intermediate

import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonBoolean, JsonNode, JsonNull, JsonNumber, JsonObject, JsonString}

object JsonGenerator {

  def generate(jsonNode: JsonNode): String = {
    val builder = new java.lang.StringBuilder()
    appendJson(jsonNode, builder)
    builder.toString
  }

  private def appendJson(node: JsonNode, builder: java.lang.StringBuilder): Unit =
    node match {
      case value: JsonObject =>
        builder.append('{')
        var first = true
        val iterator = value.value.entrySet().iterator()
        while (iterator.hasNext) {
          val entry = iterator.next()
          if (!first) {
            builder.append(',')
          }
          first = false
          builder.append('"')
          escapeJsonString(entry.getKey, builder)
          builder.append("\":")
          appendJson(entry.getValue, builder)
        }
        builder.append('}')

      case value: JsonArray =>
        builder.append('[')
        var first = true
        val iterator = value.value.iterator()
        while (iterator.hasNext) {
          if (!first) {
            builder.append(',')
          }
          first = false
          appendJson(iterator.next(), builder)
        }
        builder.append(']')

      case value: JsonString =>
        builder.append('"')
        escapeJsonString(value.value, builder)
        builder.append('"')

      case value: JsonNumber =>
        builder.append(value.value)

      case value: JsonBoolean =>
        builder.append(value.value)

      case _: JsonNull =>
        builder.append("null")

      case _ =>
        throw new IllegalStateException(s"Unexpected value: $node")
    }

  private def escapeJsonString(value: String, builder: java.lang.StringBuilder): Unit = {
    var index = 0
    val length = value.length
    while (index < length) {
      val current = value.charAt(index)
      current match {
        case '"' =>
          builder.append("\\\"")
        case '\\' =>
          builder.append("\\\\")
        case '\b' =>
          builder.append("\\b")
        case '\f' =>
          builder.append("\\f")
        case '\n' =>
          builder.append("\\n")
        case '\r' =>
          builder.append("\\r")
        case '\t' =>
          builder.append("\\t")
        case _ =>
          if (current < ' ') {
            builder.append(String.format("\\u%04x", Integer.valueOf(current.toInt)))
          } else {
            builder.append(current)
          }
      }
      index += 1
    }
  }

}
