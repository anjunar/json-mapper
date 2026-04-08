package com.anjunar.json.mapper.converter

import com.anjunar.scala.universe.ResolvedClass

trait Converter {

  def toJson(input: Any, resolvedClass: ResolvedClass): String

  def toJava(json: String, resolvedClass: ResolvedClass): Any

}
