# JSON Mapper

JSON for applications with a domain model. Deserialization merges into the objects you already have, so an update
touches what changed and keeps identity, references and relations intact.

| Version | Platform | Scala | License |
| --- | --- | --- | --- |
| 1.1.6 | JVM | 3.3 | MIT |

Documentation: [English](https://docs.anjunar.com/en/json-mapper) · [Deutsch](https://docs.anjunar.com/de/json-mapper)
Website: [English](https://anjunar.com/en/json-mapper) · [Deutsch](https://anjunar.com/de/json-mapper)

## Installation

`json-mapper` is the complete artifact, including `EntitySchema` for JPA entities. It brings
[Scala Universe](https://github.com/anjunar/scala-universe) along, which resolves the types the mapper works with.

```scala
libraryDependencies += "com.anjunar" %% "json-mapper" % "1.1.6"
```

From 1.2.0 on, applications without JPA entities can depend on `json-mapper-core` instead. It contains mapping,
schemas with `BeanSchema`, projections and the JSON Schema export, and needs no persistence provider.

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

A `JsonMapper` holds what the application provides: a Validator, an `EntityLoader` that resolves referenced IDs,
and an injector for rule classes. The defaults reject every ID and create rules through their constructor.

```scala
import com.anjunar.json.mapper.{EntityLoader, JsonMapper}
import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.scala.universe.TypeResolver
import jakarta.validation.Validation

val validator = Validation.buildDefaultValidatorFactory().getValidator
val loader: EntityLoader = (id, clazz) => entityManager.find(clazz, id)
val mapper = new JsonMapper(Some(validator), loader)
```

The target already exists, loaded from the database or held in memory. The JSON changes two properties; everything
else on the object stays.

```scala
val user = loadUser()             // an existing UserDto
val address = user.address

mapper.deserialize(
  JsonParser.parse("""{"name": "Grace", "address": {"city": "Hamburg"}}"""),
  user,
  TypeResolver.resolve(classOf[UserDto])
)

println(user.name)                // Grace
println(user.address eq address)  // true: the same address, now in Hamburg
```

The other direction, for a user Patrick, 34, living in Berlin 10115:

```scala
mapper.serialize(user, TypeResolver.resolve(classOf[UserDto]))
```

```json
{"name":"Patrick","age":34,"address":{"city":"Berlin","zipCode":"10115","@type":"AddressDto"},"@type":"UserDto"}
```

Both take an optional `EntityGraph`, and `serialize` an optional projection. `mapper.prepare(...)` takes the same
arguments as `deserialize` and returns a `PreparedChange`: the entity is available with `getEntity()`, and
`applyChanges()` performs the merge once, later.

The static calls `JsonMapper.serialize`, `deserialize` and `prepare` of 1.1 still work and are deprecated.

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

## Invalid input

Every value the client sent that its field cannot take is reported: a wrong JSON kind, a number out of range, an
unknown enum constant, an unreadable date, a reference that cannot be resolved, and every Bean Validation violation.
All of them come together in one `ErrorRequestException`, each `ErrorRequest` with the path of its field, for example
`["tags", 0, "label"]`.

Nothing is written when the document has an error. The mapper first reads, resolves and validates the whole document
and writes only when it is valid. `ErrorRequestException` is an `IllegalArgumentException`.

An `IllegalStateException` signals a fault of the application, not of the request: a collection property without an
instance, a loader that returns another object than asked for, or a missing Validator.

## Resolving relationship input

A to-one property declared as `EntityProvider` or `DTO` accepts an object or `null`.
Omitting the property preserves its value; `null` clears it if validation permits.
An object without `id` merges into the current value, or creates an instance through its public no-argument
constructor when the current value is null.

An explicit `id` must be a UUID string in canonical hyphenated form. It is resolved through `EntityLoader`,
even when the same ID is already selected. The returned object replaces the old reference; it must have the
declared type and, for `EntityProvider`, the requested ID. Returning null rejects the reference as invalid input;
exceptions from the loader propagate unchanged. The same checks apply to IDs in typed collections. Applications
should load and authorize the referenced object in this callback.

After resolution, supplied nested fields are merged using the target's schema rules, graph and validation.
An ID is not permission to edit that target. Endpoints that accept only links to shared objects should
reject fields other than `id` at their input boundary, and keep the target's property rules restrictive.

Collection and map constraints are checked against the incoming contents before replacing their contents.
The existing collection or map instance is retained. Replacing or clearing a to-one JPA relationship
also disconnects its old inverse reference before synchronizing the new one.

## Subtypes

A field declared with an abstract type or an interface reads the subtype from the alias the JSON object carries.
Register the subtypes in the standard way on the base type:

```scala
@JsonbTypeInfo(key = "kind", value = Array(
  new JsonbSubtype(alias = "circle", `type` = classOf[Circle]),
  new JsonbSubtype(alias = "square", `type` = classOf[Square])
))
abstract class Shape extends DTO
```

Without `@JsonbTypeInfo`, a subtype in the same package may carry `@JsonbSubtype(alias = "Circle", type =
classOf[Circle])` with its simple name as alias, sent in `@type`. Serialization writes the same key and alias.

## Limits

- Mutable objects by design. The mapper writes into existing instances; it is not meant for immutable models.
- Collection and map properties must hold an instance before deserialization; the mapper fills them and never
  creates them. A `null` collection is an `IllegalStateException`. The same holds for a `@JsonbAnyProperty` map.
- Serialization leaves out `null`, empty strings, empty collections and `false`, and adds `@type` (the simple class
  name or the registered alias) to every non-empty object. `LocalDateTime` and `LocalTime` are written to the minute.
- `EntitySchema` and its JPA properties need `json-mapper`; `json-mapper-core` shares the package
  `com.anjunar.json.mapper.schema` with it, so the two do not work as separate JPMS modules.

## JSON Schema and typed partial commands

The mapper exposes its own resolved property metadata as JSON Schema:

```scala
import com.anjunar.json.mapper.JsonMapper
import com.anjunar.json.mapper.schema.JsonSchema

val schema = JsonMapper.schema(classOf[UserDto])
val text = JsonSchema.encode(schema)
val nativeJavaMap = JsonSchema.toJavaMap(schema)
```

Names, inherited properties, generic collection/map elements and standard validation bounds come from the
same classes used for binding. Recursive models use embedded `$defs`/`$ref`; schema processing fetches no URLs.
Entity output contracts permit values omitted by the normal mapper. Dynamic visibility/write rules and custom
class-level validation still execute at runtime; exporting a schema does not grant access to a field.

`JsonMapper.schema(schema)` exports only the properties a `BeanSchema` or `EntitySchema` registers, and a typed
projection overload takes selected and required `Property` references. A projection may only select properties
registered by that exact schema; it grants no field access. Pass `projection = Some(...)` to `serialize` to narrow
the output to it. Exporting a schema never resolves JPA attributes, so it needs neither an EntityManager nor a
database.

A command can contain a partial entity document without copying every entity field into a DTO:

```scala
import com.anjunar.json.mapper.JsonPatch

class UpdateUser extends DTO {
  @JsonbProperty var changes: JsonPatch[UserDto] = null
}
```

Binding this command only captures the typed patch. It neither loads a relationship nor mutates an entity.
Pass `JsonParser.parse(command.changes.encoded)` to `mapper.prepare` against the actual target and its graph, then
apply that prepared change exactly once. Omitted fields stay unchanged; explicit empty strings, empty collections and
nulls retain their meaning. Schema rules remain responsible for field access.

## Development

The build uses sbt 2 with two modules, `core` (`json-mapper-core`) and `jpa` (`json-mapper`); tests use ScalaTest.
sbt 2 reruns only affected tests with `test`, `testFull` runs all of them.

```bash
sbtn testFull
sbtn "scalafmtCheckAll; scalafix --check"
```

The changes of each version are listed in [CHANGELOG.md](CHANGELOG.md).

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
