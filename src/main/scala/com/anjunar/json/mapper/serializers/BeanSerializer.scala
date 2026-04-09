package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.annotations.{JsonLdId, JsonLdProperty, JsonLdResource, JsonLdType, JsonLdVocab, UseConverter}
import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.json.mapper.schema.{EntitySchema, SchemaProvider, VisibilityRule}
import com.anjunar.json.mapper.{JavaContext, ObjectMapperProvider}
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonObject, JsonString}
import com.anjunar.scala.universe.TypeResolver
import com.anjunar.scala.universe.introspector.{AbstractProperty, AnnotationIntrospector, AnnotationProperty}
import com.typesafe.scalalogging.Logger
import jakarta.json.bind.annotation.{JsonbProperty, JsonbSubtype}
import jakarta.persistence.{Entity, EntityGraph, Subgraph}

class BeanSerializer extends Serializer[Any] {

  private val log = Logger(classOf[BeanSerializer])

  override def serialize(input: Any, context: JavaContext): JsonNode = {
    val start = System.nanoTime()
    
    val introspectStart = System.nanoTime()
    val beanModel = AnnotationIntrospector.create(context.resolvedClass, classOf[JsonbProperty])
    val introspectEnd = System.nanoTime()
    
    val nodes = new java.util.LinkedHashMap[String, JsonNode]()
    val json = new JsonObject(nodes)

    val companionStart = System.nanoTime()
    val companion = TypeResolver.companionInstance[AnyRef](context.resolvedClass.raw)
    val schemaProvider =
      if (companion != null && classOf[SchemaProvider[?]].isInstance(companion)) {
        companion.asInstanceOf[SchemaProvider[EntitySchema[Any]]]
      } else {
        null
      }
    val companionEnd = System.nanoTime()

    val properties = beanModel.properties
    var index = 0
    
    var graphFilterTime = 0L
    var schemaVisibilityTime = 0L
    var propertySerializeTime = 0L
    
    val ruleCache = new java.util.HashMap[Class[? <: VisibilityRule[?]], VisibilityRule[Any]]()

    val loopStart = System.nanoTime()
    while (index < properties.length) {
      val property = properties(index)

      val graphCheckStart = System.nanoTime()
      val skipByGraph = 
        property.name != "links" &&
          classOf[EntityProvider].isAssignableFrom(context.resolvedClass.raw) &&
          context.graph != null &&
          !isSelectedByGraph(context, property)
      graphFilterTime += (System.nanoTime() - graphCheckStart)

      if (skipByGraph) {
        index += 1
      } else {
        if (schemaProvider != null) {
          val schemaVisibilityStart = System.nanoTime()
          val schemaProperty = schemaProvider.schema.properties.get(property.name).orNull

          if (schemaProperty == null) {
            schemaVisibilityTime += (System.nanoTime() - schemaVisibilityStart)
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

            val isVisibleStart = System.nanoTime()
            val visible = visibilityRule == null || visibilityRule.isVisible(input, property)
            val isVisibleEnd = System.nanoTime()
            schemaVisibilityTime += (isVisibleEnd - schemaVisibilityStart)
            
            if ((isVisibleEnd - isVisibleStart) > 1000000) { // > 1ms
               log.info(s"Rule ${if (visibilityRule != null) visibilityRule.getClass.getSimpleName else "null"} for ${property.name} took ${(isVisibleEnd - isVisibleStart) / 1000000.0}%.2fms")
            }

            if (!visible) {
              index += 1
            } else {
              val propSerStart = System.nanoTime()
              serializeProperty(input, context, nodes, property)
              propertySerializeTime += (System.nanoTime() - propSerStart)
              index += 1
            }
          }
        } else {
          val propSerStart = System.nanoTime()
          serializeProperty(input, context, nodes, property)
          propertySerializeTime += (System.nanoTime() - propSerStart)
          index += 1
        }
      }
    }
    val loopEnd = System.nanoTime()

    val typeMetadataStart = System.nanoTime()
    if (!json.value.isEmpty) {
      applyJsonLdMetadata(input, beanModel.properties, schemaProvider, json)
      json.value.putIfAbsent("@type", resolveTypeProperty(input))
    }
    val typeMetadataEnd = System.nanoTime()

    val totalEnd = System.nanoTime()
    val totalMs = (totalEnd - start) / 1000000.0
    
    // Log if significant (e.g. > 10ms) or if it's a root object
    if (totalMs > 10 || context.parent == null) {
      log.info(f"Serialization of ${context.resolvedClass.raw.getSimpleName} took $totalMs%.2fms breakdown: " +
        f"Introspect: ${(introspectEnd - introspectStart) / 1000000.0}%.2fms, " +
        f"Companion/Schema: ${(companionEnd - companionStart) / 1000000.0}%.2fms, " +
        f"Loop Total: ${(loopEnd - loopStart) / 1000000.0}%.2fms (GraphCheck: ${graphFilterTime / 1000000.0}%.2fms, SchemaVis: ${schemaVisibilityTime / 1000000.0}%.2fms, PropSer: ${propertySerializeTime / 1000000.0}%.2fms), " +
        f"TypeMetadata: ${(typeMetadataEnd - typeMetadataStart) / 1000000.0}%.2fms")
    }

    json
  }

