package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.DeserializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonNumber, JsonString}
import java.lang.{Byte as JavaByte}
import java.lang.{Double as JavaDouble}
import java.lang.{Float as JavaFloat}
import java.lang.{Integer as JavaInteger}
import java.lang.{Long as JavaLong}
import java.lang.{Short as JavaShort}
import java.math.{BigDecimal as JavaBigDecimal, BigInteger}
import scala.math.BigDecimal

class NumberDeserializer extends Deserializer[Number] {

  override def deserialize(json: JsonNode, context: DeserializationContext): Number =
    json match {
      case value: JsonNumber =>
        try {
          NumberDeserializer.read(value.value, context.resolvedClass.raw)
        } catch {
          case _: NumberFormatException | _: ArithmeticException =>
            context.reject(NumberDeserializer.expectation(context.resolvedClass.raw))
        }
      // Clients that cannot hold an exact decimal as a number, such as JavaScript, may send its digits as a string.
      case value: JsonString if NumberDeserializer.decimal(context.resolvedClass.raw) =>
        try {
          NumberDeserializer.exact(value.value.trim, context.resolvedClass.raw)
        } catch {
          case _: NumberFormatException => context.reject("This field expects a decimal number.")
        }
      case _ =>
        context.reject(NumberDeserializer.expectation(context.resolvedClass.raw))
    }

}

object NumberDeserializer {

  private val whole: Set[Class[?]] = Set(
    classOf[Int],
    classOf[JavaInteger],
    classOf[Long],
    classOf[JavaLong],
    classOf[Short],
    classOf[JavaShort],
    classOf[Byte],
    classOf[JavaByte],
    classOf[BigInteger]
  )

  /** Declared types this deserializer reads: Java and Scala numbers, boxed or not. */
  def supports(raw: Class[?]): Boolean =
    classOf[Number].isAssignableFrom(raw) ||
      (raw.isPrimitive && raw != classOf[Boolean] && raw != classOf[Char] &&
        raw != classOf[Unit])

  /** Exact decimal types: read from the digits as written, never through a binary floating-point value. */
  def decimal(raw: Class[?]): Boolean =
    raw == classOf[JavaBigDecimal] || raw == classOf[BigDecimal]

  private def expectation(raw: Class[?]): String =
    if (whole(raw)) "This field expects a whole number in its range." else "This field expects a number."

  // The type resolver reports Scala's Int as java.lang.Integer, a Java field declares int: both mean the same.
  private def read(digits: String, raw: Class[?]): Number =
    if (raw == classOf[Int] || raw == classOf[JavaInteger]) {
      Integer.valueOf(digits.toInt)
    } else if (raw == classOf[Long] || raw == classOf[JavaLong]) {
      JavaLong.valueOf(digits.toLong)
    } else if (raw == classOf[Short] || raw == classOf[JavaShort]) {
      JavaShort.valueOf(digits.toShort)
    } else if (raw == classOf[Byte] || raw == classOf[JavaByte]) {
      JavaByte.valueOf(digits.toByte)
    } else if (raw == classOf[Float] || raw == classOf[JavaFloat]) {
      JavaFloat.valueOf(digits.toFloat)
    } else if (raw == classOf[Double] || raw == classOf[JavaDouble]) {
      JavaDouble.valueOf(digits.toDouble)
    } else if (raw == classOf[BigInteger]) {
      new JavaBigDecimal(digits).toBigIntegerExact
    } else if (decimal(raw)) {
      exact(digits, raw)
    } else if (digits.exists(c => c == '.' || c == 'e' || c == 'E')) {
      JavaDouble.valueOf(digits.toDouble)
    } else {
      // An untyped whole number stays exact even beyond the range of Long.
      val value = new BigInteger(digits)
      if (value.bitLength() < 64) JavaLong.valueOf(value.longValue()) else value
    }

  private def exact(digits: String, raw: Class[?]): Number = {
    val value = new JavaBigDecimal(digits)
    if (raw == classOf[BigDecimal]) BigDecimal(value) else value
  }

}
