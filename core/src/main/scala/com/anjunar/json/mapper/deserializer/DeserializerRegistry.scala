package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonPatch
import com.anjunar.json.mapper.annotations.JsonbAnyProperty
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonBoolean, JsonNode, JsonNull, JsonNumber, JsonObject, JsonString}
import com.anjunar.scala.universe.introspector.AnnotationProperty

import java.lang.{Boolean as JavaBoolean}
import java.time.temporal.{Temporal, TemporalAmount}
import java.util

/**
 * Picks the deserializer for the declared type of a field. Only untyped fields (`Object`) are read by the kind of JSON
 * node. A node that does not fit the declared type reaches the deserializer for that type, which reports it as an
 * invalid value of the field.
 */
object DeserializerRegistry {

  private val jsonAnyPropertyDeserializer = new JsonAnyPropertyDeserializer
  private val nullDeserializer = new NullDeserializer
  private val stringDeserializer = new StringDeserializer
  private val booleanDeserializer = new BooleanDeserializer
  private val numberDeserializer = new NumberDeserializer
  private val byteArrayDeserializer = new ByteArrayDeserializer
  private val enumDeserializer = new EnumDeserializer
  private val localeDeserializer = new LocaleDeserializer
  private val uuidDeserializer = new UUIDDeserializer
  private val temporalAmountDeserializer = new TemporalAmountDeserializer
  private val temporalDeserializer = new TemporalDeserializer
  private val arrayDeserializer = new ArrayDeserializer
  private val mapDeserializer = new MapDeserializer
  private val beanDeserializer = new BeanDeserializer
  private val jsonPatchDeserializer = new JsonPatchDeserializer

  def findDeserializer[T](clazz: Class[T], node: JsonNode): Deserializer[T] =
    (node match {
      case _: JsonNull                                    => nullDeserializer
      case _ if clazz == classOf[Object] || clazz == null => untyped(node)
      case _                                              => typed(clazz)
    }).asInstanceOf[Deserializer[T]]

  def findPropertyDeserializer(property: AnnotationProperty, node: JsonNode): Deserializer[Any] =
    if (property.findAnnotation(classOf[JsonbAnyProperty]) != null) {
      jsonAnyPropertyDeserializer.asInstanceOf[Deserializer[Any]]
    } else {
      findDeserializer(property.propertyType.raw.asInstanceOf[Class[Any]], node)
    }

  /** Whether values of this declared type are beans, read property by property. */
  def readsProperties(clazz: Class[?]): Boolean =
    clazz != classOf[Object] && typed(clazz).isInstanceOf[BeanDeserializer]

  private def typed(clazz: Class[?]): Deserializer[?] =
    if (clazz == classOf[JsonPatch[?]]) jsonPatchDeserializer
    else if (clazz == classOf[String]) stringDeserializer
    else if (clazz == classOf[Boolean] || clazz == classOf[JavaBoolean]) booleanDeserializer
    else if (NumberDeserializer.supports(clazz)) numberDeserializer
    else if (clazz == classOf[Array[Byte]]) byteArrayDeserializer
    else if (clazz.isEnum) enumDeserializer
    else if (clazz == classOf[util.Locale]) localeDeserializer
    else if (clazz == classOf[util.UUID]) uuidDeserializer
    else if (classOf[TemporalAmount].isAssignableFrom(clazz)) temporalAmountDeserializer
    else if (classOf[Temporal].isAssignableFrom(clazz)) temporalDeserializer
    else if (classOf[util.Map[?, ?]].isAssignableFrom(clazz)) mapDeserializer
    else if (classOf[util.Collection[?]].isAssignableFrom(clazz)) arrayDeserializer
    else beanDeserializer

  /** An untyped field takes the natural Java form of whatever node it receives. */
  private def untyped(node: JsonNode): Deserializer[?] =
    node match {
      case _: JsonString  => stringDeserializer
      case _: JsonNumber  => numberDeserializer
      case _: JsonBoolean => booleanDeserializer
      case _: JsonArray   => arrayDeserializer
      case _: JsonObject  => mapDeserializer
      case _              => throw new IllegalStateException(s"Unknown JSON node: $node")
    }

}
