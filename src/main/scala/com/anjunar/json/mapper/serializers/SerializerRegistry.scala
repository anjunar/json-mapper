package com.anjunar.json.mapper.serializers

import java.time.temporal.{Temporal, TemporalAmount}
import java.util.{Locale, UUID}

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

  def find[T](clazz: Class[T], instance: Any): Serializer[T] =
    (instance match {
      case _: String => stringSerializer
      case _: java.util.Collection[?] => arraySerializer
      case _: Boolean => booleanSerializer
      case _: Array[Byte] => byteArraySerializer
      case _: Enum[?] => enumSerializer
      case _: Locale => localeSerializer
      case _: java.util.Map[?, ?] => mapSerializer
      case _: UUID => uuidSerializer
      case _: Number => numberSerializer
      case _: TemporalAmount => temporalAmountSerializer
      case _: Temporal => temporalSerializer
      case _ => beanSerializer
    }).asInstanceOf[Serializer[T]]

}
