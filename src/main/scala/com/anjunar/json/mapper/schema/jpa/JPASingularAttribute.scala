package com.anjunar.json.mapper.schema.jpa

import jakarta.persistence.metamodel.*
import org.hibernate.`type`.descriptor.java.JavaType
import org.hibernate.metamodel.AttributeClassification
import org.hibernate.metamodel.model.domain.*
import org.hibernate.query.sqm.{SqmBindableType, SqmPathSource}
import org.hibernate.query.sqm.tree.domain.{SqmDomainType, SqmPath}

import java.lang.reflect.Member

trait JPASingularAttribute[X,T](val collectionAttribute: SingularAttribute[X,T] & PersistentAttribute[X,T] & SqmPathSource[T]) extends SingularAttribute[X,T] with PersistentAttribute[X,T] with SqmPathSource[T] {

  override def isId: Boolean = collectionAttribute.isId

  override def isVersion: Boolean = collectionAttribute.isVersion

  override def isOptional: Boolean = collectionAttribute.isOptional

  override def getType: Type[T] = collectionAttribute.getType

  override def getName: String = collectionAttribute.getName

  override def getPersistentAttributeType: Attribute.PersistentAttributeType = collectionAttribute.getPersistentAttributeType

  override def getDeclaringType: ManagedDomainType[X] = collectionAttribute.getDeclaringType.asInstanceOf[ManagedDomainType[X]]

  override def getJavaType: Class[T] = collectionAttribute.getJavaType

  override def getJavaMember: Member = collectionAttribute.getJavaMember

  override def isAssociation: Boolean = collectionAttribute.isAssociation

  override def isCollection: Boolean = collectionAttribute.isCollection

  override def getBindableType: Bindable.BindableType = collectionAttribute.getBindableType

  override def getBindableJavaType: Class[T] = collectionAttribute.getBindableJavaType


  override def getAttributeJavaType: JavaType[T] = collectionAttribute.getAttributeJavaType

  override def getAttributeClassification: AttributeClassification = collectionAttribute.getAttributeClassification

  override def getValueGraphType: DomainType[?] = collectionAttribute.getValueGraphType

  override def getKeyGraphType: SimpleDomainType[?] = collectionAttribute.getKeyGraphType


  override def getPathType: SqmDomainType[T] = collectionAttribute.getPathType

  override def findSubPathSource(name: String): SqmPathSource[?] = collectionAttribute.findSubPathSource(name)

  override def findSubPathSource(name: String, includeSubtypes: Boolean): SqmPathSource[?] = collectionAttribute.findSubPathSource(name, includeSubtypes)

  override def getSubPathSource(name: String): SqmPathSource[?] = collectionAttribute.getSubPathSource(name)

  override def getSubPathSource(name: String, subtypes: Boolean): SqmPathSource[?] = collectionAttribute.getSubPathSource(name, subtypes)

  override def getIntermediatePathSource(pathSource: SqmPathSource[?]): SqmPathSource[?] = collectionAttribute.getIntermediatePathSource(pathSource)

  override def createSqmPath(lhs: SqmPath[?], intermediatePathSource: SqmPathSource[?]): SqmPath[T] = collectionAttribute.createSqmPath(lhs, intermediatePathSource)

  override def getExpressible: SqmBindableType[T] = collectionAttribute.getExpressible

  override def getSqmType: SqmDomainType[T] = collectionAttribute.getSqmType

  override def isGeneric: Boolean = collectionAttribute.isGeneric

  override def getPathName: String = collectionAttribute.getPathName

  override def getExpressibleJavaType: JavaType[T] = collectionAttribute.getExpressibleJavaType

  override def getRelationalJavaType: JavaType[?] = collectionAttribute.getRelationalJavaType

  override def getTypeName: String = collectionAttribute.getTypeName

  override def getNodeJavaType: JavaType[T] = collectionAttribute.getNodeJavaType
}

