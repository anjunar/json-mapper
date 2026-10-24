package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.DeserializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.util

class LocaleDeserializer extends Deserializer[util.Locale] {

  override def deserialize(json: JsonNode, context: DeserializationContext): util.Locale =
    json match {
      case value: JsonString => util.Locale.forLanguageTag(value.value)
      case _                 => context.reject("This field expects a language tag.")
    }

}
