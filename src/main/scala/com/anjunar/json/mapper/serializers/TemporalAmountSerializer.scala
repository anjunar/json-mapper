package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.JavaContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.time.temporal.TemporalAmount

class TemporalAmountSerializer extends Serializer[TemporalAmount] {

  override def serialize(input: TemporalAmount, context: JavaContext): JsonNode =
    new JsonString(input.toString)

}
