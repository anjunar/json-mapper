package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

class StringDeserializer extends Deserializer[String] {

  override def deserialize(json: JsonNode, context: JsonContext): String =
    json match {
      case value: JsonString => value.value
      case _ => throw new IllegalArgumentException("json must be a string")
    }

}
