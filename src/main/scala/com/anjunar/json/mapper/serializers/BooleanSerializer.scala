package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.JavaContext
import com.anjunar.json.mapper.intermediate.model.{JsonBoolean, JsonNode}

class BooleanSerializer extends Serializer[Boolean] {

  override def serialize(input: Boolean, context: JavaContext): JsonNode = new JsonBoolean(input)

}
