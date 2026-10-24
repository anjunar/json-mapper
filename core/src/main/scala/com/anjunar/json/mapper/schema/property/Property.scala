package com.anjunar.json.mapper.schema.property

import com.anjunar.json.mapper.schema.{BeanSchema, Link, SchemaProvider, VisibilityRule}
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

  // Resolved on first access: two schemas whose properties reference each other would otherwise initialize each other
  // recursively while they are being built, and block forever.
  @volatile private var resolvedSchema: BeanSchema[?] = null
  @volatile private var schemaResolved = false

  /** Loads the referenced type; set by the owning schema so application classes are found in their own loader. */
  @volatile private[schema] var classLoader: ClassLoader = getClass.getClassLoader

  /** The schema of the property's type, if that type's companion provides one. */
  @JsonbProperty
  def schema: BeanSchema[?] = {
    if (!schemaResolved) {
      resolvedSchema = Property.schemaOf(propertyDescriptor.propertyType.typeName, classLoader)
      schemaResolved = true
    }
    resolvedSchema
  }

  def schema_=(value: BeanSchema[?]): Unit = {
    resolvedSchema = value
    schemaResolved = true
  }

  @JsonbProperty("$links")
  val links: util.List[Link] = new util.ArrayList[Link]()

  def addLinks(value: Link*): Unit =
    value.filter(_ != null).foreach(link => links.add(link))

}

object Property {

  private def schemaOf(typeName: String, loader: ClassLoader): BeanSchema[?] = {
    val value =
      try Class.forName(typeName, false, loader)
      catch {
        // Parameterized and primitive type names name no class of their own; such properties have no schema.
        case _: ClassNotFoundException => null
      }
    if (value == null) {
      null
    } else {
      TypeResolver.companionInstance[AnyRef](value) match {
        case provider: SchemaProvider[?] => provider.schema
        case _                           => null
      }
    }
  }

}
