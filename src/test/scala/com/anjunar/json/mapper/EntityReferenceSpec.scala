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

class EntityReferenceSpec extends AnyFunSuite with BeforeAndAfterAll {
  private val factory = Validation.byDefaultProvider().configure()
    .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()
  private val inject: [T] => Class[T] => T =
    [T] => (clazz: Class[T]) => clazz.getConstructor().newInstance()
  private val unexpectedLoader = new EntityLoader {
    override def load(id: UUID, clazz: Class[?]): Any =
      fail("This payload must not resolve a reference")
  }

  override protected def afterAll(): Unit =
    try factory.close()
    finally super.afterAll()

  private def loaderFor(target: ReferenceAuthor): EntityLoader = new EntityLoader {
    override def load(id: UUID, clazz: Class[?]): Any = {
      assert(id == target.id)
      assert(clazz == classOf[ReferenceAuthor])
      target
    }
  }

  private def prepare[T <: AnyRef](value: T, json: String, loader: EntityLoader = unexpectedLoader): PreparedChange[T] =
    JsonMapper.prepare(JsonParser.parse(json), value, TypeResolver.resolve(value.getClass),
      null, loader, inject, factory.getValidator)

  test("a new to-one EntityProvider reference is resolved only when the prepared change is applied") {
    val post = new ReferencePost()
    val target = new ReferenceAuthor()
    var loads = 0
    val loader = new EntityLoader {
      override def load(id: UUID, clazz: Class[?]): Any = {
        loads += 1
        loaderFor(target).load(id, clazz)
      }
    }
    val change = prepare(post, s"""{"author":{"id":"${target.id}"}}""", loader)
    assert(post.author == null)
    assert(loads == 0)
    change.applyChanges()
    assert(post.author eq target)
    assert(loads == 1)
    intercept[IllegalStateException](change.applyChanges())
    assert(loads == 1)
  }

  test("a different submitted ID replaces the old reference without modifying the old object") {
    val post = new ReferencePost()
    val previous = new ReferenceAuthor()
    post.author = previous
    val target = new ReferenceAuthor()
    prepare(post, s"""{"author":{"id":"${target.id}","name":"Updated"}}""", loaderFor(target)).applyChanges()
    assert(post.author eq target)
    assert(target.name == "Updated")
    assert(previous.name == "Original")
  }

  test("an explicit unchanged ID still goes through the loader") {
    val post = new ReferencePost()
    val target = new ReferenceAuthor()
    post.author = target
    var loads = 0
    val loader = new EntityLoader {
      override def load(id: UUID, clazz: Class[?]): Any = {
        loads += 1
        loaderFor(target).load(id, clazz)
      }
    }
    prepare(post, s"""{"author":{"id":"${target.id}"}}""", loader).applyChanges()
    assert(post.author eq target)
    assert(loads == 1)
  }

  test("an omitted relationship is preserved and explicit null clears it") {
    val post = new ReferencePost()
    val previous = new ReferenceAuthor()
    post.author = previous
    prepare(post, "{}").applyChanges()
    assert(post.author eq previous)
    prepare(post, """{"author":null}""").applyChanges()
    assert(post.author == null)
  }

  test("an ID-less nested object updates the current value or creates a new value") {
    val post = new ReferencePost()
    prepare(post, """{"author":{"name":"Created"}}""").applyChanges()
    val created = post.author
    assert(created.name == "Created")
    prepare(post, """{"author":{"name":"Edited"}}""").applyChanges()
    assert(post.author eq created)
    assert(created.name == "Edited")
  }

  test("a missing reference fails instead of creating a different entity") {
    val post = new ReferencePost()
    val previous = new ReferenceAuthor()
    post.author = previous
    val missing = new EntityLoader {
      override def load(id: UUID, clazz: Class[?]): Any = null
    }
    intercept[IllegalArgumentException] {
      prepare(post, s"""{"author":{"id":"${UUID.randomUUID()}","name":"Unwanted"}}""", missing).applyChanges()
    }
    assert(post.author eq previous)
    assert(previous.name == "Original")
  }

