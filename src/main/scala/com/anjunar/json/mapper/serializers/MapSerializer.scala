package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.JavaContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonObject}
import com.anjunar.scala.universe.TypeResolver

class MapSerializer extends Serializer[java.util.Map[String, ?]] {

  override def serialize(input: java.util.Map[String, ?], context: JavaContext): JsonNode = {
    val nodes = new java.util.HashMap[String, JsonNode]()
    val jsonObject = new JsonObject(nodes)
    val typeArguments = context.resolvedClass.typeArguments

    val iterator = input.entrySet().iterator()
    while (iterator.hasNext) {
      val entry = iterator.next()
      val value = entry.getValue
      val valueResolvedClass =
        if (typeArguments.length > 1) typeArguments(1)
        else TypeResolver.resolve(value.getClass)

      val serializer = SerializerRegistry
        .find(valueResolvedClass.raw.asInstanceOf[Class[Any]], value)
        .asInstanceOf[Serializer[Any]]

      val javaContext = new JavaContext(
        valueResolvedClass,
        context.graph,
        context.inject,
        context,
        context.name
      )

      val node = serializer.serialize(value, javaContext)
      nodes.put(entry.getKey, node)
    }

    jsonObject
  }

}
