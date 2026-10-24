package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.{DeserializationContext, Subtypes}
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonNode, JsonObject}
import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.scala.universe.TypeResolver
import java.util

/**
 * Builds the new contents of a collection. Object elements with an `id` are loaded, those without one are created;
 * scalar elements merge into the element at the same position. The caller replaces the collection's contents.
 */
class ArrayDeserializer extends Deserializer[util.Collection[?]] {

  override def deserialize(json: JsonNode, context: DeserializationContext): util.Collection[?] =
    json match {
      case array: JsonArray =>
        // Insertion order is kept for sets as well, so the result is deterministic.
        val collection: util.Collection[Any] =
          if (classOf[util.Set[?]].isAssignableFrom(context.resolvedClass.raw)) {
            new util.LinkedHashSet[Any]()
          } else {
            new util.ArrayList[Any]()
          }

        val elementResolvedClass =
          context.resolvedClass.typeArguments.headOption.getOrElse(TypeResolver.resolve(classOf[Object]))
        val declaredType = elementResolvedClass.raw
        val existing = context.instance match {
          case value: util.Collection[?] => value
          case _                         => null
        }

        var index = 0
        val iterator = array.value.iterator()
        while (iterator.hasNext) {
          val elementNode = iterator.next()
          val elementContext = elementNode match {
            case node: JsonObject if DeserializerRegistry.readsProperties(declaredType) =>
              val actualType = Subtypes.resolve(node, declaredType, context, index)
              val idNode = node.value.get("id")
              val entity =
                if (idNode == null) Subtypes.instantiate(actualType, context, index)
                else EntityReferences.load(idNode, actualType, context, index)
              val actualResolvedClass =
                if (actualType != declaredType) TypeResolver.resolve(actualType)
                else if (Subtypes.isConcrete(declaredType)) elementResolvedClass
                else TypeResolver.resolve(entity.getClass)
              context.child(actualResolvedClass, entity, context.name, index)
            case _ =>
              val elementInstance =
                if (declaredType == classOf[Object] || classOf[EntityProvider].isAssignableFrom(declaredType)) null
                else ArrayDeserializer.at(existing, index)
              context.child(elementResolvedClass, elementInstance, context.name, index)
          }

          val deserialized = DeserializerRegistry
            .findDeserializer(elementContext.resolvedClass.raw.asInstanceOf[Class[Any]], elementNode)
            .deserialize(elementNode, elementContext)

          collection.add(deserialized)
          index += 1
        }

        collection
      case _ =>
        context.reject("This field expects an array.")
    }

}

private object ArrayDeserializer {

  private def at(collection: util.Collection[?], index: Int): Any =
    if (collection == null || index >= collection.size()) {
      null
    } else {
      val iterator = collection.iterator()
      var current = 0
      while (current < index) {
        iterator.next()
        current += 1
      }
      iterator.next()
    }

}
