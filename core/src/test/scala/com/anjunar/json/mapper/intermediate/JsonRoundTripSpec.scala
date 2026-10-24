package com.anjunar.json.mapper.intermediate

import com.anjunar.json.mapper.intermediate.model.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util
import scala.util.Random

/** Random documents with a fixed seed: generating, parsing and generating again must give the same text. */
class JsonRoundTripSpec extends AnyFunSuite with Matchers {

  private val alphabet: IndexedSeq[Char] =
    ('a' to 'z') ++ ('0' to '9') ++ Seq('"', '\\', '/', '\b', '\f', '\n', '\r', '\t', '\u0001', '\u001f', ' ', 'ä',
      '€', '\ud83d', '\ude00')

  private def text(random: Random): String =
    Iterator.continually(alphabet(random.nextInt(alphabet.length))).take(random.nextInt(12)).mkString

  private def number(random: Random): String =
    random.nextInt(5) match {
      case 0 => random.nextInt().toString
      case 1 => random.nextLong().toString
      case 2 => s"${random.nextInt(1000)}.${random.nextInt(1000)}"
      case 3 => s"-${random.nextInt(10)}.${random.nextInt(100)}e${random.nextInt(20) - 10}"
      case _ => "0"
    }

  private def node(random: Random, depth: Int): JsonNode =
    random.nextInt(if (depth > 3) 4 else 6) match {
      case 0 => new JsonString(text(random))
      case 1 => new JsonNumber(number(random))
      case 2 => new JsonBoolean(random.nextBoolean())
      case 3 => new JsonNull
      case 4 =>
        val values = new util.ArrayList[JsonNode]()
        (0 until random.nextInt(5)).foreach(_ => values.add(node(random, depth + 1)))
        new JsonArray(values)
      case _ =>
        val values = new util.LinkedHashMap[String, JsonNode]()
        (0 until random.nextInt(5)).foreach(_ => values.put(text(random), node(random, depth + 1)))
        new JsonObject(values)
    }

  test("random documents survive generate, parse and generate unchanged") {
    val random = new Random(20261024L)
    (0 until 2000).foreach { _ =>
      val generated = JsonGenerator.generate(node(random, 0))
      JsonGenerator.generate(JsonParser.parse(generated)) shouldBe generated
    }
  }

  test("insignificant whitespace between tokens does not change the document") {
    val random = new Random(7L)
    (0 until 500).foreach { _ =>
      val generated = JsonGenerator.generate(node(random, 0))
      val spaced = generated.flatMap {
        case c @ ('{' | '}' | '[' | ']' | ',' | ':') => s" \n\t$c\r "
        case c                                       => c.toString
      }
      // Whitespace inside strings would change their content, so only documents without strings are spaced.
      if (!generated.contains('"')) {
        JsonGenerator.generate(JsonParser.parse(spaced)) shouldBe generated
      }
    }
  }
}
