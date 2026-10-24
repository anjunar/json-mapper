package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.{EntityGraphs, ErrorRequest, ErrorRequestException, JsonContext, JsonProperties}
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonNull, JsonObject}
import com.anjunar.json.mapper.provider.{DTO, EntityProvider}
import com.anjunar.json.mapper.schema.EntitySchema
import com.anjunar.scala.universe.introspector.{AnnotationIntrospector, AnnotationProperty}
import com.anjunar.scala.universe.{ResolvedClass, TypeResolver}
import jakarta.persistence.{ManyToMany, ManyToOne, OneToMany, OneToOne}

import java.lang.reflect.InvocationTargetException
import java.util
import java.lang.Iterable

class BeanDeserializer extends Deserializer[Any] {

  override def deserialize(json: JsonNode, context: JsonContext): Any =
    json match {
      case jsonObject: JsonObject =>
        val properties = JsonProperties.model(context.resolvedClass).properties
        val schema = JsonProperties.schema(context.resolvedClass)
        val anyProperty = findJsonAnyProperty(properties)

        // Only a persisted entity is narrowed by the graph; a new object and a DTO take every property.
        val persisted = context.instance match {
          case entity: EntityProvider => entity.version > -1L
          case _                      => false
        }

        for (property <- properties if property.name != "id" && !JsonProperties.isAnyProperty(property)) {
          if (!(persisted && EntityGraphs.excludes(context, property))) {
            handleProperty(jsonObject, context, property, schema)
          }
        }

        if (anyProperty != null) {
          handleAnyProperty(jsonObject, context, anyProperty)
        }

        context.instance
      case _ =>
        throw new IllegalArgumentException("json must be a object")
    }

  /** Without an EntitySchema every property is writeable; with one, only registered properties whose rule agrees. */
  private def isWriteable(context: JsonContext, schema: EntitySchema[Any], property: AnnotationProperty): Boolean =
    schema == null || schema.properties.get(property.name).exists { registered =>
      registered.rule == null || context.run.rule(registered.rule).isWriteable(context.instance, property)
    }

  private def handleProperty(
    json: JsonObject,
    context: JsonContext,
    property: AnnotationProperty,
    schema: EntitySchema[Any]
  ): Unit = {
    if (!isWriteable(context, schema, property)) {
      return
    }

    val propertyType = property.propertyType.raw

    val oldValue =
      try {
        if (context.instance == null) {
          null
        } else {
          property.get(context.instance.asInstanceOf[AnyRef])
        }
      } catch {
        case exception: InvocationTargetException =>
          if (
            exception.getCause != null &&
            exception.getCause.getClass.getSimpleName != "UninitializedPropertyAccessException"
          ) {
            throw exception.getCause
          } else {
            null
          }
      }

    val node = json.value.get(JsonProperties.jsonName(property))

    if (JsonProperties.hasConverter(property)) {
      handleNormalProperty(node, property, context, oldValue)
    } else if (classOf[DTO].isAssignableFrom(propertyType) || classOf[EntityProvider].isAssignableFrom(propertyType)) {
      handleEntityProperty(node, property, context, oldValue, propertyType)
    } else if (classOf[util.Collection[?]].isAssignableFrom(propertyType)) {
      handleCollectionProperty(node, property, context, oldValue)
    } else if (classOf[util.Map[?, ?]].isAssignableFrom(propertyType)) {
      handleMapProperty(node, property, context, oldValue)
    } else {
      handleNormalProperty(node, property, context, oldValue)
    }
  }

  private def handleAnyProperty(json: JsonObject, context: JsonContext, property: AnnotationProperty): Unit = {
    val instance = context.instance
    val targetMap =
      try {
        property.get(instance.asInstanceOf[AnyRef]).asInstanceOf[util.Map[String, Any]]
      } catch {
        case _: Exception => null
      }

    if (targetMap == null) {
      throw new IllegalStateException(s"JsonAnyProperty '${property.name}' must be initialized")
    }

    val deserialized =
      deserializePropertyValue(property, property.name, targetMap, context, json).asInstanceOf[util.Map[String, Any]]

    context.checkForViolations(
      instance.getClass,
      property.name,
      deserialized,
      () => {
        targetMap.clear()
        targetMap.putAll(deserialized)
      }
    )
  }

