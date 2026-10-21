package com.anjunar.json.mapper

import com.anjunar.json.mapper.provider.{DTO, EntityProvider}
import com.anjunar.json.mapper.schema.EntitySchema
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.persistence.{EntityManager, NamedAttributeNode, NamedEntityGraph, NamedEntityGraphs}
import jakarta.validation.constraints.{NotBlank, NotNull, Size}
import org.scalatest.funsuite.AnyFunSuite
import java.util
import scala.jdk.CollectionConverters.*

class ProjectionSpec extends AnyFunSuite {
  private val schema = new ProjectedDraftSchema()
  private val source = new ProjectedSourceSchema()
  private val attestation = source.fields(source.document, source.hash).requiringAll.withoutIdentity
  private val changes = schema
    .fields(schema.title, schema.notes)
    .and(schema.sources)
    .items(schema.sources, attestation)
    .refine(schema.notes)(field => field.copy(items = field.items.copy(minLength = Int.box(1))))
    .patch

  test("a graph projection follows the declared graph, adds presentation fields and keeps the graph name") {
    val summary = schema.graph("ProjectedDraft.summary", schema.reviewUrl).requiringAll
    assert(summary.fields == Seq(schema.title, schema.id, schema.version, schema.reviewUrl))
    assert(summary.graphName.contains("ProjectedDraft.summary"))
    assert(summary.json == JsonMapper.schema(schema, summary.fields, summary.fields, true))
    assert(summary.json.required.asScala.toSeq == Seq("heading", "id", "version", "reviewUrl"))
    assert(summary.json.properties.containsKey("@type"))
    intercept[IllegalArgumentException](schema.graph("ProjectedDraft.missing"))
    assert(
      intercept[IllegalArgumentException](schema.graph("ProjectedDraft.unregistered")).getMessage.contains("unregistered")
    )
  }

  test("all matches the EntitySchema export and requiring uses JSON aliases") {
    val document = schema.all.requiring(schema.id, schema.title)
    assert(document.json.properties == JsonMapper.schema(schema).properties)
    assert(document.json.required.asScala.toSeq == Seq("id", "heading"))
    assert(!document.json.properties.containsKey("unregistered"))
    assert(document.graphName.isEmpty)
  }

  test("a patch describes nested element projections and named refinements") {
    assert(changes.json.required.isEmpty && !changes.json.properties.containsKey("@type"))
    assert(changes.json.properties.get("sources").items == attestation.json)
    assert(attestation.json.required.asScala.toSeq == Seq("document", "hash"))
    assert(changes.json.properties.get("notes").items.minLength.intValue() == 1)
    assert(changes.json.properties.get("heading").maxLength.intValue() == 40)
    assert(changes.names == Set("heading", "notes", "sources"))
    assert(changes.requiring(schema.title).json.required.asScala.toSeq == Seq("heading"))
  }

  test("contains accepts properties, scalar collection elements and element projection properties only") {
    assert(changes.contains(Seq("heading")) && changes.contains(Seq("sources")))
    assert(changes.contains(Seq("notes", 0)) && changes.contains(Seq("sources", 2, "document")))
    assert(!changes.contains(Seq("sources", 2)) && !changes.contains(Seq("sources", 2, "internal")))
    assert(!changes.contains(Seq("heading", 0)) && !changes.contains(Seq("title")))
    assert(!changes.contains(Seq("reviewUrl")) && !changes.contains(Seq.empty))
  }

  test("projections reject foreign, unselected and non-collection properties") {
    val other = new ProjectedDraftSchema()
    intercept[IllegalArgumentException](schema.fields(other.title))
    intercept[IllegalArgumentException](schema.fields(schema.title).and(other.id))
    intercept[IllegalArgumentException](schema.fields(schema.title).requiring(schema.id))
    intercept[IllegalArgumentException](schema.fields(schema.title).items(schema.title, attestation))
    intercept[IllegalArgumentException](schema.fields(schema.title).items(schema.sources, attestation))
    intercept[IllegalArgumentException](schema.fields(schema.title).refine(schema.id)(identity))
  }

  test("an envelope requires every paging field and keeps the element graph") {
    val summary = schema.graph("ProjectedDraft.summary").requiring(schema.id)
    val page = summary.envelope(classOf[ProjectedPage], "items")
    assert(page.graphName.contains("ProjectedDraft.summary"))
    assert(page.json.properties.get("items").items == summary.json)
    assert(page.json.required.asScala.toSet == Set("items", "total", "hasMore"))
    intercept[IllegalArgumentException](summary.envelope(classOf[ProjectedPage], "total"))
  }

  test("projections never resolve persistence attributes") {
    var lookups = 0
    val lazySchema = new ProjectedDraftSchema({ lookups += 1; throw new IllegalStateException("Persistence required") })
    val values = Seq(
      lazySchema.all,
      lazySchema.graph("ProjectedDraft.summary", lazySchema.reviewUrl).requiringAll,
      lazySchema.fields(lazySchema.notes).and(lazySchema.sources).items(lazySchema.sources, attestation).patch
    )
    values.foreach(_.json)
    assert(values.last.contains(Seq("sources", 0, "hash")))
    assert(lookups == 0)
  }
}

@NamedEntityGraphs(
  Array(
    new NamedEntityGraph(
      name = "ProjectedDraft.summary",
      attributeNodes = Array(new NamedAttributeNode("title"), new NamedAttributeNode("id"), new NamedAttributeNode("version"))
    ),
    new NamedEntityGraph(
      name = "ProjectedDraft.unregistered",
      attributeNodes = Array(new NamedAttributeNode("id"), new NamedAttributeNode("unregistered"))
    )
  )
)
class ProjectedDraft extends DTO with EntityProvider {
  @JsonbProperty var id: util.UUID = util.UUID.randomUUID()
  @JsonbProperty var version: Long = 0L
  @JsonbProperty("heading") @NotBlank @Size(max = 40) var title: String = ""
  @JsonbProperty @NotNull var notes: util.List[String] = new util.ArrayList[String]()
  @JsonbProperty @NotNull var sources: util.List[ProjectedSource] = new util.ArrayList[ProjectedSource]()
  @JsonbProperty var reviewUrl: String = ""
  @JsonbProperty var unregistered: String = ""
}
class ProjectedDraftSchema(manager: => EntityManager = null) extends EntitySchema[ProjectedDraft](manager) {
  val id = property(_.id)
  val version = property(_.version)
  val title = reference(_.title)
  val notes = list(_.notes)
  val sources = list(_.sources)
  val reviewUrl = property(_.reviewUrl)
}

class ProjectedSource extends DTO {
  @JsonbProperty @NotNull var document: String = ""
  @JsonbProperty var hash: String = ""
  @JsonbProperty var internal: String = ""
}
class ProjectedSourceSchema extends EntitySchema[ProjectedSource] {
  val document = property(_.document)
  val hash = property(_.hash)
  val internal = property(_.internal)
}

class ProjectedPage extends DTO {
  @JsonbProperty @NotNull var items: util.List[ProjectedDraft] = new util.ArrayList[ProjectedDraft]()
  @JsonbProperty var total: Long = 0
  @JsonbProperty var hasMore: Boolean = false
}
