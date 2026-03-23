package fol.bridge

import munit.FunSuite
import semantics.{Domain, Interpretation, Model, ModelAugmenter}

/** Tests for NumericAugmenter — built-in comparisons, arithmetic,
  * and numeric literal resolution (ADR-005 §4).
  */
class NumericAugmenterSpec extends FunSuite:

  // ==================== Test Fixtures ====================

  /** Minimal KB-like model with string domain and one relation. */
  def kbModel: Model[Any] =
    val domain = Domain(Set[Any]("alice", "bob"))
    val fns = Map[String, List[Any] => Any](
      "alice" -> ((_: List[Any]) => "alice"),
      "bob"   -> ((_: List[Any]) => "bob")
    )
    val preds = Map[String, List[Any] => Boolean](
      "person" -> { case List(s: String) => Set("alice", "bob").contains(s)
                    case _ => false }
    )
    Model(Interpretation(domain, fns, preds))

  /** Apply the numeric augmenter to the KB model. */
  def augmented: Model[Any] = NumericAugmenter.augmenter(kbModel)

  // ==================== Comparison Predicates ====================

  test("> true") {
    assert(augmented.interpretation.getPredicate(">")(List(5.0, 3.0)))
  }

  test("> false") {
    assert(!augmented.interpretation.getPredicate(">")(List(2.0, 7.0)))
  }

  test("< true") {
    assert(augmented.interpretation.getPredicate("<")(List(1.0, 9.0)))
  }

  test("< false") {
    assert(!augmented.interpretation.getPredicate("<")(List(9.0, 1.0)))
  }

  test(">= boundary") {
    assert(augmented.interpretation.getPredicate(">=")(List(5.0, 5.0)))
    assert(augmented.interpretation.getPredicate(">=")(List(5.1, 5.0)))
    assert(!augmented.interpretation.getPredicate(">=")(List(4.9, 5.0)))
  }

  test("<= boundary") {
    assert(augmented.interpretation.getPredicate("<=")(List(5.0, 5.0)))
    assert(augmented.interpretation.getPredicate("<=")(List(4.9, 5.0)))
    assert(!augmented.interpretation.getPredicate("<=")(List(5.1, 5.0)))
  }

  test("= numeric equality") {
    assert(augmented.interpretation.getPredicate("=")(List(3.0, 3.0)))
    assert(!augmented.interpretation.getPredicate("=")(List(3.0, 4.0)))
  }

  // ==================== Mixed Types ====================

  test("Int > Long") {
    assert(augmented.interpretation.getPredicate(">")(List(100, 50L)))
  }

  test("Double >= BigDecimal") {
    assert(augmented.interpretation.getPredicate(">=")(List(3.14, BigDecimal("3.14"))))
  }

  test("String '42' < Int 100") {
    assert(augmented.interpretation.getPredicate("<")(List("42", 100)))
  }

  // ==================== Arithmetic Functions ====================

  test("+ adds two values") {
    val result = augmented.interpretation.getFunction("+")(List(3.0, 4.0))
    assertEquals(result.asInstanceOf[Double], 7.0)
  }

  test("- binary subtraction") {
    val result = augmented.interpretation.getFunction("-")(List(10.0, 3.0))
    assertEquals(result.asInstanceOf[Double], 7.0)
  }

  test("- unary negation") {
    val result = augmented.interpretation.getFunction("-")(List(5.0))
    assertEquals(result.asInstanceOf[Double], -5.0)
  }

  test("* multiplies") {
    val result = augmented.interpretation.getFunction("*")(List(3.0, 4.0))
    assertEquals(result.asInstanceOf[Double], 12.0)
  }

  test("/ divides") {
    val result = augmented.interpretation.getFunction("/")(List(10.0, 4.0))
    assertEquals(result.asInstanceOf[Double], 2.5)
  }

  test("/ division by zero throws") {
    intercept[Exception] {
      augmented.interpretation.getFunction("/")(List(1.0, 0.0))
    }
  }

  // ==================== Numeric Literal Resolution ====================

  test("numeric literal: integer resolves to Double") {
    val result = augmented.interpretation.getFunction("5000000")(Nil)
    assertEquals(result.asInstanceOf[Double], 5000000.0)
  }

  test("numeric literal: negative") {
    val result = augmented.interpretation.getFunction("-100")(Nil)
    assertEquals(result.asInstanceOf[Double], -100.0)
  }

  test("numeric literal: decimal") {
    val result = augmented.interpretation.getFunction("3.14")(Nil)
    assertEquals(result.asInstanceOf[Double], 3.14)
  }

  test("non-numeric string: throws") {
    intercept[Exception] {
      augmented.interpretation.getFunction("xyz")(Nil)
    }
  }

  // ==================== Preservation ====================

  test("augmenter does not clobber existing KB predicates") {
    // The KB had "person" predicate — it should still work
    assert(augmented.interpretation.getPredicate("person")(List("alice")))
    assert(!augmented.interpretation.getPredicate("person")(List("unknown")))
  }

  test("augmenter does not clobber existing KB functions (fallback only)") {
    // KB constants "alice" and "bob" should still resolve
    assertEquals(augmented.interpretation.getFunction("alice")(Nil), "alice")
    assertEquals(augmented.interpretation.getFunction("bob")(Nil), "bob")
  }

  // ==================== toDouble edge cases ====================

  test("toDouble: Int") {
    assertEquals(NumericAugmenter.toDouble(42), 42.0)
  }

  test("toDouble: Long") {
    assertEquals(NumericAugmenter.toDouble(5000000L), 5000000.0)
  }

  test("toDouble: BigDecimal") {
    assertEquals(NumericAugmenter.toDouble(BigDecimal("1.5")), 1.5)
  }

  test("toDouble: numeric String") {
    assertEquals(NumericAugmenter.toDouble("99.9"), 99.9)
  }

  test("toDouble: non-numeric String throws") {
    intercept[Exception] {
      NumericAugmenter.toDouble("alice")
    }
  }

  test("toDouble: unsupported type throws") {
    intercept[Exception] {
      NumericAugmenter.toDouble(List(1, 2))
    }
  }

  // ==================== numericLiteral ====================

  test("numericLiteral: parseable returns Some") {
    assert(NumericAugmenter.numericLiteral("42").isDefined)
    assert(NumericAugmenter.numericLiteral("-3.14").isDefined)
  }

  test("numericLiteral: non-parseable returns None") {
    assert(NumericAugmenter.numericLiteral("hello").isEmpty)
    assert(NumericAugmenter.numericLiteral("").isEmpty)
  }
