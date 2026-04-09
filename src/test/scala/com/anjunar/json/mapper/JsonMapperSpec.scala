package com.anjunar.json.mapper

import com.anjunar.json.mapper.annotations.{JsonLdId, JsonLdProperty, JsonLdType, JsonLdVocab}
import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.provider.DTO
import com.anjunar.json.mapper.schema.{EntitySchema, SchemaProvider}
import com.anjunar.scala.universe.TypeResolver
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.validation.executable.ExecutableValidator
import jakarta.validation.metadata.BeanDescriptor
import jakarta.validation.{ConstraintViolation, Path, Validator}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.lang.annotation.ElementType
import java.lang.reflect.{Constructor, Method}
import java.util
import java.util.UUID
import scala.annotation.meta.field

class JsonMapperSpec extends AnyFunSuite with Matchers {

  test("serialize should emit JSON for scalar nested and collection DTO properties") {
    val profile = new ProfileDto
    profile.name = "Patrick"
    profile.age = 34
    profile.active = true
    profile.nickname = "Pat"

    val primaryTag = new TagDto
    primaryTag.label = "backend"
    profile.primaryTag = primaryTag

    val listTag = new TagDto
    listTag.label = "scala"
    profile.tags.add(listTag)

    val json = JsonMapper.serialize(
      profile,
      TypeResolver.resolve(classOf[ProfileDto]),
      null,
      noInject
    )

    val parsed = JsonParser.parse(json).asInstanceOf[com.anjunar.json.mapper.intermediate.model.JsonObject]

    parsed.getString("name") shouldBe "Patrick"
    parsed.value.get("age").value shouldBe "34"
    parsed.value.get("active").value shouldBe true
    parsed.getString("nickname") shouldBe "Pat"
    parsed.getJsonObject("primaryTag").getString("label") shouldBe "backend"
    parsed.getJsonObject("primaryTag").getString("@type") shouldBe "TagDto"

    val tags = parsed.value.get("tags").asInstanceOf[com.anjunar.json.mapper.intermediate.model.JsonArray]
    tags.value.size() shouldBe 1
    tags.value.get(0).asInstanceOf[com.anjunar.json.mapper.intermediate.model.JsonObject].getString("label") shouldBe "scala"

    parsed.getString("@type") shouldBe "ProfileDto"
  }

  test("serialize should emit json-ld metadata from schema and java annotations") {
    val person = new LinkedDataProfileDto
    person.id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    person.name = "Patrick"

    val json = JsonMapper.serialize(
      person,
      TypeResolver.resolve(classOf[LinkedDataProfileDto]),
      null,
      noInject
    )

    val parsed = JsonParser.parse(json).asInstanceOf[com.anjunar.json.mapper.intermediate.model.JsonObject]
    val context = parsed.getJsonObject("@context")

    parsed.getString("@type") shouldBe "https://schema.org/Person"
    parsed.getString("@id") shouldBe "urn:uuid:550e8400-e29b-41d4-a716-446655440000"
    context.getString("@vocab") shouldBe "https://schema.org/"
    context.getString("name") shouldBe "https://schema.org/name"
    context.getString("homepage") shouldBe "https://schema.org/url"
  }

  test("deserialize should update scalar nested collection and loaded DTO properties") {
    val loadedTag = new TagDto
    loadedTag.label = "loaded"

    val profile = new ProfileDto
    profile.primaryTag = new TagDto

    val loaderId = UUID.randomUUID()
    val loader = new EntityLoader {
      override def load(id: UUID, clazz: Class[?]): Any =
        if (id == loaderId && clazz == classOf[TagDto]) loadedTag else null
    }

    val json =
      s"""{
         |  "name": "Updated",
         |  "nickname": "PJ",
         |  "primaryTag": {"label": "core"},
         |  "linkedTag": {"id": "$loaderId"},
         |  "tags": [
         |    {"label": "alpha"},
         |    {"label": "beta"}
         |  ]
         |}""".stripMargin

    val result = JsonMapper.deserialize(
      JsonParser.parse(json),
      profile,
      TypeResolver.resolve(classOf[ProfileDto]),
      null,
      loader,
      noInject,
      emptyValidator
    ).asInstanceOf[ProfileDto]

    result shouldBe profile
    result.name shouldBe "Updated"
    result.nickname shouldBe "PJ"
    result.primaryTag.label shouldBe "core"
    result.linkedTag shouldBe loadedTag
    result.tags.size() shouldBe 2
    result.tags.get(0).label shouldBe "alpha"
    result.tags.get(1).label shouldBe "beta"
  }

  test("deserialize should accept json-ld property aliases and @id") {
    val linked = new TagDto
    linked.label = "linked"

    val loaderId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    val loader = new EntityLoader {
      override def load(id: UUID, clazz: Class[?]): Any =
        if (id == loaderId && clazz == classOf[TagDto]) linked else null
    }

    val profile = new LinkedDataReadDto

    val json =
      s"""{
         |  "@context": {
         |    "@vocab": "https://schema.org/",
         |    "name": "https://schema.org/name"
         |  },
         |  "@type": "https://schema.org/Person",
         |  "https://schema.org/name": "Updated",
         |  "linkedTag": {
         |    "@id": "$loaderId"
         |  }
         |}""".stripMargin

    val result = JsonMapper.deserialize(
      JsonParser.parse(json),
      profile,
      TypeResolver.resolve(classOf[LinkedDataReadDto]),
      null,
      loader,
      noInject,
      emptyValidator
    ).asInstanceOf[LinkedDataReadDto]

    result.name shouldBe "Updated"
    result.linkedTag shouldBe linked
  }

