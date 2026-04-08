# json-mapper

`json-mapper` is a Scala 3 library for serializing and deserializing object graphs to and from JSON.

It is designed for applications that need more than plain JSON conversion:

- object graph aware serialization
- in-place deserialization into existing instances
- support for nested DTOs, collections, maps, UUIDs, locales, and temporal types
- optional integration with `EntityGraph`
- validation-aware deserialization with aggregated error reporting
- converter hooks for custom JSON representations

## Build

This project uses `sbt`.

```bash
sbt compile
sbt test
```

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
