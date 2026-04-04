package fol.typed

import LiteralValue.*
import munit.FunSuite

class TypedFunctionImplSpec extends FunSuite:

  // ─── Right path: impl succeeds, wrap is applied ────────────────────────────

  test("of[Double]: Right result is wrapped by wrap function"):
    val fn = TypedFunctionImpl.of[Double](
      impl = _ => Right(0.07),
      wrap = FloatLiteral(_)
    )
    assertEquals(fn(Nil), Right(FloatLiteral(0.07)))

  test("of[Long]: Right result is wrapped by wrap function"):
    val fn = TypedFunctionImpl.of[Long](
      impl = _ => Right(5000000L),
      wrap = IntLiteral(_)
    )
    assertEquals(fn(Nil), Right(IntLiteral(5000000L)))

  test("of[String]: Right result is wrapped by wrap function"):
    val fn = TypedFunctionImpl.of[String](
      impl = _ => Right("hello"),
      wrap = TextLiteral(_)
    )
    assertEquals(fn(Nil), Right(TextLiteral("hello")))

  // ─── Left path: impl fails, wrap is NOT called ─────────────────────────────

  test("of[Double]: Left from impl propagates without calling wrap"):
    var wrapCalled = false
    val fn = TypedFunctionImpl.of[Double](
      impl = _ => Left("computation failed"),
      wrap = d => { wrapCalled = true; FloatLiteral(d) }
    )
    assertEquals(fn(Nil), Left("computation failed"))
    assert(!wrapCalled, "wrap should not be called when impl returns Left")

  // ─── args forwarding ───────────────────────────────────────────────────────

  test("of[Double]: args list is forwarded to impl unchanged"):
    val tProb = TypeId("Probability")
    val vA    = Value(tProb, FloatLiteral(0.05))
    val vB    = Value(tProb, FloatLiteral(0.10))
    var received: List[Value] = Nil
    val fn = TypedFunctionImpl.of[Double](
      impl = args => { received = args; Right(0.0) },
      wrap = FloatLiteral(_)
    )
    fn(List(vA, vB))
    assertEquals(received, List(vA, vB))
