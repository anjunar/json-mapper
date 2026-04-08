package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.JavaContext
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonNode}

class ArraySerializer extends Serializer[java.util.Collection[?]] {

  override def serialize(input: java.util.Collection[?], context: JavaContext): JsonNode = {
    val nodes = new java.util.ArrayList[JsonNode]()
    val jsonArray = new JsonArray(nodes)

    val iterator = input.iterator()
    while (iterator.hasNext) {
      val any = iterator.next()
      val serializer = SerializerRegistry
        .find(context.resolvedClass.typeArguments(0).raw.asInstanceOf[Class[Any]], any)
        .asInstanceOf[Serializer[Any]]

      val javaContext = new JavaContext(
        context.resolvedClass.typeArguments(0),
        context.graph,
        context.inject,
        context,
        context.name
      )

      val node = serializer.serialize(any, javaContext)
      nodes.add(node)
    }

    jsonArray
  }

}
