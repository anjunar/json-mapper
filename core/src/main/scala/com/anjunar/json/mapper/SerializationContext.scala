package com.anjunar.json.mapper

import com.anjunar.scala.universe.ResolvedClass
import com.anjunar.json.mapper.schema.JsonSchema
import jakarta.persistence.EntityGraph

/** The position of one Java value while it is written as JSON, with the projection that narrows it. */
class SerializationContext(
  val resolvedClass: ResolvedClass,
  val graph: EntityGraph[?],
  val inject: [T] => Class[T] => T,
  val parent: SerializationContext,
  val name: String,
  projection: JsonSchema = null
) extends MappingContext {

  val index: Int = -1

  private[mapper] val run: MappingRun = if (parent == null) new MappingRun(inject) else parent.run

  private val rootProjection: JsonSchema = if (parent == null) projection else parent.rootProjection
  val schema: JsonSchema = if (projection == null) null else JsonSchema.resolve(projection, rootProjection)

  /** A context for a value below this one. */
  def child(resolvedClass: ResolvedClass, name: String, projection: JsonSchema): SerializationContext =
    new SerializationContext(resolvedClass, graph, inject, this, name, projection)

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
