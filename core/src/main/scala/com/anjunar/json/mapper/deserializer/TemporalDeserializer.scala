package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}

import java.time.temporal.Temporal

class TemporalDeserializer extends Deserializer[Temporal] {

  override def deserialize(json: JsonNode, context: JsonContext): Temporal =
    json match {
      case value: JsonString =>
        val parseMethod = context.resolvedClass.raw.getMethods.find(method => method.getName == "parse")
        try {
          parseMethod.orNull.invoke(null, value.value).asInstanceOf[Temporal]
        } catch {
          case _: Exception => null
        }
      case _ =>
        throw new IllegalArgumentException("json must be a string")
    }

}
