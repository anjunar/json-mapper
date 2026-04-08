package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.time.Duration
import java.time.temporal.TemporalAmount

class TemporalAmountDeserializer extends Deserializer[TemporalAmount] {

  override def deserialize(json: JsonNode, context: JsonContext): TemporalAmount =
    json match {
      case value: JsonString => Duration.parse(value.value)
      case _ => throw new IllegalArgumentException("json must be a string")
    }

}
