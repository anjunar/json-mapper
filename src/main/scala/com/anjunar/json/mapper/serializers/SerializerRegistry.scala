package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.{JsonPatch, JsonProperties}
import com.anjunar.scala.universe.introspector.AnnotationProperty

import java.time.temporal.{Temporal, TemporalAmount}
import java.util

object SerializerRegistry {

  private val stringSerializer = new StringSerializer
  private val arraySerializer = new ArraySerializer
  private val booleanSerializer = new BooleanSerializer
  private val byteArraySerializer = new ByteArraySerializer
  private val enumSerializer = new EnumSerializer
  private val localeSerializer = new LocaleSerializer
  private val mapSerializer = new MapSerializer
  private val uuidSerializer = new UUIDSerializer
  private val numberSerializer = new NumberSerializer
  private val temporalAmountSerializer = new TemporalAmountSerializer
  private val temporalSerializer = new TemporalSerializer
  private val beanSerializer = new BeanSerializer
  private val jsonAnyPropertySerializer = new JsonAnyPropertySerializer
  private val jsonPatchSerializer = new JsonPatchSerializer

  def find[T](clazz: Class[T], instance: Any): Serializer[T] =
    (instance match {
      case _: JsonPatch[?]       => jsonPatchSerializer
      case _: String             => stringSerializer
      case _: util.Collection[?] => arraySerializer
      case _: Boolean            => booleanSerializer
      case _: Array[Byte]        => byteArraySerializer
      case _: Enum[?]            => enumSerializer
      case _: util.Locale        => localeSerializer
      case _: util.Map[?, ?]     => mapSerializer
      case _: util.UUID          => uuidSerializer
      case _: Number             => numberSerializer
      case _: TemporalAmount     => temporalAmountSerializer
      case _: Temporal           => temporalSerializer
      case _                     => beanSerializer
    }).asInstanceOf[Serializer[T]]

  def findPropertySerializer(property: AnnotationProperty, instance: Any): Serializer[Any] =
    if (JsonProperties.isAnyProperty(property)) {
      jsonAnyPropertySerializer.asInstanceOf[Serializer[Any]]
    } else {
      find(property.propertyType.raw.asInstanceOf[Class[Any]], instance)
    }

}