  test("deserialize should strip json-ld id prefix for scalar id properties") {
    val dto = new LinkedDataProfileDto

    val json =
      """{
        |  "@id": "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
        |  "name": "Patrick"
        |}""".stripMargin

    val result = JsonMapper.deserialize(
      JsonParser.parse(json),
      dto,
      TypeResolver.resolve(classOf[LinkedDataProfileDto]),
      null,
      nullLoader,
      noInject,
      emptyValidator
    ).asInstanceOf[LinkedDataProfileDto]

    result.id shouldBe UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    result.name shouldBe "Patrick"
  }

  test("deserialize should aggregate validation errors into ErrorRequestException") {
    val profile = new ProfileDto
    profile.primaryTag = new TagDto

    val exception = the[ErrorRequestException] thrownBy JsonMapper.deserialize(
      JsonParser.parse("""{"name":"forbidden"}"""),
      profile,
      TypeResolver.resolve(classOf[ProfileDto]),
      null,
      nullLoader,
      noInject,
      validatorRejecting("name", "must not be forbidden")
    )

    exception.errors.size() shouldBe 1
    exception.errors.get(0).path shouldBe util.Arrays.asList("name")
    exception.errors.get(0).message shouldBe "must not be forbidden"
    profile.name shouldBe null
  }

  private val noInject: [T] => Class[T] => T =
    [T] => (_: Class[T]) => null.asInstanceOf[T]

  private val nullLoader = new EntityLoader {
    override def load(id: UUID, clazz: Class[?]): Any = null
  }

  private val emptyValidator: Validator = validatorRejecting("", "")

  private def validatorRejecting(propertyName: String, message: String): Validator =
    new Validator {
      override def validate[T](obj: T, groups: Class[?]*): util.Set[ConstraintViolation[T]] =
        util.Collections.emptySet()

      override def validateProperty[T](obj: T, propertyName: String, groups: Class[?]*): util.Set[ConstraintViolation[T]] =
        util.Collections.emptySet()

      override def validateValue[T](beanType: Class[T], candidateProperty: String, value: Any, groups: Class[?]*): util.Set[ConstraintViolation[T]] =
        if (candidateProperty == propertyName) {
          val violations = new util.HashSet[ConstraintViolation[T]]()
          violations.add(new SimpleConstraintViolation[T](candidateProperty, message))
          violations
        } else {
          util.Collections.emptySet()
        }

      override def getConstraintsForClass(clazz: Class[?]): BeanDescriptor = null

      override def unwrap[T](clazz: Class[T]): T = null.asInstanceOf[T]

      override def forExecutables(): ExecutableValidator = null
    }

  private class SimpleConstraintViolation[T](propertyName: String, overrideMessage: String) extends ConstraintViolation[T] {
    override def getMessage: String = overrideMessage
    override def getMessageTemplate: String = overrideMessage
    override def getRootBean: T = null.asInstanceOf[T]
    override def getRootBeanClass: Class[T] = null.asInstanceOf[Class[T]]
    override def getLeafBean: AnyRef = null
    override def getExecutableParameters: Array[AnyRef] = null
    override def getExecutableReturnValue: AnyRef = null
    override def getInvalidValue: AnyRef = null
    override def getPropertyPath: Path = new SimplePath(propertyName)
    override def getConstraintDescriptor = null
    override def unwrap[U](clazz: Class[U]): U = null.asInstanceOf[U]
  }

  private class SimplePath(pathValue: String) extends Path {
    override def iterator(): util.Iterator[Path.Node] = util.Collections.emptyIterator()
    override def toString: String = pathValue
  }
}

class ProfileDto {
  @(JsonbProperty @field) var name: String = null
  @(JsonbProperty @field) var age: Int = 0
  @(JsonbProperty @field) var active: Boolean = false
  @(JsonbProperty @field) var nickname: String = null
  @(JsonbProperty @field) var primaryTag: TagDto = null
  @(JsonbProperty @field) var linkedTag: TagDto = null
  @(JsonbProperty @field) var tags: java.util.List[TagDto] = new java.util.ArrayList[TagDto]()
}

class TagDto extends DTO {
  @(JsonbProperty @field) var label: String = null
}

@JsonLdType("https://schema.org/Person")
@JsonLdVocab("https://schema.org/")
class LinkedDataProfileDto extends DTO {
  @(JsonbProperty @field) @JsonLdId(prefix = "urn:uuid:") var id: UUID = null
  @(JsonbProperty @field) @JsonLdProperty("https://schema.org/name") var name: String = null
  @(JsonbProperty @field) @JsonLdProperty("https://schema.org/url") var homepage: String = null
}

object LinkedDataProfileDto extends SchemaProvider[EntitySchema[LinkedDataProfileDto]]:
  class Schema extends EntitySchema[LinkedDataProfileDto] {
    val id = property(_.id)
    val name = property(_.name)
    val homepage = property(_.homepage)
  }

class LinkedDataReadDto extends DTO {
  @(JsonbProperty @field) @JsonLdProperty("https://schema.org/name") var name: String = null
  @(JsonbProperty @field) var linkedTag: TagDto = null
}
