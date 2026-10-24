package com.anjunar.json.mapper

import com.anjunar.scala.universe.ResolvedClass
import jakarta.persistence.EntityGraph
import java.util

/** Where a value sits in the document and which entity graph governs it. Both mapping directions share this. */
trait MappingContext {

  def resolvedClass: ResolvedClass

  def graph: EntityGraph[?]

  def parent: MappingContext

  /** The property this value belongs to. Collection elements and map values repeat the owning property's name. */
  def name: String

  /** The position in a collection, or -1. */
  def index: Int

  /** State shared by every context of one mapping call. */
  private[mapper] def run: MappingRun

  /** Property names and collection positions from the root, as reported to clients. */
  def pathWithIndexes(): util.List[Any] = {
    val result = new util.ArrayList[Any]()
    var cursor: MappingContext = this
    while (cursor != null) {
      if (cursor.index > -1) {
        result.add(Integer.valueOf(cursor.index))
      } else if (cursor.name != null) {
        result.add(cursor.name)
      }
      cursor = cursor.parent
    }
    util.Collections.reverse(result)
    result
  }

  /** Property names from the root, without collection positions. */
  def path(): util.List[String] = {
    val result = new util.ArrayList[String]()
    pathWithIndexes().forEach {
      case name: String => result.add(name)
      case _            => ()
    }
    result
  }

}
