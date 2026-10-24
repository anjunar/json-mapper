package com.anjunar.json.mapper.deserializer

import com.anjunar.json.mapper.DeserializationContext
import com.anjunar.json.mapper.intermediate.model.{JsonNode, JsonString}
import java.lang.reflect.{InvocationTargetException, Method, Modifier}
import java.time.DateTimeException
import java.time.temporal.Temporal

/** Reads any `java.time` value through its static `parse(CharSequence)`, which accepts the ISO-8601 form. */
class TemporalDeserializer extends Deserializer[Temporal] {

  override def deserialize(json: JsonNode, context: DeserializationContext): Temporal =
    json match {
      case value: JsonString =>
        try {
          TemporalDeserializer.parsers.get(context.resolvedClass.raw).invoke(null, value.value).asInstanceOf[Temporal]
        } catch {
          case failure: InvocationTargetException if failure.getCause.isInstanceOf[DateTimeException] =>
            context.reject(TemporalDeserializer.expectation)
        }
      case _ =>
        context.reject(TemporalDeserializer.expectation)
    }

}

private object TemporalDeserializer {

  private val expectation = "This field expects a date or time in ISO-8601 format."

  private val parsers: ClassValue[Method] = new ClassValue[Method] {
    override def computeValue(raw: Class[?]): Method = {
      val method =
        try raw.getMethod("parse", classOf[CharSequence])
        catch {
          case _: NoSuchMethodException =>
            throw new IllegalStateException(s"${raw.getName} has no static parse(CharSequence) to read it from JSON")
        }
      if (!Modifier.isStatic(method.getModifiers)) {
        throw new IllegalStateException(s"${raw.getName} has no static parse(CharSequence) to read it from JSON")
      }
      method
    }
  }

}
