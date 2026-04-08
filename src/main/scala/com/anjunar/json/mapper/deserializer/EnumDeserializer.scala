package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.JsonNode

class EnumDeserializer extends Deserializer[Enum[?]] {

  override def deserialize(json: JsonNode, context: JsonContext): Enum[?] = {
    val enumConstants = context.resolvedClass.raw.getEnumConstants.asInstanceOf[Array[Enum[?]]]
    enumConstants.find(enumValue => enumValue.name() == json.value).getOrElse {
      throw new IllegalArgumentException("invalid enum value")
    }
  }

}
