package com.anjunar.json.mapper.converter

import com.anjunar.json.mapper.ObjectMapperProvider
import com.anjunar.scala.universe.ResolvedClass

class JacksonJsonConverter extends Converter {

  override def toJson(input: Any, resolvedClass: ResolvedClass): String =
    ObjectMapperProvider.mapper.writeValueAsString(input)

  override def toJava(json: String, resolvedClass: ResolvedClass): Any =
    ObjectMapperProvider.mapper.readerFor(resolvedClass.raw).readValue[Any](json)

}
