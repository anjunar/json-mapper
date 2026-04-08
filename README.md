# json-mapper

A JSON mapping library focused on object graphs, in-place deserialization, and structured domain binding.

`json-mapper` is a Scala 3 library for serializing and deserializing object graphs to and from JSON. It is designed for applications that need more than plain JSON conversion, especially when JSON needs to be merged into existing domain objects instead of creating fresh instances every time.

## Why This Library?

Most JSON libraries such as Jackson or circe focus on:

- simple serialization and deserialization
- stateless mapping

They are great fits for many workloads, but they tend to be less ergonomic when your application depends on:

- existing object graphs
- partial updates
- domain-driven models

`json-mapper` is designed for:

- updating existing objects
- mapping complex graphs
- integrating with domain logic

## Installation

Add the library to your `build.sbt`:

```scala
libraryDependencies += "com.anjunar" %% "json-mapper" % "1.0.0"
```

## Example

At its core, the library is built around updating an existing object graph:

```scala
mapper.deserialize(json, existingObject)
```

## Key Features

- in-place deserialization into existing instances
- support for nested object graphs
- collection handling
- validation integration with aggregated error reporting
- custom converters
- support for nested DTOs, maps, UUIDs, locales, and temporal types
- optional integration with `EntityGraph`

## Core Idea

Instead of creating new objects every time, `json-mapper` merges JSON into existing domain objects.

This enables:

- persistence integration
- domain consistency
- efficient updates

## Use Cases

- REST backends with entity graphs
- domain-driven applications
- partial updates with PATCH-like behavior
- UI to backend synchronization

## When Should You Use It?

Use it if:

- you work with existing domain objects
- you need graph updates instead of full replacement
- you build stateful backend systems

Avoid it if:

- you only need simple JSON parsing
- immutability is your primary design principle

## Positioning

- vs Jackson: better graph handling for existing instances and update-heavy flows
- vs circe or zio-json: not purely functional, but often more practical for stateful systems
- vs ORM mappers: focused on JSON mapping, not persistence

## Build

This project uses `sbt`.

```bash
sbt compile
sbt test
```

## Publish To Maven Central

This project is configured for publishing through the Sonatype Central Portal.

Before publishing, make sure you have:

- a verified Sonatype namespace for `com.anjunar`
- a Sonatype Central user token configured locally
- a public GPG key uploaded to a public keyserver
- `gpg` installed, or `GPG_COMMAND` pointing to your `gpg` executable

Local credentials can be configured in `~/.sbt/1.0/credentials.sbt`:

```scala
credentials += Credentials(Path.userHome / ".sbt" / "sonatype_central_credentials")
```

And in `~/.sbt/sonatype_central_credentials`:

```text
host=central.sonatype.com
user=<sonatype-user>
password=<sonatype-token>
```

Release flow:

```bash
sbt publishSigned
sbt sonaUpload
sbt sonaRelease
```

Notes:

- Releases are staged locally first and then uploaded to the Central Portal.
- Snapshot versions publish to `https://central.sonatype.com/repository/maven-snapshots/`.
- On Windows, the build automatically falls back to `C:/Program Files/GnuPG/bin/gpg.exe` if it exists, so no wrapper script is needed.
- This is a single-module build, so `publish / skip := true` is intentionally not set on the root project.

The current project settings are defined in `build.sbt`:

- Scala: `3.8.3`
- Version: `1.0.0`
- Organization: `com.anjunar`

## Core API

The main entry point is [`JsonMapper`](src/main/scala/com/anjunar/json/mapper/JsonMapper.scala).

It exposes two operations:

- `JsonMapper.serialize(...)`
- `JsonMapper.deserialize(...)`

The library expects a resolved type from `scala-universe`:

```scala
import com.anjunar.scala.universe.TypeResolver

val resolvedClass = TypeResolver.resolve(classOf[MyDto])
```

## Simple DTO Example

The mapper works well with mutable DTO-style classes.

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
  @(JsonbProperty @field) var tags: java.util.List[AddressDto] = new java.util.ArrayList[AddressDto]()
}
```

## Serialize an Object

```scala
import com.anjunar.json.mapper.JsonMapper
import com.anjunar.scala.universe.TypeResolver