  private def serializeProperty(
                                 input: Any,
                                 context: JavaContext,
                                 nodes: java.util.LinkedHashMap[String, JsonNode],
                                 property: AnnotationProperty
                               ): Unit = {
    val value =
      try {
        property.get(input.asInstanceOf[AnyRef])
      } catch {
        case _: Exception => null
      }

    value match {
      case booleanValue: java.lang.Boolean =>
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
      case collectionValue: java.util.Collection[?] =>
        if (!collectionValue.isEmpty) {
          convertToJsonNode(property, nodes, collectionValue, context)
        }
      case _ =>
        if (value != null) {
          convertToJsonNode(property, nodes, value, context)
        }
    }
  }

  private def applyJsonLdMetadata(
    input: Any,
    properties: Array[AnnotationProperty],
    schemaProvider: SchemaProvider[EntitySchema[Any]],
    json: JsonObject
  ): Unit = {
    val contextNode = buildJsonLdContext(input, properties, schemaProvider)
    if (!contextNode.value.isEmpty) {
      json.value.putIfAbsent("@context", contextNode)
    }

    resolveJsonLdId(input, properties, schemaProvider, json)
      .foreach(value => json.value.put("@id", new JsonString(value)))
  }

  private def buildJsonLdContext(
    input: Any,
    properties: Array[AnnotationProperty],
    schemaProvider: SchemaProvider[EntitySchema[Any]]
  ): JsonObject = {
    val context = new JsonObject(new java.util.LinkedHashMap[String, JsonNode]())
    val classVocab = findAnnotationOnHierarchy(classOf[JsonLdVocab], input.getClass)
    if (classVocab != null) {
      context.put("@vocab", classVocab.value())
    }

    orderedProperties(properties, schemaProvider).foreach { property =>
      val jsonLdProperty = property.findAnnotation(classOf[JsonLdProperty])
      if (jsonLdProperty != null) {
        val term = serializedName(property)
        if (jsonLdProperty.asReference()) {
          context.put(
            term,
            new JsonObject(new java.util.LinkedHashMap[String, JsonNode]())
              .put("@id", jsonLdProperty.value())
              .put("@type", "@id")
          )
        } else {
          context.put(term, jsonLdProperty.value())
        }
      }
    }

    context
  }

  private def resolveJsonLdId(
    input: Any,
    properties: Array[AnnotationProperty],
    schemaProvider: SchemaProvider[EntitySchema[Any]],
    json: JsonObject
  ): Option[String] = {
    val resource = findAnnotationOnHierarchy(classOf[JsonLdResource], input.getClass)

    orderedProperties(properties, schemaProvider)
      .iterator
      .flatMap { property =>
        Option(property.findAnnotation(classOf[JsonLdId])).flatMap { annotation =>
          val propertyName = serializedName(property)
          val jsonValue = json.value.get(propertyName)
          extractScalarValue(jsonValue).map { scalar =>
            val prefix =
              if (annotation.prefix().isEmpty) {
                if (resource == null) "" else resource.value()
              } else {
                annotation.prefix()
              }

            if (prefix.isEmpty) scalar else s"$prefix$scalar"
          }
        }
      }
      .take(1)
      .toList
      .headOption
  }

