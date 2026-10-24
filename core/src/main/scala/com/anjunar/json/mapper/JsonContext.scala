package com.anjunar.json.mapper

import com.anjunar.json.mapper.provider.DTO
import com.anjunar.scala.universe.ResolvedClass
import jakarta.persistence.EntityGraph
import jakarta.validation.{ConstraintViolation, Validator}
import java.util

class JsonContext(
  val resolvedClass: ResolvedClass,
  val instance: Any,
  val graph: EntityGraph[?],
  val loader: EntityLoader,
  val validator: Validator,
  val inject: [T] => Class[T] => T,
  val parent: JsonContext,
  val name: String,
  val index: Int = -1
) {

  if (parent != null) {
    parent.children.add(this)
  }

  val children: util.List[JsonContext] = new util.ArrayList[JsonContext]()

  val violations: util.Set[ConstraintViolation[?]] = new util.HashSet[ConstraintViolation[?]]()

  def flatten(): util.List[JsonContext] = {
    val result = new util.ArrayList[JsonContext]()
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

  def path(): util.List[String] = {
    val parentPath = new util.ArrayList[String]()

    var cursor: JsonContext = this

    while (cursor != null) {
      if (cursor.name != null) {
        parentPath.add(cursor.name)
      }
      cursor = cursor.parent
    }

    util.Collections.reverse(parentPath)
    parentPath
  }

  def pathWithIndexes(): util.List[Any] = {
    val parentPath = new util.ArrayList[Any]()

    var cursor: JsonContext = this

    while (cursor != null) {
      if (cursor.index > -1) {
        parentPath.add(Integer.valueOf(cursor.index))
      } else if (cursor.name != null) {
        parentPath.add(cursor.name)
      }
      cursor = cursor.parent
    }

    util.Collections.reverse(parentPath)
    parentPath
  }

}
