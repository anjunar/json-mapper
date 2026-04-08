package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.JavaContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.util.Base64

class ByteArraySerializer extends Serializer[Array[Byte]] {

  override def serialize(input: Array[Byte], context: JavaContext): JsonNode =
    new JsonString(Base64.getEncoder.encodeToString(input))

}
