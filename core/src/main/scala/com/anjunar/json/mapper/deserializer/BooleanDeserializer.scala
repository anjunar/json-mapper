package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.DeserializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonBoolean, JsonNode}

class BooleanDeserializer extends Deserializer[Boolean] {

  override def deserialize(json: JsonNode, context: DeserializationContext): Boolean =
    json match {
      case value: JsonBoolean => value.value
      case _                  => context.reject("This field expects true or false.")
    }

}
