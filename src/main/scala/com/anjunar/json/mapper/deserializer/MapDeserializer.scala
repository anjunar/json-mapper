package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonObject}
import com.anjunar.scala.universe.TypeResolver

class MapDeserializer extends Deserializer[java.util.Map[String, ?]] {

  override def deserialize(json: JsonNode, context: JsonContext): java.util.Map[String, ?] =
    json match {
      case jsonObject: JsonObject =>
        val collection = new java.util.LinkedHashMap[String, Any]()
        val elementResolvedClass =
          context.resolvedClass.typeArguments.lift(1).getOrElse(TypeResolver.resolve(classOf[Object]))

        val iterator = jsonObject.value.entrySet().iterator()
        while (iterator.hasNext) {
          val entry = iterator.next()
          val entityCollection =
            context.instance match {
              case value: java.util.Map[?, ?] => value.asInstanceOf[java.util.Map[String, Any]]
              case _ => null
            }

          val entity =
            if (entityCollection != null && entityCollection.containsKey(entry.getKey)) {
              entityCollection.get(entry.getKey)
            } else if (elementResolvedClass.raw == classOf[Object] || elementResolvedClass.raw == classOf[java.lang.Object]) {
              null
            } else {
              elementResolvedClass.raw.getConstructor().newInstance()
            }

          val jsonContext = new JsonContext(
            elementResolvedClass,
            entity,
            context.graph,
            context.loader,
            context.validator,
            context.inject,
            context,
            context.name
          )

          val deserialized = DeserializerRegistry
            .findDeserializer(elementResolvedClass.raw.asInstanceOf[Class[Any]], entry.getValue)
            .deserialize(entry.getValue, jsonContext)

          collection.put(entry.getKey, deserialized)
        }

        collection
      case _ =>
        throw new IllegalStateException(s"not a json array: $json")
    }

}
