package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.{JsonContext, JsonProperties}
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonObject}
import com.anjunar.scala.universe.TypeResolver
import java.util

class JsonAnyPropertyDeserializer extends Deserializer[util.Map[String, ?]] {

  override def deserialize(json: JsonNode, context: JsonContext): util.Map[String, ?] =
    json match {
      case jsonObject: JsonObject =>
        val beanContext = context.parent
        if (beanContext == null) {
          throw new IllegalStateException("@JsonbAnyProperty requires a parent bean context")
        }

        // Every member the bean declares itself, plus its identity, stays out of the open map.
        val excludedNames = new util.HashSet[String]()
        excludedNames.add("id")
        excludedNames.add("@type")
        for (property <- JsonProperties.model(beanContext.resolvedClass).properties) {
          if (!JsonProperties.isAnyProperty(property)) {
            excludedNames.add(JsonProperties.jsonName(property))
          }
        }

        val collection = new util.LinkedHashMap[String, Any]()
        val iterator = jsonObject.value.entrySet().iterator()
        while (iterator.hasNext) {
          val entry = iterator.next()
          if (!excludedNames.contains(entry.getKey)) {
            val jsonContext = new JsonContext(
              TypeResolver.resolve(classOf[Object]),
              null,
              context.graph,
              context.loader,
              context.validator,
              context.inject,
              context,
              entry.getKey
            )

            val deserialized = DeserializerRegistry
              .findDeserializer(classOf[Object], entry.getValue)
              .deserialize(entry.getValue, jsonContext)

            collection.put(entry.getKey, deserialized)
          }
        }

        collection
      case _ =>
        throw new IllegalStateException(s"JsonbAnyProperty must be deserialized from an object: $json")
    }

}
