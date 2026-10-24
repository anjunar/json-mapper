package com.anjunar.json.mapper.schema

import com.anjunar.json.mapper.provider.DTO
import jakarta.json.bind.annotation.JsonbProperty

import scala.annotation.meta.field

class Link(
  @(JsonbProperty @field) val rel: String,
  @(JsonbProperty @field) val url: String,
  @(JsonbProperty @field) val method: String,
  @(JsonbProperty @field)("@type") val id: String
) extends DTO
