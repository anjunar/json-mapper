package com.anjunar.json.mapper

import com.anjunar.json.mapper.deserializer.DeserializerRegistry
import com.anjunar.json.mapper.intermediate.JsonGenerator
import com.anjunar.json.mapper.intermediate.model.JsonNode
import com.anjunar.json.mapper.schema.{BeanSchema, JsonSchema, JsonSchemaDerivation}
import com.anjunar.json.mapper.schema.property.Property
import com.anjunar.json.mapper.serializers.SerializerRegistry
import com.anjunar.scala.universe.ResolvedClass
import com.anjunar.scala.universe.introspector.AnnotationProperty
import jakarta.persistence.EntityGraph
import jakarta.validation.Validator
import java.lang.reflect.Type
import scala.jdk.CollectionConverters.*

/**
 * Maps between JSON and an existing object graph with what the application provides: a Validator for incoming
 * values, an EntityLoader that resolves and authorizes referenced IDs, and an injector for rule classes. Build it
 * where these are known, for example once per request, and use it for every call. Only serialization and documents
 * without properties, such as a single date, work without a Validator.
 */
final class JsonMapper(
  val validator: Option[Validator] = None,
  val loader: EntityLoader = EntityLoader.none,
  val inject: Inject = Inject.constructor
) {

  /** Writes the value as JSON. A graph limits entity properties, a projection narrows the output further. */
  def serialize(
    instance: Any,
    resolvedClass: ResolvedClass,
    graph: Option[EntityGraph[?]] = None,
    projection: Option[JsonSchema] = None
  ): String = {
    val serializer = SerializerRegistry.find(resolvedClass.raw.asInstanceOf[Class[Any]], instance)
    val context = new SerializationContext(resolvedClass, graph.orNull, inject, null, null, projection.orNull)
    JsonGenerator.generate(serializer.serialize(instance, context))
  }

  /**
   * Merges the document into the instance. Every invalid value is reported at once in an ErrorRequestException, and
   * then nothing is written.
   */
  def deserialize(
    json: JsonNode,
    instance: AnyRef,
    resolvedClass: ResolvedClass,
    graph: Option[EntityGraph[?]] = None
  ): Any = {
    val deserializer = DeserializerRegistry.findDeserializer(resolvedClass.raw.asInstanceOf[Class[Any]], json)
    val context =
      new DeserializationContext(resolvedClass, instance, graph.orNull, loader, validator.orNull, inject, null, null)
    val deserialized =
      try {
        deserializer.deserialize(json, context)
      } catch {
        case invalid: InvalidValue =>
          context.run.reject(invalid.error)
          null
      }
    // Writes happen only now, and only if no field of the whole document was invalid.
    context.run.complete()
    deserialized
  }

  /** Defers [[deserialize]] until [[PreparedChange.applyChanges]]; nothing is read, loaded or written before. */
  def prepare[T](
    json: JsonNode,
    entity: T,
    resolvedClass: ResolvedClass,
    graph: Option[EntityGraph[?]] = None
  ): PreparedChange[T] =
    new PreparedChange(entity, json, resolvedClass, graph, this)

}

object JsonMapper {

  /** Export the same resolved property metadata used by JSON binding. This grants no runtime field rights. */
  def schema(
    target: Type,
    select: AnnotationProperty => Boolean = _ => true,
    entityIdentity: Boolean = true
  ): JsonSchema =
    JsonSchemaDerivation.model(target, select, entityIdentity)

  /** Export only properties registered by the data owner's BeanSchema. No rules or JPA attributes are evaluated. */
  def schema[E](metadata: BeanSchema[E]): JsonSchema =
    JsonSchemaDerivation.model(metadata.ownerEntityType, property => metadata.properties.contains(property.name))

  /** A typed projection of existing properties; validation metadata and JSON aliases remain mapper-owned. */
  def schema[E](
    metadata: BeanSchema[E],
    selected: Seq[Property[E, ?]],
    required: Seq[Property[E, ?]],
    entityIdentity: Boolean
  ): JsonSchema = {
    require(
      selected.forall(property => metadata.properties.get(property.name).contains(property)),
      "Foreign BeanSchema property"
    )
    require(required.forall(selected.contains), "Required properties must belong to the projection")
    val selectedNames = selected.map(_.name).toSet
    val result =
      JsonSchemaDerivation.model(metadata.ownerEntityType, property => selectedNames(property.name), entityIdentity)
    val aliases = JsonSchemaDerivation.properties(metadata.ownerEntityType).map(property =>
      property.name -> JsonSchemaDerivation.name(property)
    ).toMap
    require(selected.forall(property => aliases.contains(property.name)), "Projected property has no JSON binding")
    result.copy(required = required.map(property => aliases(property.name)).asJava)
  }

  @deprecated(
    "Use new JsonMapper(Some(validator), loader, inject).prepare(json, entity, resolvedClass, Option(graph))",
    "1.2.0"
  )
  def prepare[T](
    jsonNode: JsonNode,
    entity: T,
    resolvedClass: ResolvedClass,
    graph: EntityGraph[?],
    loader: EntityLoader,
    inject: [X] => Class[X] => X,
    validator: Validator
  ): PreparedChange[T] =
    legacy(loader, inject, validator).prepare(jsonNode, entity, resolvedClass, Option(graph))

  @deprecated(
    "Use new JsonMapper(Some(validator), loader, inject).deserialize(json, instance, resolvedClass, Option(graph))",
    "1.2.0"
  )
  def deserialize(
    jsonNode: JsonNode,
    instance: AnyRef,
    resolvedClass: ResolvedClass,
    graph: EntityGraph[?],
    loader: EntityLoader,
    inject: [T] => Class[T] => T,
    validator: Validator
  ): Any =
    legacy(loader, inject, validator).deserialize(jsonNode, instance, resolvedClass, Option(graph))

  @deprecated("Use new JsonMapper(inject = inject).serialize(instance, resolvedClass, Option(graph))", "1.2.0")
  def serialize(
    instance: Any,
    resolvedClass: ResolvedClass,
    graph: EntityGraph[?],
    inject: [T] => Class[T] => T
  ): String =
    legacy(null, inject, null).serialize(instance, resolvedClass, Option(graph))

  private def legacy(loader: EntityLoader, inject: Inject, validator: Validator): JsonMapper =
    new JsonMapper(Option(validator), Option(loader).getOrElse(EntityLoader.none), inject)

}
