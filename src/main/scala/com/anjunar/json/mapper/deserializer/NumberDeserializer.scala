package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonNumber}

class NumberDeserializer extends Deserializer[Number] {

  override def deserialize(json: JsonNode, context: JsonContext): Number =
    json match {
      case value: JsonNumber =>
        if (context.resolvedClass.raw == classOf[Int]) {
          Integer.valueOf(value.value.toInt)
        } else if (context.resolvedClass.raw == classOf[Long]) {
          java.lang.Long.valueOf(value.value.toLong)
        } else if (context.resolvedClass.raw == classOf[Float]) {
          java.lang.Float.valueOf(value.value.toFloat)
        } else if (context.resolvedClass.raw == classOf[Double]) {
          java.lang.Double.valueOf(value.value.toDouble)
        } else if (value.value.contains(".")) {
          java.lang.Double.valueOf(value.value.toDouble)
        } else {
          java.lang.Long.valueOf(value.value.toLong)
        }
      case _ =>
        throw new IllegalArgumentException("json must be a string")
    }

}
