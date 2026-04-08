package com.anjunar.json.mapper.intermediate.model

import com.anjunar.json.mapper.intermediate.JsonGenerator

class JsonObject(
  override val value: java.util.Map[String, JsonNode] = new java.util.HashMap[String, JsonNode]()
) extends JsonNode {

  def encode(): String = JsonGenerator.generate(this)

  def getString(key: String): String = value.get(key).value.asInstanceOf[String]

  def getJsonObject(key: String): JsonObject = value.get(key).asInstanceOf[JsonObject]

  def put(key: String, node: JsonNode): JsonObject = {
    value.put(key, node)
    this
  }

  def put(key: String, valueString: String): JsonObject = {
    value.put(key, new JsonString(valueString))
    this
  }

  def put(key: String, valueBoolean: Boolean): JsonObject = {
    value.put(key, new JsonBoolean(valueBoolean))
    this
  }

  def put(key: String, valueNumber: Number): JsonObject = {
    value.put(key, new JsonNumber(valueNumber.toString))
    this
  }

}
