package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.provider.DTO
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonObject}

class MapDeserializer extends Deserializer[java.util.Map[String, ?]] {

  override def deserialize(json: JsonNode, context: JsonContext): java.util.Map[String, ?] =
    json match {
      case jsonObject: JsonObject =>
        val collection = new java.util.HashMap[String, Any]()
        val elementResolvedClass = context.resolvedClass.typeArguments(1)

        val iterator = jsonObject.value.entrySet().iterator()
        while (iterator.hasNext) {
          val entry = iterator.next()
          val entityCollection = context.instance.asInstanceOf[java.util.Map[String, Any]]

          val entity =
            if (entityCollection.containsKey(entry.getKey)) {
              entityCollection.get(entry.getKey)
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
