package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.{BeanMetadata, SerializationContext}
import com.anjunar.json.mapper.annotations.UseConverter
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonObject, JsonString}
import com.anjunar.json.mapper.schema.BeanSchema
import com.anjunar.scala.universe.TypeResolver
import com.anjunar.scala.universe.introspector.AnnotationProperty
import jakarta.json.bind.annotation.JsonbSubtype
import java.lang.{Boolean as JavaBoolean}
import java.util

class BeanSerializer extends Serializer[Any] {

  override def serialize(input: Any, context: SerializationContext): JsonNode = {
    val nodes = new util.LinkedHashMap[String, JsonNode]()
    val json = new JsonObject(nodes)
    val schema = BeanMetadata.schema(context.resolvedClass)

    val properties = BeanMetadata.model(context.resolvedClass).properties
    var index = 0
    while (index < properties.length) {
      val property = properties(index)
      index += 1

      val isAnyProperty = BeanMetadata.isAnyProperty(property)
      val included =
        isAnyProperty ||
          (!BeanMetadata.excludedByGraph(context, property) &&
            context.includes(BeanMetadata.jsonName(property)))

      if (included && visible(input, context, schema, property)) {
        if (isAnyProperty) {
          serializeAnyProperty(input, context, nodes, property)
        } else {
          serializeProperty(input, context, nodes, property)
        }
      }
    }
    if (!json.value.isEmpty && context.includes("@type")) {
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

  /** With a schema, only registered properties whose rule allows reading are written. */
  private def visible(
    input: Any,
    context: SerializationContext,
    schema: BeanSchema[Any],
    property: AnnotationProperty
  ): Boolean =
    if (schema == null) {
      true
    } else {
      schema.properties.get(property.name) match {
        case Some(schemaProperty) => context.run.rule(schemaProperty.rule).isVisible(input, property)
        case None                 => false
      }
    }

  private def serializeProperty(
    input: Any,
    context: SerializationContext,
    nodes: util.LinkedHashMap[String, JsonNode],
    property: AnnotationProperty
  ): Unit = {
    val value =
      try {
        property.get(input.asInstanceOf[AnyRef])
      } catch {
        case _: Exception => null
      }

    val preserveEmpty = context.required(BeanMetadata.jsonName(property))
    value match {
      case booleanValue: JavaBoolean =>
        if (preserveEmpty || booleanValue.booleanValue()) {
          convertToJsonNode(property, nodes, booleanValue, context)
        }
      case booleanValue: Boolean =>
        if (preserveEmpty || booleanValue) {
          convertToJsonNode(property, nodes, Boolean.box(booleanValue), context)
        }
      case stringValue: String =>
        if (preserveEmpty || !stringValue.isEmpty) {
          convertToJsonNode(property, nodes, stringValue, context)
        }
      case collectionValue: util.Collection[?] =>
        if (preserveEmpty || !collectionValue.isEmpty) {
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
    context: SerializationContext,
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

    val javaContext = context.child(property.propertyType, property.name, context.childSchema(property.name))
    val serializer = SerializerRegistry.findPropertySerializer(property, value)

    serializer.serialize(value, javaContext) match {
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
    property: AnnotationProperty,
    nodes: util.LinkedHashMap[String, JsonNode],
    value: Any,
    context: SerializationContext
  ): Unit = {
    val name = BeanMetadata.jsonName(property)

    val propertyType =
      if (
        classOf[util.Collection[?]].isAssignableFrom(property.propertyType.raw) ||
        classOf[util.Map[?, ?]].isAssignableFrom(property.propertyType.raw)
      ) {
        property.propertyType
      } else {
        TypeResolver.resolve(value.getClass)
      }

    val javaContext = context.child(propertyType, property.name, context.childSchema(name))
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
        if (context.required(name) || !value.value.isEmpty) {
          nodes.put(name, jsonNode)
        }
      case _ =>
        nodes.put(name, jsonNode)
    }
  }

}
