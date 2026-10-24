package com.anjunar.json.mapper

import com.anjunar.json.mapper.provider.DTO
import com.anjunar.json.mapper.schema.{BeanSchema, SchemaProvider}
import jakarta.json.bind.annotation.JsonbProperty
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.{Executors, TimeUnit}
import scala.annotation.meta.field

class SchemaProviderSpec extends AnyFunSuite with Matchers {

  test("schemas whose properties reference each other initialize without blocking") {
    val pool = Executors.newSingleThreadExecutor()
    try {
      val left = pool.submit(() => CycleLeft.schema).get(10, TimeUnit.SECONDS)
      left.right.schema should be theSameInstanceAs CycleRight.schema
      CycleRight.schema.left.schema should be theSameInstanceAs left
    } finally {
      pool.shutdownNow()
    }
  }

  test("a provider without a nested Schema class says so") {
    val failure = the[IllegalStateException] thrownBy MissingSchema.schema
    failure.getMessage should include("provides no nested class Schema")
  }
}

class CycleLeft extends DTO {
  @(JsonbProperty @field)
  var right: CycleRight = null
}

object CycleLeft extends SchemaProvider[CycleLeft.Schema] {
  class Schema extends BeanSchema[CycleLeft] {
    val right = property(_.right)
  }
}

class CycleRight extends DTO {
  @(JsonbProperty @field)
  var left: CycleLeft = null
}

object CycleRight extends SchemaProvider[CycleRight.Schema] {
  class Schema extends BeanSchema[CycleRight] {
    val left = property(_.left)
  }
}

class MissingSchema

object MissingSchema extends SchemaProvider[BeanSchema[MissingSchema]]
