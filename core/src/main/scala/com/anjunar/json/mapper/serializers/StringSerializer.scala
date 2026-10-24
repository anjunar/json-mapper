package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.JavaContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

class StringSerializer extends Serializer[String] {

  override def serialize(input: String, context: JavaContext): JsonNode = new JsonString(input)

}
