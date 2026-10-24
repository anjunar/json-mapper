package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.util

class UUIDDeserializer extends Deserializer[util.UUID] {

  override def deserialize(json: JsonNode, context: JsonContext): util.UUID =
    json match {
      case value: JsonString => util.UUID.fromString(value.value)
      case _                 => throw new IllegalArgumentException("json must be a string")
    }

}
