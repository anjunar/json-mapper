package com.anjunar.json.mapper.intermediate.model

class JsonArray(
  override val value: java.util.List[JsonNode] = new java.util.ArrayList[JsonNode]()
) extends JsonNode {

  def add(node: JsonNode): JsonArray = {
    value.add(node)
    this
  }

}
