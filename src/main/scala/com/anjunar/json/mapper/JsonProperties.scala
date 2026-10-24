package com.anjunar.json.mapper

import com.anjunar.json.mapper.annotations.{JsonbAnyProperty, UseConverter}
import com.anjunar.json.mapper.converter.Converter
import com.anjunar.json.mapper.schema.{EntitySchema, SchemaProvider}
import com.anjunar.scala.universe.{ResolvedClass, TypeResolver}
import com.anjunar.scala.universe.introspector.{AbstractProperty, AnnotationIntrospector, AnnotationModel}
import jakarta.json.bind.annotation.JsonbProperty

/** How the mapper reads its own annotations: which properties take part, under which name, and with which converter. */
object JsonProperties {

  /** The properties of a class that carry `@JsonbProperty`, inherited ones included. */
  def model(resolvedClass: ResolvedClass): AnnotationModel =
    AnnotationIntrospector.create(resolvedClass, classOf[JsonbProperty])

  /** The JSON member name: the alias given to `@JsonbProperty`, otherwise the property name. */
  def jsonName(property: AbstractProperty): String = {
    val annotation = property.findAnnotation(classOf[JsonbProperty])
    if (annotation == null || annotation.value().isEmpty) property.name else annotation.value()
  }

  def isAnyProperty(property: AbstractProperty): Boolean =
    property.findAnnotation(classOf[JsonbAnyProperty]) != null

  /** Whether `@UseConverter` gives the property its own wire form instead of the mapping by type. */
  def hasConverter(property: AbstractProperty): Boolean =
    property.findAnnotation(classOf[UseConverter]) != null

  /** A new instance of the converter named by `@UseConverter`, or null when the property is mapped by type. */
  def converter(property: AbstractProperty): Converter = {
    val annotation = property.findAnnotation(classOf[UseConverter])
    if (annotation == null) null else annotation.value().getDeclaredConstructor().newInstance()
  }

  /** The EntitySchema of a class, when its companion object provides one. */
  def schema(resolvedClass: ResolvedClass): EntitySchema[Any] =
    TypeResolver.companionInstance[AnyRef](resolvedClass.raw) match {
      case provider: SchemaProvider[?] => provider.schema.asInstanceOf[EntitySchema[Any]]
      case _                           => null
    }

}
