package com.anjunar.json.mapper

import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.scala.universe.TypeResolver
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.{Duration, OffsetDateTime, Period, Year, YearMonth, ZoneId, ZoneOffset, ZonedDateTime}
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

  test("invalid Instant input is reported as an invalid value instead of becoming null") {
    val failure = the[ErrorRequestException] thrownBy readInstant("\"not-an-instant\"")
    failure.errors.get(0).message shouldBe "This field expects a date or time in ISO-8601 format."
  }

  test("local temporal serialization retains its existing format and precision") {
    write(LocalDate.of(2026, 10, 20)) shouldBe "\"2026-10-20\""
    write(LocalDateTime.of(2026, 10, 20, 10, 11, 12)) shouldBe "\"2026-10-20T10:11:00\""
    write(LocalTime.of(10, 11, 12)) shouldBe "\"10:11:00\""
  }

  private def read[T](json: String, clazz: Class[T]): T =
    JsonMapper.deserialize(JsonParser.parse(json), null, TypeResolver.resolve(clazz), null, null, noInject, null)
      .asInstanceOf[T]

  test("every other java.time type is written in ISO-8601 form and read back") {
    val values = Seq(
      OffsetDateTime.of(2026, 10, 20, 10, 11, 12, 0, ZoneOffset.ofHours(2)),
      ZonedDateTime.of(2026, 10, 20, 10, 11, 12, 0, ZoneId.of("Europe/Berlin")),
      YearMonth.of(2026, 10),
      Year.of(2026)
    )
    values.foreach(value => read(write(value), value.getClass) shouldBe value)
  }

  test("periods and durations are read as the declared type") {
    read("\"P1Y2M\"", classOf[Period]) shouldBe Period.of(1, 2, 0)
    read("\"PT90M\"", classOf[Duration]) shouldBe Duration.ofMinutes(90)
    write(Period.ofDays(3)) shouldBe "\"P3D\""
  }

  test("an invalid duration is reported as an invalid value") {
    val failure = the[ErrorRequestException] thrownBy read("\"soon\"", classOf[Duration])
    failure.errors.get(0).message shouldBe "This field expects an ISO-8601 duration."
  }
}
