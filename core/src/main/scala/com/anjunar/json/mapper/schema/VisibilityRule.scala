package com.anjunar.json.mapper.schema

import com.anjunar.scala.universe.introspector.AbstractProperty

trait VisibilityRule[E] {

  def isVisible(instance: E, property: AbstractProperty): Boolean

  def isWriteable(instance: E, property: AbstractProperty): Boolean

}
