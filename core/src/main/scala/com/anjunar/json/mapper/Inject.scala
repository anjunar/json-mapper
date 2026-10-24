package com.anjunar.json.mapper

/** Creates the rule classes a schema names, typically through the application's dependency injection. */
type Inject = [T] => Class[T] => T

object Inject {

  /** Calls the public no-argument constructor. */
  val constructor: Inject = [T] => (clazz: Class[T]) => clazz.getDeclaredConstructor().newInstance()

}
