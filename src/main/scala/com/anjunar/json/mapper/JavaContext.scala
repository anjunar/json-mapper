package com.anjunar.json.mapper

import com.anjunar.scala.universe.ResolvedClass
import com.anjunar.json.mapper.schema.JsonSchema
import jakarta.persistence.EntityGraph

/** The serialization context: the value's type, the graph and projection narrowing the output, and the position. */
class JavaContext(
  val resolvedClass: ResolvedClass,
  val graph: EntityGraph[?],
  val inject: [T] => Class[T] => T,
  val parent: JavaContext,
  val name: String,
  projection: JsonSchema = null
) extends MappingContext {

  private val rootProjection: JsonSchema = if (parent == null) projection else parent.rootProjection
  val schema: JsonSchema = if (projection == null) null else JsonSchema.resolve(projection, rootProjection)

  def includes(jsonName: String): Boolean =
    schema == null || schema.properties == null || schema.properties.containsKey(jsonName)

  def required(jsonName: String): Boolean =
    schema != null && schema.required != null && schema.required.contains(jsonName)

  def childSchema(jsonName: String): JsonSchema =
    if (schema == null) null
    else if (schema.properties != null) schema.properties.get(jsonName)
    else schema.additionalProperties match {
      case value: JsonSchema => value
      case _                 => null
    }

}
