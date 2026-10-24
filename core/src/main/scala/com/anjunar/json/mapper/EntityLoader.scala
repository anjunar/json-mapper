package com.anjunar.json.mapper

import java.util

trait EntityLoader {

  def load(id: util.UUID, clazz: Class[?]): Any

}
