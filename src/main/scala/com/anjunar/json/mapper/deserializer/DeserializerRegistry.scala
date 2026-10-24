package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.{JsonPatch, JsonProperties}
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonBoolean, JsonNode, JsonNull, JsonNumber, JsonObject, JsonString}
import com.anjunar.scala.universe.introspector.AnnotationProperty

import java.time.temporal.{Temporal, TemporalAmount}
import java.util
import java.lang.{Object as LangObject}

object DeserializerRegistry {

  private val arrayDeserializer = new ArrayDeserializer
  private val beanDeserializer = new BeanDeserializer
  private val booleanDeserializer = new BooleanDeserializer
  private val byteArrayDeserializer = new ByteArrayDeserializer
  private val enumDeserializer = new EnumDeserializer
  private val jsonAnyPropertyDeserializer = new JsonAnyPropertyDeserializer
  private val jsonPatchDeserializer = new JsonPatchDeserializer
  private val localeDeserializer = new LocaleDeserializer
  private val mapDeserializer = new MapDeserializer
  private val nullDeserializer = new NullDeserializer
  private val numberDeserializer = new NumberDeserializer
  private val stringDeserializer = new StringDeserializer
  private val temporalAmountDeserializer = new TemporalAmountDeserializer
  private val temporalDeserializer = new TemporalDeserializer
  private val uuidDeserializer = new UUIDDeserializer

  /** The deserializer follows the JSON node first and the declared type second, so that `null` always applies. */
  def findDeserializer[T](clazz: Class[T], node: JsonNode): Deserializer[T] =
    (if (clazz == classOf[JsonPatch[?]] && !node.isInstanceOf[JsonNull]) jsonPatchDeserializer
     else node match {
       case _: JsonNumber  => numberDeserializer
       case _: JsonBoolean => booleanDeserializer
       case _: JsonArray   => arrayDeserializer
       case _: JsonObject  =>
         if (
           clazz == classOf[Object] ||
           clazz == classOf[LangObject] ||
           classOf[util.Map[?, ?]].isAssignableFrom(clazz)
         ) {
           mapDeserializer
         } else {
           beanDeserializer
         }
       case _: JsonString =>
         if (clazz == classOf[Array[Byte]]) {
           byteArrayDeserializer
         } else if (classOf[Enum[?]].isAssignableFrom(clazz)) {
           enumDeserializer
         } else if (classOf[util.Locale].isAssignableFrom(clazz)) {
           localeDeserializer
         } else if (classOf[String].isAssignableFrom(clazz)) {
           stringDeserializer
         } else if (classOf[TemporalAmount].isAssignableFrom(clazz)) {
           temporalAmountDeserializer
         } else if (classOf[Temporal].isAssignableFrom(clazz)) {
           temporalDeserializer
         } else if (classOf[util.UUID].isAssignableFrom(clazz)) {
           uuidDeserializer
         } else if (NumberDeserializer.decimal(clazz)) {
           numberDeserializer
         } else if (clazz == classOf[Object] || clazz == classOf[LangObject]) {
           stringDeserializer
         } else {
           throw new IllegalArgumentException(s"Unsupported type: $clazz")
         }
       case _: JsonNull =>
         nullDeserializer
       case _: JsonNode =>
         throw new IllegalArgumentException(s"Unsupported type: $clazz")
     }).asInstanceOf[Deserializer[T]]

  def findPropertyDeserializer(property: AnnotationProperty, node: JsonNode): Deserializer[Any] =
    if (JsonProperties.isAnyProperty(property)) {
      jsonAnyPropertyDeserializer.asInstanceOf[Deserializer[Any]]
    } else {
      findDeserializer(property.propertyType.raw.asInstanceOf[Class[Any]], node)
    }

}
