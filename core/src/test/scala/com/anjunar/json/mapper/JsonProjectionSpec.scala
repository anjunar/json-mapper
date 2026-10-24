package com.anjunar.json.mapper

import com.anjunar.json.mapper.provider.{DTO, EntityProvider}
import com.anjunar.json.mapper.schema.{BeanSchema, JsonSchema, SchemaProvider, VisibilityRule}
import com.anjunar.scala.universe.TypeResolver
import com.anjunar.scala.universe.introspector.AbstractProperty
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.persistence.{AttributeNode, EntityGraph}
import jakarta.validation.constraints.NotNull
import org.scalatest.funsuite.AnyFunSuite
import tools.jackson.databind.json.JsonMapper as JacksonMapper
import java.lang.reflect.Proxy
import java.util
import scala.jdk.CollectionConverters.*

class JsonProjectionSpec extends AnyFunSuite {
  private val json = JacksonMapper.builder().build()
  private val inject = [T] => (clazz: Class[T]) => clazz.getDeclaredConstructor().newInstance()

  test("required empty values are serialized in one pass, including recursive collections and typed maps") {
    val value = new ProjectionEnvelope
    value.children.add(new ProjectionEnvelope)
    value.entries.put("nested", new ProjectionEnvelope)
    val schema = JsonMapper.schema(classOf[ProjectionEnvelope], _.name != "excluded")
    val output = json.readTree(new JsonMapper(inject =
      inject
    ).serialize(value, TypeResolver.resolve(value.getClass), projection = Option(schema)))
    assert(output.has("enabled") && !output.get("enabled").asBoolean())
    assert(output.has("label") && output.get("label").asString() == "")
    assert(output.at("/children/0/children").isArray() && output.at("/children/0/children").isEmpty())
    assert(output.at("/entries/nested/entries").isObject() && output.at("/entries/nested/entries").isEmpty())
    assert(!output.has("optional") && !output.has("excluded") && !output.has("@type"))
    assert(!output.at("/children/0").has("@type") && !output.at("/entries/nested").has("excluded"))
  }

  test("ordinary serialization retains its existing omission rules") {
    val value = new ProjectionEnvelope
    val output = json.readTree(new JsonMapper(inject = inject).serialize(value, TypeResolver.resolve(value.getClass)))
    assert(!output.has("enabled") && !output.has("label") && !output.has("children"))
    assert(output.path("excluded").asString() == "private")
    assert(output.path("@type").asString() == "ProjectionEnvelope")
  }

  test("a required field never overrides BeanSchema visibility") {
    val value = new ProjectionEntity
    val schema =
      JsonMapper.schema(classOf[ProjectionEntity]).copy(required = util.List.of("id", "version", "enabled", "secret"))
    val output = json.readTree(new JsonMapper(inject =
      inject
    ).serialize(value, TypeResolver.resolve(value.getClass), projection = Option(schema)))
    assert(output.has("enabled") && !output.get("enabled").asBoolean())
    assert(!output.has("secret") && schema.required.contains("secret"))
  }

  test("a required field never overrides an entity graph") {
    val node = Proxy.newProxyInstance(
      getClass.getClassLoader,
      Array(classOf[AttributeNode[?]]),
      (_, method, _) => if (method.getName == "getAttributeName") "id" else null
    ).asInstanceOf[AttributeNode[?]]
    val graph = Proxy.newProxyInstance(
      getClass.getClassLoader,
      Array(classOf[EntityGraph[?]]),
      (proxy, method, args) =>
        method.getName match {
          case "getAttributeNodes" => util.List.of(node)
          case "hashCode"          => Int.box(System.identityHashCode(proxy))
          case "equals"            => Boolean.box(proxy eq args(0))
          case _                   => null
        }
    ).asInstanceOf[EntityGraph[?]]
    val value = new ProjectionEntity
    val output = json.readTree(new JsonMapper(inject = inject).serialize(
      value,
      TypeResolver.resolve(value.getClass),
      Option(graph),
      projection = Option(JsonMapper.schema(classOf[ProjectionEntity]).copy(required = util.List.of("id", "enabled")))
    ))
    assert(output.has("id") && !output.has("enabled") && !output.has("secret"))
  }
}

class ProjectionEnvelope extends DTO {
  @JsonbProperty @NotNull var enabled: Boolean = false
  @JsonbProperty("label") @NotNull var title: String = ""
  @JsonbProperty @NotNull var children: util.List[ProjectionEnvelope] = new util.ArrayList[ProjectionEnvelope]()
  @JsonbProperty @NotNull var entries: util.Map[String, ProjectionEnvelope] =
    new util.LinkedHashMap[String, ProjectionEnvelope]()
  @JsonbProperty var optional: Boolean = false
  @JsonbProperty var excluded: String = "private"
}

class ProjectionEntity extends DTO with EntityProvider {
  @JsonbProperty var id: util.UUID = util.UUID.randomUUID()
  @JsonbProperty var version: Long = 0L
  @JsonbProperty @NotNull var enabled: Boolean = false
  @JsonbProperty @NotNull var secret: String = "private"
}
object ProjectionEntity extends SchemaProvider[ProjectionEntity.Schema] {
  class Schema extends BeanSchema[ProjectionEntity] {
    val id = property(_.id)
    val version = property(_.version)
    val enabled = property(_.enabled)
    val secret = property(_.secret, classOf[ProjectionHiddenRule])
  }
}
class ProjectionHiddenRule extends VisibilityRule[ProjectionEntity] {
  def isVisible(instance: ProjectionEntity, property: AbstractProperty): Boolean = false
  def isWriteable(instance: ProjectionEntity, property: AbstractProperty): Boolean = false
}
