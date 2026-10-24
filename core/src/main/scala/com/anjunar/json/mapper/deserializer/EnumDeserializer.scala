package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.DeserializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

class EnumDeserializer extends Deserializer[Enum[?]] {

  override def deserialize(json: JsonNode, context: DeserializationContext): Enum[?] =
    json match {
      case value: JsonString =>
        val enumConstants = context.resolvedClass.raw.getEnumConstants.asInstanceOf[Array[Enum[?]]]
        enumConstants
          .find(_.name() == value.value)
          .getOrElse(context.reject("This value is not one of the allowed options."))
      case _ => context.reject("This value is not one of the allowed options.")
    }

}
