package com.anjunar.json.mapper.schema

import com.anjunar.scala.universe.TypeResolver
import java.util

import java.lang.reflect.{ParameterizedType, Type}

@deprecated("Unused by the mapper; it will be removed in 2.0", "1.2.0")
object Helper {

  def entityType(propertyType: Type): Class[?] = {
    propertyType match {
      case clazz: Class[?] => clazz
      case parameterizedType: ParameterizedType
          if classOf[util.Collection[?]].isAssignableFrom(TypeResolver.rawType(parameterizedType)) =>
        TypeResolver.rawType(parameterizedType.getActualTypeArguments.head)
      case _ => null
    }
  }

}
