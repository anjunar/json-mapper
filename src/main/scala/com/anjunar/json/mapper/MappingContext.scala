package com.anjunar.json.mapper

import com.anjunar.scala.universe.ResolvedClass
import jakarta.persistence.EntityGraph
import java.util

/** The position in the object graph that serialization and deserialization share: type, graph, parent and name. */
trait MappingContext {

  def resolvedClass: ResolvedClass

  def graph: EntityGraph[?]

  def inject: [T] => Class[T] => T

  def parent: MappingContext

  def name: String

  /** State shared by every context of one mapping call; children take it from their parent. */
  lazy val run: MappingRun = if (parent == null) new MappingRun(inject) else parent.run

  /** The property names from the root to this position, without collection indexes. */
  def path(): util.List[String] = {
    val result = new util.ArrayList[String]()

    var cursor: MappingContext = this

    while (cursor != null) {
      if (cursor.name != null) {
        result.add(cursor.name)
      }
      cursor = cursor.parent
    }

    util.Collections.reverse(result)
    result
  }

}
