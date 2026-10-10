package com.anjunar.json.mapper

import com.anjunar.scala.universe.ResolvedClass
import jakarta.persistence.EntityGraph
import java.util

class JavaContext(
  val resolvedClass: ResolvedClass,
  val graph: EntityGraph[?],
  val inject: [T] => Class[T] => T,
  val parent: JavaContext,
  val name: String
) {

  def path(): util.List[String] = {
    val parentPath = new util.ArrayList[String]()

    var cursor: JavaContext = this

    while (cursor != null) {
      cursor = cursor.parent
    }

    util.Collections.reverse(parentPath)
    parentPath
  }

}
