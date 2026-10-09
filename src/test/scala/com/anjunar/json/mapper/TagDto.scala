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
import java.util.UUID
import scala.annotation.meta.field

class TagDto extends DTO {
  @(JsonbProperty @field) var label: String = null
}

