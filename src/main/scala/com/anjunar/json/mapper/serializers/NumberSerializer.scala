package com.anjunar.json.mapper.serializers

import com.anjunar.json.mapper.JavaContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonNumber}

class NumberSerializer extends Serializer[Number] {

  override def serialize(input: Number, context: JavaContext): JsonNode =
    input match {
      // Exact decimals keep their digits and scale: toString may switch to an exponent, e.g. 1E+3 for 1000.
      case value: java.math.BigDecimal => new JsonNumber(value.toPlainString)
      case value: scala.math.BigDecimal => new JsonNumber(value.bigDecimal.toPlainString)
      case other => new JsonNumber(other.toString)
    }

}
