package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonNode, JsonObject}
import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.scala.universe.TypeResolver
import jakarta.json.bind.annotation.JsonbSubtype

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

        val elementResolvedClass =
          context.resolvedClass.typeArguments.headOption.getOrElse(TypeResolver.resolve(classOf[Object]))
        var index = 0
        val iterator = array.value.iterator()
        while (iterator.hasNext) {
          val elementNode = iterator.next()
          elementNode match {
            case node: JsonObject =>
              val declaredType = elementResolvedClass.raw
              val actualType = subtype(node, declaredType)
              val idNode = node.value.get("id")
              val entity =
                if (declaredType == classOf[Object]) {
                  null
                } else if (idNode == null) {
                  actualType.getConstructor().newInstance()
                } else {
                  EntityReferences.load(idNode, actualType, context)
                }

              val actualResolvedClass =
                if (actualType != declaredType) TypeResolver.resolve(actualType)
                else if (entity == null || !java.lang.reflect.Modifier.isAbstract(declaredType.getModifiers)) elementResolvedClass
                else TypeResolver.resolve(entity.getClass)
              val jsonContext = new JsonContext(
                actualResolvedClass,
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
                .findDeserializer(actualResolvedClass.raw.asInstanceOf[Class[Any]], node)
                .deserialize(node, jsonContext)

              collection.add(deserialized)
            case _ =>
              val elementInstance =
                if (
                  elementResolvedClass.raw == classOf[Object] ||
                  elementResolvedClass.raw == classOf[java.lang.Object] ||
                  classOf[EntityProvider].isAssignableFrom(elementResolvedClass.raw)
                ) {
                  null
                } else {
                  val existingCollection =
                    context.instance match {
                      case value: java.util.Collection[?] => value.asInstanceOf[java.util.Collection[Any]]
                      case _ => null
                    }
                  if (existingCollection == null) {
                    null
                  } else {
                    val existingIterator = existingCollection.iterator()
                    var currentIndex = 0
                    var currentValue: Any = null
                    while (existingIterator.hasNext && currentIndex <= index) {
                      currentValue = existingIterator.next()
                      currentIndex += 1
                    }
                    if (currentIndex == index + 1) currentValue else null
                  }
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

  private def subtype(node: JsonObject, declaredType: Class[?]): Class[?] = {
    if (!declaredType.isInterface && !java.lang.reflect.Modifier.isAbstract(declaredType.getModifiers))
      return declaredType
    val alias = node.value.get("@type") match {
      case value: com.anjunar.json.mapper.intermediate.model.JsonString => value.value
      case null => return declaredType
      case _ => throw new IllegalArgumentException("Collection element type must be a string")
    }
    if (!alias.matches("[A-Za-z][A-Za-z0-9_]*"))
      throw new IllegalArgumentException("Invalid collection element type")
    val resolved =
      try Class.forName(s"${declaredType.getPackageName}.$alias", false, declaredType.getClassLoader)
      catch { case _: ClassNotFoundException => throw new IllegalArgumentException("Unknown collection element type") }
    val annotation = resolved.getAnnotation(classOf[JsonbSubtype])
    if (!declaredType.isAssignableFrom(resolved) || annotation == null || annotation.alias() != alias ||
        annotation.`type`() != resolved)
      throw new IllegalArgumentException("Collection element type is not allowed")
    resolved
  }

}
