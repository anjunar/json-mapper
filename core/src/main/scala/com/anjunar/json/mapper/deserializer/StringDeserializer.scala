package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.DeserializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

class StringDeserializer extends Deserializer[String] {

  override def deserialize(json: JsonNode, context: DeserializationContext): String =
    json match {
      case value: JsonString => value.value
      case _                 => context.reject("This field expects a string.")
    }

}
