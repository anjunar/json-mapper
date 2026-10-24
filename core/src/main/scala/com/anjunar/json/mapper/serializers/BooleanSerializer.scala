package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.SerializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonBoolean, JsonNode}

class BooleanSerializer extends Serializer[Boolean] {

  override def serialize(input: Boolean, context: SerializationContext): JsonNode = new JsonBoolean(input)

}
