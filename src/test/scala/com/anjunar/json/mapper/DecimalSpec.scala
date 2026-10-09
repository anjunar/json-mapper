package com.anjunar.json.mapper

import com.anjunar.json.mapper.deserializer.{DeserializerRegistry, NumberDeserializer}
import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.intermediate.model.{JsonNumber, JsonObject, JsonString}
import com.anjunar.scala.universe.TypeResolver
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.validation.executable.ExecutableValidator
import jakarta.validation.metadata.BeanDescriptor
import jakarta.validation.{ConstraintViolation, Validator}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.annotation.meta.field

class DecimalSpec extends AnyFunSuite with Matchers {

  private val noInject: [T] => Class[T] => T = [T] => (_: Class[T]) => null.asInstanceOf[T]

  private val loader = new EntityLoader {
    override def load(id: java.util.UUID, clazz: Class[?]): Any = null
  }

  private def serialize(price: PriceDto): JsonObject =
    JsonParser.parse(JsonMapper.serialize(price, TypeResolver.resolve(classOf[PriceDto]), null, noInject)).asInstanceOf[JsonObject]

  private def deserialize(json: String): PriceDto =
    JsonMapper.deserialize(JsonParser.parse(json), new PriceDto, TypeResolver.resolve(classOf[PriceDto]), null,
      loader, noInject, DecimalSpec.validator).asInstanceOf[PriceDto]

  test("exact decimals are written with their digits and scale, never with an exponent") {
    val price = new PriceDto
    price.amount = new java.math.BigDecimal("120.00")
    price.rounded = new java.math.BigDecimal("1E+3")
    price.total = scala.math.BigDecimal("0.10")
    val json = serialize(price)
    json.value.get("amount").asInstanceOf[JsonNumber].value shouldBe "120.00"
    json.value.get("rounded").asInstanceOf[JsonNumber].value shouldBe "1000"
    json.value.get("total").asInstanceOf[JsonNumber].value shouldBe "0.10"
  }

  test("exact decimals are read from the digits, without a detour through Double") {
    val price = deserialize("""{"amount": 0.1, "rounded": 12345678901234567890.123456789, "total": 19.90}""")
    price.amount shouldBe new java.math.BigDecimal("0.1")
    price.rounded shouldBe new java.math.BigDecimal("12345678901234567890.123456789")
    price.total shouldBe scala.math.BigDecimal("19.90")
    price.total.scale shouldBe 2
  }

  test("exact decimals are also accepted as strings of digits, as JavaScript clients may send them") {
    val price = deserialize("""{"amount": "120.50", "total": " 7.5 "}""")
    price.amount shouldBe new java.math.BigDecimal("120.50")
    price.total shouldBe scala.math.BigDecimal("7.5")
  }

  test("the registry picks the number deserializer for decimals given as numbers and as strings") {
    DeserializerRegistry.findDeserializer(classOf[java.math.BigDecimal], new JsonNumber("1.5")) shouldBe a[NumberDeserializer]
    DeserializerRegistry.findDeserializer(classOf[java.math.BigDecimal], new JsonString("1.5")) shouldBe a[NumberDeserializer]
    DeserializerRegistry.findDeserializer(classOf[scala.math.BigDecimal], new JsonString("1.5")) shouldBe a[NumberDeserializer]
  }

  test("a string that is no decimal is rejected") {
    an[IllegalArgumentException] should be thrownBy deserialize("""{"amount": "twelve"}""")
  }

  test("whole numbers are read as the declared type, also where the resolver reports a boxed class") {
    val price = deserialize("""{"count": 3, "ratio": 0.25, "units": 9000000000, "share": 2}""")
    price.count shouldBe 3
    price.ratio shouldBe 0.25
    price.units shouldBe 9000000000L
    price.share shouldBe 2.0
  }
}

object DecimalSpec {
  private val validator: Validator = new Validator {
    override def validate[T](obj: T, groups: Class[?]*): java.util.Set[ConstraintViolation[T]] = java.util.Collections.emptySet()
    override def validateProperty[T](obj: T, propertyName: String, groups: Class[?]*): java.util.Set[ConstraintViolation[T]] =
      java.util.Collections.emptySet()
    override def validateValue[T](beanType: Class[T], propertyName: String, value: Any, groups: Class[?]*): java.util.Set[ConstraintViolation[T]] =
      java.util.Collections.emptySet()
    override def getConstraintsForClass(clazz: Class[?]): BeanDescriptor = null
    override def unwrap[T](clazz: Class[T]): T = null.asInstanceOf[T]
    override def forExecutables(): ExecutableValidator = null
  }
}

