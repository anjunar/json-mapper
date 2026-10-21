package com.anjunar.json.mapper.schema

import com.anjunar.json.mapper.JsonPatch
import com.anjunar.json.mapper.provider.{DTO, EntityProvider}
import com.anjunar.scala.universe.{ResolvedClass, TypeResolver}
import com.anjunar.scala.universe.introspector.{AnnotationIntrospector, AnnotationProperty}
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.persistence.{Column, NamedEntityGraph, NamedEntityGraphs, Version}
import jakarta.validation.constraints.{AssertFalse, AssertTrue, DecimalMax, DecimalMin, Max, Min, NotBlank, NotNull, Pattern, Size}
import java.lang.{Boolean as JavaBoolean, Byte as JavaByte, Short as JavaShort, Integer as JavaInteger, Long as JavaLong}
import java.lang.reflect.Type
import java.time.temporal.{TemporalAccessor, TemporalAmount}
import java.math.{BigDecimal as JavaBigDecimal}
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util
import scala.jdk.CollectionConverters.*

/** Static JSON contract metadata. Runtime EntitySchema rules still decide every actual read and write. */
object JsonSchemaDerivation {
  def obj(fields: Seq[(String, JsonSchema)], required: Seq[String] = Seq.empty): JsonSchema = JsonSchema(
    "object",
    new util.LinkedHashMap(fields.toMap.asJava),
    required.asJava,
    Boolean.box(false)
  )

  def properties(target: Type): Seq[AnnotationProperty] =
    AnnotationIntrospector.create(TypeResolver.resolve(target), classOf[JsonbProperty]).properties.toSeq

  def name(property: AnnotationProperty): String = Option(property.findAnnotation(classOf[JsonbProperty]).value())
    .filter(_.nonEmpty)
    .getOrElse(property.name)

  def model(
    target: Type,
    select: AnnotationProperty => Boolean = _ => true,
    entityIdentity: Boolean = true
  ): JsonSchema = {
    val context = new Context
    val resolved = TypeResolver.resolve(target)
    val result = if (classOf[DTO].isAssignableFrom(resolved.raw) || properties(target).nonEmpty)
      model(target, select, entityIdentity, context)
    else value(resolved, context)
    context.complete(result)
  }

