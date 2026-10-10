package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.JavaContext
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonNode}
import com.anjunar.scala.universe.TypeResolver
import jakarta.json.bind.annotation.JsonbSubtype
import java.util

class ArraySerializer extends Serializer[util.Collection[?]] {

  override def serialize(input: util.Collection[?], context: JavaContext): JsonNode = {
    val nodes = new util.ArrayList[JsonNode]()
    val jsonArray = new JsonArray(nodes)

    val iterator = input.iterator()
    while (iterator.hasNext) {
      val any = iterator.next()
      val declaredType = context.resolvedClass.typeArguments(0)
      val elementType =
        if (any == null) declaredType
        else {
          var current: Class[?] = any.getClass
          while (
            current != null && declaredType.raw.isAssignableFrom(current) &&
            current.getDeclaredAnnotation(classOf[JsonbSubtype]) == null
          )
            current = current.getSuperclass
          if (current != null && declaredType.raw.isAssignableFrom(current)) TypeResolver.resolve(current)
          else declaredType
        }
      val serializer = SerializerRegistry
        .find(elementType.raw.asInstanceOf[Class[Any]], any)
        .asInstanceOf[Serializer[Any]]

      val javaContext = new JavaContext(
        elementType,
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
