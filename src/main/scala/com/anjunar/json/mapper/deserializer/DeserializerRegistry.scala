package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonBoolean, JsonNode, JsonNumber, JsonObject, JsonString}

import java.time.temporal.{Temporal, TemporalAmount}
import java.util.{Locale, UUID}

object DeserializerRegistry {

  def findDeserializer[T](clazz: Class[T], node: JsonNode): Deserializer[T] =
    (node match {
      case _: JsonNumber => new NumberDeserializer
      case _: JsonBoolean => new BooleanDeserializer
      case _: JsonArray => new ArrayDeserializer
      case _: JsonObject =>
        if (classOf[java.util.Map[?, ?]].isAssignableFrom(clazz)) {
          new MapDeserializer
        } else if (clazz == classOf[Object] || clazz == classOf[java.lang.Object]) {
          new BeanDeserializer
        } else {
          new BeanDeserializer
        }
      case _: JsonString =>
        if (clazz == classOf[Array[Byte]]) {
          new ByteArrayDeserializer
        } else if (classOf[Enum[?]].isAssignableFrom(clazz)) {
          new EnumDeserializer
        } else if (classOf[Locale].isAssignableFrom(clazz)) {
          new LocaleDeserializer
        } else if (classOf[String].isAssignableFrom(clazz)) {
          new StringDeserializer
        } else if (classOf[TemporalAmount].isAssignableFrom(clazz)) {
          new TemporalAmountDeserializer
        } else if (classOf[Temporal].isAssignableFrom(clazz)) {
          new TemporalDeserializer
        } else if (classOf[UUID].isAssignableFrom(clazz)) {
          new UUIDDeserializer
        } else if (clazz == classOf[Object] || clazz == classOf[java.lang.Object]) {
          new StringDeserializer
        } else {
          throw new IllegalArgumentException(s"Unsupported type: $clazz")
        }
      case _: JsonNode =>
        throw new IllegalArgumentException(s"Unsupported type: $clazz")
    }).asInstanceOf[Deserializer[T]]

}
