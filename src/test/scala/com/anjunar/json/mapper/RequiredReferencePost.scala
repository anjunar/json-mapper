package com.anjunar.json.mapper

import com.anjunar.json.mapper.annotations.JsonbAnyProperty
import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.provider.{DTO, EntityProvider}
import com.anjunar.json.mapper.schema.{DefaultWritableRule, EntitySchema, SchemaProvider}
import com.anjunar.scala.universe.TypeResolver
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.persistence.{AttributeNode, EntityGraph, ManyToOne, OneToMany, OneToOne, Subgraph}
import jakarta.validation.Validation
import jakarta.validation.constraints.{NotNull, Size}
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import java.lang.reflect.Proxy
import java.util

class RequiredReferencePost {
  @JsonbProperty @NotNull var author: ReferenceAuthor = new ReferenceAuthor()
}
