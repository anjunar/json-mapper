package com.anjunar.json.mapper.schema

import com.anjunar.scala.universe.TypeResolver

import java.lang.reflect.Modifier

/**
 * Marks the companion object of a class whose properties a schema describes. The schema is the class named by the
 * type argument, created on first use with no argument or with the described class; it may be declared anywhere.
 */
trait SchemaProvider[E <: BeanSchema[?]] {

  lazy val schema: E = SchemaProvider.instantiate(getClass).asInstanceOf[E]

}

object SchemaProvider {

  private def instantiate(providerClass: Class[?]): BeanSchema[?] = {
    val schemaClass = TypeResolver.resolve(providerClass).hierarchy
      .find(_.raw == classOf[SchemaProvider[?]])
      .flatMap(_.typeArguments.headOption)
      .map(_.raw)
      .filterNot(candidate => candidate.isInterface || Modifier.isAbstract(candidate.getModifiers))
      .getOrElse(throw new IllegalStateException(s"${providerClass.getName} names no concrete schema class"))

    val constructor = schemaClass.getConstructors.headOption.getOrElse {
      throw new IllegalStateException(s"No public constructor for ${schemaClass.getName}")
    }

    constructor.getParameterCount match {
      case 0 =>
        constructor.newInstance().asInstanceOf[BeanSchema[?]]
      case 1 =>
        // The application's classes may live in another class loader than this library, e.g. in an application server.
        val described = Class.forName(providerClass.getName.stripSuffix("$"), false, providerClass.getClassLoader)
        constructor.newInstance(described).asInstanceOf[BeanSchema[?]]
      case n =>
        throw new IllegalStateException(s"Unsupported constructor with $n parameters in ${schemaClass.getName}")
    }
  }

}
