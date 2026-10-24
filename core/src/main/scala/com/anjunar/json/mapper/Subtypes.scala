package com.anjunar.json.mapper

import com.anjunar.json.mapper.intermediate.model.{JsonObject, JsonString}
import jakarta.json.bind.annotation.{JsonbSubtype, JsonbTypeInfo}
import java.lang.reflect.Modifier

/**
 * Which class a JSON object stands for when its field is declared with a base type, and which alias names a class in
 * JSON. A subtype is accepted only when it is registered, in one of two ways:
 *
 *   - the standard Jakarta way, `@JsonbTypeInfo(value = Array(new JsonbSubtype(...)))` on the base type, whose `key`
 *     names the JSON field that carries the alias, or
 *   - `@JsonbSubtype(alias = "Name", type = classOf[Name])` on the subtype itself, which must then live in the base
 *     type's package and use its simple name as alias. The field is `@type`.
 */
private[mapper] object Subtypes {

  private val defaultKey = "@type"

  private val aliasPattern = "[A-Za-z][A-Za-z0-9_]*"

  /** The JSON field that carries the alias for values declared with this type. */
  def key(declared: Class[?]): String =
    Option(typeInfo(declared)).map(_.key()).filter(_.nonEmpty).getOrElse(defaultKey)

  /** The class to create or load for an object node of a field declared with `declared`. */
  def resolve(node: JsonObject, declared: Class[?], context: DeserializationContext, at: Any*): Class[?] = {
    val info = typeInfo(declared)
    if (info == null && isConcrete(declared)) {
      return declared
    }
    val typeKey = key(declared)
    val alias = node.value.get(typeKey) match {
      case value: JsonString => value.value
      case null              => return declared
      case _                 => context.reject("The type of this value must be a string.", (at :+ typeKey)*)
    }
    val resolved =
      if (info != null) {
        info.value().find(_.alias() == alias).map(_.`type`()).orNull
      } else if (alias.matches(aliasPattern)) {
        conventional(declared, alias)
      } else {
        null
      }
    if (resolved == null || !declared.isAssignableFrom(resolved)) {
      context.reject("This type is not allowed here.", (at :+ typeKey)*)
    }
    resolved
  }

  /** A new instance of the resolved class, or a field error when the client did not say which subtype it means. */
  def instantiate(clazz: Class[?], context: DeserializationContext, at: Any*): Any = {
    if (!isConcrete(clazz)) {
      context.reject(s"This value needs a type in '${key(clazz)}'.", at*)
    }
    clazz.getConstructor().newInstance()
  }

  def isConcrete(clazz: Class[?]): Boolean =
    !clazz.isInterface && !Modifier.isAbstract(clazz.getModifiers)

  /** Whether a class is registered as a subtype of the declared type, directly or through `@JsonbTypeInfo`. */
  def registered(clazz: Class[?], declared: Class[?]): Boolean =
    clazz.getDeclaredAnnotation(classOf[JsonbSubtype]) != null ||
      Option(typeInfo(declared)).exists(_.value().exists(_.`type`() == clazz))

  /** The JSON field and alias to write for a value of this class. */
  def alias(clazz: Class[?]): (String, String) = {
    var current: Class[?] = clazz
    while (current != null && current != classOf[Object]) {
      val info = current.getDeclaredAnnotation(classOf[JsonbTypeInfo])
      if (info != null) {
        val entry = info.value().find(_.`type`().isAssignableFrom(clazz))
        if (entry.isDefined) {
          return (Option(info.key()).filter(_.nonEmpty).getOrElse(defaultKey), entry.get.alias())
        }
      }
      current = current.getSuperclass
    }
    val subtype = clazz.getAnnotation(classOf[JsonbSubtype])
    if (subtype != null) (defaultKey, subtype.alias())
    else (defaultKey, clazz.getSimpleName.replace("$HibernateProxy", ""))
  }

  private def typeInfo(declared: Class[?]): JsonbTypeInfo = {
    var current: Class[?] = declared
    while (current != null && current != classOf[Object]) {
      val info = current.getDeclaredAnnotation(classOf[JsonbTypeInfo])
      if (info != null) {
        return info
      }
      current = current.getSuperclass
    }
    null
  }

  private def conventional(declared: Class[?], alias: String): Class[?] = {
    val resolved =
      try Class.forName(s"${declared.getPackageName}.$alias", false, declared.getClassLoader)
      catch { case _: ClassNotFoundException => return null }
    val annotation = resolved.getAnnotation(classOf[JsonbSubtype])
    if (annotation == null || annotation.alias() != alias || annotation.`type`() != resolved) null else resolved
  }

}
