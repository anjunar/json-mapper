package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonNode, JsonObject}
import com.anjunar.json.mapper.provider.{DTO, EntityProvider}

import java.util.UUID

class ArrayDeserializer extends Deserializer[java.util.Collection[?]] {

  override def deserialize(json: JsonNode, context: JsonContext): java.util.Collection[?] =
    json match {
      case array: JsonArray =>
        val collection: java.util.Collection[Any] =
          if (classOf[java.util.Set[?]].isAssignableFrom(context.resolvedClass.raw)) {
            new java.util.HashSet[Any]()
          } else {
            new java.util.ArrayList[Any]()
          }

        val elementResolvedClass = context.resolvedClass.typeArguments(0)
        DeserializerRegistry.findDeserializer(elementResolvedClass.raw.asInstanceOf[Class[Any]], json)

        var index = 0
        val iterator = array.value.iterator()
        while (iterator.hasNext) {
          val elementNode = iterator.next()
          elementNode match {
            case node: JsonObject =>
              val entityCollection = context.instance.asInstanceOf[java.util.Collection[EntityProvider]]
              val idNode = node.value.get("id")

              val entity =
                if (idNode == null) {
                  elementResolvedClass.raw.getConstructor().newInstance()
                } else {
                  val entityId = UUID.fromString(idNode.value.toString)
                  val existing = entityCollection.stream().filter(entityProvider => entityProvider.id == entityId).findFirst()
                  if (existing.isPresent) {
                    existing.get()
                  } else {
                    val value = context.loader.load(entityId, elementResolvedClass.raw)
                    if (value == null) {
                      elementResolvedClass.raw.getConstructor().newInstance()
                    } else {
                      value
                    }
                  }
                }

              val jsonContext = new JsonContext(
                elementResolvedClass,
                entity,
                context.graph,
                context.loader,
                context.validator,
                context.inject,
                context,
                context.name,
                index
              )

              val deserialized = DeserializerRegistry
                .findDeserializer(elementResolvedClass.raw.asInstanceOf[Class[Any]], node)
                .deserialize(node, jsonContext)

              collection.add(deserialized)
            case _ =>
              val elementInstance =
                if (classOf[EntityProvider].isAssignableFrom(elementResolvedClass.raw)) {
                  null
                } else {
                  val existingIterator = context.instance.asInstanceOf[java.util.Collection[Any]].iterator()
                  var currentIndex = 0
                  var currentValue: Any = null
                  while (existingIterator.hasNext && currentIndex <= index) {
                    currentValue = existingIterator.next()
                    currentIndex += 1
                  }
                  if (currentIndex == index + 1) currentValue else null
                }

              val jsonContext = new JsonContext(
                elementResolvedClass,
                elementInstance,
                context.graph,
                context.loader,
                context.validator,
                context.inject,
                context,
                context.name,
                index
              )

              val deserialized = DeserializerRegistry
                .findDeserializer(elementResolvedClass.raw.asInstanceOf[Class[Any]], elementNode)
                .deserialize(elementNode, jsonContext)

              collection.add(deserialized)
          }

          index += 1
        }

        collection
      case _ =>
        throw new IllegalStateException(s"not a json array: $json")
    }

}
