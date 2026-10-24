package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.{EntityGraphs, JavaContext, JsonProperties}
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonObject, JsonString}
import com.anjunar.json.mapper.schema.EntitySchema
import com.anjunar.scala.universe.TypeResolver
import com.anjunar.scala.universe.introspector.AnnotationProperty
import jakarta.json.bind.annotation.JsonbSubtype
import java.lang.{Boolean as JavaBoolean}
import java.util

class BeanSerializer extends Serializer[Any] {

  override def serialize(input: Any, context: JavaContext): JsonNode = {
    val nodes = new util.LinkedHashMap[String, JsonNode]()
    val json = new JsonObject(nodes)
    val schema = JsonProperties.schema(context.resolvedClass)

    for (property <- JsonProperties.model(context.resolvedClass).properties) {
      val isAnyProperty = JsonProperties.isAnyProperty(property)
      val selected =
        isAnyProperty ||
          (!EntityGraphs.excludes(context, property) && context.includes(JsonProperties.jsonName(property)))

      if (selected && isVisible(input, context, schema, property)) {
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

  /** Without an EntitySchema every property is visible; with one, only registered properties whose rule agrees. */
  private def isVisible(
    input: Any,
    context: JavaContext,
    schema: EntitySchema[Any],
    property: AnnotationProperty
  ): Boolean =
    schema == null || schema.properties.get(property.name).exists { registered =>
      registered.rule == null || context.run.rule(registered.rule).isVisible(input, property)
    }

  /** A value the instance cannot provide, for example an uninitialized lazy association, is left out. */
  private def read(input: Any, property: AnnotationProperty): Any =
    try {
      property.get(input.asInstanceOf[AnyRef])
    } catch {
      case _: Exception => null
    }

  private def serializeProperty(
    input: Any,
    context: JavaContext,
    nodes: util.LinkedHashMap[String, JsonNode],
    property: AnnotationProperty
  ): Unit = {
    val value = read(input, property)
    val jsonName = JsonProperties.jsonName(property)
    val preserveEmpty = context.required(jsonName)

    // Empty values are omitted, unless the projection requires the member: then the client sees them explicitly.
    val present = value match {
      case null                                => false
      case booleanValue: JavaBoolean           => preserveEmpty || booleanValue.booleanValue()
      case stringValue: String                 => preserveEmpty || !stringValue.isEmpty
      case collectionValue: util.Collection[?] => preserveEmpty || !collectionValue.isEmpty
      case _                                   => true
    }

    if (present) {
      convertToJsonNode(property, jsonName, nodes, value, context)
    }
  }

  private def serializeAnyProperty(
    input: Any,
    context: JavaContext,
    nodes: util.LinkedHashMap[String, JsonNode],
    property: AnnotationProperty
  ): Unit = {
    val value = read(input, property)

    if (value == null) {
      return
    }

    val javaContext = new JavaContext(
      property.propertyType,
      context.graph,
      context.inject,
      context,
      property.name,
      context.childSchema(property.name)
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
    property: AnnotationProperty,
    jsonName: String,
    nodes: util.LinkedHashMap[String, JsonNode],
    value: Any,
    context: JavaContext
  ): Unit = {
    // Collections and maps keep their declared element types; any other value is described by its runtime class.
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
      property.name,
      context.childSchema(jsonName)
    )

    val converter = JsonProperties.converter(property)

    val jsonNode =
      if (converter == null) {
        val serializer = SerializerRegistry.find(
          property.propertyType.raw.asInstanceOf[Class[Any]],
          value
        ).asInstanceOf[Serializer[Any]]
        serializer.serialize(value, javaContext)
      } else {
        val toJson = converter.toJson(value, property.propertyType)
        val serializer =
          SerializerRegistry.find(classOf[String].asInstanceOf[Class[Any]], toJson).asInstanceOf[Serializer[Any]]
        serializer.serialize(toJson, javaContext)
      }

    jsonNode match {
      case value: JsonObject if value.value.isEmpty && !context.required(jsonName) =>
      case _                                                                       =>
        nodes.put(jsonName, jsonNode)
    }
  }

}
