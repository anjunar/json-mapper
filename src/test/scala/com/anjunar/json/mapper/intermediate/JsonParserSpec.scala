package com.anjunar.json.mapper.intermediate

import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonBoolean, JsonNull, JsonNumber, JsonObject, JsonString}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonParserSpec extends AnyFunSuite with Matchers {

  test("parse should read nested objects arrays and scalar values") {
    val json =
      """{
        |  "name": "Patrick",
        |  "active": true,
        |  "count": -12.5e+2,
        |  "items": [1, null, {"role": "admin"}]
        |}""".stripMargin

    val parsed = JsonParser.parse(json).asInstanceOf[JsonObject]

    parsed.value.get("name") shouldBe a[JsonString]
    parsed.getString("name") shouldBe "Patrick"
    parsed.value.get("active").asInstanceOf[JsonBoolean].value shouldBe true
    parsed.value.get("count").asInstanceOf[JsonNumber].value shouldBe "-12.5e+2"

    val items = parsed.value.get("items").asInstanceOf[JsonArray]
    items.value.get(0).asInstanceOf[JsonNumber].value shouldBe "1"
    items.value.get(1) shouldBe a[JsonNull]
    items.value.get(2).asInstanceOf[JsonObject].getString("role") shouldBe "admin"
  }

  test("parse should unescape escaped characters and unicode sequences") {
    val parsed =
      JsonParser
        .parse("""{"text":"line\nquote:\"slash:\\ unicode:\u0041"}""")
        .asInstanceOf[JsonObject]

    parsed.getString("text") shouldBe "line\nquote:\"slash:\\ unicode:A"
  }

  test("parse should reject trailing data") {
    val exception = the[IllegalStateException] thrownBy JsonParser.parse("""{"ok":true} nope""")

    exception.getMessage should include("Unexpected trailing data")
  }

  test("parse should reject invalid exponent") {
    val exception = the[IllegalStateException] thrownBy JsonParser.parse("""{"value":1e}""")

    exception.getMessage should include("Invalid exponent")
  }
}
