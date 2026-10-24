package com.anjunar.json.mapper

import com.anjunar.json.mapper.annotations.{JsonbAnyProperty, JsonbGraphProperty}
import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.json.mapper.schema.{BeanSchema, SchemaProvider}
import com.anjunar.scala.universe.{ResolvedClass, TypeResolver}
import com.anjunar.scala.universe.introspector.{AbstractProperty, AnnotationIntrospector, AnnotationModel, AnnotationProperty}
import jakarta.json.bind.annotation.JsonbProperty
import java.util

/** How the mapper reads a class: its JSON properties, their names, the schema and the entity graph rules. */
private[mapper] object BeanMetadata {

  def model(resolvedClass: ResolvedClass): AnnotationModel =
    AnnotationIntrospector.create(resolvedClass, classOf[JsonbProperty])

  /** The JSON name: the `@JsonbProperty` value, or the property name when it is empty. */
  def jsonName(property: AbstractProperty): String = {
    val annotation = property.findAnnotation(classOf[JsonbProperty])
    if (annotation != null && annotation.value().nonEmpty) annotation.value() else property.name
  }

  def isAnyProperty(property: AbstractProperty): Boolean =
    property.findAnnotation(classOf[JsonbAnyProperty]) != null

  def anyProperty(properties: Array[AnnotationProperty]): AnnotationProperty = {
    val matches = properties.filter(isAnyProperty)
    if (matches.length > 1) {
      throw new IllegalStateException("Only one property can be annotated with @JsonbAnyProperty")
    }
    matches.headOption.orNull
  }

  /** The schema the companion object provides, or null for a class without one. */
  def schema(resolvedClass: ResolvedClass): BeanSchema[Any] =
    TypeResolver.companionInstance[AnyRef](resolvedClass.raw) match {
      case provider: SchemaProvider[?] => provider.schema.asInstanceOf[BeanSchema[Any]]
      case _                           => null
    }

  /**
   * Properties an entity graph never filters: a transitive `@JsonbGraphProperty`, and `links`, the navigation links an
   * application attaches to an entity and which no graph can name because they are not persisted.
   */
  def bypassesGraph(property: AbstractProperty): Boolean = {
    val graphProperty = property.findAnnotation(classOf[JsonbGraphProperty])
    property.name == "links" || (graphProperty != null && graphProperty.transitive())
  }

  /** Whether the entity graph of this context leaves the property out. Without a graph nothing is left out. */
  def excludedByGraph(context: MappingContext, property: AbstractProperty): Boolean =
    context.graph != null &&
      classOf[EntityProvider].isAssignableFrom(context.resolvedClass.raw) &&
      !bypassesGraph(property) &&
      !selectedByGraph(context, property)

  private def selectedByGraph(context: MappingContext, property: AbstractProperty): Boolean =
    graphContainer(context) match {
      case null      => true
      case container => context.run.attributeNames(container).contains(property.name)
    }

  /**
   * The entity graph or subgraph that governs the properties of this context's value. Collections pass their owner's
   * container through; below a non-entity the root graph applies again.
   */
  private def graphContainer(context: MappingContext): AnyRef = {
    val parent = context.parent
    if (parent == null) {
      context.graph
    } else if (
      classOf[util.Collection[?]].isAssignableFrom(parent.resolvedClass.raw) || parent.resolvedClass.raw.isArray
    ) {
      graphContainer(parent)
    } else if (!classOf[EntityProvider].isAssignableFrom(parent.resolvedClass.raw)) {
      context.graph
    } else {
      graphContainer(parent) match {
        case null      => null
        case container => context.run.subgraph(container, context.name)
      }
    }
  }

}
