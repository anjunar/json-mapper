package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.SerializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.time.temporal.TemporalAmount

class TemporalAmountSerializer extends Serializer[TemporalAmount] {

  override def serialize(input: TemporalAmount, context: SerializationContext): JsonNode =
    new JsonString(input.toString)

}
