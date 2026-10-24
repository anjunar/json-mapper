package com.anjunar.json.mapper

import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.json.mapper.schema.Link
import com.anjunar.scala.universe.TypeResolver
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.persistence.{AttributeNode, EntityGraph, Subgraph}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.lang.reflect.Proxy
import java.util
import java.util.concurrent.{Callable, Executors, TimeUnit}
import scala.annotation.meta.field
import scala.jdk.CollectionConverters.*
import java.lang.{Boolean as JavaBoolean}

class GraphSerializationSpec extends AnyFunSuite with Matchers {

  private val mapper = new JsonMapper()

  private def article: GraphArticle = {
    val author = new GraphPerson("Ada", "ada@example.org")
    val value = new GraphArticle
    value.title = "Engines"
    value.body = "Long text"
    value.author = author
    value.contributors.add(new GraphPerson("Grace", "grace@example.org"))
    value.links.add(new Link("self", "/articles/1", "GET", "Article"))
    value
  }

  private def write(value: GraphArticle, graph: EntityGraph[?]): String =
    mapper.serialize(value, TypeResolver.resolve(classOf[GraphArticle]), Some(graph))

  private val titleAndNames: EntityGraph[?] = GraphStubs.graph(
    GraphStubs.attribute("title"),
    GraphStubs.attribute("author", GraphStubs.subgraph(GraphStubs.attribute("name"))),
    GraphStubs.attribute("contributors", GraphStubs.subgraph(GraphStubs.attribute("name")))
  )

  private val bodyAndEmails: EntityGraph[?] = GraphStubs.graph(
    GraphStubs.attribute("body"),
    GraphStubs.attribute("contributors", GraphStubs.subgraph(GraphStubs.attribute("email")))
  )

  test("a graph selects entity properties, and subgraphs select them on to-one and collection relations") {
    write(article, titleAndNames) shouldBe
      """{"title":"Engines","author":{"name":"Ada","@type":"GraphPerson"},""" +
      """"contributors":[{"name":"Grace","@type":"GraphPerson"}],""" +
      """"links":[{"rel":"self","url":"/articles/1","method":"GET","@type":"Article"}],"@type":"GraphArticle"}"""
  }

  test("an attribute without a subgraph writes the related entity completely") {
    val graph = GraphStubs.graph(GraphStubs.attribute("author"))
    write(article, graph) shouldBe
      """{"author":{"name":"Ada","email":"ada@example.org",""" +
      """"id":"00000000-0000-0000-0000-000000000001","version":1,"@type":"GraphPerson"},""" +
      """"links":[{"rel":"self","url":"/articles/1","method":"GET","@type":"Article"}],"@type":"GraphArticle"}"""
  }

  test("concurrent calls with different graphs never see each other's selection") {
    val expectedTitle = write(article, titleAndNames)
    val expectedBody = write(article, bodyAndEmails)
    val pool = Executors.newFixedThreadPool(8)
    try {
      val tasks = (0 until 400).map { index =>
        val graph = if (index % 2 == 0) titleAndNames else bodyAndEmails
        val expected = if (index % 2 == 0) expectedTitle else expectedBody
        (() => write(article, graph) == expected): Callable[Boolean]
      }
      val results = pool.invokeAll(tasks.asJava, 60, TimeUnit.SECONDS).asScala.map(_.get())
      results.forall(identity) shouldBe true
    } finally {
      pool.shutdownNow()
    }
  }
}

class GraphArticle extends EntityProvider {
  @JsonbProperty var id: util.UUID = util.UUID.fromString("00000000-0000-0000-0000-000000000002")
  @JsonbProperty var version: Long = 1L
  @JsonbProperty var title: String = null
  @JsonbProperty var body: String = null
  @JsonbProperty var author: GraphPerson = null
  @JsonbProperty val contributors: util.List[GraphPerson] = new util.ArrayList[GraphPerson]()
  @JsonbProperty val links: util.List[Link] = new util.ArrayList[Link]()
}

class GraphPerson(
  @(JsonbProperty @field) var name: String,
  @(JsonbProperty @field) var email: String
) extends EntityProvider {
  def this() = this(null, null)
  @JsonbProperty var id: util.UUID = util.UUID.fromString("00000000-0000-0000-0000-000000000001")
  @JsonbProperty var version: Long = 1L
}

/** Entity graphs without a persistence provider: only the attribute and subgraph lookups the mapper performs. */
object GraphStubs {

  def graph(nodes: AttributeNode[?]*): EntityGraph[?] =
    stub(classOf[EntityGraph[?]], Map("getAttributeNodes" -> util.List.of(nodes*)))

  def subgraph(nodes: AttributeNode[?]*): Subgraph[?] =
    stub(classOf[Subgraph[?]], Map("getAttributeNodes" -> util.List.of(nodes*)))

  def attribute(name: String, subgraph: Subgraph[?] = null): AttributeNode[?] = {
    val subgraphs = new util.HashMap[Class[?], Subgraph[?]]()
    if (subgraph != null) subgraphs.put(classOf[Object], subgraph)
    stub(classOf[AttributeNode[?]], Map("getAttributeName" -> name, "getSubgraphs" -> subgraphs))
  }

  private def stub[T](clazz: Class[T], values: Map[String, AnyRef]): T =
    Proxy
      .newProxyInstance(
        clazz.getClassLoader,
        Array(clazz),
        (proxy, method, arguments) =>
          method.getName match {
            case "hashCode" => Integer.valueOf(System.identityHashCode(proxy))
            case "equals"   => JavaBoolean.valueOf(proxy eq arguments(0))
            case name       => values.getOrElse(name, throw new UnsupportedOperationException(name))
          }
      )
      .asInstanceOf[T]
}
