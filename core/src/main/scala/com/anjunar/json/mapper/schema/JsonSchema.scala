package com.anjunar.json.mapper.schema

import com.anjunar.json.mapper.intermediate.JsonGenerator
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonBoolean, JsonNode, JsonNumber, JsonObject, JsonString}
import java.math.{BigDecimal as JavaBigDecimal}
import java.lang.{Boolean as JavaBoolean, Integer as JavaInteger}
import java.util

/** JSON Schema protocol metadata. Entity values continue to use the entity/schema mapper. */
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
  ref: String = null,
  definitions: util.Map[String, JsonSchema] = null,
  exclusiveMinimum: Number = null,
  exclusiveMaximum: Number = null,
  contentEncoding: String = null
)

object JsonSchema {

  /** JSON keywords whose names are no valid Scala identifiers. */
  private val keywords = Map("ref" -> "$ref", "definitions" -> "$defs")

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

  def encode(value: JsonSchema): String = JsonGenerator.generate(node(toJavaMap(value)))

  /**
   * Java-native containers, suitable for protocol adapters that do not install Scala Jackson modules. Keywords keep
   * the declaration order of this class, unset ones are left out.
   */
  def toJavaMap(value: JsonSchema): util.Map[String, Object] = {
    val result = new util.LinkedHashMap[String, Object]()
    value.productElementNames.zip(value.productIterator).foreach { (name, element) =>
      if (element != null) {
        result.put(keywords.getOrElse(name, name), java(element))
      }
    }
    result
  }

  private def java(value: Any): Object =
    value match {
      case schema: JsonSchema  => toJavaMap(schema)
      case map: util.Map[?, ?] =>
        val result = new util.LinkedHashMap[String, Object]()
        map.forEach((key, element) => result.put(key.toString, java(element)))
        result
      case list: util.List[?] =>
        val result = new util.ArrayList[Object]()
        list.forEach(element => result.add(java(element)))
        result
      case other => other.asInstanceOf[Object]
    }

  private def node(value: Any): JsonNode =
    value match {
      case map: util.Map[?, ?] =>
        val result = new JsonObject()
        map.forEach((key, element) => result.put(key.toString, node(element)))
        result
      case list: util.List[?] =>
        val result = new JsonArray()
        list.forEach(element => result.add(node(element)))
        result
      case text: String            => new JsonString(text)
      case flag: JavaBoolean       => new JsonBoolean(flag)
      case decimal: JavaBigDecimal => new JsonNumber(decimal.toPlainString)
      case number: Number          => new JsonNumber(number.toString)
      case other                   => throw new IllegalStateException(s"No JSON form for schema value $other")
    }
}
