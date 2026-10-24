package com.anjunar.json.mapper

import com.anjunar.json.mapper.annotations.JsonbGraphProperty
import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.scala.universe.introspector.AnnotationProperty
import jakarta.persistence.Graph
import java.util

/** Applies a JPA entity graph to the properties of an entity at the current position of a mapping. */
object EntityGraphs {

  /** A transitive graph property is mapped wherever its entity is, whatever the graph selects. */
  def isTransitive(property: AnnotationProperty): Boolean = {
    val annotation = property.findAnnotation(classOf[JsonbGraphProperty])
    annotation != null && annotation.transitive()
  }

  /**
   * Whether the graph leaves this property out. Only entities are restricted, only while a graph is given, and never
   * their `links`, which describe the representation rather than persistent state.
   */
  def excludes(context: MappingContext, property: AnnotationProperty): Boolean =
    property.name != "links" &&
      classOf[EntityProvider].isAssignableFrom(context.resolvedClass.raw) &&
      context.graph != null &&
      !isTransitive(property) &&
      !isSelected(context, property)

  def isSelected(context: MappingContext, property: AnnotationProperty): Boolean = {
    val node = container(context)
    node == null || context.run.attributeNames(node).contains(property.name)
  }

  /**
   * The graph node in effect at this position: the root graph, or the subgraph the enclosing entity declares for the
   * property that led here. Collections are transparent; a non-entity parent falls back to the root graph.
   */
  private def container(context: MappingContext): Graph[?] = {
    val parent = context.parent
    if (parent == null) {
      context.graph
    } else if (
      classOf[util.Collection[?]].isAssignableFrom(parent.resolvedClass.raw) ||
      parent.resolvedClass.raw.isArray
    ) {
      container(parent)
    } else if (!classOf[EntityProvider].isAssignableFrom(parent.resolvedClass.raw)) {
      context.graph
    } else {
      val enclosing = container(parent)
      if (enclosing == null) null else context.run.subgraph(enclosing, context.name)
    }
  }

}
