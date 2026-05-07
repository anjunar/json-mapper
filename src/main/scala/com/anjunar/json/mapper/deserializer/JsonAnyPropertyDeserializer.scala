package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonObject}
import com.anjunar.scala.universe.TypeResolver
import com.anjunar.scala.universe.introspector.{AnnotationIntrospector, AnnotationProperty}
import jakarta.json.bind.annotation.JsonbProperty

class JsonAnyPropertyDeserializer extends Deserializer[java.util.Map[String, ?]] {

  override def deserialize(json: JsonNode, context: JsonContext): java.util.Map[String, ?] =
    json match {
      case jsonObject: JsonObject =>
        val beanContext = context.parent
        if (beanContext == null) {
          throw new IllegalStateException("@JsonbAnyProperty requires a parent bean context")
        }

        val beanModel = AnnotationIntrospector.create(beanContext.resolvedClass, classOf[JsonbProperty])
        val excludedNames = new java.util.HashSet[String]()
        val propertyIterator = beanModel.properties.iterator
        while (propertyIterator.hasNext) {
          val property = propertyIterator.next()
          if (property.name == "id") {
            excludedNames.add("id")
          } else if (!isJsonAnyProperty(property)) {
            excludedNames.add(resolveJsonName(property))
          }
        }

        val collection = new java.util.LinkedHashMap[String, Any]()
        val iterator = jsonObject.value.entrySet().iterator()
        while (iterator.hasNext) {
          val entry = iterator.next()
          if (entry.getKey != "@type" && !excludedNames.contains(entry.getKey)) {
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

  private def isJsonAnyProperty(property: AnnotationProperty): Boolean =
    property.findAnnotation(classOf[com.anjunar.json.mapper.annotations.JsonbAnyProperty]) != null

  private def resolveJsonName(property: AnnotationProperty): String = {
    val jsonbProperty = property.findAnnotation(classOf[JsonbProperty])
    if (jsonbProperty != null && jsonbProperty.value().nonEmpty) {
      jsonbProperty.value()
    } else {
      property.name
    }
  }

}
