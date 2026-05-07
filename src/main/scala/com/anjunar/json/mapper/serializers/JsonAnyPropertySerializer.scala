package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.JavaContext
import com.anjunar.json.mapper.intermediate.model.JsonNode

class JsonAnyPropertySerializer extends Serializer[java.util.Map[String, ?]] {

  private val mapSerializer = new MapSerializer

  override def serialize(input: java.util.Map[String, ?], context: JavaContext): JsonNode =
    mapSerializer.serialize(input, context)

}
