package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.{DeserializationContext, Subtypes}
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonObject}
import com.anjunar.scala.universe.TypeResolver
import java.util

/** Builds the new entries of a map with string keys. Object values merge into the entry already under their key. */
class MapDeserializer extends Deserializer[util.Map[String, ?]] {

  override def deserialize(json: JsonNode, context: DeserializationContext): util.Map[String, ?] =
    json match {
      case jsonObject: JsonObject =>
        val collection = new util.LinkedHashMap[String, Any]()
        val valueResolvedClass =
          context.resolvedClass.typeArguments.lift(1).getOrElse(TypeResolver.resolve(classOf[Object]))
        val declaredType = valueResolvedClass.raw
        val existing = context.instance match {
          case value: util.Map[?, ?] => value.asInstanceOf[util.Map[String, Any]]
          case _                     => null
        }

        val iterator = jsonObject.value.entrySet().iterator()
        while (iterator.hasNext) {
          val entry = iterator.next()
          val valueContext = entry.getValue match {
            case node: JsonObject if DeserializerRegistry.readsProperties(declaredType) =>
              val current = if (existing == null) null else existing.get(entry.getKey)
              val actualType = Subtypes.resolve(node, declaredType, context, entry.getKey)
              val instance =
                if (current != null && actualType.isInstance(current)) current
                else Subtypes.instantiate(actualType, context, entry.getKey)
              val resolved =
                if (actualType != declaredType) TypeResolver.resolve(actualType)
                else if (Subtypes.isConcrete(declaredType)) valueResolvedClass
                else TypeResolver.resolve(instance.getClass)
              context.child(resolved, instance, entry.getKey)
            case _ =>
              val current = if (existing == null) null else existing.get(entry.getKey)
              context.child(valueResolvedClass, current, entry.getKey)
          }

          val deserialized = DeserializerRegistry
            .findDeserializer(valueContext.resolvedClass.raw.asInstanceOf[Class[Any]], entry.getValue)
            .deserialize(entry.getValue, valueContext)

          collection.put(entry.getKey, deserialized)
        }

        collection
      case _ =>
        context.reject("This field expects an object.")
    }

}
