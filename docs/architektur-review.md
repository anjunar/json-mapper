# Architektur-Review json-mapper

Stand: 2026-10-24, Basis `1.1.8-SNAPSHOT`, umgesetzt in `1.2.0-SNAPSHOT`. Veröffentlicht ist 1.1.6.

Dieses Dokument hält die Befunde des Architektur-Reviews fest und zu jedem Punkt, wie er umgesetzt wurde.
Geprüft wurden die Quellen dieses Repos, die Nachbarbibliothek `scala-universe` und als Konsument das öffentliche
`anjunar-blog-tutorial`. Das private `anjunar-stack` war in dieser Sitzung nicht zugänglich.

## Ausgangslage

Die Schichtung ist klar:

- `intermediate`: eigener JSON-Parser, Generator und Knotenmodell.
- `serializers` und `deserializer`: Registries und je ein Typ-Handler, Kern sind `BeanSerializer` und
  `BeanDeserializer`.
- `schema`: Zugriffsregeln pro Property, JPA-Metamodell-Delegation und JSON-Schema-Export.

Gut gelöst ist die Referenzbehandlung: der `EntityLoader` ist die Autorisierungsgrenze, Subtypen werden nur nach
expliziter Registrierung erzeugt, und die Standardregel ist nicht schreibbar.

## Befunde und Maßnahmen

Jeder Punkt endet mit dem Ergebnis der Umsetzung.

### 1. Nebenläufigkeit

**Befund.** `BeanSerializer` ist ein Singleton und hält zwei unsynchronisierte `WeakHashMap`-Caches. Parallele
Requests können die Maps korrumpieren. `scala-universe` hat dieselbe Lücke global in `TypeResolver`,
`BeanIntrospector` und `AnnotationIntrospector`.

**Maßnahme.** Die Caches gehören zu einem Serialisierungsaufruf, denn Graphen sind pro Aufruf. Sie wandern in den
Wurzelkontext. Für `scala-universe` wird ein eigener Fix vorgeschlagen.

Status: umgesetzt. `MappingRun` hält Regeln und Graph-Lookups pro Aufruf, `BeanSerializer` hat keinen Zustand mehr.
Ein Test serialisiert parallel mit zwei Graphen. Die Lücke in `scala-universe` liegt außerhalb dieses Repos und bleibt
offen, siehe unten.

### 2. Fehlermodell

**Befund.** Nur Bean-Validation-Verletzungen kommen gesammelt und mit Pfad als `ErrorRequestException`. Alle anderen
Eingabefehler sind `IllegalArgumentException` oder `IllegalStateException` ohne Pfad. Der Tutorial-Konsument bildet
nur `ErrorRequestException` auf HTTP 400 ab, alles andere wird zu 500. Der Converter-Fall wirft sofort und bricht
die Sammlung ab.

**Maßnahme.** Eingabefehler werden wie Verletzungen pro Feld gesammelt und gemeinsam als `ErrorRequestException`
geworfen. `ErrorRequestException` erweitert künftig `IllegalArgumentException`, damit bestehende `catch`-Blöcke
weiter greifen. `IllegalStateException` bleibt Programmierfehlern vorbehalten.

Status: umgesetzt. Jeder Deserializer meldet unpassende Werte über `DeserializationContext.reject`, die Bean sammelt
den Fehler und prüft ihre übrigen Felder weiter. Das Registry wählt jetzt nach deklariertem Typ statt nach
JSON-Knoten; das war die Ursache für "argument type mismatch" und `ClassCastException` bei falschen Knotentypen. Ein
Loader, der ein falsches Objekt liefert, ist jetzt `IllegalStateException`.

### 3. Teilweise Schreibvorgänge

**Befund.** Gültige Felder werden geschrieben, bevor die Exception fliegt. Das README verlangt deshalb Rollback
oder Arbeit auf einer Kopie.

**Maßnahme.** Zwei Phasen: erst alles lesen, auflösen und validieren, dann nur bei null Fehlern schreiben.

Status: umgesetzt. `MappingRun` sammelt die Schreibvorgänge und führt sie in Reihenfolge aus, wenn kein Fehler
aufgetreten ist. Das README nennt die Rollback-Pflicht nicht mehr als Grenze.

### 4. Doppelte Graph- und Namenslogik

**Befund.** Graph-Navigation, Schema-Provider-Suche und JSON-Namensauflösung stehen fast gleich in
`BeanSerializer` und `BeanDeserializer`, die Namensauflösung auch im Any-Property-Deserializer.

**Maßnahme.** Gemeinsame Objekte für Graph-Auswahl und Property-Metadaten, ein gemeinsamer Kontext-Trait.

Status: umgesetzt mit `BeanMetadata` und `MappingContext`. Auch `JsonSchemaDerivation` nutzt dieselbe
Namensauflösung.

### 5. Benennung der Kontexte

**Befund.** `JsonContext` ist der Deserialisierungs-, `JavaContext` der Serialisierungskontext.
`JavaContext.path()` liefert immer eine leere Liste.

**Maßnahme.** Umbenennen in `DeserializationContext` und `SerializationContext`, die alten Namen bleiben als
veraltete Aliase. `path()` korrekt implementieren.

