package com.anjunar.json.mapper.schema.property

import com.anjunar.json.mapper.schema.VisibilityRule
import com.anjunar.json.mapper.schema.jpa.JPASingularAttribute
import _root_.reflect.{PropertyAccessor, PropertyDescriptor}
import jakarta.persistence.metamodel.SingularAttribute
import org.hibernate.metamodel.model.domain.PersistentAttribute
import org.hibernate.query.sqm.SqmPathSource

class SingularProperty[T,V](
  propertyAccessor: PropertyAccessor[T,V],
  propertyDescriptor: PropertyDescriptor,
  rule: Class[? <: VisibilityRule[T]],
  collectionAttribute: SingularAttribute[T,V] & PersistentAttribute[T,V] & SqmPathSource[V]
) extends Property[T,V](propertyAccessor, propertyDescriptor, rule), JPASingularAttribute[T,V](collectionAttribute)
