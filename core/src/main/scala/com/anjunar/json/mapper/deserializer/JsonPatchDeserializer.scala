package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.{DeserializationContext, JsonPatch}
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonObject}

private[mapper] final class JsonPatchDeserializer extends Deserializer[JsonPatch[?]] {
  override def deserialize(json: JsonNode, context: DeserializationContext): JsonPatch[?] = json match {
    case value: JsonObject => new JsonPatch[Any](value)
    case _                 => throw IllegalArgumentException("An entity patch must be an object")
  }
}