  test("loader authorization failures propagate before replacing or editing the old reference") {
    val post = new ReferencePost()
    val previous = new ReferenceAuthor()
    post.author = previous
    val denied = new SecurityException("Not allowed")
    val loader = new EntityLoader {
      override def load(id: UUID, clazz: Class[?]): Any = throw denied
    }
    val failure = intercept[SecurityException] {
      prepare(post, s"""{"author":{"id":"${previous.id}","name":"Unwanted"}}""", loader).applyChanges()
    }
    assert(failure eq denied)
    assert(post.author eq previous)
    assert(previous.name == "Original")
  }

  for (invalid <- Seq("null", "42", "\"\"", "\"not-a-uuid\"", "\"1-1-1-1-1\"", "{}")) {
    test(s"an invalid reference ID $invalid is rejected without calling the loader") {
      val post = new ReferencePost()
      intercept[IllegalArgumentException] {
        prepare(post, s"""{"author":{"id":$invalid}}""").applyChanges()
      }
      assert(post.author == null)
    }
  }

  test("a loader result must match the declared reference type") {
    val post = new ReferencePost()
    val loader = new EntityLoader {
      override def load(id: UUID, clazz: Class[?]): Any = "wrong type"
    }
    intercept[IllegalArgumentException] {
      prepare(post, s"""{"author":{"id":"${UUID.randomUUID()}"}}""", loader).applyChanges()
    }
    assert(post.author == null)
  }

  test("an EntityProvider returned by the loader must have the requested identity") {
    val post = new ReferencePost()
    val loader = new EntityLoader {
      override def load(id: UUID, clazz: Class[?]): Any = new ReferenceAuthor()
    }
    intercept[IllegalArgumentException] {
      prepare(post, s"""{"author":{"id":"${UUID.randomUUID()}"}}""", loader).applyChanges()
    }
    assert(post.author == null)
  }

  test("DTO references also replace an existing value and merge permitted nested fields") {
    val profile = new ReferenceDtoOwner()
    profile.address = new ReferenceAddress()
    val previous = profile.address
    val target = new ReferenceAddress()
    val expected = UUID.randomUUID()
    val loader = new EntityLoader {
      override def load(id: UUID, clazz: Class[?]): Any = {
        assert(id == expected && clazz == classOf[ReferenceAddress])
        target
      }
    }
    prepare(profile, s"""{"address":{"id":"$expected","city":"Berlin"}}""", loader).applyChanges()
    assert(profile.address eq target)
    assert(target.city == "Berlin")
    assert(previous.city == "Original")
  }

  test("schema rules still deny a relationship before its ID reaches the loader") {
    val post = new ReadOnlyReferencePost()
    val previous = new ReferenceAuthor()
    post.author = previous
    prepare(post, s"""{"author":{"id":"${UUID.randomUUID()}"}}""").applyChanges()
    assert(post.author eq previous)
  }

  test("reference assignment respects NotNull validation") {
    val post = new RequiredReferencePost()
    val previous = post.author
    val failure = intercept[ErrorRequestException] {
      prepare(post, """{"author":null}""").applyChanges()
    }
    assert(failure.errors.get(0).path == util.Arrays.asList("author"))
    assert(post.author eq previous)
  }

  test("loaded references still apply nested field validation") {
    val post = new ReferencePost()
    val target = new ReferenceAuthor()
    val failure = intercept[ErrorRequestException] {
      prepare(post, s"""{"author":{"id":"${target.id}","name":""}}""", loaderFor(target)).applyChanges()
    }
    assert(target.name == "Original")
    assert(failure.errors.get(0).path == util.Arrays.asList("author", "name"))
  }

  test("replacing and clearing a ManyToOne reference removes the old inverse membership") {
    val post = new ReferencePost()
    val previous = new ReferenceAuthor()
    previous.posts.add(post)
    post.author = previous
    val target = new ReferenceAuthor()
    prepare(post, s"""{"author":{"id":"${target.id}"}}""", loaderFor(target)).applyChanges()
    assert(!previous.posts.contains(post))
    assert(target.posts.contains(post))
    prepare(post, """{"author":null}""").applyChanges()
    assert(!target.posts.contains(post))
  }

