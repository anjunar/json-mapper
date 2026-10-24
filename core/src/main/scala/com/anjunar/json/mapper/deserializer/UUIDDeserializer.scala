package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.DeserializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.util

class UUIDDeserializer extends Deserializer[util.UUID] {

  override def deserialize(json: JsonNode, context: DeserializationContext): util.UUID =
    json match {
      case value: JsonString =>
        try util.UUID.fromString(value.value)
        catch { case _: IllegalArgumentException => context.reject("This field expects a UUID.") }
      case _ => context.reject("This field expects a UUID.")
    }

}
