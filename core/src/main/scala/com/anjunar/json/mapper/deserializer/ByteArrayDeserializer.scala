package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.DeserializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.util

class ByteArrayDeserializer extends Deserializer[Array[Byte]] {

  override def deserialize(json: JsonNode, context: DeserializationContext): Array[Byte] =
    json match {
      case value: JsonString =>
        try util.Base64.getDecoder.decode(value.value)
        catch { case _: IllegalArgumentException => context.reject("This field expects base64 data.") }
      case _ => context.reject("This field expects base64 data.")
    }

}
