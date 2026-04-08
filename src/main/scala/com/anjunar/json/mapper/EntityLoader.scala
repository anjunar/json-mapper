package com.anjunar.json.mapper

import java.util.UUID

trait EntityLoader {

  def load(id: UUID, clazz: Class[?]): Any

}
