package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.JavaContext
import com.anjunar.json.mapper.annotations.{JsonbAnyProperty, JsonbGraphProperty, UseConverter}
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonObject, JsonString}
import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.json.mapper.schema.{EntitySchema, SchemaProvider, VisibilityRule}
import com.anjunar.scala.universe.TypeResolver
import com.anjunar.scala.universe.introspector.{AbstractProperty, AnnotationIntrospector, AnnotationProperty}
import jakarta.json.bind.annotation.{JsonbProperty, JsonbSubtype}
import jakarta.persistence.{EntityGraph, Subgraph}
import java.lang.{Boolean as JavaBoolean}
import java.util

class BeanSerializer extends Serializer[Any] {

  override def serialize(input: Any, context: JavaContext): JsonNode = {
    val beanModel = AnnotationIntrospector.create(context.resolvedClass, classOf[JsonbProperty])

    val nodes = new util.LinkedHashMap[String, JsonNode]()
    val json = new JsonObject(nodes)

    val companion = TypeResolver.companionInstance[AnyRef](context.resolvedClass.raw)
    val schemaProvider =
      if (companion != null && classOf[SchemaProvider[?]].isInstance(companion)) {
        companion.asInstanceOf[SchemaProvider[EntitySchema[Any]]]
      } else {
        null
      }

    val properties = beanModel.properties
    var index = 0

    val ruleCache = new util.HashMap[Class[? <: VisibilityRule[?]], VisibilityRule[Any]]()

    while (index < properties.length) {
      val property = properties(index)
      val isAnyProperty = property.findAnnotation(classOf[JsonbAnyProperty]) != null

      val skipByGraph =
        !isAnyProperty &&
          property.name != "links" &&
          classOf[EntityProvider].isAssignableFrom(context.resolvedClass.raw) &&
          (!isJsonGraphProperty(property) && context.graph != null && !isSelectedByGraph(context, property))

      if (skipByGraph) {
        index += 1
      } else {
        if (schemaProvider != null) {
          val schemaProperty = schemaProvider.schema.properties.get(property.name).orNull

          if (schemaProperty == null) {
            index += 1
          } else {
            val visibilityRule =
              if (schemaProperty.rule == null) null
              else {
                var rule = ruleCache.get(schemaProperty.rule)
                if (rule == null) {
                  rule = context.inject(schemaProperty.rule).asInstanceOf[VisibilityRule[Any]]
                  ruleCache.put(schemaProperty.rule, rule)
                }
                rule
              }

            val visible = visibilityRule == null || visibilityRule.isVisible(input, property)

            if (!visible) {
              index += 1
            } else {
              if (isAnyProperty) {
                serializeAnyProperty(input, context, nodes, property)
              } else {
                serializeProperty(input, context, nodes, property)
              }
              index += 1
            }
          }
        } else {
          if (isAnyProperty) {
            serializeAnyProperty(input, context, nodes, property)
          } else {
            serializeProperty(input, context, nodes, property)
          }
          index += 1
        }
      }
    }
    if (!json.value.isEmpty) {
      val subtype = input.getClass.getAnnotation(classOf[JsonbSubtype])
      val typeProperty = if (subtype != null) {
        new JsonString(subtype.alias())
      } else {
        new JsonString(input.getClass.getSimpleName.replace("$HibernateProxy", ""))
      }
      json.value.putIfAbsent("@type", typeProperty)
    }
    json
  }

  private def serializeProperty(
    input: Any,
    context: JavaContext,
    nodes: util.LinkedHashMap[String, JsonNode],
    property: AnnotationProperty
  ): Unit = {
    val value =
      try {
        property.get(input.asInstanceOf[AnyRef])
      } catch {
        case _: Exception => null
      }

    value match {
      case booleanValue: JavaBoolean =>
        if (booleanValue.booleanValue()) {
          convertToJsonNode(property, nodes, booleanValue, context)
        }
      case booleanValue: Boolean =>
        if (booleanValue) {
          convertToJsonNode(property, nodes, Boolean.box(booleanValue), context)
        }
      case stringValue: String =>
        if (!stringValue.isEmpty) {
          convertToJsonNode(property, nodes, stringValue, context)
        }
      case collectionValue: util.Collection[?] =>
        if (!collectionValue.isEmpty) {
          convertToJsonNode(property, nodes, collectionValue, context)
        }
      case _ =>
        if (value != null) {
          convertToJsonNode(property, nodes, value, context)
        }
    }
  }

  private def serializeAnyProperty(
    input: Any,
    context: JavaContext,
    nodes: util.LinkedHashMap[String, JsonNode],
    property: AnnotationProperty
  ): Unit = {
    val value =
      try {
        property.get(input.asInstanceOf[AnyRef])
      } catch {
        case _: Exception => null
      }

    if (value == null) {
      return
    }

    val javaContext = new JavaContext(
      property.propertyType,
      context.graph,
      context.inject,
      context,
      property.name
    )

    val serializer = SerializerRegistry.findPropertySerializer(property, value)
    val jsonNode = serializer.serialize(value, javaContext)

    jsonNode match {
      case jsonObject: JsonObject =>
        val iterator = jsonObject.value.entrySet().iterator()
        while (iterator.hasNext) {
          val entry = iterator.next()
          nodes.putIfAbsent(entry.getKey, entry.getValue)
        }
      case _ =>
        throw new IllegalStateException(s"JsonAnyProperty '${property.name}' must serialize to an object")
    }
  }

