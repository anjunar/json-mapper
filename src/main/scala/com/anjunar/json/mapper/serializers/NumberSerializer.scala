package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.JavaContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonNumber}

class NumberSerializer extends Serializer[Number] {

  override def serialize(input: Number, context: JavaContext): JsonNode =
    new JsonNumber(input.toString)

}
