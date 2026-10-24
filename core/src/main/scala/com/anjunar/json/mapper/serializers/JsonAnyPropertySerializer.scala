package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.SerializationContext
import com.anjunar.json.mapper.intermediate.model.JsonNode
import java.util

class JsonAnyPropertySerializer extends Serializer[util.Map[String, ?]] {

  private val mapSerializer = new MapSerializer

  override def serialize(input: util.Map[String, ?], context: SerializationContext): JsonNode =
    mapSerializer.serialize(input, context)

}