  private def convertToJsonNode(
    property: AbstractProperty,
    nodes: util.LinkedHashMap[String, JsonNode],
    value: Any,
    context: JavaContext
  ): Unit = {
    val jsonbProperty = property.findAnnotation(classOf[JsonbProperty])
    if (jsonbProperty == null) {
      return
    }

    val name =
      if (jsonbProperty.value().isEmpty) property.name else jsonbProperty.value()

    val propertyType =
      if (
        classOf[util.Collection[?]].isAssignableFrom(property.propertyType.raw) ||
        classOf[util.Map[?, ?]].isAssignableFrom(property.propertyType.raw)
      ) {
        property.propertyType
      } else {
        TypeResolver.resolve(value.getClass)
      }

    val javaContext = new JavaContext(
      propertyType,
      context.graph,
      context.inject,
      context,
      property.name
    )

    val converterAnnotation = property.findAnnotation(classOf[UseConverter])

    val jsonNode =
      if (converterAnnotation == null) {
        val serializer = SerializerRegistry.find(
          property.propertyType.raw.asInstanceOf[Class[Any]],
          value
        ).asInstanceOf[Serializer[Any]]
        serializer.serialize(value, javaContext)
      } else {
        val converter = converterAnnotation.value().getDeclaredConstructor().newInstance()
        val toJson = converter.toJson(value, property.propertyType)
        val serializer =
          SerializerRegistry.find(classOf[String].asInstanceOf[Class[Any]], toJson).asInstanceOf[Serializer[Any]]
        serializer.serialize(toJson, javaContext)
      }

    jsonNode match {
      case value: JsonObject =>
        if (!value.value.isEmpty) {
          nodes.put(name, jsonNode)
        }
      case _ =>
        nodes.put(name, jsonNode)
    }
  }

  private val attributeNamesCache = new util.WeakHashMap[Any, util.Set[String]]()

  private def isJsonGraphProperty(property: AnnotationProperty): Boolean = {
    val graphProperty = property.findAnnotation(classOf[JsonbGraphProperty])
    if (graphProperty == null) {
      false
    } else {
      graphProperty.transitive()
    }
  }

  private def isSelectedByGraph(context: JavaContext, property: AnnotationProperty): Boolean = {
    val currentContainer = resolveContainer(context)

    if (currentContainer != null) {
      var names = attributeNamesCache.get(currentContainer)
      if (names == null) {
        val attributeNodes =
          currentContainer match {
            case value: EntityGraph[?] => value.getAttributeNodes
            case value: Subgraph[?]    => value.getAttributeNodes
            case _                     => util.Collections.emptyList()
          }
        names = new util.HashSet[String]()
        val iterator = attributeNodes.iterator()
        while (iterator.hasNext) {
          names.add(iterator.next().getAttributeName)
        }
        attributeNamesCache.put(currentContainer, names)
      }
      names.contains(property.name)
    } else {
      true
    }
  }

  private def resolveContainer(context: JavaContext): Any = {
    if (context.parent == null) {
      return context.graph
    }

    if (
      classOf[util.Collection[?]].isAssignableFrom(context.parent.resolvedClass.raw) ||
      context.parent.resolvedClass.raw.isArray
    ) {
      return resolveContainer(context.parent)
    }

    if (!classOf[EntityProvider].isAssignableFrom(context.parent.resolvedClass.raw)) {
      return context.graph
    }

    findSubgraph(context)
  }

  private val subgraphCache = new util.WeakHashMap[Any, util.Map[String, Subgraph[?]]]()

  private def findSubgraph(context: JavaContext): Subgraph[?] = {
    val parent = context.parent
    if (parent == null) {
      return null
    }

    val parentContainer = resolveContainer(parent)
    if (parentContainer == null) return null

    var subgraphsMap = subgraphCache.get(parentContainer)
    if (subgraphsMap == null) {
      val nodes =
        parentContainer match {
          case value: EntityGraph[?] => value.getAttributeNodes
          case value: Subgraph[?]    => value.getAttributeNodes
          case _                     => null
        }

      if (nodes == null) return null

      subgraphsMap = new util.HashMap[String, Subgraph[?]]()
      val iterator = nodes.iterator()
      while (iterator.hasNext) {
        val node = iterator.next()
        val subgraphs = node.getSubgraphs.values().iterator()
        if (subgraphs.hasNext) {
          subgraphsMap.put(node.getAttributeName, subgraphs.next())
        }
      }
      subgraphCache.put(parentContainer, subgraphsMap)
    }

    subgraphsMap.get(context.name)
  }

}
