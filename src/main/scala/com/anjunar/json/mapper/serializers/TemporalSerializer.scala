package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.JavaContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.time.format.DateTimeFormatter
import java.time.temporal.{ChronoUnit, Temporal}
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TemporalSerializer extends Serializer[Temporal] {

  override def serialize(input: Temporal, context: JavaContext): JsonNode =
    input match {
      case value: LocalDate =>
        new JsonString(value.format(DateTimeFormatter.ISO_LOCAL_DATE))
      case value: LocalDateTime =>
        new JsonString(value.truncatedTo(ChronoUnit.MINUTES).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
      case value: LocalTime =>
        new JsonString(value.truncatedTo(ChronoUnit.MINUTES).format(DateTimeFormatter.ISO_TIME))
      case _ =>
        throw new IllegalArgumentException(s"Unsupported type: ${context.resolvedClass}")
    }

}
