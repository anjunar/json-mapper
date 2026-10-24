package com.anjunar.json.mapper.schema.property

import com.anjunar.json.mapper.schema.{EntitySchema, Link, SchemaProvider, VisibilityRule}
import _root_.reflect.{ParameterizedTypeDescriptor, PropertyAccessor, PropertyDescriptor, TypeDescriptor}
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

  /**
   * The EntitySchema of the property's type, when its companion object provides one. Resolved on first use, so that
   * declaring a schema never instantiates the schemas of the types it refers to.
   */
  @JsonbProperty
  lazy val schema: EntitySchema[?] =
    Property.rawClass(propertyDescriptor.propertyType) match {
      case null          => null
      case propertyClass =>
        TypeResolver.companionInstance[AnyRef](propertyClass) match {
          case provider: SchemaProvider[?] => provider.schema
          case _                           => null
        }
    }

  @JsonbProperty("$links")
  val links: util.List[Link] = new util.ArrayList[Link]()

  def addLinks(value: Link*): Unit =
    value.filter(_ != null).foreach(link => links.add(link))

}

object Property {

  /** The runtime class a compile-time type descriptor names, or null for primitives and other non-classes. */
  private def rawClass(descriptor: TypeDescriptor): Class[?] = {
    val className = descriptor match {
      case parameterized: ParameterizedTypeDescriptor => parameterized.rawType.typeName
      case other                                      => other.typeName
    }
    try {
      Class.forName(className)
    } catch {
      case _: ClassNotFoundException => null
    }
  }

}
