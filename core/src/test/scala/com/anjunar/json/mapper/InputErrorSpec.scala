package com.anjunar.json.mapper

import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.provider.DTO
import com.anjunar.scala.universe.TypeResolver
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.validation.Validation
import jakarta.validation.constraints.Size
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.lang.{Integer as JavaInteger}
import java.math.BigInteger
import java.time.DayOfWeek
import java.util
import scala.annotation.meta.field
import scala.jdk.CollectionConverters.*
import java.lang.{Double as JavaDouble}
import java.lang.{Long as JavaLong}

class InputErrorSpec extends AnyFunSuite with Matchers {

  private val validator = Validation
    .byDefaultProvider()
    .configure()
    .messageInterpolator(new ParameterMessageInterpolator())
    .buildValidatorFactory()
    .getValidator

  private val inject: [T] => Class[T] => T = [T] => (clazz: Class[T]) => clazz.getDeclaredConstructor().newInstance()

  private val noLoader: EntityLoader = (_, _) => null

  private def deserialize[T <: AnyRef](target: T, json: String): T = {
    new JsonMapper(
      Option(validator),
      noLoader,
      inject
    ).deserialize(JsonParser.parse(json), target, TypeResolver.resolve(target.getClass))
    target
  }

  private def failure(target: AnyRef, json: String): ErrorRequestException =
    the[ErrorRequestException] thrownBy deserialize(target, json)

  private def paths(error: ErrorRequestException): Set[Seq[Any]] =
    error.errors.asScala.map(_.path.asScala.toSeq).toSet

  test("every invalid field of a document is reported at once and nothing is written") {
    val input = new InputDto
    val error = failure(
      input,
      """{"name":"far too long","count":"three","tags":[1],"day":"Funday","tag":{"label":5},"scores":{"a":1.5}}"""
    )

    paths(error) shouldBe Set(
      Seq("name"),
      Seq("count"),
      Seq("tags", 0),
      Seq("day"),
      Seq("tag", "label"),
      Seq("scores", "a")
    )
    input.name shouldBe null
    input.count shouldBe 0
    input.tags.isEmpty shouldBe true
    input.tag shouldBe null
    input.scores.isEmpty shouldBe true
  }

  test("one invalid field keeps the valid fields of the same document unwritten") {
    val input = new InputDto
    input.name = "kept"
    failure(input, """{"name":"new","count":"three"}""")
    input.name shouldBe "kept"
  }

  test("a valid document is written completely") {
    val input = deserialize(new InputDto, """{"name":"ok","count":3,"tags":["a"],"day":"MONDAY","scores":{"a":1}}""")
    input.name shouldBe "ok"
    input.count shouldBe 3
    input.tags.asScala.toSeq shouldBe Seq("a")
    input.day shouldBe DayOfWeek.MONDAY
    input.scores.get("a") shouldBe JavaInteger.valueOf(1)
  }

  test("a value of the wrong JSON kind is reported at its field") {
    val error = failure(new InputDto, """{"name":5,"tags":"single","tag":"label","count":1.5}""")
    error.errors.asScala.map(e => e.path.asScala.toSeq -> e.message).toMap shouldBe Map(
      Seq("name") -> "This field expects a string.",
      Seq("tags") -> "This field expects an array.",
      Seq("tag") -> "This field expects an object or null.",
      Seq("count") -> "This field expects a whole number in its range."
    )
  }

  test("a document that is no object is reported at the root") {
    val error = failure(new InputDto, """[1, 2]""")
    error.errors.get(0).path.isEmpty shouldBe true
    error.errors.get(0).message shouldBe "This field expects an object."
  }

  test("invalid input is an IllegalArgumentException, so existing handlers keep catching it") {
    an[IllegalArgumentException] should be thrownBy deserialize(new InputDto, """{"count":"three"}""")
  }

  test("untyped values keep whole numbers exact and read exponents as decimals") {
    val profile = deserialize(new ProfileDto, """{"big":12345678901234567890,"small":7,"exp":1e3}""")
    profile.attributes.get("big") shouldBe new BigInteger("12345678901234567890")
    profile.attributes.get("small") shouldBe JavaLong.valueOf(7)
    profile.attributes.get("exp") shouldBe JavaDouble.valueOf(1000)
  }
}

class InputDto extends DTO {
  @(JsonbProperty @field) @(Size @field)(max = 5)
  var name: String = null
  @(JsonbProperty @field)
  var count: Int = 0
  @(JsonbProperty @field)
  var tags: util.List[String] = new util.ArrayList[String]()
  @(JsonbProperty @field)
  var day: DayOfWeek = null
  @(JsonbProperty @field)
  var tag: TagDto = null
  @(JsonbProperty @field)
  var scores: util.Map[String, JavaInteger] = new util.LinkedHashMap[String, JavaInteger]()
}
