package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.JavaContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.util.Locale

class LocaleSerializer extends Serializer[Locale] {

  override def serialize(input: Locale, context: JavaContext): JsonNode =
    new JsonString(input.getDisplayLanguage)

}
