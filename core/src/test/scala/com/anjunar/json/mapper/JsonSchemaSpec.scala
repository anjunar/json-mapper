package com.anjunar.json.mapper

import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.provider.{DTO, EntityProvider}
import com.anjunar.json.mapper.schema.{DefaultWritableRule, BeanSchema, JsonSchema, SchemaProvider}
import com.anjunar.scala.universe.TypeResolver
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.validation.Validation
import jakarta.validation.constraints.{DecimalMax, DecimalMin, Max, Min, NotBlank, NotNull, Size}
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator
import org.scalatest.funsuite.AnyFunSuite
import tools.jackson.databind.json.JsonMapper as JacksonMapper
import java.math.BigDecimal as JavaBigDecimal
import java.util
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import com.anjunar.json.mapper.schema.JsonSchemaDerivation

class JsonSchemaSpec extends AnyFunSuite {
  test("scalar roots and empty command classes are supported without synthetic fields") {
    assert(JsonMapper.schema(classOf[String]).`type` == "string")
    assert(JsonMapper.schema(classOf[Double]).`type` == "number")
    val empty = JsonMapper.schema(classOf[SchemaEmptyCommand])
    assert(empty.`type` == "object" && empty.properties.isEmpty && empty.required.isEmpty)
  }
  private val jackson = JacksonMapper.builder().build()
  private val inject = [T] => (clazz: Class[T]) => clazz.getDeclaredConstructor().newInstance()

  test("schema uses inherited aliases, generic containers and validation metadata") {
    val schema = JsonMapper.schema(classOf[SchemaProbe])
    val value = jackson.readTree(JsonSchema.encode(schema))
    assert(value.at("/properties/label/maxLength").asInt() == 12)
    assert(!value.path("properties").has("caption"))
    assert(value.at("/properties/entries/additionalProperties/items/format").asText() == "uuid")
    assert(value.at("/properties/offset/minimum").asLong() == 0)
    assert(value.at("/properties/offset/maximum").asLong() == 8)
    assert(schema.required.asScala.contains("label"))
  }

  test("a converter property is described by its string wire form, not by its Java type") {
    val schema = jackson.readTree(JsonSchema.encode(JsonMapper.schema(classOf[ConvertedProfileDto])))
    assert(schema.at("/properties/primaryTag/type").asText() == "string")
    assert(!schema.at("/properties/primaryTag").has("properties"))
  }

  test("numbers, decimals, enums, sets and binary values match their JSON representations") {
    val schema = jackson.readTree(JsonSchema.encode(JsonMapper.schema(classOf[SchemaProbe])))
    assert(schema.at("/properties/price/type").asText() == "number")
    assert(schema.at("/properties/price/exclusiveMinimum").decimalValue() == new JavaBigDecimal("0.01"))
    assert(schema.at("/properties/price/maximum").decimalValue() == new JavaBigDecimal("999.99"))
    assert(schema.at("/properties/unit/enum").size() == TimeUnit.values().length)
    assert(schema.at("/properties/tags/uniqueItems").asBoolean())
    assert(schema.at("/properties/bytes/contentEncoding").asText() == "base64")
  }

  test("recursive object graphs use finite local references with resolvable definitions") {
    val text = JsonSchema.encode(JsonMapper.schema(classOf[SchemaRecursive]))
    val schema = jackson.readTree(text)
    val reference = schema.at("/properties/child/$ref").asText()
    assert(reference.startsWith("#/$defs/"))
    assert(schema.at(reference.drop(1)).path("properties").has("name"))
    assert(text.length < 6000)
    assert(JsonSchema.encode(JsonMapper.schema(classOf[SchemaRecursive])) == text)
  }

  test("protocol maps contain Java-native empty objects and arrays without Scala bean properties") {
    val empty = JsonSchemaDerivation.obj(Seq.empty)
    val encoded = jackson.readTree(jackson.writeValueAsString(JsonSchema.toJavaMap(empty)))
    assert(encoded.path("properties").isObject() && encoded.path("properties").isEmpty())
    assert(encoded.path("required").isArray() && encoded.path("required").isEmpty())
    assert(!encoded.path("additionalProperties").asBoolean(true))
  }

