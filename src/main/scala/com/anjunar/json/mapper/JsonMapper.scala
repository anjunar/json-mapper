package com.anjunar.json.mapper

import com.anjunar.json.mapper.deserializer.DeserializerRegistry
import com.anjunar.json.mapper.intermediate.JsonGenerator
import com.anjunar.json.mapper.intermediate.model.JsonNode
import com.anjunar.json.mapper.provider.DTO
import com.anjunar.json.mapper.serializers.{Serializer, SerializerRegistry}
import com.anjunar.scala.universe.ResolvedClass
import jakarta.persistence.EntityGraph
import jakarta.validation.Validator

object JsonMapper {

  def deserialize(jsonNode: JsonNode,
                  instance: AnyRef,
                  resolvedClass: ResolvedClass,
                  graph: EntityGraph[?],
                  loader: EntityLoader,
                  inject: [T] => Class[T] => T,
                  validator: Validator): Any = {
    val deserializer = DeserializerRegistry.findDeserializer(resolvedClass.raw.asInstanceOf[Class[Any]], jsonNode)
    val context = new JsonContext(resolvedClass, instance, graph, loader, validator, inject, null, null)
    val deserialized = deserializer.deserialize(jsonNode, context)

    val errorRequests = new java.util.ArrayList[ErrorRequest]()
    val contexts = context.flatten().iterator()

    while (contexts.hasNext) {
      val current = contexts.next()
      val violations = current.violations.iterator()
      while (violations.hasNext) {
        val violation = violations.next()
        val path = new java.util.ArrayList[Any](current.pathWithIndexes())
        path.add(violation.getPropertyPath.toString)
        errorRequests.add(new ErrorRequest(path, violation.getMessage))
      }
    }

    if (errorRequests.isEmpty) {
      deserialized
    } else {
      throw new ErrorRequestException(errorRequests)
    }
  }

  def serialize(instance: Any, resolvedClass: ResolvedClass, graph: EntityGraph[?], inject : [T] => Class[T] => T): String = {
    val serializer = SerializerRegistry.find(resolvedClass.raw.asInstanceOf[Class[Any]], instance)
    val node = serializer.serialize(instance, new JavaContext(resolvedClass, graph, inject, null, null))
    JsonGenerator.generate(node)
  }

}
