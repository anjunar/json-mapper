package com.anjunar.json.mapper

import com.anjunar.json.mapper.deserializer.DeserializerRegistry
import com.anjunar.json.mapper.intermediate.JsonGenerator
import com.anjunar.json.mapper.intermediate.model.JsonNode
import com.anjunar.json.mapper.schema.{BeanSchema, JsonSchema, JsonSchemaDerivation}
import com.anjunar.json.mapper.schema.property.Property
import scala.jdk.CollectionConverters.*
import com.anjunar.scala.universe.introspector.AnnotationProperty
import java.lang.reflect.Type
import com.anjunar.json.mapper.serializers.{Serializer, SerializerRegistry}
import com.anjunar.scala.universe.ResolvedClass
import jakarta.persistence.EntityGraph
import jakarta.validation.Validator
import java.util

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

  def prepare[T](
    jsonNode: JsonNode,
    entity: T,
    resolvedClass: ResolvedClass,
    graph: EntityGraph[?],
    loader: EntityLoader,
    inject: [X] => Class[X] => X,
    validator: Validator
  ): PreparedChange[T] =
    new PreparedChange(entity, jsonNode, resolvedClass, graph, loader, inject, validator)

  def deserialize(
    jsonNode: JsonNode,
    instance: AnyRef,
    resolvedClass: ResolvedClass,
    graph: EntityGraph[?],
    loader: EntityLoader,
    inject: [T] => Class[T] => T,
    validator: Validator
  ): Any = {
    val deserializer = DeserializerRegistry.findDeserializer(resolvedClass.raw.asInstanceOf[Class[Any]], jsonNode)
    val context = new JsonContext(resolvedClass, instance, graph, loader, validator, inject, null, null)
    val deserialized = deserializer.deserialize(jsonNode, context)

    val errorRequests = new util.ArrayList[ErrorRequest]()
    val contexts = context.flatten().iterator()

    while (contexts.hasNext) {
      val current = contexts.next()
      val violations = current.violations.iterator()
      while (violations.hasNext) {
        val violation = violations.next()
        val path = new util.ArrayList[Any](current.pathWithIndexes())
        path.add(violation.getPropertyPath.toString)
        errorRequests.add(new ErrorRequest(path, violation.getMessage))
      }
    }

    if (errorRequests.isEmpty) {
      deserialized
    } else {
      throw new ErrorRequestException(errorRequests)
    }
  }

  def serialize(
    instance: Any,
    resolvedClass: ResolvedClass,
    graph: EntityGraph[?],
    inject: [T] => Class[T] => T
  ): String = serialize(instance, resolvedClass, graph, inject, null)

  /** A projection narrows output and retains required empty values; BeanSchema and graph rights still apply. */
  def serialize(
    instance: Any,
    resolvedClass: ResolvedClass,
    graph: EntityGraph[?],
    inject: [T] => Class[T] => T,
    projection: JsonSchema
  ): String = {
    val serializer = SerializerRegistry.find(resolvedClass.raw.asInstanceOf[Class[Any]], instance)
    val node = serializer.serialize(instance, new JavaContext(resolvedClass, graph, inject, null, null, projection))
    JsonGenerator.generate(node)
  }

}
