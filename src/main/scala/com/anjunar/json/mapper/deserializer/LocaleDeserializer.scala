package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.util.Locale

class LocaleDeserializer extends Deserializer[Locale] {

  override def deserialize(json: JsonNode, context: JsonContext): Locale =
    json match {
      case value: JsonString => Locale.forLanguageTag(value.value)
      case _ => throw new IllegalArgumentException("json must be a string")
    }

}
