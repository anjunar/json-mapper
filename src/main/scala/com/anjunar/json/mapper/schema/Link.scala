package com.anjunar.json.mapper.schema

import com.anjunar.json.mapper.macros.PropertyMacrosHelper
import com.anjunar.json.mapper.provider.DTO
import com.anjunar.json.mapper.schema.property.Property
import jakarta.json.bind.annotation.JsonbProperty

import scala.annotation.meta.field
import scala.collection.mutable

class Link(@(JsonbProperty @field) val rel: String,
           @(JsonbProperty @field) val url: String,
           @(JsonbProperty @field) val method: String,
           @(JsonbProperty @field)("@type") val id: String) extends DTO