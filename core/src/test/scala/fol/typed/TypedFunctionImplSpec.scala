package fol.typed

import munit.FunSuite

/** Phase 4 (PLAN-symmetric-value-boundaries §6 / ADR-015 §1, §2).
  *
  * `TypedFunctionImpl.of[A]` no longer takes a `wrap: A => LiteralValue`
  * parameter. The dispatcher boundary now carries `Any` end-to-end; the
  * consumer's native `A` is widened at the registration site, and any
  * downstream lambda recovers a typed view via `value.extract[A]`
  * (`Extract[A]` instance, ADR-015 §2). The T-003 normalizer concern
  * (uniform shape for inline literals vs function returns) is deferred
  * to a separate plan.
  */
class TypedFunctionImplSpec extends FunSuite:

  // ─── Right path: native A flows out as Any ─────────────────────────────────

  test("of[Double]: Right(0.07) flows through as Right(0.07: Any)"):
    val fn = TypedFunctionImpl.of[Double](impl = _ => Right(0.07))
    assertEquals(fn(Nil), Right(0.07))

  test("of[Long]: Right(5000000L) flows through unchanged"):
    val fn = TypedFunctionImpl.of[Long](impl = _ => Right(5000000L))
    assertEquals(fn(Nil), Right(5000000L))

  test("of[String]: Right(\"hello\") flows through unchanged"):
    val fn = TypedFunctionImpl.of[String](impl = _ => Right("hello"))
    assertEquals(fn(Nil), Right("hello"))

  // ─── Left path: error propagates ───────────────────────────────────────────

  test("of[Double]: Left from impl propagates"):
    val fn = TypedFunctionImpl.of[Double](impl = _ => Left("computation failed"))
    assertEquals(fn(Nil), Left("computation failed"))

  // ─── args forwarding ───────────────────────────────────────────────────────

  test("of[Double]: args list is forwarded to impl unchanged"):
    val tProb = TypeId("Probability")
    val vA    = Value(tProb, 0.05)
    val vB    = Value(tProb, 0.10)
    var received: List[Value] = Nil
    val fn = TypedFunctionImpl.of[Double](
      impl = args => { received = args; Right(0.0) }
    )
    fn(List(vA, vB))
    assertEquals(received, List(vA, vB))
