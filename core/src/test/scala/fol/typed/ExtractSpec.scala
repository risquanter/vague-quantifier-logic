package fol.typed

import munit.FunSuite
import scala.compiletime.testing.typeChecks

/** Tests for [[Extract]] and the `Value.extract[A]` extension
  * (ADR-015 §2 + PLAN-symmetric-value-boundaries §3).
  *
  * Coverage:
  *   - `Extract[Long]` accepts a `Value` whose `raw` is a `Long`; rejects
  *     `String`-carrying values with a descriptive `Left`.
  *   - `Extract[Double]` accepts `Double`-carrying values; widens `Long`
  *     carriers; rejects non-numeric.
  *   - `Extract[String]` accepts `String`-carrying values; rejects others.
  *   - `value.extract[A]` extension delegates to the given `Extract[A]`.
  *   - Compile-error guarantee: extracting to a type with no `Extract`
  *     given fails to compile.
  */
class ExtractSpec extends FunSuite:

  private val sort = TypeId("AnySort")

  test("Extract[Long] accepts Long-carrying Value"):
    assertEquals(summon[Extract[Long]](Value(sort, 42L)), Right(42L))

  test("Extract[Long] rejects String-carrying Value with descriptive Left"):
    val res = summon[Extract[Long]](Value(sort, "42"))
    assert(res.isLeft, s"expected Left, got $res")
    assert(res.left.exists(_.toLowerCase.contains("long")),
      s"expected diagnostic to mention Long, got $res")

  test("Extract[Double] accepts Double-carrying Value"):
    assertEquals(summon[Extract[Double]](Value(sort, 3.14)), Right(3.14))

  test("Extract[Double] widens Long carrier to Double"):
    assertEquals(summon[Extract[Double]](Value(sort, 42L)), Right(42.0))

  test("Extract[Double] rejects String carrier"):
    assert(summon[Extract[Double]](Value(sort, "abc")).isLeft)

  test("Extract[String] accepts String-carrying Value"):
    assertEquals(summon[Extract[String]](Value(sort, "x")), Right("x"))

  test("Extract[String] rejects Long carrier"):
    assert(summon[Extract[String]](Value(sort, 42L)).isLeft)

  test("Value.extract[A] extension delegates to given Extract[A]"):
    assertEquals(Value(sort, 7L).extract[Long], Right(7L))
    assertEquals(Value(sort, "z").extract[String], Right("z"))
    assert(Value(sort, "z").extract[Long].isLeft)

  test("extracting to a type with no Extract given fails to compile"):
    // A local class with no `given Extract[Unregistered]` in scope.
    class Unregistered
    assert(
      !typeChecks("Value(sort, new Unregistered).extract[Unregistered]"),
      "Value.extract[Unregistered] must NOT compile without a given Extract instance"
    )
