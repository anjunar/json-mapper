package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.util.UUID

class UUIDDeserializer extends Deserializer[UUID] {

  override def deserialize(json: JsonNode, context: JsonContext): UUID =
    json match {
      case value: JsonString => UUID.fromString(value.value)
      case _ => throw new IllegalArgumentException("json must be a string")
    }

}
