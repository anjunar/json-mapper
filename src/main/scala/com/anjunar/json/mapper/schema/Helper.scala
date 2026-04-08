package com.anjunar.json.mapper.schema

import com.anjunar.scala.universe.TypeResolver
import java.util

import java.lang.reflect.{ParameterizedType, Type}

object Helper {

  def entityType(propertyType: Type): Class[?] = {
    propertyType match {
      case clazz: Class[?] => clazz
      case parameterizedType: ParameterizedType if classOf[util.Collection[?]].isAssignableFrom(TypeResolver.rawType(parameterizedType)) =>
        TypeResolver.rawType(parameterizedType.getActualTypeArguments.head)
      case _ => null
    }
  }

}
