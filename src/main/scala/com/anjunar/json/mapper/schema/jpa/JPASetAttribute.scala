package com.anjunar.json.mapper.schema.jpa

import jakarta.persistence.metamodel.*

import java.lang.reflect.Member
import java.util
import java.util.concurrent.CompletionStage

trait JPASetAttribute[X,E](val collectionAttribute: SetAttribute[X,E]) extends SetAttribute[X,E] {

  override def getCollectionType: PluralAttribute.CollectionType = collectionAttribute.getCollectionType

  override def getElementType: Type[E] = collectionAttribute.getElementType

  override def getName: String = collectionAttribute.getName

  override def getPersistentAttributeType: Attribute.PersistentAttributeType = collectionAttribute.getPersistentAttributeType

  override def getDeclaringType: ManagedType[X] = collectionAttribute.getDeclaringType

  override def getJavaType: Class[util.Set[E]] = collectionAttribute.getJavaType

  override def getJavaMember: Member = collectionAttribute.getJavaMember

  override def isAssociation: Boolean = collectionAttribute.isAssociation

  override def isCollection: Boolean = collectionAttribute.isCollection

  override def getBindableType: Bindable.BindableType = collectionAttribute.getBindableType

  override def getBindableJavaType: Class[E] = collectionAttribute.getBindableJavaType
}
