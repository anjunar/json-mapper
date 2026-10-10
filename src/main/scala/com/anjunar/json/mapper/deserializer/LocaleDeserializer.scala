package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.util

class LocaleDeserializer extends Deserializer[util.Locale] {

  override def deserialize(json: JsonNode, context: JsonContext): util.Locale =
    json match {
      case value: JsonString => util.Locale.forLanguageTag(value.value)
      case _                 => throw new IllegalArgumentException("json must be a string")
    }

}
