# JSON Mapper

JSON for applications with a domain model. Deserialization merges into the objects you already have, so an update
touches what changed and keeps identity, references and relations intact.

| Version | Platform | Scala | License |
| --- | --- | --- | --- |
| 1.1.5 | JVM | 3.3 | MIT |

Documentation: [English](https://docs.anjunar.com/en/json-mapper) · [Deutsch](https://docs.anjunar.com/de/json-mapper)
Website: [English](https://anjunar.com/en/json-mapper) · [Deutsch](https://anjunar.com/de/json-mapper)

## Installation

One artifact. It brings [Scala Universe](https://github.com/anjunar/scala-universe) along, which resolves the types
the mapper works with.

```scala
libraryDependencies += "com.anjunar" %% "json-mapper" % "1.1.5"
```

## First example

A DTO names its JSON properties with `@JsonbProperty`:

```scala
import com.anjunar.json.mapper.provider.DTO
import jakarta.json.bind.annotation.JsonbProperty
import scala.annotation.meta.field

class AddressDto extends DTO {
  @(JsonbProperty @field) var city: String = null
  @(JsonbProperty @field) var zipCode: String = null
}

class UserDto extends DTO {
  @(JsonbProperty @field) var name: String = null
  @(JsonbProperty @field) var age: Int = 0
  @(JsonbProperty @field) var address: AddressDto = null
  @(JsonbProperty @field) var tags: java.util.List[String] = new java.util.ArrayList[String]()
}
```

The target already exists, loaded from the database or held in memory. The JSON changes two properties; everything
else on the object stays.

```scala
import com.anjunar.json.mapper.{EntityLoader, JsonMapper}
import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.scala.universe.TypeResolver
import jakarta.validation.Validation

val validator = Validation.buildDefaultValidatorFactory().getValidator
val loader: EntityLoader = (id, clazz) => entityManager.find(clazz, id)
val inject = [T] => (clazz: Class[T]) => clazz.getDeclaredConstructor().newInstance()

val user = loadUser()             // an existing UserDto
val address = user.address

JsonMapper.deserialize(
  JsonParser.parse("""{"name": "Grace", "address": {"city": "Hamburg"}}"""),
  user, TypeResolver.resolve(classOf[UserDto]), null, loader, inject, validator
)

println(user.name)                // Grace
println(user.address eq address)  // true: the same address, now in Hamburg
```

The other direction, for a user Patrick, 34, living in Berlin 10115:

```scala
JsonMapper.serialize(user, TypeResolver.resolve(classOf[UserDto]), null, inject)
```

```json
{"name":"Patrick","age":34,"address":{"city":"Berlin","zipCode":"10115","@type":"AddressDto"},"@type":"UserDto"}
```

`JsonMapper.prepare(...)` takes the same arguments as `deserialize` and returns a `PreparedChange`: the entity is
available with `getEntity()`, and `applyChanges()` performs the merge once, later.

## The principle

**01 / Merge – In place, not new.** Nested objects and collections are updated where they are. Elements keep their
identity by ID.

**02 / Rules – The model decides what is visible.** A schema per entity lists its properties; a rule per property
decides who may read and who may write it.

**03 / Integrity – Validated, referenced, in sync.** Bean Validation per property, references loaded by ID, and both
sides of a JPA relation kept consistent.

## Contents

The basics work with plain DTOs. The domain pages add schemas, graphs and references for JPA entities.

**Basics**
- [Serializing](https://docs.anjunar.com/en/json-mapper/serialize) – which properties become JSON, and what the output looks like
- [Deserializing in place](https://docs.anjunar.com/en/json-mapper/deserialize) – JSON merged into an instance you already have, collections included
- [Validation](https://docs.anjunar.com/en/json-mapper/validation) – Bean Validation per property, and every violation in one exception

**Domain models**
- [Schemas and rules](https://docs.anjunar.com/en/json-mapper/schemas) – which properties an entity exposes, and who may read or write them
- [Graphs and references](https://docs.anjunar.com/en/json-mapper/graphs) – `EntityGraph`s limit the output, an `EntityLoader` resolves references, relations stay in sync
- [Converters and open properties](https://docs.anjunar.com/en/json-mapper/converters) – custom value formats, and JSON fields the class does not declare

**Reference**
- [API](https://docs.anjunar.com/en/json-mapper/api) – `JsonMapper`, the callbacks it takes and the annotations it reads
- [Value types](https://docs.anjunar.com/en/json-mapper/types) – how each supported Java and Scala value is written and read

## Limits

- Mutable objects by design. The mapper writes into existing instances; it is not meant for immutable models.
- Collection and map properties must hold an instance before deserialization; the mapper fills them and never
  creates them. A `null` collection is an `IllegalStateException`. The same holds for a `@JsonbAnyProperty` map.
- Validation errors are collected and thrown together as one `ErrorRequestException` – after the valid properties
  were written. Deserialize managed entities inside a transaction and roll it back on that exception, or work on a
  copy.
- Serialization leaves out `null`, empty strings, empty collections and `false`, and adds `@type` (the simple class
  name) to every non-empty object. Deserialization never reads `@type`; it always uses the declared type of the
  property.

## Development

The build uses sbt 1; tests use ScalaTest.

```bash
sbt test
```

### Releasing

Set `ThisBuild / version` in `build.sbt`, then sign, bundle and upload to the Sonatype Central Portal in one step.
Without a version argument the scripts read the one in `build.sbt`, and they wait until Maven Central has published
the release.

```powershell
.\scripts\publish-central.ps1
```

```bash
scripts/publish-central.sh
```

Credentials come from `SONATYPE_CENTRAL_USERNAME` and `SONATYPE_CENTRAL_PASSWORD`, or from the lines `user=` and
`password=` in `~/.sbt/sonatype_central_credentials`. `-PublishingType USER_MANAGED` (`PUBLISHING_TYPE=USER_MANAGED`)
stops after validation so the release is published by hand in the portal; `-SkipPublishSigned`
(`SKIP_PUBLISH_SIGNED=1`) uploads an existing staging directory again. Signing uses `GPG_COMMAND` when set, otherwise
`C:/Program Files/GnuPG/bin/gpg.exe` if it exists, otherwise `gpg`.

## License

JSON Mapper is available under the [MIT License](LICENSE).
