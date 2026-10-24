package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.{JavaContext, JsonPatch}
import com.anjunar.json.mapper.intermediate.model.JsonNode

private[mapper] final class JsonPatchSerializer extends Serializer[JsonPatch[?]] {
  override def serialize(input: JsonPatch[?], context: JavaContext): JsonNode = input.value
}
