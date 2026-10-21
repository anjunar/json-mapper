package com.anjunar.json.mapper

import com.anjunar.json.mapper.provider.DTO
import com.anjunar.json.mapper.schema.EntitySchema
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.validation.constraints.Size
import org.scalatest.funsuite.AnyFunSuite
import java.util
import scala.jdk.CollectionConverters.*

class EntitySchemaExportSpec extends AnyFunSuite {
  test("schema exports use registered properties and aliases without resolving persistence attributes") {
    var lookups = 0
    val metadata = new LazyMetadataSchema({ lookups += 1; throw new IllegalStateException("Persistence required") })
    assert(lookups == 0)
    val full = JsonMapper.schema(metadata)
    assert(full.properties.keySet().asScala.toSet == Set("title", "names", "codes"))
    assert(full.properties.get("title").maxLength.intValue() == 12)
    val selected = JsonMapper.schema(metadata, Seq(metadata.label), Seq(metadata.label), false)
    assert(selected.properties.keySet().asScala.toSet == Set("title"))
    assert(selected.required.asScala.toSeq == Seq("title"))
    assert(lookups == 0)
    intercept[IllegalStateException](metadata.label.getName)
    assert(lookups == 1)
    intercept[IllegalStateException](metadata.names.getName)
    intercept[IllegalStateException](metadata.codes.getName)
    assert(lookups == 3)
  }

  test("a projection rejects properties from another schema and missing required selections") {
    val first = new LazyMetadataSchema()
    val other = new LazyMetadataSchema()
    intercept[IllegalArgumentException](JsonMapper.schema(first, Seq(other.label), Seq.empty, false))
    intercept[IllegalArgumentException](JsonMapper.schema(first, Seq.empty, Seq(first.label), false))
  }
}

class LazyMetadataEntity extends DTO {
  @JsonbProperty("title") @Size(max = 12) var label: String = ""
  @JsonbProperty var names: util.List[String] = new util.ArrayList[String]()
  @JsonbProperty var codes: util.Set[String] = new util.LinkedHashSet[String]()
  @JsonbProperty var unregistered: String = "private"
}
class LazyMetadataSchema(manager: => jakarta.persistence.EntityManager = null)
    extends EntitySchema[LazyMetadataEntity](manager) {
  val label = reference(_.label)
  val names = list(_.names)
  val codes = set(_.codes)
}
