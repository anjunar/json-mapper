package com.anjunar.json.mapper.schema.property

import com.anjunar.json.mapper.schema.VisibilityRule
import com.anjunar.json.mapper.schema.jpa.JPASetAttribute
import _root_.reflect.{PropertyAccessor, PropertyDescriptor}
import jakarta.persistence.metamodel.SetAttribute

class SetProperty[T,V](
  propertyAccessor: PropertyAccessor[T,V],
  propertyDescriptor: PropertyDescriptor,
  rule: Class[? <: VisibilityRule[T]],
  collectionAttribute: SetAttribute[T,V]
) extends Property[T,V](propertyAccessor, propertyDescriptor, rule), JPASetAttribute[T,V](collectionAttribute)
