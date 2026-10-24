package com.anjunar.json.mapper.schema

import com.anjunar.scala.universe.TypeResolver

/** Mixed into the companion object of a class to name the EntitySchema that governs its JSON representation. */
trait SchemaProvider[E <: EntitySchema[?]] {

  /** The schema this provider's type argument names, created once through its public constructor. */
  lazy val schema: E = {
    val schemaClass = TypeResolver.resolve(getClass).hierarchy
      .find(_.raw == classOf[SchemaProvider[?]])
      .flatMap(_.typeArguments.headOption)
      .map(_.raw)
      .getOrElse(throw new IllegalStateException(s"${getClass.getName} does not name its EntitySchema type"))

    val constructor = schemaClass.getConstructors.headOption.getOrElse {
      throw new IllegalStateException(s"No public constructor for ${schemaClass.getName}")
    }

    constructor.getParameterCount match {
      case 0 =>
        constructor.newInstance().asInstanceOf[E]
      case 1 =>
        val entityType = Class.forName(getClass.getName.stripSuffix("$"))
        constructor.newInstance(entityType).asInstanceOf[E]
      case n =>
        throw new IllegalStateException(s"Unsupported constructor with $n parameters in ${schemaClass.getName}")
    }
  }

}
