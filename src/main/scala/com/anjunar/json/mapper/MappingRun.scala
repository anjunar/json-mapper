package com.anjunar.json.mapper

import com.anjunar.json.mapper.schema.VisibilityRule
import jakarta.persistence.{Graph, Subgraph}
import java.util

/**
 * What one mapping call computes once and reuses: the attribute names and subgraphs of every graph node it meets, and
 * one instance per visibility rule. Graph nodes are indexed by identity, so request-scoped graphs need no hashCode, and
 * the state ends with the call, so nothing is retained or shared between threads.
 */
final class MappingRun(inject: [T] => Class[T] => T) {

  private val attributeNamesByNode = new util.IdentityHashMap[Graph[?], util.Set[String]]()

  private val subgraphsByNode = new util.IdentityHashMap[Graph[?], util.Map[String, Subgraph[?]]]()

  private val rulesByClass = new util.HashMap[Class[? <: VisibilityRule[?]], VisibilityRule[Any]]()

  def attributeNames(node: Graph[?]): util.Set[String] =
    attributeNamesByNode.computeIfAbsent(
      node,
      graph => {
        val names = new util.HashSet[String]()
        val attributes = graph.getAttributeNodes.iterator()
        while (attributes.hasNext) {
          names.add(attributes.next().getAttributeName)
        }
        names
      }
    )

  /** The subgraph declared for an attribute of this node, or null when the attribute has none. */
  def subgraph(node: Graph[?], attributeName: String): Subgraph[?] =
    subgraphsByNode.computeIfAbsent(
      node,
      graph => {
        val result = new util.HashMap[String, Subgraph[?]]()
        val attributes = graph.getAttributeNodes.iterator()
        while (attributes.hasNext) {
          val attribute = attributes.next()
          val declared = attribute.getSubgraphs.values().iterator()
          if (declared.hasNext) {
            result.put(attribute.getAttributeName, declared.next())
          }
        }
        result
      }
    ).get(attributeName)

  def rule(ruleClass: Class[? <: VisibilityRule[?]]): VisibilityRule[Any] =
    rulesByClass.computeIfAbsent(ruleClass, clazz => inject(clazz).asInstanceOf[VisibilityRule[Any]])

}
