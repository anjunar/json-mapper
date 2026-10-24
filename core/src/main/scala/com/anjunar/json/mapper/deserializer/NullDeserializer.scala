package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.JsonNode

class NullDeserializer extends Deserializer[Null] {

  override def deserialize(json: JsonNode, context: JsonContext): Null = null

}
