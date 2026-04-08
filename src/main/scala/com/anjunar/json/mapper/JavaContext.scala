package com.anjunar.json.mapper

import com.anjunar.scala.universe.ResolvedClass
import jakarta.persistence.EntityGraph

class JavaContext(
  val resolvedClass: ResolvedClass,
  val graph: EntityGraph[?],
  val inject : [T] => Class[T] => T,               
  val parent: JavaContext,
  val name: String
) {

  def path(): java.util.List[String] = {
    val parentPath = new java.util.ArrayList[String]()

    var cursor: JavaContext = this

    while (cursor != null) {
      cursor = cursor.parent
    }

    java.util.Collections.reverse(parentPath)
    parentPath
  }

}
