package com.anjunar.json.mapper

import java.util

/**
 * Resolves an ID the client sent. This is the application's authorization boundary: load only what the caller may
 * reference, and return null to reject the ID.
 */
trait EntityLoader {

  def load(id: util.UUID, clazz: Class[?]): Any

}

object EntityLoader {

  /** Rejects every ID, for documents that must not reference anything. */
  val none: EntityLoader = (_, _) => null

}
