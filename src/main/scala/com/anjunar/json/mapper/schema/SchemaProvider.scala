package com.anjunar.json.mapper.schema

trait SchemaProvider[E <: EntitySchema[?]] {

  lazy val schema: E = {
    val providerClass = this.getClass
    val baseName = providerClass.getName.stripSuffix("$")
    val schemaClassName = s"${baseName}$$Schema"

    val schemaClass = try {
      Class.forName(schemaClassName)
    } catch {
      case _: ClassNotFoundException =>
        null
    }

    val ctor = schemaClass.getConstructors.headOption.getOrElse {
      throw new IllegalStateException(s"No public constructor for $schemaClassName")
    }

    ctor.getParameterCount match {
      case 0 =>
        ctor.newInstance().asInstanceOf[E]
      case 1 =>
        val entityType = Class.forName(baseName)
        ctor.newInstance(entityType).asInstanceOf[E]
      case n =>
        throw new IllegalStateException(s"Unsupported constructor with $n parameters in $schemaClassName")
    }
  }

}
