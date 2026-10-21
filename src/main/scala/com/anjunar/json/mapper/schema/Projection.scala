package com.anjunar.json.mapper.schema

import com.anjunar.json.mapper.JsonMapper
import com.anjunar.json.mapper.schema.property.Property
import java.lang.reflect.Type
import java.util

/** A JSON contract together with the named entity graph that serializes it, if any. */
trait JsonProjection {
  def json: JsonSchema
  def graphName: Option[String]
}

object JsonProjection {
  def apply(json: JsonSchema, graphName: Option[String]): JsonProjection = Fixed(json, graphName)

  private final case class Fixed(json: JsonSchema, graphName: Option[String]) extends JsonProjection
}

/**
 * An immutable, typed selection of EntitySchema properties. It is static contract metadata only: EntitySchema rules,
 * entity graphs and validation still decide every actual read and write.
 */
final class Projection[E] private (
  val owner: EntitySchema[E],
  val fields: Seq[Property[E, ?]],
  val required: Seq[Property[E, ?]],
  val identity: Boolean,
  val graphName: Option[String],
  elements: Seq[(Property[E, ?], Projection[?])],
  refinements: Seq[(Property[E, ?], JsonSchema => JsonSchema)]
) extends JsonProjection {

  private def copy(
    fields: Seq[Property[E, ?]] = this.fields,
    required: Seq[Property[E, ?]] = this.required,
    identity: Boolean = this.identity,
    elements: Seq[(Property[E, ?], Projection[?])] = this.elements,
    refinements: Seq[(Property[E, ?], JsonSchema => JsonSchema)] = this.refinements
  ): Projection[E] = new Projection(owner, fields, required, identity, graphName, elements, refinements)

  private def selected(values: Seq[Property[E, ?]]): Unit = {
    Projection.owned(owner, values)
    require(values.forall(fields.contains), "Property is not part of this projection")
  }

  /** Adds further properties of the same EntitySchema. */
  def and(more: Property[E, ?]*): Projection[E] = {
    Projection.owned(owner, more)
    copy(fields = (fields ++ more).distinct)
  }

  /** Replaces the required properties; every one must be part of the projection. */
  def requiring(values: Property[E, ?]*): Projection[E] = {
    selected(values)
    copy(required = values.distinct)
  }

  def requiringAll: Projection[E] = copy(required = fields)

  def withoutIdentity: Projection[E] = copy(identity = false)

  /** A partial input: nothing is required and no entity identity is expected. */
  def patch: Projection[E] = copy(required = Seq.empty, identity = false)

  /** Describes the elements of a collection property with another typed projection. */
  def items[F](collection: Property[E, ?], element: Projection[F]): Projection[E] = {
    selected(Seq(collection))
    require(Projection.derived(owner, collection).`type` == "array", s"${collection.name} is not a collection")
    copy(elements = elements.filterNot(_._1 eq collection) :+ (collection -> element))
  }

  /** A named contract adjustment for properties whose constraint has no derivable annotation yet. */
  def refine(values: Property[E, ?]*)(change: JsonSchema => JsonSchema): Projection[E] = {
    selected(values)
    copy(refinements = refinements ++ values.map(_ -> change))
  }

  private lazy val aliases: Map[String, String] = JsonSchemaDerivation
    .properties(owner.ownerEntityType)
    .map(property => property.name -> JsonSchemaDerivation.name(property))
    .toMap

  private def alias(property: Property[E, ?]): String = aliases(property.name)

  private lazy val byAlias: Map[String, Property[E, ?]] = fields.map(property => alias(property) -> property).toMap

  private def element(property: Property[E, ?]): Option[Projection[?]] =
    elements.find(_._1 eq property).map(_._2)

  /** JSON names of all selected properties, for example as a write whitelist. */
  lazy val names: Set[String] = byAlias.keySet

  lazy val json: JsonSchema = {
    val base = JsonMapper.schema(owner, fields, required, identity)
    if (elements.isEmpty && refinements.isEmpty)
      base
    else {
      val properties = new util.LinkedHashMap[String, JsonSchema](base.properties)
      elements.foreach { (property, projection) =>
        val nested = Projection.embeddable(projection.json)
        properties.put(alias(property), properties.get(alias(property)).copy(items = nested))
      }
      refinements.foreach((property, change) => properties.put(alias(property), change(properties.get(alias(property)))))
      base.copy(properties = properties)
    }
  }

  /**
   * Whether a JSON field path addresses this contract: a property, an element of a scalar collection, or a property of
   * an element projection.
   */
  def contains(path: Seq[String | Int]): Boolean =
    path match {
      case Seq(name: String) =>
        byAlias.contains(name)
      case Seq(name: String, _: Int) =>
        byAlias.get(name).exists(property => element(property).isEmpty && json.properties.get(name).`type` == "array")
      case Seq(name: String, _: Int, rest*) =>
        byAlias.get(name).flatMap(element).exists(_.contains(rest))
      case _ =>
        false
    }

  /**
   * Places this projection into a DTO envelope's collection. Every envelope property is required so that empty paging
   * values are still emitted; serialization keeps this projection's entity graph.
   */
  def envelope(target: Type, collection: String): JsonProjection = {
    val model = JsonMapper.schema(target)
    val field = Option(model.properties).map(_.get(collection)).orNull
    require(field != null && field.`type` == "array", s"Envelope has no collection property: $collection")
    val properties = new util.LinkedHashMap[String, JsonSchema](model.properties)
    properties.put(collection, field.copy(items = Projection.embeddable(json)))
    JsonProjection(model.copy(properties = properties, required = new util.ArrayList(model.properties.keySet())), graphName)
  }
}

object Projection {
  private[schema] def apply[E](
    owner: EntitySchema[E],
    fields: Seq[Property[E, ?]],
    graphName: Option[String] = None
  ): Projection[E] = {
    owned(owner, fields)
    new Projection(owner, fields.distinct, Seq.empty, true, graphName, Seq.empty, Seq.empty)
  }

  private def owned[E](owner: EntitySchema[E], values: Seq[Property[E, ?]]): Unit =
    require(
      values.forall(property => owner.properties.get(property.name).exists(_ eq property)),
      "Foreign EntitySchema property"
    )

  private def derived[E](owner: EntitySchema[E], property: Property[E, ?]): JsonSchema = JsonSchemaDerivation.field(
    JsonSchemaDerivation
      .properties(owner.ownerEntityType)
      .find(_.name == property.name)
      .getOrElse(throw IllegalArgumentException(s"Projected property has no JSON binding: ${property.name}"))
  )

  private def embeddable(value: JsonSchema): JsonSchema = {
    require(value.definitions == null, "An embedded projection must not contain recursive definitions")
    value
  }
}
