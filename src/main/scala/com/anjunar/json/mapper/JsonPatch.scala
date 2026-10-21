package com.anjunar.json.mapper

import com.anjunar.json.mapper.intermediate.{JsonGenerator, JsonParser}
import com.anjunar.json.mapper.intermediate.model.JsonObject

/** A typed, unapplied partial document. Binding it never changes or loads an entity. */
final class JsonPatch[T] private[mapper] (private[mapper] val value: JsonObject) {
  def encoded: String = JsonGenerator.generate(value)
}

object JsonPatch {
  def parse[T](raw: String): JsonPatch[T] = JsonParser.parse(raw) match {
    case value: JsonObject => new JsonPatch[T](value)
    case _                 => throw IllegalArgumentException("An entity patch must be an object")
  }
}
