package com.anjunar.json.mapper.registry

import com.anjunar.json.mapper.deserializer.*
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonNumber, JsonObject, JsonString}
import com.anjunar.json.mapper.serializers.*
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.Locale
import java.util.UUID

class RegistrySpec extends AnyFunSuite with Matchers {

  private class SampleBean

  test("serializer registry should select specialized serializers") {
    SerializerRegistry.find(classOf[String], "value") shouldBe a[StringSerializer]
    SerializerRegistry.find(classOf[Locale], Locale.GERMANY) shouldBe a[LocaleSerializer]
    SerializerRegistry.find(classOf[UUID], UUID.randomUUID()) shouldBe a[UUIDSerializer]
    SerializerRegistry.find(classOf[SampleBean], new SampleBean) shouldBe a[BeanSerializer]
  }

  test("deserializer registry should select specialized deserializers") {
    DeserializerRegistry.findDeserializer(classOf[String], new JsonString("value")) shouldBe a[StringDeserializer]
    DeserializerRegistry.findDeserializer(classOf[Array[Byte]], new JsonString("YQ==")) shouldBe a[ByteArrayDeserializer]
    DeserializerRegistry.findDeserializer(classOf[java.util.Map[?, ?]], new JsonObject()) shouldBe a[MapDeserializer]
    DeserializerRegistry.findDeserializer(classOf[java.lang.Integer], new JsonNumber("5")) shouldBe a[NumberDeserializer]
    DeserializerRegistry.findDeserializer(classOf[java.util.List[?]], new JsonArray()) shouldBe a[ArrayDeserializer]
  }

  test("deserializer registry should reject unsupported string target types") {
    val exception =
      the[IllegalArgumentException] thrownBy DeserializerRegistry.findDeserializer(classOf[SampleBean], new JsonString("value"))

    exception.getMessage should include("Unsupported type")
  }
}
