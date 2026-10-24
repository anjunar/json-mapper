package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.SerializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.util

class LocaleSerializer extends Serializer[util.Locale] {

  override def serialize(input: util.Locale, context: SerializationContext): JsonNode =
    new JsonString(input.getDisplayLanguage)

}