  private def handleNormalProperty(
    node: JsonNode,
    property: AnnotationProperty,
    context: JsonContext,
    oldValue: Any
  ): Unit = {
    if (node == null) {
      return
    }

    if (node.isInstanceOf[JsonNull]) {
      if (context.instance != null) {
        context.checkForViolations(
          context.instance.getClass,
          property.name,
          null,
          () => property.set(context.instance.asInstanceOf[AnyRef], null)
        )
      }
      return
    }

    val instance = context.instance
    val converter = JsonProperties.converter(property)

    if (converter == null) {
      val value = deserializeValue(property.propertyType, property.name, oldValue, context, node)
      context.checkForViolations(
        instance.getClass,
        property.name,
        value,
        () => property.set(instance.asInstanceOf[AnyRef], value)
      )
    } else {
      val rawValue = deserializeValue(TypeResolver.resolve(classOf[String]), property.name, oldValue, context, node)
      if (!rawValue.isInstanceOf[String]) {
        // The client sees which field needs a string instead of an error without a location.
        val path = new util.ArrayList[Any](context.pathWithIndexes())
        path.add(property.name)
        throw new ErrorRequestException(
          util.List.of(new ErrorRequest(path, "This field is sent as a string."))
        )
      }

      val convertedValue = converter.toJava(rawValue.asInstanceOf[String], property.propertyType)

      context.checkForViolations(
        instance.getClass,
        property.name,
        convertedValue,
        () => property.set(instance.asInstanceOf[AnyRef], convertedValue)
      )
    }
  }

  private def handleCollectionProperty(
    node: JsonNode,
    property: AnnotationProperty,
    context: JsonContext,
    oldValue: Any
  ): Unit = {
    if (node == null) {
      return
    }

    val instance = context.instance
    val existingCollection =
      if (oldValue != null) {
        oldValue
      } else {
        throw new IllegalStateException("Collection property must be initialized")
      }

    val deserialized = deserializeValue(
      property.propertyType,
      property.name,
      existingCollection,
      context,
      node
    ).asInstanceOf[util.Collection[Any]]
    val targetCollection = property.get(instance.asInstanceOf[AnyRef]).asInstanceOf[util.Collection[Any]]

    context.checkForViolations(
      instance.getClass,
      property.name,
      deserialized,
      () => {
        targetCollection.clear()
        targetCollection.addAll(deserialized)
        synchronizeBidirectionalRelations(instance, property, targetCollection)
      }
    )
  }

  private def handleMapProperty(
    node: JsonNode,
    property: AnnotationProperty,
    context: JsonContext,
    oldValue: Any
  ): Unit = {
    if (node == null) {
      return
    }

    val instance = context.instance
    val existingMap =
      if (oldValue != null) {
        oldValue
      } else {
        throw new IllegalStateException("Collection property must be initialized")
      }

    val deserialized = deserializeValue(
      property.propertyType,
      property.name,
      existingMap,
      context,
      node
    ).asInstanceOf[util.Map[String, Any]]
    val targetMap = property.get(instance.asInstanceOf[AnyRef]).asInstanceOf[util.Map[String, Any]]

    context.checkForViolations(
      instance.getClass,
      property.name,
      deserialized,
      () => {
        targetMap.clear()
        targetMap.putAll(deserialized)
        synchronizeBidirectionalRelations(instance, property, targetMap)
      }
    )
  }

  private def handleEntityProperty(
    node: JsonNode,
    property: AnnotationProperty,
    context: JsonContext,
    oldValue: Any,
    propertyType: Class[?]
  ): Unit = {
    if (node == null) {
      return
    }

    val instance = context.instance

    if (node.isInstanceOf[JsonNull]) {
      context.checkForViolations(
        instance.getClass,
        property.name,
        null,
        () => setPropertyAndSynchronize(instance, property, oldValue, null)
      )
      return
    }

    val jsonObject = node match {
      case value: JsonObject => value
      case _ => throw new IllegalArgumentException(s"Relationship '${property.name}' must be an object or null")
    }
    val jsonId = jsonObject.value.get("id")
    val target =
      if (jsonId != null) {
        EntityReferences.load(jsonId, propertyType, context)
      } else if (oldValue != null) {
        oldValue
      } else {
        propertyType.getConstructor().newInstance()
      }

    val value = deserializeValue(property.propertyType, property.name, target, context, node)
    context.checkForViolations(
      instance.getClass,
      property.name,
      value,
      () => setPropertyAndSynchronize(instance, property, oldValue, value)
    )
  }

  private def setPropertyAndSynchronize(owner: Any, property: AnnotationProperty, previous: Any, value: Any): Unit = {
    property.set(owner.asInstanceOf[AnyRef], value)
    if (previous != null && (previous.asInstanceOf[AnyRef] ne value.asInstanceOf[AnyRef])) {
      disconnectToOne(owner, property, previous)
    }
    synchronizeBidirectionalRelations(owner, property, value)
  }

