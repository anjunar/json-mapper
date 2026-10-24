package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.SerializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.util

class UUIDSerializer extends Serializer[util.UUID] {

  override def serialize(input: util.UUID, context: SerializationContext): JsonNode =
    new JsonString(input.toString)

}
