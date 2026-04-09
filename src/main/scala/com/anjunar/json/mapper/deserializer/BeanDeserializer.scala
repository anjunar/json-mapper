package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.annotations.{JsonLdId, JsonLdProperty, UseConverter}
import com.anjunar.json.mapper.provider.{DTO, EntityProvider}
import com.anjunar.json.mapper.schema.{EntitySchema, SchemaProvider, VisibilityRule}
import com.anjunar.json.mapper.{JsonContext, ObjectMapperProvider}
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonNull, JsonObject, JsonString}
import com.anjunar.scala.universe.{ResolvedClass, TypeResolver}
import com.anjunar.scala.universe.introspector.{AnnotationIntrospector, AnnotationProperty}
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.persistence.{EntityGraph, ManyToMany, ManyToOne, OneToMany, OneToOne, Subgraph}

import java.lang.reflect.InvocationTargetException
import java.util.UUID

class BeanDeserializer extends Deserializer[Any] {

  override def deserialize(json: JsonNode, context: JsonContext): Any =
    json match {
      case jsonObject: JsonObject =>
        val beanModel = AnnotationIntrospector.create(context.resolvedClass, classOf[JsonbProperty])

        val companion = TypeResolver.companionInstance[AnyRef](context.resolvedClass.raw)
        val schemaProvider =
          if (companion != null && classOf[SchemaProvider[?]].isInstance(companion)) {
            companion.asInstanceOf[SchemaProvider[EntitySchema[Any]]]
          } else {
            null
          }

        val properties = beanModel.properties
        var index = 0
        while (index < properties.length) {
          val property = properties(index)

          if (property.name == "id" && shouldSkipIdProperty(jsonObject, property, context)) {
            index += 1
          } else if (context.instance != null && context.instance.isInstanceOf[EntityProvider] && context.instance.asInstanceOf[EntityProvider].version > -1L) {
            if (
              property.name != "links" &&
              classOf[EntityProvider].isAssignableFrom(context.resolvedClass.raw) &&
              context.graph != null &&
              !isSelectedByGraph(context, property)
            ) {
              index += 1
            } else {
              handleProperty(jsonObject, context, property, schemaProvider)
              index += 1
            }
          } else {
            handleProperty(jsonObject, context, property, schemaProvider)
            index += 1
          }
        }

        context.instance
      case _ =>
        throw new IllegalArgumentException("json must be a object")
    }

  private def shouldSkipIdProperty(
    json: JsonObject,
    property: AnnotationProperty,
    context: JsonContext
  ): Boolean = {
    if (!classOf[EntityProvider].isAssignableFrom(context.resolvedClass.raw)) {
      return false
    }

    val jsonLdId = property.findAnnotation(classOf[JsonLdId])
    if (jsonLdId != null && json.value.get("@id") != null) {
      return false
    }

    true
  }