Status: umgesetzt. Beide Kontexte haben `child(...)` statt der handgebauten Konstruktoraufrufe.

### 6. Verwaister Code

**Befund.** `RequestContext`, `OwnerProvider`, `Helper` und `PropertyMacrosHelper` werden nirgends benutzt. `Link`
und `"links"` werden dagegen vom Konsumenten genutzt, sind also kein toter Code.

**Maßnahme.** Die vier ungenutzten Typen als veraltet markieren und in 2.0 entfernen, weil 1.1.6 sie veröffentlicht
hat. Die `"links"`-Sonderregel nur noch an einer Stelle führen.

Status: umgesetzt. Die vier Typen sind veraltet markiert. `Link` bleibt, die `"links"`-Regel steht nur noch in
`BeanMetadata.bypassesGraph`.

### 7. Zeitwerte

**Befund.** `TemporalDeserializer` nimmt die erste `parse`-Methode aus `getMethods` und verschluckt jede Exception
zu `null`. `TemporalAmountDeserializer` liest immer `Duration`, ein `Period` scheitert. Der Serializer kennt nur vier
Typen, ein `OffsetDateTime` wird abgelehnt, obwohl es gelesen werden kann.

**Maßnahme.** Deterministisch `parse(CharSequence)` verwenden, ungültige Eingaben als Feldfehler melden, `Period`
unterstützen und alle `java.time`-Typen im ISO-Format schreiben. Die Kürzung von `LocalDateTime` und `LocalTime`
auf Minuten bleibt: ein Test hält sie als bewusstes Wire-Format fest.

Status: umgesetzt. Der bisherige Test, der `null` für ungültige Eingaben festhielt, prüft jetzt den Feldfehler.

### 8. Polymorphie

**Befund.** `@type` wird nur für Collection-Elemente gelesen. Eine abstrakte To-One-Property scheitert am
Konstruktor. Der Alias muss dem einfachen Klassennamen im selben Package entsprechen.

**Maßnahme.** Eine gemeinsame Subtyp-Auflösung für To-One und Collections. Zusätzlich die Standard-Registrierung
über `@JsonbTypeInfo` am Basistyp, die bisherige Konvention bleibt gültig.

Status: umgesetzt in `Subtypes`. Beim Test fiel ein weiterer Fehler auf: ein leeres To-One-Feld mit einem
Bean-Typ ohne `DTO` endete in einer `NullPointerException`. Es wird jetzt angelegt.

### 9. Regel-Injektion und Ordnung

**Befund.** Der Deserializer injiziert Regeln je Property und Objekt neu. Sets werden als `HashSet`, Maps als
`HashMap` gebaut, die Ausgabe ist nicht deterministisch.

**Maßnahme.** Ein Regel-Cache pro Aufruf für beide Richtungen. `LinkedHashSet` und `LinkedHashMap` überall.

Status: umgesetzt. Dazu werden `null`-Elemente in Listen und Maps als `null` geschrieben statt im
Bean-Serializer abzustürzen, und typisierte Maps konstruieren keine Skalare mehr.

### 10. Schema-Provider

**Befund.** `SchemaProvider` lädt `$Schema` mit dem Classloader der Bibliothek. Fehlt die Klasse, folgt eine
`NullPointerException`. `Property.schema` löst das Schema des Zieltyps eifrig auf, wechselseitige To-One-Referenzen
können die Initialisierung rekursiv machen.

**Maßnahme.** Classloader der Provider-Klasse, klare Fehlermeldung, Zyklus prüfen und gegebenenfalls verzögert
auflösen.

Status: umgesetzt. Der Zyklus war real: zwei Schemas mit wechselseitigen To-One-Properties blockierten dauerhaft.
`Property.schema` löst jetzt beim ersten Zugriff auf, über den Classloader des besitzenden Schemas. Die JSON-Form
von `Property` bleibt gleich. Der Namensvergleich mit `UninitializedPropertyAccessException` bleibt bewusst: er
erkennt Kotlins `lateinit` ohne eine Kotlin-Abhängigkeit.

### 11. Abhängigkeiten und Modulschnitt

**Befund.** Guava wird nicht benutzt. Hibernate wird nur für `EntitySchema.reference` gebraucht. Jackson dient dem
JSON-Schema-Export und dem Standard-Converter, obwohl die Bibliothek einen eigenen Generator hat.

**Maßnahme.** Guava entfernen. JSON Schema mit dem eigenen Generator schreiben. Ein Modul `json-mapper-core` ohne
Hibernate abtrennen. Das Artefakt `json-mapper` behält seine Koordinaten und enthält `EntitySchema`, damit
Konsumenten nichts ändern müssen.

Status: umgesetzt. Module `core` (`json-mapper-core`) und `jpa` (`json-mapper`), die neue Basisklasse
`BeanSchema` trägt alles ohne JPA. Beide Module stagen in dasselbe Verzeichnis, die Publish-Skripte bleiben
unverändert. Guava kommt nur noch transitiv über `scala-universe`. Jackson bleibt im Core für
`JacksonJsonConverter`, weil `@UseConverter` ihn als Standardwert nennt; ihn zu entfernen wäre ein Bruch für 2.0.
Die beiden Module teilen das Package `schema`, was für JPMS-Module nicht taugt; das README nennt das.

