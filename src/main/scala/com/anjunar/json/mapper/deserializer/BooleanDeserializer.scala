package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonBoolean, JsonNode}

class BooleanDeserializer extends Deserializer[Boolean] {

  override def deserialize(json: JsonNode, context: JsonContext): Boolean =
    json match {
      case value: JsonBoolean => value.value
      case _ => throw new IllegalArgumentException("json must be a boolean")
    }

}
