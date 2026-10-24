# Architektur-Review json-mapper

Stand: 2026-10-24, Basis `1.1.8-SNAPSHOT` (veröffentlicht ist 1.1.6).

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

Status: *offen* wird beim Abarbeiten durch das Ergebnis ersetzt.

### 1. Nebenläufigkeit

**Befund.** `BeanSerializer` ist ein Singleton und hält zwei unsynchronisierte `WeakHashMap`-Caches. Parallele
Requests können die Maps korrumpieren. `scala-universe` hat dieselbe Lücke global in `TypeResolver`,
`BeanIntrospector` und `AnnotationIntrospector`.

**Maßnahme.** Die Caches gehören zu einem Serialisierungsaufruf, denn Graphen sind pro Aufruf. Sie wandern in den
Wurzelkontext. Für `scala-universe` wird ein eigener Fix vorgeschlagen.

Status: offen

### 2. Fehlermodell

**Befund.** Nur Bean-Validation-Verletzungen kommen gesammelt und mit Pfad als `ErrorRequestException`. Alle anderen
Eingabefehler sind `IllegalArgumentException` oder `IllegalStateException` ohne Pfad. Der Tutorial-Konsument bildet
nur `ErrorRequestException` auf HTTP 400 ab, alles andere wird zu 500. Der Converter-Fall wirft sofort und bricht
die Sammlung ab.

**Maßnahme.** Eingabefehler werden wie Verletzungen pro Feld gesammelt und gemeinsam als `ErrorRequestException`
geworfen. `ErrorRequestException` erweitert künftig `IllegalArgumentException`, damit bestehende `catch`-Blöcke
weiter greifen. `IllegalStateException` bleibt Programmierfehlern vorbehalten.

Status: offen

### 3. Teilweise Schreibvorgänge

**Befund.** Gültige Felder werden geschrieben, bevor die Exception fliegt. Das README verlangt deshalb Rollback
oder Arbeit auf einer Kopie.

**Maßnahme.** Zwei Phasen: erst alles lesen, auflösen und validieren, dann nur bei null Fehlern schreiben.

Status: offen

### 4. Doppelte Graph- und Namenslogik

**Befund.** Graph-Navigation, Schema-Provider-Suche und JSON-Namensauflösung stehen fast gleich in
`BeanSerializer` und `BeanDeserializer`, die Namensauflösung auch im Any-Property-Deserializer.

**Maßnahme.** Gemeinsame Objekte für Graph-Auswahl und Property-Metadaten, ein gemeinsamer Kontext-Trait.

Status: offen

### 5. Benennung der Kontexte

**Befund.** `JsonContext` ist der Deserialisierungs-, `JavaContext` der Serialisierungskontext.
`JavaContext.path()` liefert immer eine leere Liste.

**Maßnahme.** Umbenennen in `DeserializationContext` und `SerializationContext`, die alten Namen bleiben als
veraltete Aliase. `path()` korrekt implementieren.

Status: offen

### 6. Verwaister Code

**Befund.** `RequestContext`, `OwnerProvider`, `Helper` und `PropertyMacrosHelper` werden nirgends benutzt. `Link`
und `"links"` werden dagegen vom Konsumenten genutzt, sind also kein toter Code.

**Maßnahme.** Die vier ungenutzten Typen als veraltet markieren und in 2.0 entfernen, weil 1.1.6 sie veröffentlicht
hat. Die `"links"`-Sonderregel nur noch an einer Stelle führen.

Status: offen

### 7. Zeitwerte

**Befund.** `TemporalDeserializer` nimmt die erste `parse`-Methode aus `getMethods` und verschluckt jede Exception
zu `null`. `TemporalAmountDeserializer` liest immer `Duration`, ein `Period` scheitert. Der Serializer kennt nur vier
Typen, ein `OffsetDateTime` wird abgelehnt, obwohl es gelesen werden kann.

**Maßnahme.** Deterministisch `parse(CharSequence)` verwenden, ungültige Eingaben als Feldfehler melden, `Period`
unterstützen und alle `java.time`-Typen im ISO-Format schreiben. Die Kürzung von `LocalDateTime` und `LocalTime`
auf Minuten bleibt: ein Test hält sie als bewusstes Wire-Format fest.

Status: offen

### 8. Polymorphie

**Befund.** `@type` wird nur für Collection-Elemente gelesen. Eine abstrakte To-One-Property scheitert am
Konstruktor. Der Alias muss dem einfachen Klassennamen im selben Package entsprechen.

**Maßnahme.** Eine gemeinsame Subtyp-Auflösung für To-One und Collections. Zusätzlich die Standard-Registrierung
über `@JsonbTypeInfo` am Basistyp, die bisherige Konvention bleibt gültig.

Status: offen

### 9. Regel-Injektion und Ordnung

**Befund.** Der Deserializer injiziert Regeln je Property und Objekt neu. Sets werden als `HashSet`, Maps als
`HashMap` gebaut, die Ausgabe ist nicht deterministisch.

**Maßnahme.** Ein Regel-Cache pro Aufruf für beide Richtungen. `LinkedHashSet` und `LinkedHashMap` überall.

Status: offen

### 10. Schema-Provider

**Befund.** `SchemaProvider` lädt `$Schema` mit dem Classloader der Bibliothek. Fehlt die Klasse, folgt eine
`NullPointerException`. `Property.schema` löst das Schema des Zieltyps eifrig auf, wechselseitige To-One-Referenzen
können die Initialisierung rekursiv machen.

**Maßnahme.** Classloader der Provider-Klasse, klare Fehlermeldung, Zyklus prüfen und gegebenenfalls verzögert
auflösen.

Status: offen

### 11. Abhängigkeiten und Modulschnitt

**Befund.** Guava wird nicht benutzt. Hibernate wird nur für `EntitySchema.reference` gebraucht. Jackson dient dem
JSON-Schema-Export und dem Standard-Converter, obwohl die Bibliothek einen eigenen Generator hat.

**Maßnahme.** Guava entfernen. JSON Schema mit dem eigenen Generator schreiben. Ein Modul `json-mapper-core` ohne
Hibernate abtrennen. Das Artefakt `json-mapper` behält seine Koordinaten und enthält `EntitySchema`, damit
Konsumenten nichts ändern müssen.

Status: offen

### 12. Öffentliche API

**Befund.** `serialize`, `deserialize` und `prepare` nehmen bis zu sieben positionale Parameter, mehrere nullable.

**Maßnahme.** Eine einmal gebaute `MapperSettings` mit Validator, Loader und Injektor, Graph und Projektion als
`Option`. Die alten Signaturen bleiben und werden als veraltet markiert.

Status: offen

### 13. Doku, Build und CI

**Befund.** README nennt sbt 1 und Version 1.1.5, der Build nutzt sbt 2.0.11. Changelog-Abschnitte stehen im
README. Kein CI, das Scalafix-Plugin fehlt trotz Regel im Repo.

**Maßnahme.** README angleichen, `CHANGELOG.md` anlegen, GitHub-Workflow mit Format-Check und Tests, Scalafix
verdrahten.

Status: offen

### 14. Testlücken

**Befund.** Kaum abgedeckt sind Graph- und Subgraph-Traversal beim Serialisieren, Any-Property- und
Map-Serialisierung, Zeitwert-Roundtrips und Nebenläufigkeit.

**Maßnahme.** Gezielte Tests plus ein randomisierter Roundtrip-Test für Parser und Generator mit festem Seed.

Status: offen
