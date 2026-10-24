package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.JsonContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonNumber, JsonString}
import java.lang.{Byte as JavaByte}
import java.lang.{Double as JavaDouble}
import java.lang.{Float as JavaFloat}
import java.lang.{Integer as JavaInteger}
import java.lang.{Long as JavaLong}
import java.lang.{Short as JavaShort}
import java.math.{BigDecimal as JavaBigDecimal}
import scala.math.BigDecimal

class NumberDeserializer extends Deserializer[Number] {

  override def deserialize(json: JsonNode, context: JsonContext): Number =
    json match {
      case value: JsonNumber =>
        // The type resolver reports Scala's Int as java.lang.Integer, a Java field declares int: both mean the same.
        val raw = context.resolvedClass.raw
        if (raw == classOf[Int] || raw == classOf[JavaInteger]) {
          Integer.valueOf(value.value.toInt)
        } else if (raw == classOf[Long] || raw == classOf[JavaLong]) {
          JavaLong.valueOf(value.value.toLong)
        } else if (raw == classOf[Short] || raw == classOf[JavaShort]) {
          JavaShort.valueOf(value.value.toShort)
        } else if (raw == classOf[Byte] || raw == classOf[JavaByte]) {
          JavaByte.valueOf(value.value.toByte)
        } else if (raw == classOf[Float] || raw == classOf[JavaFloat]) {
          JavaFloat.valueOf(value.value.toFloat)
        } else if (raw == classOf[Double] || raw == classOf[JavaDouble]) {
          JavaDouble.valueOf(value.value.toDouble)
        } else if (NumberDeserializer.decimal(raw)) {
          NumberDeserializer.exact(value.value, raw)
        } else if (value.value.contains(".")) {
          JavaDouble.valueOf(value.value.toDouble)
        } else {
          JavaLong.valueOf(value.value.toLong)
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
    raw == classOf[JavaBigDecimal] || raw == classOf[BigDecimal]

  private def exact(digits: String, raw: Class[?]): Number = {
    val value =
      try new JavaBigDecimal(digits)
      catch { case _: NumberFormatException => throw new IllegalArgumentException(s"Not a decimal number: $digits") }
    if (raw == classOf[BigDecimal]) BigDecimal(value) else value
  }

}
