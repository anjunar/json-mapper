package com.anjunar.json.mapper.schema


import com.anjunar.json.mapper.schema.property.{ListProperty, Property, SetProperty, SingularProperty}
import com.anjunar.scala.universe.TypeResolver
import reflect.{ClassDescriptor, ParameterizedTypeDescriptor, TypeDescriptor}
import reflect.macros.PropertySupport
import jakarta.persistence.EntityManager
import jakarta.persistence.metamodel.{ListAttribute, SetAttribute, SingularAttribute}
import org.hibernate.metamodel.model.domain.PersistentAttribute
import org.hibernate.query.sqm.SqmPathSource

import java.lang.reflect.{ParameterizedType, Type}
import scala.collection.mutable

abstract class EntitySchema[T](val entityManager: EntityManager = null) {

  val properties: mutable.Map[String, Property[T, Any]] = new mutable.LinkedHashMap[String, Property[T, Any]]()

  private lazy val ownerEntityType: Class[T] = resolveOwnerEntityType(getClass).asInstanceOf[Class[T]]

  def findProperty[V](name: String): Property[T, V] = properties(name).asInstanceOf[Property[T, V]]

  protected inline def property[V](inline selector: T => V,
                                   rule: Class[? <: VisibilityRule[T]] = classOf[DefaultRule[T]]): Property[T, V] = {
    val propertyWithAccessor = PropertySupport.makeProperty(selector)
    val value = new Property[T, V](propertyWithAccessor.accessor, propertyWithAccessor.descriptor, rule)
    properties.put(propertyWithAccessor.descriptor.name, value.asInstanceOf[Property[T, Any]])
    value
  }

  protected inline def reference[V](inline selector: T => V,
                                    rule: Class[? <: VisibilityRule[T]] = classOf[DefaultRule[T]]): SingularProperty[T, V] = {
    val propertyWithAccessor = PropertySupport.makeProperty(selector)

    val metamodel = entityManager.getMetamodel
    val entityType = metamodel.entity(ownerEntityType)
    val attribute = entityType.getSingularAttribute(propertyWithAccessor.descriptor.name).asInstanceOf[SingularAttribute[T, V] & PersistentAttribute[T, V] & SqmPathSource[V]]

    val value = new SingularProperty[T, V](propertyWithAccessor.accessor, propertyWithAccessor.descriptor, rule, attribute)
    properties.put(propertyWithAccessor.descriptor.name, value.asInstanceOf[Property[T, Any]])
    value
  }

  protected inline def set[V](inline selector: T => V,
                              rule: Class[? <: VisibilityRule[T]] = classOf[DefaultRule[T]]): SetProperty[T, V] = {
    val propertyWithAccessor = PropertySupport.makeProperty(selector)

    val metamodel = entityManager.getMetamodel
    val entityType = metamodel.entity(ownerEntityType)
    val attribute = entityType.getSet(propertyWithAccessor.descriptor.name).asInstanceOf[SetAttribute[T, V]]

    val value = new SetProperty[T, V](propertyWithAccessor.accessor, propertyWithAccessor.descriptor, rule, attribute)
    properties.put(propertyWithAccessor.descriptor.name, value.asInstanceOf[Property[T, Any]])
    value
  }

  protected inline def list[V](inline selector: T => V,
                               rule: Class[? <: VisibilityRule[T]] = classOf[DefaultRule[T]]): ListProperty[T, V] = {
    val propertyWithAccessor = PropertySupport.makeProperty(selector)

    val metamodel = entityManager.getMetamodel
    val entityType = metamodel.entity(ownerEntityType)
    val attribute = entityType.getList(propertyWithAccessor.descriptor.name).asInstanceOf[ListAttribute[T, V]]

    val value = new ListProperty[T, V](propertyWithAccessor.accessor, propertyWithAccessor.descriptor, rule, attribute)
    properties.put(propertyWithAccessor.descriptor.name, value.asInstanceOf[Property[T, Any]])
    value
  }

  private def extractRawType(typeDescriptor: TypeDescriptor): Class[?] = {
    typeDescriptor match {
      case cd: ClassDescriptor => Class.forName(cd.typeName)
      case pd: ParameterizedTypeDescriptor => Class.forName(pd.rawType.typeName)
      case _ => Class.forName(typeDescriptor.typeName)
    }
  }

  private def resolveOwnerEntityType(schemaClass: Class[?]): Class[?] = {
    @annotation.tailrec
    def loop(current: Class[?]): Class[?] = {
      if (current == null || current == classOf[Object]) {
        throw new IllegalStateException(s"Unable to resolve entity type for schema ${schemaClass.getName}")
      }

      extractOwnerType(current.getGenericSuperclass) match {
        case Some(value) => value
        case None => loop(current.getSuperclass)
      }
    }

    loop(schemaClass)
  }

  private def extractOwnerType(candidate: Type): Option[Class[?]] = candidate match {
    case parameterized: ParameterizedType =>
      val rawType = TypeResolver.rawType(parameterized)

      if (classOf[EntitySchema[?]].isAssignableFrom(rawType)) {
        parameterized.getActualTypeArguments.headOption.map(TypeResolver.rawType)
      } else {
        extractOwnerType(rawType.getGenericSuperclass)
      }

    case clazz: Class[?] =>
      Option(clazz.getGenericSuperclass).flatMap(extractOwnerType)

    case _ => None
  }

}
