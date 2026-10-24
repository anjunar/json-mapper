package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.DeserializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}
import com.anjunar.json.mapper.provider.EntityProvider

import java.util

/** Resolves an `id` the client sent through the application's loader, its authorization boundary. */
private[deserializer] object EntityReferences {

  /** `at` is the position of the reference below `context`, e.g. a property name or a collection index. */
  def load(node: JsonNode, declaredType: Class[?], context: DeserializationContext, at: Any*): Any = {
    val id = node match {
      case text: JsonString =>
        val parsed =
          try util.UUID.fromString(text.value)
          catch { case _: IllegalArgumentException => context.reject("A reference ID must be a UUID.", (at :+ "id")*) }
        if (!parsed.toString.equalsIgnoreCase(text.value)) {
          context.reject("A reference ID must be a canonical UUID.", (at :+ "id")*)
        }
        parsed
      case _ => context.reject("A reference ID must be a UUID string.", (at :+ "id")*)
    }

    // Even an unchanged ID goes through the application's authorization boundary.
    val entity = context.loader.load(id, declaredType)
    if (entity == null) {
      context.reject("This reference could not be resolved.", at*)
    }
    // A loader that answers with another object breaks its contract; that is a fault of the application.
    if (!declaredType.isInstance(entity)) {
      throw new IllegalStateException(
        s"The loader returned a ${entity.getClass.getName} for a ${declaredType.getName} reference"
      )
    }
    entity match {
      case provider: EntityProvider if provider.id != id =>
        throw new IllegalStateException(s"The loader returned entity ${provider.id} for reference $id")
      case _ => entity
    }
  }

}
