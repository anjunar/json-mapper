package com.anjunar.json.mapper

import com.anjunar.json.mapper.annotations.{JsonbAnyProperty, UseConverter}
import com.anjunar.json.mapper.converter.JacksonJsonConverter
import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.provider.DTO
import com.anjunar.scala.universe.{ResolvedClass, TypeResolver}
import jakarta.json.bind.annotation.{JsonbProperty, JsonbSubtype}
import jakarta.validation.executable.ExecutableValidator
import jakarta.validation.metadata.BeanDescriptor
import jakarta.validation.{ConstraintViolation, Path, Validator}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.lang.annotation.ElementType
import java.lang.reflect.{Constructor, Method}
import java.util
import scala.annotation.meta.field

class ProfileDto {
  @(JsonbProperty @field)
  var name: String = null
  @(JsonbProperty @field)
  var age: Int = 0
  @(JsonbProperty @field)
  var active: Boolean = false
  @(JsonbProperty @field)
  var nickname: String = null
  @(JsonbProperty @field)
  var primaryTag: TagDto = null
  @(JsonbProperty @field)
  var linkedTag: TagDto = null
  @(JsonbProperty @field)
  var tags: util.List[TagDto] = new util.ArrayList[TagDto]()
  @(JsonbAnyProperty @field) @(JsonbProperty @field)
  var attributes: util.Map[String, Any] = new util.LinkedHashMap[String, Any]()
}
