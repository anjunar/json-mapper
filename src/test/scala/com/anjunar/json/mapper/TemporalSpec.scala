package com.anjunar.json.mapper

import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.scala.universe.TypeResolver
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime}

class TemporalSpec extends AnyFunSuite with Matchers {
  private val noInject: [T] => Class[T] => T = [T] => (_: Class[T]) => null.asInstanceOf[T]

  private def write(value: Any): String =
    JsonMapper.serialize(value, TypeResolver.resolve(value.getClass), null, noInject)

  private def readInstant(json: String): Instant =
    JsonMapper.deserialize(
      JsonParser.parse(json),
      null,
      TypeResolver.resolve(classOf[Instant]),
      null,
      null,
      noInject,
      null
    ).asInstanceOf[Instant]

  test("Instant round trips as ISO-8601 UTC without losing nanoseconds") {
    for (text <- Seq("1970-01-01T00:00:00Z", "2026-10-20T10:11:12.123456789Z", "1969-12-31T23:59:59.999999999Z")) {
      val instant = Instant.parse(text)
      val json = write(instant)
      json shouldBe s"\"$text\""
      readInstant(json) shouldBe instant
    }
  }

  test("Instant input with an offset is normalized to UTC") {
    val instant = readInstant("\"2026-10-20T12:11:12.123456789+02:00\"")
    instant shouldBe Instant.parse("2026-10-20T10:11:12.123456789Z")
    write(instant) shouldBe "\"2026-10-20T10:11:12.123456789Z\""
  }

  test("invalid Instant input retains the temporal deserializer's null result") {
    readInstant("\"not-an-instant\"") shouldBe null
  }

  test("local temporal serialization retains its existing format and precision") {
    write(LocalDate.of(2026, 10, 20)) shouldBe "\"2026-10-20\""
    write(LocalDateTime.of(2026, 10, 20, 10, 11, 12)) shouldBe "\"2026-10-20T10:11:00\""
    write(LocalTime.of(10, 11, 12)) shouldBe "\"10:11:00\""
  }
}
