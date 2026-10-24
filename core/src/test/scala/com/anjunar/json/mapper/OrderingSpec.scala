package com.anjunar.json.mapper

import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.scala.universe.TypeResolver
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.validation.Validation
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util
import scala.annotation.meta.field
import scala.jdk.CollectionConverters.*

class OrderingSpec extends AnyFunSuite with Matchers {

  private val inject: [T] => Class[T] => T = [T] => (clazz: Class[T]) => clazz.getDeclaredConstructor().newInstance()

  private val validator = Validation
    .byDefaultProvider()
    .configure()
    .messageInterpolator(new ParameterMessageInterpolator())
    .buildValidatorFactory()
    .getValidator

  test("map entries are written in the order of the source map, and null values as null") {
    val value = new OrderedDto
    value.entries.put("zeta", "1")
    value.entries.put("alpha", null)
    value.entries.put("mid", "2")
    new JsonMapper(inject = inject).serialize(value, TypeResolver.resolve(classOf[OrderedDto])) shouldBe
      """{"entries":{"zeta":"1","alpha":null,"mid":"2"},"@type":"OrderedDto"}"""
  }

  test("set elements keep the order in which the document lists them") {
    val value = new OrderedDto
    new JsonMapper(
      Option(validator),
      (_, _) => null,
      inject
    ).deserialize(JsonParser.parse("""{"labels":["c","a","b"]}"""), value, TypeResolver.resolve(classOf[OrderedDto]))
    value.labels.asScala.toSeq shouldBe Seq("c", "a", "b")
  }

  test("null elements of a collection are written as null") {
    val value = new OrderedDto
    value.names.add("a")
    value.names.add(null)
    new JsonMapper(inject = inject).serialize(value, TypeResolver.resolve(classOf[OrderedDto])) shouldBe
      """{"names":["a",null],"@type":"OrderedDto"}"""
  }
}

class OrderedDto {
  @(JsonbProperty @field)
  val entries: util.Map[String, String] = new util.LinkedHashMap[String, String]()
  @(JsonbProperty @field)
  val labels: util.Set[String] = new util.LinkedHashSet[String]()
  @(JsonbProperty @field)
  val names: util.List[String] = new util.ArrayList[String]()
}