  private def disconnectToOne(owner: Any, property: AnnotationProperty, previous: Any): Unit = {
    val oneToOne = property.findAnnotation(classOf[OneToOne])
    if (oneToOne != null) {
      val otherSide =
        if (!oneToOne.mappedBy().isBlank) {
          JsonProperties.model(TypeResolver.resolve(previous.getClass)).findProperty(oneToOne.mappedBy())
        } else {
          resolveInverseOneToOne(previous, property.name, owner.getClass)
        }
      if (
        otherSide != null && otherSide.isWriteable &&
        (otherSide.get(previous.asInstanceOf[AnyRef]).asInstanceOf[AnyRef] eq owner.asInstanceOf[AnyRef])
      ) {
        otherSide.set(previous.asInstanceOf[AnyRef], null)
      }
    } else if (property.findAnnotation(classOf[ManyToOne]) != null) {
      val inverse = resolveInverseOneToMany(previous, property.name, owner.getClass)
      if (inverse != null) {
        val collection = inverse.get(previous.asInstanceOf[AnyRef]).asInstanceOf[util.Collection[Any]]
        if (collection != null) {
          collection.remove(owner)
        }
      }
    }
  }

  private def synchronizeBidirectionalRelations(owner: Any, property: AnnotationProperty, value: Any): Unit = {
    if (value == null) {
      return
    }

    val oneToOne = property.findAnnotation(classOf[OneToOne])
    if (oneToOne != null) {
      synchronizeOneToOne(owner, property, value, oneToOne)
      return
    }

    val oneToMany = property.findAnnotation(classOf[OneToMany])
    if (oneToMany != null) {
      synchronizeOneToMany(owner, value.asInstanceOf[Iterable[?]], oneToMany)
      return
    }

    val manyToOne = property.findAnnotation(classOf[ManyToOne])
    if (manyToOne != null) {
      synchronizeManyToOne(owner, property, value)
      return
    }

    val manyToMany = property.findAnnotation(classOf[ManyToMany])
    if (manyToMany != null) {
      synchronizeManyToMany(owner, property, value.asInstanceOf[Iterable[?]], manyToMany)
    }
  }

  private def synchronizeOneToOne(owner: Any, property: AnnotationProperty, value: Any, oneToOne: OneToOne): Unit = {
    val mappedBy = oneToOne.mappedBy()
    val otherModel = JsonProperties.model(TypeResolver.resolve(value.getClass))

    if (!mappedBy.isBlank) {
      val owningSide = otherModel.findProperty(mappedBy)
      if (owningSide != null) {
        setRelationIfNeeded(value, owningSide, owner)
      }
      return
    }

    val inverseSide = resolveInverseOneToOne(value, property.name, owner.getClass)
    if (inverseSide != null) {
      setRelationIfNeeded(value, inverseSide, owner)
    }
  }

  private def synchronizeOneToMany(owner: Any, values: Iterable[?], oneToMany: OneToMany): Unit = {
    val mappedBy = oneToMany.mappedBy()
    if (mappedBy.isBlank) {
      return
    }

    val iterator = values.iterator()
    while (iterator.hasNext) {
      val element = iterator.next()
      if (element != null) {
        val elementModel = JsonProperties.model(TypeResolver.resolve(element.getClass))
        val owningSide = elementModel.findProperty(mappedBy)
        if (owningSide != null) {
          setRelationIfNeeded(element, owningSide, owner)
        }
      }
    }
  }

  private def synchronizeManyToOne(owner: Any, property: AnnotationProperty, value: Any): Unit = {
    val inverseCollection = resolveInverseOneToMany(value, property.name, owner.getClass)
    if (inverseCollection != null) {
      addToCollectionIfNeeded(value, inverseCollection, owner)
    }
  }

  private def synchronizeManyToMany(
    owner: Any,
    property: AnnotationProperty,
    values: Iterable[?],
    manyToMany: ManyToMany
  ): Unit = {
    val mappedBy = manyToMany.mappedBy()
    val iterator = values.iterator()

    while (iterator.hasNext) {
      val element = iterator.next()
      if (element != null) {
        val otherModel = JsonProperties.model(TypeResolver.resolve(element.getClass))

        val otherSideProperty =
          if (!mappedBy.isBlank) {
            otherModel.findProperty(mappedBy)
          } else {
            resolveInverseManyToMany(element, property.name, owner.getClass)
          }

        if (otherSideProperty != null) {
          addToCollectionIfNeeded(element, otherSideProperty, owner)
        }
      }
    }
  }

