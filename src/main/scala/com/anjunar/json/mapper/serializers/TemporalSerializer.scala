package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.JavaContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.time.format.DateTimeFormatter
import java.time.temporal.{ChronoUnit, Temporal}

class TemporalSerializer extends Serializer[Temporal] {

  override def serialize(input: Temporal, context: JavaContext): JsonNode =
    input match {
      case value: java.time.LocalDate =>
        new JsonString(value.format(DateTimeFormatter.ISO_LOCAL_DATE))
      case value: java.time.LocalDateTime =>
        new JsonString(value.truncatedTo(ChronoUnit.MINUTES).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
      case value: java.time.LocalTime =>
        new JsonString(value.truncatedTo(ChronoUnit.MINUTES).format(DateTimeFormatter.ISO_TIME))
      case _ =>
        throw new IllegalArgumentException(s"Unsupported type: ${context.resolvedClass}")
    }

}
