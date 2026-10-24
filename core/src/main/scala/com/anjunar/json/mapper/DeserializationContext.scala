package com.anjunar.json.mapper

import com.anjunar.scala.universe.ResolvedClass
import jakarta.persistence.EntityGraph
import jakarta.validation.Validator
import java.util

/** The position of one JSON value while it is merged into the Java object graph. */
class DeserializationContext(
  val resolvedClass: ResolvedClass,
  val instance: Any,
  val graph: EntityGraph[?],
  val loader: EntityLoader,
  val validator: Validator,
  val inject: [T] => Class[T] => T,
  val parent: DeserializationContext,
  val name: String,
  val index: Int = -1
) extends MappingContext {

  private[mapper] val run: MappingRun = if (parent == null) new MappingRun(inject) else parent.run

  /** A context for a value below this one. */
  def child(
    resolvedClass: ResolvedClass,
    instance: Any,
    name: String = this.name,
    index: Int = -1
  ): DeserializationContext =
    new DeserializationContext(resolvedClass, instance, graph, loader, validator, inject, this, name, index)

  /**
   * Validates a value against the constraints of a property. Without violations the write runs once the whole
   * document is known to be valid; with violations they are reported at this context's path and nothing is written.
   */
  def checkForViolations(clazz: Class[?], name: String, value: Any, callback: () => Unit): Unit = {
    val checked = validator.validateValue(clazz, name, value)
    if (checked.isEmpty) {
      run.defer(callback)
    } else {
      checked.forEach { violation =>
        val path = new util.ArrayList[Any](pathWithIndexes())
        path.add(violation.getPropertyPath.toString)
        run.reject(new ErrorRequest(path, violation.getMessage))
      }
    }
  }

  /** Rejects the value at this context, or at a position below it, as invalid input. */
  private[mapper] def reject(message: String, below: Any*): Nothing = {
    val path = new util.ArrayList[Any](pathWithIndexes())
    below.foreach(path.add)
    throw new InvalidValue(path, message)
  }

}
