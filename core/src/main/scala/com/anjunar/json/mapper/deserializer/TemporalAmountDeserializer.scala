package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.DeserializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}
import java.time.{DateTimeException, Duration, Period}
import java.time.temporal.TemporalAmount

/** Reads a `Period` or a `Duration` in ISO-8601 form; a field declared as `TemporalAmount` takes a `Duration`. */
class TemporalAmountDeserializer extends Deserializer[TemporalAmount] {

  override def deserialize(json: JsonNode, context: DeserializationContext): TemporalAmount =
    json match {
      case value: JsonString =>
        try {
          if (classOf[Period].isAssignableFrom(context.resolvedClass.raw)) Period.parse(value.value)
          else Duration.parse(value.value)
        } catch {
          case _: DateTimeException => context.reject("This field expects an ISO-8601 duration.")
        }
      case _ => context.reject("This field expects an ISO-8601 duration.")
    }

}
