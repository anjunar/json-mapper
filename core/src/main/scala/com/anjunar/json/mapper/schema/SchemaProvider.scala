package com.anjunar.json.mapper.schema

/**
 * Marks the companion object of a class whose properties a schema describes. The schema is the class `Schema` nested
 * in the companion, created on first use with no argument or with the described class.
 */
trait SchemaProvider[E <: BeanSchema[?]] {

  lazy val schema: E = SchemaProvider.instantiate(getClass).asInstanceOf[E]

}

object SchemaProvider {

  private def instantiate(providerClass: Class[?]): BeanSchema[?] = {
    val baseName = providerClass.getName.stripSuffix("$")
    val schemaClassName = s"$baseName$$Schema"
    // The application's classes may live in another class loader than this library, e.g. in an application server.
    val loader = providerClass.getClassLoader

    val schemaClass =
      try Class.forName(schemaClassName, true, loader)
      catch {
        case _: ClassNotFoundException =>
          throw new IllegalStateException(s"${providerClass.getName} provides no nested class Schema")
      }

    val constructor = schemaClass.getConstructors.headOption.getOrElse {
      throw new IllegalStateException(s"No public constructor for $schemaClassName")
    }

    constructor.getParameterCount match {
      case 0 =>
        constructor.newInstance().asInstanceOf[BeanSchema[?]]
      case 1 =>
        constructor.newInstance(Class.forName(baseName, false, loader)).asInstanceOf[BeanSchema[?]]
      case n =>
        throw new IllegalStateException(s"Unsupported constructor with $n parameters in $schemaClassName")
    }
  }

}