### 12. Öffentliche API

**Befund.** `serialize`, `deserialize` und `prepare` nehmen bis zu sieben positionale Parameter, mehrere nullable.

**Maßnahme.** Eine einmal gebaute `MapperSettings` mit Validator, Loader und Injektor, Graph und Projektion als
`Option`. Die alten Signaturen bleiben und werden als veraltet markiert.

Status: umgesetzt, aber anders als geplant. Eine statische Überladung mit `Option` hätte bestehende Aufrufe mit
`null` mehrdeutig gemacht und Konsumenten-Code gebrochen. Stattdessen ist `JsonMapper` jetzt auch eine Klasse:
`new JsonMapper(validator, loader, inject)` mit `serialize`, `deserialize` und `prepare`. Die statischen Aufrufe
leiten weiter und sind veraltet, ein eigener Test hält ihre Kompatibilität fest.

### 13. Doku, Build und CI

**Befund.** README nennt sbt 1 und Version 1.1.5, der Build nutzt sbt 2.0.11. Changelog-Abschnitte stehen im
README. Kein CI, das Scalafix-Plugin fehlt trotz Regel im Repo.

**Maßnahme.** README angleichen, `CHANGELOG.md` anlegen, GitHub-Workflow mit Format-Check und Tests, Scalafix
verdrahten.

Status: umgesetzt. `.github/workflows/ci.yml` prüft Format und Imports und führt `sbtn testFull` aus. Die
Version steht jetzt auf `1.2.0-SNAPSHOT`, weil neue API und Deprecations nach dem deklarierten `early-semver` kein
Patch-Release sind.

### 14. Testlücken

**Befund.** Kaum abgedeckt sind Graph- und Subgraph-Traversal beim Serialisieren, Any-Property- und
Map-Serialisierung, Zeitwert-Roundtrips und Nebenläufigkeit.

**Maßnahme.** Gezielte Tests plus ein randomisierter Roundtrip-Test für Parser und Generator mit festem Seed.

Status: umgesetzt. Neu sind Tests für Eingabefehler, Subtypen, Schema-Provider, Ordnung, Zeitwerte,
Graph-Serialisierung, Nebenläufigkeit, Parser-Roundtrips und die alte API. Die Zahl der Tests stieg von 89 auf
117.

### 15. Klassenversion der Java-Quellen

**Befund.** Beim Gegentest mit dem Tutorial fiel auf: im veröffentlichten 1.1.6 haben die Scala-Klassen Version 52
(Java 8), die drei Java-Annotationen aber 69 (Java 25). Auf Java 21 scheitert der Mapper beim Laden von
`@JsonbAnyProperty`. javac übernimmt ohne Vorgabe das JDK, auf dem der Release gebaut wurde.

**Maßnahme.** `javacOptions` mit `--release 17`, der Untergrenze, die Jackson 3 und Hibernate 7 ohnehin setzen.

Status: umgesetzt. Die Annotationen haben jetzt Version 61.

## Gegentest mit dem Konsumenten

Das öffentliche `anjunar-blog-tutorial` wurde gegen den lokal veröffentlichten `1.2.0-SNAPSHOT` gebaut. Backend und
Tests kompilieren ohne Änderung; es gibt nur die erwarteten Deprecation-Warnungen für die statischen Aufrufe. Von
den Tests laufen die 57 ohne Datenbank grün, darunter `MapperReferenceSpec`. Die übrigen brauchen PostgreSQL und
SMTP, die hier fehlen; alle zwölf Serverfehler gehen auf `Persistence.initialize` zurück. Ein Vergleichslauf gegen
1.1.6 war auf diesem JDK 21 nicht möglich, eben wegen Befund 15.

## Offen

- **scala-universe.** Die globalen Caches `TypeResolver.cache`, `TypeResolver.companionCache`,
  `BeanIntrospector.cache` und `AnnotationIntrospector.cache` sind einfache `HashMap`s, die beim ersten Zugriff auf
  einen Typ von beliebigen Request-Threads beschrieben werden. Der Fix gehört in jenes Repo: `ConcurrentHashMap`,
  und wo die Berechnung selbst wieder in denselben Cache greift, außerhalb rechnen und mit `putIfAbsent` eintragen,
  denn `computeIfAbsent` darf nicht rekursiv aufgerufen werden. Dazu ein Test mit parallelem Erstzugriff.
- **Version 2.0.** Entfernen der veralteten Typen und statischen Aufrufe, und `@UseConverter` ohne Jackson als
  Standard, damit der Core ohne Jackson auskommt.
- **Konsumenten mit Datenbank.** Das private `anjunar-stack` war in dieser Sitzung nicht lesbar, und die
  Datenbank-Tests des Tutorials liefen mangels PostgreSQL nicht. Beides sollte vor dem Release einmal gegen den
  Snapshot laufen, vor allem wegen der geänderten Fehlerarten und des Zwei-Phasen-Schreibens.

