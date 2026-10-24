package com.anjunar.json.mapper

import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.scala.universe.TypeResolver
import jakarta.validation.Validation
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.annotation.nowarn

/** The static calls of 1.1 stay source compatible: same arguments, null for an absent graph or loader. */
@nowarn("cat=deprecation")
class LegacyApiSpec extends AnyFunSuite with Matchers {

  private val validator = Validation
    .byDefaultProvider()
    .configure()
    .messageInterpolator(new ParameterMessageInterpolator())
    .buildValidatorFactory()
    .getValidator

  private val inject: [T] => Class[T] => T = [T] => (clazz: Class[T]) => clazz.getDeclaredConstructor().newInstance()

  test("the deprecated static calls still serialize, deserialize and prepare") {
    val profile = new ProfileDto
    JsonMapper.deserialize(
      JsonParser.parse("""{"name":"Grace"}"""),
      profile,
      TypeResolver.resolve(classOf[ProfileDto]),
      null,
      null,
      inject,
      validator
    )
    profile.name shouldBe "Grace"

    val change = JsonMapper.prepare(
      JsonParser.parse("""{"name":"Ada"}"""),
      profile,
      TypeResolver.resolve(classOf[ProfileDto]),
      null,
      null,
      inject,
      validator
    )
    profile.name shouldBe "Grace"
    change.applyChanges().name shouldBe "Ada"

    JsonMapper.serialize(profile, TypeResolver.resolve(classOf[ProfileDto]), null, inject) shouldBe
      """{"name":"Ada","age":0,"@type":"ProfileDto"}"""
  }

  test("deserializing properties without a validator says what is missing") {
    val failure = the[IllegalStateException] thrownBy new JsonMapper().deserialize(
      JsonParser.parse("""{"name":"Grace"}"""),
      new ProfileDto,
      TypeResolver.resolve(classOf[ProfileDto])
    )
    failure.getMessage shouldBe "Deserializing properties needs a Validator"
  }
}
