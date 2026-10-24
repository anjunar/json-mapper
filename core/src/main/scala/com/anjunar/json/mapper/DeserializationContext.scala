package com.anjunar.json.mapper

import com.anjunar.scala.universe.ResolvedClass
import jakarta.persistence.EntityGraph
import jakarta.validation.{ConstraintViolation, Validator}
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

  if (parent != null) {
    parent.children.add(this)
  }

  val children: util.List[DeserializationContext] = new util.ArrayList[DeserializationContext]()

  val violations: util.Set[ConstraintViolation[?]] = new util.HashSet[ConstraintViolation[?]]()

  /** A context for a value below this one. */
  def child(
    resolvedClass: ResolvedClass,
    instance: Any,
    name: String = this.name,
    index: Int = -1
  ): DeserializationContext =
    new DeserializationContext(resolvedClass, instance, graph, loader, validator, inject, this, name, index)

  def flatten(): util.List[DeserializationContext] = {
    val result = new util.ArrayList[DeserializationContext]()
    result.add(this)

    val iterator = children.iterator()
    while (iterator.hasNext) {
      result.addAll(iterator.next().flatten())
    }

    result
  }

  def checkForViolations(clazz: Class[?], name: String, value: Any, callback: () => Unit): Unit = {
    val checked = validator.validateValue(clazz, name, value)
    if (!checked.isEmpty) {
      violations.addAll(checked)
    } else {
      callback()
    }
  }

}
