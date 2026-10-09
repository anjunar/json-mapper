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
import java.util.UUID

class ReferenceAuthor extends EntityProvider {
  @JsonbProperty var id: UUID = UUID.randomUUID()
  @JsonbProperty var version: Long = 0L
  @JsonbProperty @Size(min = 1) var name: String = "Original"
  @JsonbProperty var internalNote: String = "Private"
  @JsonbProperty @OneToMany(mappedBy = "author") var posts: util.Set[ReferencePost] = new util.HashSet[ReferencePost]()
}

object ReferenceAuthor extends SchemaProvider[ReferenceAuthor.Schema] {
  class Schema extends EntitySchema[ReferenceAuthor] {
    val id = property(_.id)
    val version = property(_.version, classOf[DefaultWritableRule[ReferenceAuthor]])
    val name = property(_.name, classOf[DefaultWritableRule[ReferenceAuthor]])
    val internalNote = property(_.internalNote)
    val posts = property(_.posts)
  }
}

