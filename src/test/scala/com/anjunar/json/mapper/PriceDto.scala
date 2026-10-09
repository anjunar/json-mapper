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

class PriceDto {
  @(JsonbProperty @field) var amount: java.math.BigDecimal = null
  @(JsonbProperty @field) var rounded: java.math.BigDecimal = null
  @(JsonbProperty @field) var total: scala.math.BigDecimal = null
  @(JsonbProperty @field) var count: Int = 0
  @(JsonbProperty @field) var ratio: Double = 0.0
  @(JsonbProperty @field) var units: Long = 0L
  @(JsonbProperty @field) var share: Double = 0.0
}
