package com.anjunar.json.mapper.intermediate.model

import java.util

class JsonArray(
  override val value: util.List[JsonNode] = new util.ArrayList[JsonNode]()
) extends JsonNode {

  def add(node: JsonNode): JsonArray = {
    value.add(node)
    this
  }

}