val user = new UserDto
user.name = "Patrick"
user.age = 34

val address = new AddressDto
address.city = "Berlin"
address.zipCode = "10115"
user.address = address

val json =
  JsonMapper.serialize(
    user,
    TypeResolver.resolve(classOf[UserDto]),
    null,                                  // EntityGraph
    [T] => (_: Class[T]) => null.asInstanceOf[T] // dependency injection hook
  )

println(json)
```

Example output:

```json
{"name":"Patrick","age":34,"address":{"city":"Berlin","zipCode":"10115","@type":"AddressDto"},"@type":"UserDto"}
```

## Deserialize into an Existing Instance

Deserialization updates an existing object instance instead of always creating a brand-new one.

```scala
import com.anjunar.json.mapper.{EntityLoader, JsonMapper}
import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.scala.universe.TypeResolver
import jakarta.validation.Validation

import java.util.UUID

val json =
  """
    {
      "name": "Updated User",
      "age": 35,
      "address": {
        "city": "Hamburg",
        "zipCode": "20095"
      }
    }
  """

val target = new UserDto
target.address = new AddressDto

val validator = Validation.buildDefaultValidatorFactory().getValidator

val loader = new EntityLoader {
  override def load(id: UUID, clazz: Class[?]): Any = null
}

JsonMapper.deserialize(
  JsonParser.parse(json),
  target,
  TypeResolver.resolve(classOf[UserDto]),
  null,                                  // EntityGraph
  loader,
  [T] => (_: Class[T]) => null.asInstanceOf[T],
  validator
)

println(target.name)         // Updated User
println(target.address.city) // Hamburg
```

## Validation Errors

During deserialization, the mapper collects validation failures and throws a single `ErrorRequestException`.

```scala
import com.anjunar.json.mapper.{ErrorRequestException, JsonMapper}
import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.scala.universe.TypeResolver

try {
  JsonMapper.deserialize(
    JsonParser.parse("""{"name": null}"""),
    target,
    TypeResolver.resolve(classOf[UserDto]),
    null,
    loader,
    [T] => (_: Class[T]) => null.asInstanceOf[T],
    validator
  )
} catch {
  case error: ErrorRequestException =>
    error.errors.forEach { request =>
      println(s"path=${request.path}, message=${request.message}")
    }
}
```

## Custom Converters

Custom converters can be attached with `@UseConverter`. The default converter implementation is `JacksonJsonConverter`.

```scala
import com.anjunar.json.mapper.annotations.UseConverter
import com.anjunar.json.mapper.converter.JacksonJsonConverter
import com.anjunar.scala.universe.ResolvedClass

class MoneyConverter extends JacksonJsonConverter {
  override def toJson(input: Any, resolvedClass: ResolvedClass): String = {
    val money = input.asInstanceOf[Money]
    s"""{"amount":${money.amount},"currency":"${money.currency}"}"""
  }

  override def toJava(json: String, resolvedClass: ResolvedClass): Any = {
    // parse the JSON string here and create a Money instance
    Money(10, "EUR")
  }
}

case class Money(amount: BigDecimal, currency: String)

class InvoiceDto extends DTO {
  @(UseConverter(classOf[MoneyConverter]) @field)
  @(JsonbProperty @field)
  var total: Money = null
}
```

## Notes

- Serialization includes an `@type` property for non-empty objects.
- `ObjectMapperProvider` configures Jackson with `DefaultScalaModule`.
- Empty strings, empty collections, and `false` booleans may be omitted during serialization depending on the serializer behavior.
- Collection properties should be initialized before deserialization.
- For nested entity references, provide an `EntityLoader` implementation.

## Project Structure

```text
src/main/scala/com/anjunar/json/mapper
├── JsonMapper.scala
├── ObjectMapperProvider.scala
├── converter/
├── deserializer/
├── intermediate/
├── provider/
├── schema/
└── serializers/
```

## Tests

The project already includes ScalaTest coverage for:

- JSON parsing
- JSON generation
- serializer and deserializer registry selection
- end-to-end `JsonMapper` serialization and deserialization

Run the full suite with:

```bash
sbt test
```