  private def handleProperty(
    json: JsonObject,
    context: JsonContext,
    property: AnnotationProperty,
    schemaProvider: SchemaProvider[EntitySchema[Any]]
  ): Unit = {
    if (schemaProvider != null) {
      val schemaProperties = schemaProvider.schema.properties
      val schemaProperty = schemaProperties.get(property.name).orNull
      if (schemaProperty == null) {
        return
      }

      val visibilityRule =
        if (schemaProperty.rule == null) null
        else context.inject(schemaProperty.rule)

      if (visibilityRule != null && !visibilityRule.isWriteable(context.instance, property)) {
        return
      }
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
          if (exception.getCause != null && exception.getCause.getClass.getSimpleName != "UninitializedPropertyAccessException") {
            throw exception.getCause
          } else {
            null
          }
      }

    val jsonbProperty = property.findAnnotation(classOf[JsonbProperty])

    val node = resolveNode(json, property, jsonbProperty)

    if (classOf[DTO].isAssignableFrom(propertyType)) {
      handleEntityProperty(node, property, context, oldValue, propertyType)
    } else if (classOf[java.util.Collection[?]].isAssignableFrom(propertyType)) {
      handleCollectionProperty(node, property, context, oldValue)
    } else if (classOf[java.util.Map[?, ?]].isAssignableFrom(propertyType)) {
      handleMapProperty(node, property, context, oldValue)
    } else {
      handleNormalProperty(node, property, context, oldValue)
    }
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
        context.checkForViolations(context.instance.getClass, property.name, null, () => property.set(context.instance.asInstanceOf[AnyRef], null))
      }
      return
    }

    val instance = context.instance
    val useConverter = property.findAnnotation(classOf[UseConverter])

    if (useConverter == null) {
      val value = deserializeValue(property.propertyType, property.name, oldValue, context, node)
      context.checkForViolations(instance.getClass, property.name, value, () => property.set(instance.asInstanceOf[AnyRef], value))
    } else {
      val rawValue = deserializeValue(TypeResolver.resolve(classOf[String]), property.name, oldValue, context, node)
      if (!rawValue.isInstanceOf[String]) {
        throw new IllegalArgumentException("Converter only support string type")
      }

      val converter = useConverter.value().getDeclaredConstructor().newInstance()
      val convertedValue = converter.toJava(rawValue.asInstanceOf[String], property.propertyType)

      context.checkForViolations(instance.getClass, property.name, convertedValue, () => property.set(instance.asInstanceOf[AnyRef], convertedValue))
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

    val deserialized = deserializeValue(property.propertyType, property.name, existingCollection, context, node).asInstanceOf[java.util.Collection[Any]]
    val targetCollection = property.get(instance.asInstanceOf[AnyRef]).asInstanceOf[java.util.Collection[Any]]

    context.checkForViolations(instance.getClass, property.name, targetCollection, () => {
      targetCollection.clear()
      targetCollection.addAll(deserialized)
      synchronizeBidirectionalRelations(instance, property, targetCollection)
    })
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

    val deserialized = deserializeValue(property.propertyType, property.name, existingMap, context, node).asInstanceOf[java.util.Map[String, Any]]
    val targetMap = property.get(instance.asInstanceOf[AnyRef]).asInstanceOf[java.util.Map[String, Any]]

    context.checkForViolations(instance.getClass, property.name, targetMap, () => {
      targetMap.clear()
      targetMap.putAll(deserialized)
      synchronizeBidirectionalRelations(instance, property, targetMap)
    })
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
      context.checkForViolations(instance.getClass, property.name, null, () => property.set(instance.asInstanceOf[AnyRef], null))
      return
    }

    if (oldValue != null) {
      val value = deserializeValue(property.propertyType, property.name, oldValue, context, node)
      context.checkForViolations(instance.getClass, property.name, value, () => setPropertyAndSynchronize(instance, property, value))
      return
    }

    val jsonObject = node.asInstanceOf[JsonObject]
    val jsonId = resolveEntityReferenceId(jsonObject)

    if (jsonId != null && jsonId.isInstanceOf[JsonString]) {
      val id = extractUuid(jsonId.value.toString)
      val entity = context.loader.load(id, propertyType)
      if (entity != null) {
        context.checkForViolations(instance.getClass, property.name, entity, () => property.set(instance.asInstanceOf[AnyRef], entity))
        return
      }
    }

    val value = deserializeNewEntity(propertyType, property, context, node)
    context.checkForViolations(instance.getClass, property.name, value, () => setPropertyAndSynchronize(instance, property, value))
  }

  private def resolveNode(
    json: JsonObject,
    property: AnnotationProperty,
    jsonbProperty: JsonbProperty
  ): JsonNode = {
    val jsonLdProperty = property.findAnnotation(classOf[JsonLdProperty])
    val serializedName =
      if (jsonbProperty != null && jsonbProperty.value().nonEmpty) {
        jsonbProperty.value()
      } else {
        property.name
      }

    if (jsonLdProperty != null) {
      val jsonLdNode = json.value.get(jsonLdProperty.value())
      if (jsonLdNode != null) {
        return jsonLdNode
      }
    }

    if (property.findAnnotation(classOf[JsonLdId]) != null) {
      val jsonLdIdNode = json.value.get("@id")
      if (jsonLdIdNode != null) {
        return normalizeJsonLdIdNode(jsonLdIdNode, property.findAnnotation(classOf[JsonLdId]))
      }
    }

    json.value.get(serializedName)
  }

  private def normalizeJsonLdIdNode(node: JsonNode, annotation: JsonLdId): JsonNode =
    node match {
      case value: JsonString if annotation != null && annotation.prefix() != null && annotation.prefix().nonEmpty =>
        val raw = Option(value.value).map(_.toString).orNull
        if (raw != null && raw.startsWith(annotation.prefix())) {
          new JsonString(raw.substring(annotation.prefix().length))
        } else {
          value
        }
      case _ =>
        node
    }

  private def resolveEntityReferenceId(jsonObject: JsonObject): JsonNode = {
    val jsonLdId = jsonObject.value.get("@id")
    if (jsonLdId != null) {
      jsonLdId
    } else {
      jsonObject.value.get("id")
    }
  }

  private def extractUuid(raw: String): UUID = {
    val value = Option(raw).getOrElse("").trim
    val candidate =
      if (value.contains("/")) value.split('/').lastOption.getOrElse(value)
      else value

    UUID.fromString(candidate)
  }

  private def deserializeNewEntity(
    propertyType: Class[?],
    property: AnnotationProperty,
    context: JsonContext,
    node: JsonNode
  ): Any = {
    val newInstance = propertyType.getConstructor().newInstance()
    deserializeValue(property.propertyType, property.name, newInstance, context, node)
  }

  private def setPropertyAndSynchronize(owner: Any, property: AnnotationProperty, value: Any): Unit = {
    property.set(owner.asInstanceOf[AnyRef], value)
    synchronizeBidirectionalRelations(owner, property, value)
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
      synchronizeOneToMany(owner, value.asInstanceOf[java.lang.Iterable[?]], oneToMany)
      return
    }

    val manyToOne = property.findAnnotation(classOf[ManyToOne])
    if (manyToOne != null) {
      synchronizeManyToOne(owner, property, value)
      return
    }

    val manyToMany = property.findAnnotation(classOf[ManyToMany])
    if (manyToMany != null) {
      synchronizeManyToMany(owner, property, value.asInstanceOf[java.lang.Iterable[?]], manyToMany)
    }
  }

  private def synchronizeOneToOne(owner: Any, property: AnnotationProperty, value: Any, oneToOne: OneToOne): Unit = {
    val mappedBy = oneToOne.mappedBy()
    val otherModel = AnnotationIntrospector.createWithType(value.getClass, classOf[JsonbProperty])

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

  private def synchronizeOneToMany(owner: Any, values: java.lang.Iterable[?], oneToMany: OneToMany): Unit = {
    val mappedBy = oneToMany.mappedBy()
    if (mappedBy.isBlank) {
      return
    }

    val iterator = values.iterator()
    while (iterator.hasNext) {
      val element = iterator.next()
      if (element != null) {
        val elementModel = AnnotationIntrospector.createWithType(element.getClass, classOf[JsonbProperty])
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

  private def synchronizeManyToMany(owner: Any, property: AnnotationProperty, values: java.lang.Iterable[?], manyToMany: ManyToMany): Unit = {
    val mappedBy = manyToMany.mappedBy()
    val iterator = values.iterator()

    while (iterator.hasNext) {
      val element = iterator.next()
      if (element != null) {
        val otherModel = AnnotationIntrospector.createWithType(element.getClass, classOf[JsonbProperty])

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
    if (!classOf[java.util.Collection[?]].isAssignableFrom(property.propertyType.raw)) {
      return
    }

    val elementType = property.propertyType.typeArguments.headOption.map(_.raw).orNull
    if (elementType != null && !elementType.isAssignableFrom(value.getClass)) {
      return
    }

    val collection =
      try {
        property.get(instance.asInstanceOf[AnyRef]).asInstanceOf[java.util.Collection[Any]]
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

  private def resolveInverseOneToMany(target: Any, mappedBy: String, expectedElementType: Class[?]): AnnotationProperty = {
    val targetModel = AnnotationIntrospector.createWithType(target.getClass, classOf[OneToMany])
    targetModel.properties.find { candidate =>
      val annotation = candidate.findAnnotation(classOf[OneToMany])
      val elementType = candidate.propertyType.typeArguments.headOption.map(_.raw).orNull
      annotation != null &&
      annotation.mappedBy() == mappedBy &&
      (elementType == null || elementType.isAssignableFrom(expectedElementType))
    }.orNull
  }

  private def resolveInverseManyToMany(target: Any, mappedBy: String, expectedElementType: Class[?]): AnnotationProperty = {
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
    val jsonContext = new JsonContext(propertyType, existingInstance, context.graph, context.loader, context.validator, context.inject,context, name)
    deserializer.deserialize(node, jsonContext)
  }

  private def isSelectedByGraph(context: JsonContext, property: AnnotationProperty): Boolean = {
    val currentContainer = resolveContainer(context)

    if (currentContainer != null) {
      val attributeNodes =
        currentContainer match {
          case value: EntityGraph[?] => value.getAttributeNodes
          case value: Subgraph[?] => value.getAttributeNodes
          case _ => java.util.Collections.emptyList()
        }

      val iterator = attributeNodes.iterator()
      while (iterator.hasNext) {
        if (iterator.next().getAttributeName == property.name) {
          return true
        }
      }
      false
    } else {
      true
    }
  }

  private def resolveContainer(context: JsonContext): Any = {
    if (context.parent == null) {
      return context.graph
    }

    if (
      classOf[java.util.Collection[?]].isAssignableFrom(context.parent.resolvedClass.raw) ||
      context.parent.resolvedClass.raw.isArray
    ) {
      return resolveContainer(context.parent)
    }

    if (!classOf[EntityProvider].isAssignableFrom(context.parent.resolvedClass.raw)) {
      return context.graph
    }

    findSubgraph(context)
  }

  private def findSubgraph(context: JsonContext): Subgraph[?] = {
    val parent = context.parent
    if (parent == null) {
      return null
    }

    val parentContainer = resolveContainer(parent)
    val nodes =
      parentContainer match {
        case value: EntityGraph[?] => value.getAttributeNodes
        case value: Subgraph[?] => value.getAttributeNodes
        case _ => null
      }

    if (nodes == null) {
      return null
    }

    val iterator = nodes.iterator()
    while (iterator.hasNext) {
      val node = iterator.next()
      if (node.getAttributeName == context.name) {
        val subgraphs = node.getSubgraphs.values().iterator()
        if (subgraphs.hasNext) {
          return subgraphs.next()
        }
      }
    }

    null
  }

}
