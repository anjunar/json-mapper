package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.SerializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonNull}

/** A null element of a collection or a null map value. Bean properties that are null are left out instead. */
class NullSerializer extends Serializer[Null] {

  override def serialize(input: Null, context: SerializationContext): JsonNode = new JsonNull

}
