package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.SerializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

class EnumSerializer extends Serializer[Enum[?]] {

  override def serialize(input: Enum[?], context: SerializationContext): JsonNode =
    new JsonString(input.name())

}
