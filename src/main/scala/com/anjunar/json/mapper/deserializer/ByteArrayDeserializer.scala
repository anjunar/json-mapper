package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.util

class ByteArrayDeserializer extends Deserializer[Array[Byte]] {

  override def deserialize(json: JsonNode, context: JsonContext): Array[Byte] =
    json match {
      case value: JsonString => util.Base64.getDecoder.decode(value.value)
      case _                 => throw new IllegalArgumentException("json must be a string")
    }

}
