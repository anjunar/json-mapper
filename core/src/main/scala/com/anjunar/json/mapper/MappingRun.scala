package com.anjunar.json.mapper

import com.anjunar.json.mapper.schema.{DefaultRule, VisibilityRule}
import jakarta.persistence.{EntityGraph, Subgraph}
import java.util
import jakarta.persistence.AttributeNode

/**
 * State of one serialize or deserialize call. Rules and graph lookups are cached here rather than in the shared
 * (de)serializer instances: they belong to the caller's graph and injector, and concurrent calls must not share them.
 */
private[mapper] final class MappingRun(inject: [T] => Class[T] => T) {

  private val rules = new util.HashMap[Class[?], VisibilityRule[Any]]()

  private val attributeNames = new util.IdentityHashMap[AnyRef, util.Set[String]]()

  private val subgraphs = new util.IdentityHashMap[AnyRef, util.Map[String, Subgraph[?]]]()

  /** The rule instance for a property. A property without a rule behaves like [[DefaultRule]]. */
  def rule(ruleClass: Class[? <: VisibilityRule[?]]): VisibilityRule[Any] =
    if (ruleClass == null) MappingRun.defaultRule
    else rules.computeIfAbsent(ruleClass, key => inject(key).asInstanceOf[VisibilityRule[Any]])

  /** Attribute names selected by an entity graph or subgraph. */
  def attributeNames(container: AnyRef): util.Set[String] =
    attributeNames.computeIfAbsent(
      container,
      _ => {
        val names = new util.HashSet[String]()
        MappingRun.attributeNodes(container).forEach(node => names.add(node.getAttributeName))
        names
      }
    )

  /** The subgraph an entity graph or subgraph declares for one attribute, or null. */
  def subgraph(container: AnyRef, attribute: String): Subgraph[?] =
    subgraphs.computeIfAbsent(
      container,
      _ => {
        val byName = new util.HashMap[String, Subgraph[?]]()
        MappingRun.attributeNodes(container).forEach { node =>
          val declared = node.getSubgraphs.values().iterator()
          if (declared.hasNext) {
            byName.put(node.getAttributeName, declared.next())
          }
        }
        byName
      }
    ).get(attribute)

}

private object MappingRun {

  private val defaultRule: VisibilityRule[Any] = new DefaultRule[Any]

  private def attributeNodes(container: AnyRef): util.List[? <: AttributeNode[?]] =
    container match {
      case graph: EntityGraph[?]  => graph.getAttributeNodes
      case subgraph: Subgraph[?] => subgraph.getAttributeNodes
      case _                     => util.Collections.emptyList()
    }

}
