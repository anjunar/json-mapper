package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.JavaContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

class EnumSerializer extends Serializer[Enum[?]] {

  override def serialize(input: Enum[?], context: JavaContext): JsonNode =
    new JsonString(input.name())

}
