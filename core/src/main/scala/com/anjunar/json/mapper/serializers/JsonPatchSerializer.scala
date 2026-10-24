package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.{SerializationContext, JsonPatch}
import com.anjunar.json.mapper.intermediate.model.JsonNode

private[mapper] final class JsonPatchSerializer extends Serializer[JsonPatch[?]] {
  override def serialize(input: JsonPatch[?], context: SerializationContext): JsonNode = input.value
}
