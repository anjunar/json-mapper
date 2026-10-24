package com.anjunar.json.mapper.schema

import com.anjunar.json.mapper.schema.property.Property
import com.anjunar.scala.universe.TypeResolver
import reflect.macros.PropertySupport

import java.lang.reflect.{ParameterizedType, Type}
import scala.collection.mutable

/**
 * The properties a class exposes and the rule that decides who may read or write each one. It needs no persistence:
 * DTOs use it directly, and `EntitySchema` in the json-mapper module adds the JPA-backed property kinds on top.
 */
abstract class BeanSchema[T] {

  val properties: mutable.Map[String, Property[T, Any]] = new mutable.LinkedHashMap[String, Property[T, Any]]()

  lazy val ownerEntityType: Class[T] = BeanSchema.ownerType(getClass).asInstanceOf[Class[T]]

  def findProperty[V](name: String): Property[T, V] = properties(name).asInstanceOf[Property[T, V]]

  /** Every registered property. */
  def all: Projection[T] = Projection(this, properties.values.toSeq)

  def fields(selected: Property[T, ?]*): Projection[T] = Projection(this, selected)

  /** The attributes of a named entity graph in declared order, plus transient presentation properties. */
  def graph(name: String, presentation: Property[T, ?]*): Projection[T] = {
    val selected = JsonSchemaDerivation.graphAttributes(ownerEntityType, name).map(attribute =>
      properties.getOrElse(
        attribute,
        throw IllegalArgumentException(s"Graph $name selects $attribute, which this schema does not register")
      )
    )
    Projection(this, selected ++ presentation, Some(name))
  }

  protected inline def property[V](
    inline selector: T => V,
    rule: Class[? <: VisibilityRule[T]] = classOf[DefaultRule[T]]
  ): Property[T, V] = {
    val propertyWithAccessor = PropertySupport.makeProperty(selector)
    register(new Property[T, V](propertyWithAccessor.accessor, propertyWithAccessor.descriptor, rule))
  }

  protected def register[P <: Property[T, ?]](property: P): P = {
    property.classLoader = getClass.getClassLoader
    properties.put(property.name, property.asInstanceOf[Property[T, Any]])
    property
  }

}

private object BeanSchema {

  /** The type argument a schema class passes to [[BeanSchema]], also through intermediate base classes. */
  def ownerType(schemaClass: Class[?]): Class[?] = {
    @annotation.tailrec
    def loop(current: Class[?]): Class[?] = {
      if (current == null || current == classOf[Object]) {
        throw new IllegalStateException(s"Unable to resolve entity type for schema ${schemaClass.getName}")
      }

      extractOwnerType(current.getGenericSuperclass) match {
        case Some(value) => value
        case None        => loop(current.getSuperclass)
      }
    }

    loop(schemaClass)
  }

  private def extractOwnerType(candidate: Type): Option[Class[?]] = candidate match {
    case parameterized: ParameterizedType =>
      val rawType = TypeResolver.rawType(parameterized)

      if (classOf[BeanSchema[?]].isAssignableFrom(rawType)) {
        parameterized.getActualTypeArguments.headOption.map(TypeResolver.rawType)
      } else {
        extractOwnerType(rawType.getGenericSuperclass)
      }

    case clazz: Class[?] =>
      Option(clazz.getGenericSuperclass).flatMap(extractOwnerType)

    case _ => None
  }

}
