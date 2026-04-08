package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.JavaContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.util.UUID

class UUIDSerializer extends Serializer[UUID] {

  override def serialize(input: UUID, context: JavaContext): JsonNode =
    new JsonString(input.toString)

}
