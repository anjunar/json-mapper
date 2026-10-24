package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.SerializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonObject}
import com.anjunar.scala.universe.TypeResolver
import java.util

class MapSerializer extends Serializer[util.Map[String, ?]] {

  override def serialize(input: util.Map[String, ?], context: SerializationContext): JsonNode = {
    // Entries keep the order of the source map, so output is deterministic for ordered maps.
    val nodes = new util.LinkedHashMap[String, JsonNode]()
    val jsonObject = new JsonObject(nodes)
    val typeArguments = context.resolvedClass.typeArguments

    val iterator = input.entrySet().iterator()
    while (iterator.hasNext) {
      val entry = iterator.next()
      if (context.includes(entry.getKey)) {
        val value = entry.getValue
        val valueResolvedClass =
          if (typeArguments.length > 1) typeArguments(1)
          else if (value == null) TypeResolver.resolve(classOf[Object])
          else TypeResolver.resolve(value.getClass)

        val serializer = SerializerRegistry
          .find(valueResolvedClass.raw.asInstanceOf[Class[Any]], value)
          .asInstanceOf[Serializer[Any]]

        val javaContext = context.child(valueResolvedClass, context.name, context.childSchema(entry.getKey))

        val node = serializer.serialize(value, javaContext)
        nodes.put(entry.getKey, node)
      }
    }

    jsonObject
  }

}
