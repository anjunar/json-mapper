package com.anjunar.json.mapper.schema

import com.anjunar.json.mapper.annotations.{JsonLdProperty, JsonLdType, JsonLdVocab}
import com.anjunar.json.mapper.macros.PropertyMacrosHelper
import com.anjunar.json.mapper.provider.DTO
import com.anjunar.json.mapper.schema.property.Property
import jakarta.json.bind.annotation.JsonbProperty

import scala.annotation.meta.field
import scala.collection.mutable

@JsonLdType("https://technologyspeaks.com/ns/Link")
@JsonLdVocab("https://technologyspeaks.com/ns/")
class Link(@(JsonbProperty @field) @field @JsonLdProperty("https://technologyspeaks.com/ns/rel") val rel: String,
           @(JsonbProperty @field) @field @JsonLdProperty("https://technologyspeaks.com/ns/url") val url: String,
           @(JsonbProperty @field) @field @JsonLdProperty("https://technologyspeaks.com/ns/method") val method: String,
           @(JsonbProperty @field) @field @JsonLdProperty("https://technologyspeaks.com/ns/id") val id: String) extends DTO