  private def extractScalarValue(node: JsonNode): Option[String] =
    Option(node).flatMap {
      case value: JsonString => Option(value.value)
      case value => Option(value.value).map(_.toString)
    }

  private def orderedProperties(
    properties: Array[AnnotationProperty],
    schemaProvider: SchemaProvider[EntitySchema[Any]]
  ): Seq[AnnotationProperty] = {
    val propertyLookup = indexedProperties(properties)
    if (schemaProvider == null) {
      properties.toSeq
    } else {
      schemaProvider.schema.properties.keys.flatMap(name => propertyLookup.get(name)).toSeq
    }
  }

  private def indexedProperties(properties: Array[AnnotationProperty]): Map[String, AnnotationProperty] =
    properties.iterator.map(property => property.name -> property).toMap

  private def serializedName(property: AnnotationProperty): String = {
    val jsonbProperty = property.findAnnotation(classOf[JsonbProperty])
    if (jsonbProperty == null || jsonbProperty.value().isEmpty) property.name else jsonbProperty.value()
  }

  private def resolveTypeProperty(input: Any): JsonString = {
    val jsonLdType = findAnnotationOnHierarchy(classOf[JsonLdType], input.getClass)
    if (jsonLdType != null) {
      new JsonString(jsonLdType.value())
    } else {
      val subtype = findAnnotationOnHierarchy(classOf[JsonbSubtype], input.getClass)
      if (subtype != null) {
        new JsonString(subtype.alias())
      } else {
        new JsonString(input.getClass.getSimpleName.replace("$HibernateProxy", ""))
      }
    }
  }

  private def findAnnotationOnHierarchy[A <: java.lang.annotation.Annotation](annotationClass: Class[A], clazz: Class[?]): A = {
    var current = clazz
    while (current != null) {
      val annotation = current.getAnnotation(annotationClass)
      if (annotation != null) {
        return annotation
      }
      current = current.getSuperclass
    }
    null.asInstanceOf[A]
  }

  private def convertToJsonNode(property: AbstractProperty,
                                nodes: java.util.LinkedHashMap[String, JsonNode],
                                value: Any,
                                context: JavaContext): Unit = {
    val jsonbProperty = property.findAnnotation(classOf[JsonbProperty])
    if (jsonbProperty == null) {
      return
    }

    val name =
      if (jsonbProperty.value().isEmpty) property.name else jsonbProperty.value()

    val propertyType =
      if (
        classOf[java.util.Collection[?]].isAssignableFrom(property.propertyType.raw) ||
          classOf[java.util.Map[?, ?]].isAssignableFrom(property.propertyType.raw)
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
        val serializer = SerializerRegistry.find(property.propertyType.raw.asInstanceOf[Class[Any]], value).asInstanceOf[Serializer[Any]]
        serializer.serialize(value, javaContext)
      } else {
        val converter = converterAnnotation.value().getDeclaredConstructor().newInstance()
        val toJson = converter.toJson(value, property.propertyType)
        val serializer = SerializerRegistry.find(classOf[String].asInstanceOf[Class[Any]], toJson).asInstanceOf[Serializer[Any]]
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

  private val attributeNamesCache = new java.util.WeakHashMap[Any, java.util.Set[String]]()

  private def isSelectedByGraph(context: JavaContext, property: AnnotationProperty): Boolean = {
    val currentContainer = resolveContainer(context)

    if (currentContainer != null) {
      var names = attributeNamesCache.get(currentContainer)
      if (names == null) {
        val attributeNodes =
          currentContainer match {
            case value: EntityGraph[?] => value.getAttributeNodes
            case value: Subgraph[?] => value.getAttributeNodes
            case _ => java.util.Collections.emptyList()
          }
        names = new java.util.HashSet[String]()
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

  private val subgraphCache = new java.util.WeakHashMap[Any, java.util.Map[String, Subgraph[?]]]()

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
          case value: Subgraph[?] => value.getAttributeNodes
          case _ => null
        }

      if (nodes == null) return null

      subgraphsMap = new java.util.HashMap[String, Subgraph[?]]()
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
