package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.{BeanMetadata, DeserializationContext}
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonObject}
import com.anjunar.scala.universe.TypeResolver
import java.util

class JsonAnyPropertyDeserializer extends Deserializer[util.Map[String, ?]] {

  override def deserialize(json: JsonNode, context: DeserializationContext): util.Map[String, ?] =
    json match {
      case jsonObject: JsonObject =>
        val beanContext = context.parent
        if (beanContext == null) {
          throw new IllegalStateException("@JsonbAnyProperty requires a parent bean context")
        }

        val beanModel = BeanMetadata.model(beanContext.resolvedClass)
        val excludedNames = new util.HashSet[String]()
        val propertyIterator = beanModel.properties.iterator
        while (propertyIterator.hasNext) {
          val property = propertyIterator.next()
          if (property.name == "id") {
            excludedNames.add("id")
          } else if (!BeanMetadata.isAnyProperty(property)) {
            excludedNames.add(BeanMetadata.jsonName(property))
          }
        }

        val collection = new util.LinkedHashMap[String, Any]()
        val iterator = jsonObject.value.entrySet().iterator()
        while (iterator.hasNext) {
          val entry = iterator.next()
          if (entry.getKey != "@type" && !excludedNames.contains(entry.getKey)) {
            val jsonContext = context.child(TypeResolver.resolve(classOf[Object]), null, entry.getKey)

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