  private final class Context {
    val active = scala.collection.mutable.Set.empty[String]
    val referenced = scala.collection.mutable.Set.empty[String]
    val definitions = new util.LinkedHashMap[String, JsonSchema]()
    def id(key: String): String = "type_" +
      MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)).take(12).map(byte =>
        f"${byte & 0xff}%02x"
      ).mkString
    def complete(value: JsonSchema): JsonSchema =
      if (definitions.isEmpty) value else value.copy(definitions = definitions)
  }

  private def model(
    target: Type,
    select: AnnotationProperty => Boolean,
    entityIdentity: Boolean,
    context: Context
  ): JsonSchema = {
    val key = target.getTypeName
    if (context.active(key)) {
      context.referenced.add(key)
      return JsonSchema(ref = "#/$defs/" + context.id(key))
    }
    context.active.add(key)
    val fields = properties(target).filter(select)
    require(
      fields.nonEmpty || classOf[DTO].isAssignableFrom(TypeResolver.resolve(target).raw),
      s"No JSON property metadata for ${TypeResolver.resolve(target).fullName}"
    )
    val entity = entityIdentity && classOf[EntityProvider].isAssignableFrom(TypeResolver.resolve(target).raw)
    val result = obj(
      fields.map(property => name(property) -> field(property, context)) ++
        Option.when(entity)("@type" -> JsonSchema("string")),
      if (entity) Seq("id", "version") else fields.filter(required).map(name)
    )
    context.active.remove(key)
    if (context.referenced.contains(key)) context.definitions.put(context.id(key), result)
    result
  }

  def field(property: AnnotationProperty): JsonSchema = {
    val context = new Context
    context.complete(field(property, context))
  }

  private def field(property: AnnotationProperty, context: Context): JsonSchema = {

    var schema = value(property.propertyType, context)
    Option(property.findAnnotation(classOf[Size])).foreach { size =>
      schema =
        schema.`type` match {
          case "string" =>
            schema.copy(minLength = Int.box(size.min()), maxLength = Int.box(size.max()))
          case "array" =>
            schema.copy(minItems = Int.box(size.min()), maxItems = Int.box(size.max()))
          case "object" =>
            schema.copy(minProperties = Int.box(size.min()), maxProperties = Int.box(size.max()))
          case _ =>
            throw IllegalArgumentException(s"Unsupported @Size property: ${property.name}")
        }
    }
    Option(property.findAnnotation(classOf[Column])).foreach { column =>
      if (schema.`type` == "string" && schema.format != "uuid" && schema.maxLength == null)
        schema = schema.copy(maxLength = Int.box(column.length()))
      else if (schema.`type` == "array" && schema.items.`type` == "string" && schema.items.format != "uuid")
        schema = schema.copy(items = schema.items.copy(maxLength = Int.box(column.length())))
    }
    Option(property.findAnnotation(classOf[Pattern])).foreach { rule =>
      require(rule.flags().isEmpty, "JSON Schema export does not support Java pattern flags")
      schema = schema.copy(pattern = s"^(?:${rule.regexp()})$$")
      if (rule.regexp().matches("[A-Z_]+(\\|[A-Z_]+)+"))
        schema = schema.copy(`enum` = rule.regexp().split('|').toSeq.asJava)
    }
    if (property.findAnnotation(classOf[NotBlank]) != null)
      schema = schema.copy(
        minLength = Int.box(math.max(1, Option(schema.minLength).map(_.intValue()).getOrElse(0))),
        pattern = Option(schema.pattern).getOrElse("\\S")
      )
    Option(property.findAnnotation(classOf[Min])).foreach(rule =>
      schema = schema.copy(minimum = Long.box(rule.value()))
    )
    Option(property.findAnnotation(classOf[Max])).foreach(rule =>
      schema = schema.copy(maximum = Long.box(rule.value()))
    )
    Option(property.findAnnotation(classOf[DecimalMin])).foreach { rule =>
      val bound = new JavaBigDecimal(rule.value())
      schema = if (rule.inclusive()) schema.copy(minimum = bound) else schema.copy(exclusiveMinimum = bound)
    }
    Option(property.findAnnotation(classOf[DecimalMax])).foreach { rule =>
      val bound = new JavaBigDecimal(rule.value())
      schema = if (rule.inclusive()) schema.copy(maximum = bound) else schema.copy(exclusiveMaximum = bound)
    }
    if (property.findAnnotation(classOf[Version]) != null)
      schema = schema.copy(minimum = Long.box(0))
    if (property.findAnnotation(classOf[AssertTrue]) != null)
      schema = schema.copy(const = Boolean.box(true))
    if (property.findAnnotation(classOf[AssertFalse]) != null)
      schema = schema.copy(const = Boolean.box(false))
    schema
  }

  private def required(property: AnnotationProperty): Boolean =
    property.findAnnotation(classOf[NotNull]) != null || property.findAnnotation(classOf[NotBlank]) != null

  private def value(target: ResolvedClass, context: Context): JsonSchema = {
    val raw = target.raw
    if (raw == classOf[String])
      JsonSchema("string")
    else if (raw == classOf[JsonPatch[?]]) {
      require(target.typeArguments.length == 1, "A patch needs its resolved target type")
      model(target.typeArguments(0).underlying, _ => true, false, context).copy(required = util.List.of[String]())
    } else if (raw == classOf[Object])
      JsonSchema()
    else if (raw == classOf[util.UUID])
      JsonSchema(
        "string",
        format = "uuid",
        pattern = "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$"
      )
    else if ((raw == classOf[JavaBoolean] || raw == classOf[Boolean]))
      JsonSchema("boolean")
    else if (
      Seq(
        classOf[JavaByte],
        classOf[JavaShort],
        classOf[JavaInteger],
        classOf[JavaLong],
        classOf[Byte],
        classOf[Short],
        classOf[Int],
        classOf[Long]
      ).contains(raw)
    )
      JsonSchema("integer")
    else if (classOf[Number].isAssignableFrom(raw) || raw == classOf[Float] || raw == classOf[Double])
      JsonSchema("number")
    else if (raw.isEnum)
      JsonSchema("string", `enum` = raw.getEnumConstants.toSeq.map(_.asInstanceOf[Enum[?]].name()).asJava)
    else if (raw == classOf[util.Locale])
      JsonSchema("string")
    else if (raw == classOf[Array[Byte]])
      JsonSchema("string", contentEncoding = "base64")
    else if (classOf[TemporalAccessor].isAssignableFrom(raw) || classOf[TemporalAmount].isAssignableFrom(raw))
      JsonSchema("string")
    else if (classOf[util.Collection[?]].isAssignableFrom(raw)) {
      require(target.typeArguments.length == 1, s"Collection has no resolved element type: ${target.name}")
      JsonSchema(
        "array",
        items = value(target.typeArguments(0), context),
        uniqueItems = if (classOf[util.Set[?]].isAssignableFrom(raw)) Boolean.box(true) else null
      )
    } else if (classOf[util.Map[?, ?]].isAssignableFrom(raw)) {
      require(
        target.typeArguments.length == 2 && target.typeArguments(0).raw == classOf[String],
        "JSON maps need resolved string keys"
      )
      JsonSchema("object", additionalProperties = value(target.typeArguments(1), context))
    } else
      model(target.underlying, _ => true, true, context)
  }

  /** Named graphs select persisted properties; explicit transient presentation fields may be added by the owner. */
  def graph(target: Type, graphName: String, presentation: Set[String] = Set.empty): Set[String] = {
    val metadata = TypeResolver.resolve(target)
    val graphs =
      Option(metadata.findAnnotation(classOf[NamedEntityGraphs])).toSeq.flatMap(_.value()) ++
        Option(metadata.findAnnotation(classOf[NamedEntityGraph])).toSeq
    val selected = graphs
      .find(_.name() == graphName)
      .getOrElse(throw IllegalArgumentException(s"Unknown graph: $graphName"))
    selected.attributeNodes().map(_.value()).toSet ++ presentation
  }

}
