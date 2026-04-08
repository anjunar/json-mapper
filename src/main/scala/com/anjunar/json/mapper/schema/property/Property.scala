package com.anjunar.json.mapper.schema.property

import com.anjunar.json.mapper.schema.{EntitySchema, Helper, Link, SchemaProvider, VisibilityRule}
import _root_.reflect.{ClassDescriptor, PropertyAccessor, PropertyDescriptor}
import com.anjunar.scala.universe.TypeResolver
import jakarta.json.bind.annotation.JsonbProperty

import java.util

class Property[T, V](
  val propertyAccessor: PropertyAccessor[T, V],
  val propertyDescriptor: PropertyDescriptor,
  val rule: Class[? <: VisibilityRule[T]]
) {

  val name: String = propertyDescriptor.name

  val isWriteable: Boolean = propertyDescriptor.isWriteable

  def get(instance: T): V = propertyAccessor.get(instance)

  def set(instance: T, value: V): Unit = propertyAccessor.set(instance, value)

  def hasAnnotation(annotationClass: String): Boolean =
    propertyDescriptor.hasAnnotation(annotationClass)

  def getAnnotation(annotationClass: String): Option[_root_.reflect.Annotation] =
    propertyDescriptor.getAnnotation(annotationClass)

  @JsonbProperty("type")
  val typeName: String = propertyDescriptor.propertyType.simpleName

  @JsonbProperty
  var schema: EntitySchema[?] =
    val value =
      try {
        Class.forName(propertyDescriptor.propertyType.typeName)
      } catch {
        case _: Throwable => null
      }

    if (value == null) {
      null
    } else {
      val companion = TypeResolver.companionInstance[SchemaProvider[?]](value)

      if (companion == null) {
        null
      } else {
        companion.schema
      }
    }

  @JsonbProperty("$links")
  val links: util.List[Link] = new util.ArrayList[Link]()

  def addLinks(value: Link*): Unit =
    value.filter(_ != null).foreach(link => links.add(link))

}
