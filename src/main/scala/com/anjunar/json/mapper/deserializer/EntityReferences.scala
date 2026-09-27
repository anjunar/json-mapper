package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}
import com.anjunar.json.mapper.provider.EntityProvider

import java.util.UUID

private[deserializer] object EntityReferences {
  def load(node: JsonNode, declaredType: Class[?], context: JsonContext): Any = {
    val id = node match {
      case text: JsonString =>
        val parsed = UUID.fromString(text.value)
        if (!parsed.toString.equalsIgnoreCase(text.value)) {
          throw new IllegalArgumentException("Reference ID must be a canonical UUID")
        }
        parsed
      case _ => throw new IllegalArgumentException("Reference ID must be a UUID string")
    }

    // Even an unchanged ID goes through the application's authorization boundary.
    val entity = context.loader.load(id, declaredType)
    if (entity == null) {
      throw new IllegalArgumentException("Reference could not be resolved")
    }
    if (!declaredType.isInstance(entity)) {
      throw new IllegalArgumentException("Resolved reference has the wrong type")
    }
    entity match {
      case provider: EntityProvider if provider.id != id =>
        throw new IllegalArgumentException("Resolved reference has the wrong identity")
      case _ => entity
    }
  }
}
