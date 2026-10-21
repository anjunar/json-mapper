package com.anjunar.json.mapper.schema

import com.fasterxml.jackson.annotation.{JsonInclude, JsonProperty}
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.scala.DefaultScalaModule
import java.lang.{Boolean as JavaBoolean, Integer as JavaInteger, Long as JavaLong}
import java.util

/** JSON Schema protocol metadata. Entity values continue to use the entity/schema mapper. */
@JsonInclude(JsonInclude.Include.NON_NULL)
final case class JsonSchema(
  `type`: String = null,
  properties: util.Map[String, JsonSchema] = null,
  required: util.List[String] = null,
  additionalProperties: JavaBoolean | JsonSchema = null,
  items: JsonSchema = null,
  minLength: JavaInteger = null,
  maxLength: JavaInteger = null,
  minItems: JavaInteger = null,
  maxItems: JavaInteger = null,
  minProperties: JavaInteger = null,
  maxProperties: JavaInteger = null,
  minimum: Number = null,
  maximum: Number = null,
  pattern: String = null,
  format: String = null,
  `enum`: util.List[String] = null,
  uniqueItems: JavaBoolean = null,
  const: Object = null,
  anyOf: util.List[JsonSchema] = null,
  @JsonProperty("$ref") ref: String = null,
  @JsonProperty("$defs") definitions: util.Map[String, JsonSchema] = null,
  exclusiveMinimum: Number = null,
  exclusiveMaximum: Number = null,
  contentEncoding: String = null
)

object JsonSchema {
  private val writer = JsonMapper.builder().addModule(DefaultScalaModule).build()
  private val reader = JsonMapper.builder().build()

  /** Resolve only definitions embedded in this document; schema processing never fetches a URL. */
  def resolve(value: JsonSchema, root: JsonSchema): JsonSchema = {
    if (value.ref == null) value
    else {
      require(
        value.ref.startsWith("#/$defs/") && root.definitions != null,
        "Only embedded schema references are supported"
      )
      val resolved = root.definitions.get(value.ref.stripPrefix("#/$defs/"))
      require(resolved != null, "Missing embedded schema definition")
      resolved
    }
  }

  def encode(value: JsonSchema): String = writer.writeValueAsString(value)

  /** Java-native containers, suitable for protocol adapters that do not install Scala Jackson modules. */
  def toJavaMap(value: JsonSchema): util.Map[String, Object] =
    reader.readValue(encode(value), classOf[util.Map[String, Object]])
}
