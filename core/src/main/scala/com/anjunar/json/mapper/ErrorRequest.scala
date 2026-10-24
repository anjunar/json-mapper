package com.anjunar.json.mapper

import jakarta.json.bind.annotation.JsonbProperty
import scala.annotation.meta.field
import scala.beans.BeanProperty
import java.util

class ErrorRequest(
  @(JsonbProperty @field) val path: util.List[Any],
  @(JsonbProperty @field) val message: String
)
