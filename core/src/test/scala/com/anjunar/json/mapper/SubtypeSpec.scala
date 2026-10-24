package com.anjunar.json.mapper

import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonObject, JsonString}
import com.anjunar.json.mapper.provider.DTO
import com.anjunar.scala.universe.TypeResolver
import jakarta.json.bind.annotation.{JsonbProperty, JsonbSubtype, JsonbTypeInfo}
import jakarta.validation.Validation
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util
import scala.annotation.meta.field

class SubtypeSpec extends AnyFunSuite with Matchers {

  private val validator = Validation
    .byDefaultProvider()
    .configure()
    .messageInterpolator(new ParameterMessageInterpolator())
    .buildValidatorFactory()
    .getValidator

  private val inject: [T] => Class[T] => T = [T] => (clazz: Class[T]) => clazz.getDeclaredConstructor().newInstance()

  private def deserialize[T <: AnyRef](target: T, json: String): T = {
    new JsonMapper(
      Option(validator),
      (_, _) => null,
      inject
    ).deserialize(JsonParser.parse(json), target, TypeResolver.resolve(target.getClass))
    target
  }

  private def serialize(value: AnyRef): JsonObject =
    JsonParser.parse(new JsonMapper(inject = inject).serialize(value, TypeResolver.resolve(value.getClass)))
      .asInstanceOf[JsonObject]

  test("a to-one field declared with a base type creates the subtype its @JsonbTypeInfo alias names") {
    val drawing = deserialize(new DrawingDto, """{"main":{"kind":"circle","label":"sun","radius":3}}""")
    drawing.main shouldBe a[CircleDto]
    drawing.main.label shouldBe "sun"
    drawing.main.asInstanceOf[CircleDto].radius shouldBe 3
  }

  test("collections use the same @JsonbTypeInfo registration") {
    val drawing =
      deserialize(new DrawingDto, """{"shapes":[{"kind":"square","side":2},{"kind":"circle","radius":1}]}""")
    drawing.shapes.get(0) shouldBe a[SquareDto]
    drawing.shapes.get(1) shouldBe a[CircleDto]
  }

  test("an existing value of the named subtype is merged in place") {
    val drawing = new DrawingDto
    val circle = new CircleDto
    circle.radius = 1
    drawing.main = circle
    deserialize(drawing, """{"main":{"kind":"circle","label":"moon"}}""")
    drawing.main should be theSameInstanceAs circle
    circle.label shouldBe "moon"
    circle.radius shouldBe 1
  }

  test("an unknown alias or a missing alias for an abstract type is reported at the field") {
    val unknown = the[ErrorRequestException] thrownBy deserialize(new DrawingDto, """{"main":{"kind":"star"}}""")
    unknown.errors.get(0).path shouldBe util.List.of("main", "kind")
    val missing = the[ErrorRequestException] thrownBy deserialize(new DrawingDto, """{"main":{"label":"x"}}""")
    missing.errors.get(0).path shouldBe util.List.of("main")
    missing.errors.get(0).message shouldBe "This value needs a type in 'kind'."
  }

  test("serialization writes the registered alias under the registration's key") {
    val drawing = new DrawingDto
    drawing.main = new CircleDto
    drawing.main.label = "sun"
    val square = new SquareDto
    square.side = 4
    drawing.shapes.add(square)

    val json = serialize(drawing)
    json.getJsonObject("main").getString("kind") shouldBe "circle"
    val shape = json.value.get("shapes").asInstanceOf[JsonArray].value.get(0).asInstanceOf[JsonObject]
    shape.getString("kind") shouldBe "square"
    shape.value.get("side").value shouldBe "4"
  }

  test("a to-one field also accepts the conventional @JsonbSubtype registration") {
    val holder = deserialize(new SingleFieldHolder, """{"field":{"@type":"TextFieldDto","key":"k","value":"v"}}""")
    holder.field shouldBe a[TextFieldDto]
    holder.field.asInstanceOf[TextFieldDto].value shouldBe "v"
    serialize(holder).getJsonObject("field").value.get("@type") shouldBe a[JsonString]
  }
}

@JsonbTypeInfo(
  key = "kind",
  value = Array(
    new JsonbSubtype(alias = "circle", `type` = classOf[CircleDto]),
    new JsonbSubtype(alias = "square", `type` = classOf[SquareDto])
  )
)
abstract class ShapeDto extends DTO {
  @(JsonbProperty @field)
  var label: String = null
}

class CircleDto extends ShapeDto {
  @(JsonbProperty @field)
  var radius: Int = 0
}

class SquareDto extends ShapeDto {
  @(JsonbProperty @field)
  var side: Int = 0
}

class DrawingDto {
  @(JsonbProperty @field)
  var main: ShapeDto = null
  @(JsonbProperty @field)
  val shapes: util.List[ShapeDto] = new util.ArrayList[ShapeDto]()
}

class SingleFieldHolder {
  @(JsonbProperty @field)
  var field: AbstractFieldDto = null
}
