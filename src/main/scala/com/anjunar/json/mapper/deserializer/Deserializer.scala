package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.JsonNode

trait Deserializer[T] {

  def deserialize(json: JsonNode, context: JsonContext): T

}
