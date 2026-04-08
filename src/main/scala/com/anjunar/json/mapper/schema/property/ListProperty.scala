package com.anjunar.json.mapper.schema.property

import com.anjunar.json.mapper.schema.VisibilityRule
import com.anjunar.json.mapper.schema.jpa.JPAListAttribute
import _root_.reflect.{PropertyAccessor, PropertyDescriptor}
import jakarta.persistence.metamodel.ListAttribute

class ListProperty[T,V](
  propertyAccessor: PropertyAccessor[T,V],
  propertyDescriptor: PropertyDescriptor,
  rule: Class[? <: VisibilityRule[T]],
  collectionAttribute: ListAttribute[T,V]
) extends Property[T,V](propertyAccessor, propertyDescriptor, rule), JPAListAttribute[T,V](collectionAttribute)
