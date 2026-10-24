# Changelog

## 1.2.0 (unreleased)

### Modules and API

- `json-mapper-core` is a new artifact without Hibernate. It holds mapping, `BeanSchema`, projections and the JSON
  Schema export. `json-mapper` keeps its coordinates and adds `EntitySchema` with the JPA-backed `reference`, `set`
  and `list` properties; `EntitySchema` now extends `BeanSchema`.
- `new JsonMapper(validator, loader, inject)` is built once and offers `serialize`, `deserialize` and `prepare` with
  an optional graph and projection. `EntityLoader.none` and `Inject.constructor` are the defaults. The static calls
  are deprecated and forward to it.
- `JsonContext` and `JavaContext` are renamed to `DeserializationContext` and `SerializationContext`. The old names
  remain as deprecated type aliases; code compiled against the old classes must be recompiled.
- `RequestContext`, `OwnerProvider`, `Helper` and `PropertyMacrosHelper` are deprecated and will be removed in 2.0.
- Guava is no longer a declared dependency, and the JSON Schema export no longer uses Jackson.

### Invalid input

- Every invalid value is reported with the path of its field, together in one `ErrorRequestException`. Before, only
  Bean Validation violations were collected; other invalid input ended as an exception without a location.
- `ErrorRequestException` now extends `IllegalArgumentException`.
- Nothing is written when a document has an error. Before, the valid fields were written before the exception.
- A JSON node of the wrong kind becomes a field error. Before, the deserializer was chosen by the node, so a number
  for a string field failed with "argument type mismatch" and a string for a collection with a ClassCastException.
- An unresolvable reference is invalid input. A loader that returns an object of another type or identity is now an
  `IllegalStateException`, because the application broke the loader contract.
- An invalid date or time is reported instead of being written as `null`.

### Values and types

- Subtypes are resolved the same way for to-one fields, collections and maps, and also through the standard
  `@JsonbTypeInfo` registration. Before, `@type` was read only in collections, and an abstract to-one field failed.
- A nested bean that is no `DTO` is created when its field is empty. Before, this failed with a NullPointerException.
- `Period` is read as a `Period`, and every `java.time` type is written in ISO-8601 form.
- Untyped whole numbers beyond the range of `Long` stay exact, and exponents are read as decimals.
- Typed maps no longer try to construct scalar values, which failed for `Integer` and similar types.
- Sets keep the order of the document, maps are written in source order, and null elements and map values are
  written as `null`.

### Robustness

- Rule instances and entity graph lookups are cached per call. The shared serializer held unsynchronized caches that
  concurrent requests could corrupt.
- Two schemas whose properties reference each other no longer block forever during initialization.
- `SchemaProvider` loads the nested `Schema` class with the provider's class loader and explains a missing one.
- Java sources compile with `--release 17`. In 1.1.6 the annotations had class file version 69 and could not be
  loaded below Java 25, while the Scala classes ran on Java 8.

### Unreleased 1.1.7 changes, included here

- Numbers are read as the declared type also where the type resolver reports a boxed class. Before, a whole number
  for a Scala `Int` (or `Short`, `Byte`, `Float`) property arrived as `Long` and failed with "argument type
  mismatch".
- `java.math.BigDecimal` and `scala.math.BigDecimal` are written with all digits and scale and never with an
  exponent, and read from the digits of a JSON number or of a string such as `"120.00"`.
- JSON Schema export, typed projections and `JsonPatch` commands.

## 1.1.6

- To-one `EntityProvider` IDs are no longer ignored, a to-one reference whose ID changed is replaced, existing
  collection members go through the loader, and an unresolved ID is rejected instead of silently creating a new
  object. Loaders must resolve or reject every explicit ID.
- Nested fields on a newly resolved to-one reference follow the same merge path as existing objects.