  test("collection references resolve both new and already selected IDs through the loader") {
    val post = new ReferencePost()
    val previous = new ReferenceAuthor()
    val target = new ReferenceAuthor()
    post.reviewers.add(previous)
    val identity = post.reviewers
    val loaded = new util.ArrayList[UUID]()
    val loader = new EntityLoader {
      override def load(id: UUID, clazz: Class[?]): Any = {
        assert(clazz == classOf[ReferenceAuthor])
        loaded.add(id)
        if (id == previous.id) previous else if (id == target.id) target else null
      }
    }
    prepare(post, s"""{"reviewers":[{"id":"${previous.id}"},{"id":"${target.id}"}]}""", loader).applyChanges()
    assert(post.reviewers eq identity)
    assert(post.reviewers.contains(previous) && post.reviewers.contains(target))
    assert(loaded == util.Arrays.asList(previous.id, target.id))
  }

  test("a loader may deny an ID that already occurs in a collection") {
    val post = new ReferencePost()
    val previous = new ReferenceAuthor()
    post.reviewers.add(previous)
    val loader = new EntityLoader {
      override def load(id: UUID, clazz: Class[?]): Any = throw new SecurityException("Not allowed")
    }
    intercept[SecurityException] {
      prepare(post, s"""{"reviewers":[{"id":"${previous.id}","name":"Unwanted"}]}""", loader).applyChanges()
    }
    assert(post.reviewers.size() == 1 && post.reviewers.get(0) == previous)
    assert(previous.name == "Original")
  }

  test("missing collection references are not silently replaced with new objects") {
    val post = new ReferencePost()
    val loader = new EntityLoader {
      override def load(id: UUID, clazz: Class[?]): Any = null
    }
    intercept[IllegalArgumentException] {
      prepare(post, s"""{"reviewers":[{"id":"${UUID.randomUUID()}"}]}""", loader).applyChanges()
    }
    assert(post.reviewers.isEmpty)
  }

  test("ID-less collection values can still be created and an empty array clears the collection") {
    val post = new ReferencePost()
    prepare(post, """{"reviewers":[{"name":"Created"}]}""").applyChanges()
    assert(post.reviewers.get(0).name == "Created")
    prepare(post, """{"reviewers":[]}""").applyChanges()
    assert(post.reviewers.isEmpty)
  }

  test("collection size is validated against incoming elements before replacement") {
    val post = new ReferencePost()
    val previous = new ReferenceAuthor()
    post.reviewers.add(previous)
    val failure = intercept[ErrorRequestException] {
      prepare(post, """{"reviewers":[{"name":"One"},{"name":"Two"},{"name":"Three"}]}""").applyChanges()
    }
    assert(failure.errors.get(0).path == util.Arrays.asList("reviewers"))
    assert(post.reviewers.size() == 1 && post.reviewers.get(0) == previous)
  }

  test("a valid replacement may repair an oversized collection") {
    val post = new ReferencePost()
    (1 to 3).foreach(_ => post.reviewers.add(new ReferenceAuthor()))
    prepare(post, """{"reviewers":[]}""").applyChanges()
    assert(post.reviewers.isEmpty)
  }

  test("a replaced owning OneToOne reference disconnects its old inverse") {
    val member = new ReferenceMember()
    val previous = new ReferenceBiography()
    member.biography = previous
    previous.member = member
    val target = new ReferenceBiography()
    val loader = new EntityLoader {
      override def load(id: UUID, clazz: Class[?]): Any = {
        assert(id == target.id && clazz == classOf[ReferenceBiography])
        target
      }
    }
    prepare(member, s"""{"biography":{"id":"${target.id}"}}""", loader).applyChanges()
    assert(previous.member == null)
    assert(target.member eq member)
    prepare(member, """{"biography":null}""").applyChanges()
    assert(target.member == null)
  }

  test("replacing the inverse OneToOne side disconnects the previous owning side") {
    val biography = new ReferenceBiography()
    val previous = new ReferenceMember()
    biography.member = previous
    previous.biography = biography
    val target = new ReferenceMember()
    val loader = new EntityLoader {
      override def load(id: UUID, clazz: Class[?]): Any = target
    }
    prepare(biography, s"""{"member":{"id":"${target.id}"}}""", loader).applyChanges()
    assert(previous.biography == null)
    assert(target.biography eq biography)
    prepare(biography, """{"member":null}""").applyChanges()
    assert(target.biography == null)
  }

