package com.anjunar.json.mapper.intermediate

import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonBoolean, JsonNull, JsonNumber, JsonObject, JsonString}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonGeneratorSpec extends AnyFunSuite with Matchers {

  test("generate should escape special characters and control characters") {
    val node = new JsonObject()
      .put("quote", new JsonString("a\"b\\c"))
      .put("control", new JsonString("row1\nrow2\t\u0001"))

    JsonGenerator.generate(node) shouldBe """{"quote":"a\"b\\c","control":"row1\nrow2\t\u0001"}"""
  }

  test("generate should serialize arrays nulls and booleans") {
    val array = new JsonArray()
      .add(new JsonNumber("1"))
      .add(new JsonBoolean(false))
      .add(JsonNull())

    JsonGenerator.generate(array) shouldBe "[1,false,null]"
  }

  test("generate should round trip through parser without changing content") {
    val source = """{"message":"hello\nworld","nested":{"value":42},"items":[true,null,"x"]}"""

    val generated = JsonGenerator.generate(JsonParser.parse(source))

    generated shouldBe source
  }
}
