package com.anjunar.json.mapper

import com.anjunar.json.mapper.intermediate.model.JsonNode
import com.anjunar.scala.universe.ResolvedClass
import jakarta.persistence.EntityGraph

/** A deserialization that has not happened yet. It applies at most once. */
final class PreparedChange[T] private[mapper] (
  entity: T,
  json: JsonNode,
  resolvedClass: ResolvedClass,
  graph: Option[EntityGraph[?]],
  mapper: JsonMapper
) {

  private var applied = false

  def getEntity(): T = entity

  def isApplied: Boolean = applied

  def applyChanges(): T = {
    if (applied) throw IllegalStateException("The change has already been applied")
    mapper.deserialize(json, entity.asInstanceOf[AnyRef], resolvedClass, graph)
    applied = true
    entity
  }

}
