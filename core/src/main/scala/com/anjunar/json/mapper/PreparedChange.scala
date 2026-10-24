package com.anjunar.json.mapper

import com.anjunar.json.mapper.intermediate.model.JsonNode
import com.anjunar.scala.universe.ResolvedClass
import jakarta.persistence.EntityGraph
import jakarta.validation.Validator

final class PreparedChange[T] private[mapper] (
  entity: T,
  json: JsonNode,
  resolvedClass: ResolvedClass,
  graph: EntityGraph[?],
  loader: EntityLoader,
  inject: [X] => Class[X] => X,
  validator: Validator
) {

  private var applied = false

  def getEntity(): T = entity

  def isApplied: Boolean = applied

  def applyChanges(): T = {
    if (applied) throw IllegalStateException("The change has already been applied")
    JsonMapper.deserialize(json, entity.asInstanceOf[AnyRef], resolvedClass, graph, loader, inject, validator)
    applied = true
    entity
  }

}