  test("typed partial documents defer all entity changes and preserve field rights and omitted values") {
    val factory = Validation.byDefaultProvider().configure().messageInterpolator(
      new ParameterMessageInterpolator
    ).buildValidatorFactory()
    try {
      val command = JsonMapper.deserialize(
        JsonParser.parse("""{"changes":{"title":"","protectedValue":"attack"}}"""),
        new SchemaPatchCommand,
        TypeResolver.resolve(classOf[SchemaPatchCommand]),
        null,
        null,
        inject,
        factory.getValidator
      ).asInstanceOf[SchemaPatchCommand]
      val target = new SchemaPatchTarget
      assert(target.title == "old" && target.protectedValue == "retained" && target.description == "untouched")
      val prepared = JsonMapper.prepare(
        JsonParser.parse(command.changes.encoded),
        target,
        TypeResolver.resolve(classOf[SchemaPatchTarget]),
        null,
        null,
        inject,
        factory.getValidator
      )
      assert(!prepared.isApplied)
      prepared.applyChanges()
      assert(target.title == "" && target.protectedValue == "retained" && target.description == "untouched")
      intercept[IllegalStateException](prepared.applyChanges())
      val contract = JsonMapper.schema(classOf[SchemaPatchCommand]).properties.get("changes")
      assert(contract.`type` == "object" && contract.required.isEmpty)
    } finally factory.close()
  }

  test("a typed patch accepts only objects and retains explicit null and empty values") {
    val patch = JsonPatch.parse[SchemaPatchTarget]("""{"title":"","description":null}""")
    assert(jackson.readTree(patch.encoded).path("description").isNull())
    assert(jackson.readTree(patch.encoded).path("title").asText() == "")
    intercept[IllegalArgumentException](JsonPatch.parse[SchemaPatchTarget]("[]"))
  }

  test("entity output schemas allow mapper-omitted values while keeping identity") {
    val schema = JsonMapper.schema(classOf[SchemaEntity])
    assert(schema.required.asScala.toSet == Set("id", "version"))
    assert(schema.properties.containsKey("@type"))
    assert(schema.properties.get("label").minLength.intValue() == 1)
  }
}

class SchemaParent extends DTO {
  @JsonbProperty("label") @NotBlank @Size(max = 12) var caption: String = ""
}
class SchemaProbe extends SchemaParent {
  @JsonbProperty var entries: util.Map[String, util.List[util.UUID]] =
    new util.LinkedHashMap[String, util.List[util.UUID]]()
  @JsonbProperty @Min(value = 0L) @Max(value = 8L) var offset: Long = 0
  @JsonbProperty @DecimalMin(value = "0.01", inclusive = false) @DecimalMax(value =
    "999.99"
  ) var price: JavaBigDecimal = null
  @JsonbProperty var unit: TimeUnit = TimeUnit.SECONDS
  @JsonbProperty var tags: util.Set[String] = new util.LinkedHashSet[String]()
  @JsonbProperty var bytes: Array[Byte] = Array.emptyByteArray
}
class SchemaRecursive extends DTO {
  @JsonbProperty var name: String = ""
  @JsonbProperty var child: SchemaRecursive = null
}
class SchemaPatchCommand extends DTO {
  @JsonbProperty @NotNull var changes: JsonPatch[SchemaPatchTarget] = null
}
class SchemaPatchTarget extends DTO {
  @JsonbProperty var title: String = "old"
  @JsonbProperty var protectedValue: String = "retained"
  @JsonbProperty var description: String = "untouched"
}
object SchemaPatchTarget extends SchemaProvider[SchemaPatchTarget.Schema] {
  class Schema extends BeanSchema[SchemaPatchTarget] {
    val title = property(_.title, classOf[DefaultWritableRule[SchemaPatchTarget]])
    val protectedValue = property(_.protectedValue)
    val description = property(_.description, classOf[DefaultWritableRule[SchemaPatchTarget]])
  }
}
class SchemaEntity extends SchemaParent with EntityProvider {
  @JsonbProperty val id: util.UUID = util.UUID.randomUUID()
  @JsonbProperty var version: Long = 0
}

class SchemaEmptyCommand extends DTO
