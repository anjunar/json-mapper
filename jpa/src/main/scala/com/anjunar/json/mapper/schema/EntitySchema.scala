package com.anjunar.json.mapper.schema

import com.anjunar.json.mapper.schema.property.{ListProperty, SetProperty, SingularProperty}
import reflect.macros.PropertySupport
import jakarta.persistence.EntityManager
import jakarta.persistence.metamodel.{ListAttribute, SetAttribute, SingularAttribute}
import org.hibernate.metamodel.model.domain.PersistentAttribute
import org.hibernate.query.sqm.SqmPathSource

/**
 * A [[BeanSchema]] for a JPA entity. `reference`, `set` and `list` properties also act as metamodel attributes in
 * criteria queries. The EntityManager is only consulted when such an attribute is used.
 */
abstract class EntitySchema[T](manager: => EntityManager = null) extends BeanSchema[T] {

  def entityManager: EntityManager = manager

  protected inline def reference[V](
    inline selector: T => V,
    rule: Class[? <: VisibilityRule[T]] = classOf[DefaultRule[T]]
  ): SingularProperty[T, V] = {
    val propertyWithAccessor = PropertySupport.makeProperty(selector)

    def metamodel = entityManager.getMetamodel
    def entityType = metamodel.entity(ownerEntityType)
    def attribute =
      entityType.getSingularAttribute(propertyWithAccessor.descriptor.name).asInstanceOf[SingularAttribute[T, V] &
        PersistentAttribute[T, V] & SqmPathSource[V]]

    register(new SingularProperty[T, V](
      propertyWithAccessor.accessor,
      propertyWithAccessor.descriptor,
      rule,
      attribute
    ))
  }

  protected inline def set[V](
    inline selector: T => V,
    rule: Class[? <: VisibilityRule[T]] = classOf[DefaultRule[T]]
  ): SetProperty[T, V] = {
    val propertyWithAccessor = PropertySupport.makeProperty(selector)

    def metamodel = entityManager.getMetamodel
    def entityType = metamodel.entity(ownerEntityType)
    def attribute = entityType.getSet(propertyWithAccessor.descriptor.name).asInstanceOf[SetAttribute[T, V]]

    register(new SetProperty[T, V](propertyWithAccessor.accessor, propertyWithAccessor.descriptor, rule, attribute))
  }

  protected inline def list[V](
    inline selector: T => V,
    rule: Class[? <: VisibilityRule[T]] = classOf[DefaultRule[T]]
  ): ListProperty[T, V] = {
    val propertyWithAccessor = PropertySupport.makeProperty(selector)

    def metamodel = entityManager.getMetamodel
    def entityType = metamodel.entity(ownerEntityType)
    def attribute = entityType.getList(propertyWithAccessor.descriptor.name).asInstanceOf[ListAttribute[T, V]]

    register(new ListProperty[T, V](propertyWithAccessor.accessor, propertyWithAccessor.descriptor, rule, attribute))
  }

}