  test("clearing a OneToOne value does not erase an inverse that now belongs to another owner") {
    val member = new ReferenceMember()
    val previous = new ReferenceBiography()
    val other = new ReferenceMember()
    member.biography = previous
    previous.member = other
    prepare(member, """{"biography":null}""").applyChanges()
    assert(previous.member eq other)
  }

  test("schema rules on a loaded target still protect read-only nested properties") {
    val post = new ReferencePost()
    val target = new ReferenceAuthor()
    prepare(post, s"""{"author":{"id":"${target.id}","name":"Updated","internalNote":"Unwanted"}}""",
      loaderFor(target)).applyChanges()
    assert(post.author eq target)
    assert(target.name == "Updated")
    assert(target.internalNote == "Private")
  }

  test("a selected relationship uses its subgraph when merging a loaded reference") {
    val post = new ReferencePost()
    val target = new ReferenceAuthor()
    val graph = stub(classOf[EntityGraph[?]], Map("getAttributeNodes" -> util.Arrays.asList(
      attribute("author", Map(classOf[ReferenceAuthor] -> stub(classOf[Subgraph[?]],
        Map("getAttributeNodes" -> util.Arrays.asList(attribute("name"))))))
    )))
    JsonMapper.prepare(JsonParser.parse(
      s"""{"author":{"id":"${target.id}","name":"Updated","version":99}}"""),
      post, TypeResolver.resolve(classOf[ReferencePost]), graph, loaderFor(target),
      inject, factory.getValidator).applyChanges()
    assert(post.author eq target)
    assert(target.name == "Updated")
    assert(target.version == 0L)
  }

  test("an unselected relationship never reaches the loader") {
    val post = new ReferencePost()
    val graph = stub(classOf[EntityGraph[?]], Map("getAttributeNodes" -> util.Collections.emptyList()))
    JsonMapper.prepare(JsonParser.parse(s"""{"author":{"id":"${UUID.randomUUID()}"}}"""),
      post, TypeResolver.resolve(classOf[ReferencePost]), graph, unexpectedLoader,
      inject, factory.getValidator).applyChanges()
    assert(post.author == null)
  }

  test("non-object to-one values are rejected without replacing the current reference") {
    val post = new ReferencePost()
    val previous = new ReferenceAuthor()
    post.author = previous
    for (invalid <- Seq("[]", "42", "true", "\"value\"")) {
      intercept[IllegalArgumentException] {
        prepare(post, s"""{"author":$invalid}""").applyChanges()
      }
      assert(post.author eq previous)
    }
  }

  test("JsonAnyProperty size is checked against incoming entries") {
    val value = new ReferenceAttributes()
    value.attributes.put("old", "Preserved")
    intercept[ErrorRequestException] {
      prepare(value, """{"one":"1","two":"2"}""").applyChanges()
    }
    assert(value.attributes.size() == 1 && value.attributes.get("old") == "Preserved")
  }

  private def attribute(name: String, children: Map[Class[?], Subgraph[?]] = Map.empty): AttributeNode[?] = {
    val subgraphs = new util.HashMap[Class[?], Subgraph[?]]()
    children.foreach { case (clazz, subgraph) => subgraphs.put(clazz, subgraph) }
    stub(classOf[AttributeNode[?]], Map("getAttributeName" -> name, "getSubgraphs" -> subgraphs))
  }

  private def stub[T](clazz: Class[T], values: Map[String, AnyRef]): T =
    Proxy.newProxyInstance(clazz.getClassLoader, Array(clazz),
      (_, method, _) => values.getOrElse(method.getName, throw new UnsupportedOperationException(method.getName)))
      .asInstanceOf[T]

  test("map size is validated against incoming entries before replacement") {
    val post = new ReferencePost()
    post.labels.put("old", "Preserved")
    val failure = intercept[ErrorRequestException] {
      prepare(post, """{"labels":{"a":"A","b":"B","c":"C"}}""").applyChanges()
    }
    assert(failure.errors.get(0).path == util.Arrays.asList("labels"))
    assert(post.labels.size() == 1 && post.labels.get("old") == "Preserved")
  }
}

