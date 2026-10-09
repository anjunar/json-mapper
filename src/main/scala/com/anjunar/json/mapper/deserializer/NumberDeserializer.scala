package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonNumber, JsonString}

class NumberDeserializer extends Deserializer[Number] {

  override def deserialize(json: JsonNode, context: JsonContext): Number =
    json match {
      case value: JsonNumber =>
        // The type resolver reports Scala's Int as java.lang.Integer, a Java field declares int: both mean the same.
        val raw = context.resolvedClass.raw
        if (raw == classOf[Int] || raw == classOf[java.lang.Integer]) {
          Integer.valueOf(value.value.toInt)
        } else if (raw == classOf[Long] || raw == classOf[java.lang.Long]) {
          java.lang.Long.valueOf(value.value.toLong)
        } else if (raw == classOf[Short] || raw == classOf[java.lang.Short]) {
          java.lang.Short.valueOf(value.value.toShort)
        } else if (raw == classOf[Byte] || raw == classOf[java.lang.Byte]) {
          java.lang.Byte.valueOf(value.value.toByte)
        } else if (raw == classOf[Float] || raw == classOf[java.lang.Float]) {
          java.lang.Float.valueOf(value.value.toFloat)
        } else if (raw == classOf[Double] || raw == classOf[java.lang.Double]) {
          java.lang.Double.valueOf(value.value.toDouble)
        } else if (NumberDeserializer.decimal(raw)) {
          NumberDeserializer.exact(value.value, raw)
        } else if (value.value.contains(".")) {
          java.lang.Double.valueOf(value.value.toDouble)
        } else {
          java.lang.Long.valueOf(value.value.toLong)
        }
      // Clients that cannot hold an exact decimal as a number, such as JavaScript, may send its digits as a string.
      case value: JsonString if NumberDeserializer.decimal(context.resolvedClass.raw) =>
        NumberDeserializer.exact(value.value.trim, context.resolvedClass.raw)
      case _ =>
        throw new IllegalArgumentException("json must be a number")
    }

}

object NumberDeserializer {

  /** Exact decimal types: read from the digits as written, never through a binary floating-point value. */
  def decimal(raw: Class[?]): Boolean =
    raw == classOf[java.math.BigDecimal] || raw == classOf[scala.math.BigDecimal]

  private def exact(digits: String, raw: Class[?]): Number = {
    val value =
      try new java.math.BigDecimal(digits)
      catch { case _: NumberFormatException => throw new IllegalArgumentException(s"Not a decimal number: $digits") }
    if (raw == classOf[scala.math.BigDecimal]) scala.math.BigDecimal(value) else value
  }

}