  private def setRelationIfNeeded(instance: Any, property: AnnotationProperty, value: Any): Unit = {
    if (!property.isWriteable) {
      return
    }

    if (!property.propertyType.raw.isAssignableFrom(value.getClass)) {
      return
    }

    val existing =
      try {
        property.get(instance.asInstanceOf[AnyRef])
      } catch {
        case _: Exception => null
      }

    if (existing.asInstanceOf[AnyRef] ne value.asInstanceOf[AnyRef]) {
      property.set(instance.asInstanceOf[AnyRef], value)
    }
  }

  private def addToCollectionIfNeeded(instance: Any, property: AnnotationProperty, value: Any): Unit = {
    if (!classOf[util.Collection[?]].isAssignableFrom(property.propertyType.raw)) {
      return
    }

    val elementType = property.propertyType.typeArguments.headOption.map(_.raw).orNull
    if (elementType != null && !elementType.isAssignableFrom(value.getClass)) {
      return
    }

    val collection =
      try {
        property.get(instance.asInstanceOf[AnyRef]).asInstanceOf[util.Collection[Any]]
      } catch {
        case _: Exception => null
      }

    if (collection != null && !collection.contains(value)) {
      collection.add(value)
    }
  }

  private def resolveInverseOneToOne(target: Any, mappedBy: String, expectedType: Class[?]): AnnotationProperty = {
    val targetModel = AnnotationIntrospector.createWithType(target.getClass, classOf[OneToOne])
    targetModel.properties.find { candidate =>
      val annotation = candidate.findAnnotation(classOf[OneToOne])
      annotation != null &&
      annotation.mappedBy() == mappedBy &&
      candidate.propertyType.raw.isAssignableFrom(expectedType)
    }.orNull
  }

  private def resolveInverseOneToMany(
    target: Any,
    mappedBy: String,
    expectedElementType: Class[?]
  ): AnnotationProperty = {
    val targetModel = AnnotationIntrospector.createWithType(target.getClass, classOf[OneToMany])
    targetModel.properties.find { candidate =>
      val annotation = candidate.findAnnotation(classOf[OneToMany])
      val elementType = candidate.propertyType.typeArguments.headOption.map(_.raw).orNull
      annotation != null &&
      annotation.mappedBy() == mappedBy &&
      (elementType == null || elementType.isAssignableFrom(expectedElementType))
    }.orNull
  }

  private def resolveInverseManyToMany(
    target: Any,
    mappedBy: String,
    expectedElementType: Class[?]
  ): AnnotationProperty = {
    val targetModel = AnnotationIntrospector.createWithType(target.getClass, classOf[ManyToMany])
    targetModel.properties.find { candidate =>
      val annotation = candidate.findAnnotation(classOf[ManyToMany])
      val elementType = candidate.propertyType.typeArguments.headOption.map(_.raw).orNull
      annotation != null &&
      annotation.mappedBy() == mappedBy &&
      (elementType == null || elementType.isAssignableFrom(expectedElementType))
    }.orNull
  }

  private def deserializeValue(
    propertyType: ResolvedClass,
    name: String,
    existingInstance: Any,
    context: JsonContext,
    node: JsonNode
  ): Any = {
    val deserializer = DeserializerRegistry.findDeserializer(propertyType.raw.asInstanceOf[Class[Any]], node)
    val jsonContext = new JsonContext(
      propertyType,
      existingInstance,
      context.graph,
      context.loader,
      context.validator,
      context.inject,
      context,
      name
    )
    deserializer.deserialize(node, jsonContext)
  }

  private def deserializePropertyValue(
    property: AnnotationProperty,
    name: String,
    existingInstance: Any,
    context: JsonContext,
    node: JsonNode
  ): Any = {
    val deserializer = DeserializerRegistry.findPropertyDeserializer(property, node)
    val jsonContext = new JsonContext(
      property.propertyType,
      existingInstance,
      context.graph,
      context.loader,
      context.validator,
      context.inject,
      context,
      name
    )
    deserializer.deserialize(node, jsonContext)
  }

  private def findJsonAnyProperty(properties: Array[AnnotationProperty]): AnnotationProperty = {
    val matches = properties.filter(JsonProperties.isAnyProperty)
    if (matches.length > 1) {
      throw new IllegalStateException("Only one property can be annotated with @JsonAnyProperty")
    }
    matches.headOption.orNull
  }

}
