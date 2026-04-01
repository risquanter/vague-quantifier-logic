package fol.bridge

import munit.FunSuite
import fol.datastore.RelationValue
import fol.datastore.RelationValue.{Const, Num}
import semantics.{Domain, Interpretation, Model, ModelAugmenter}

/** Tests for NumericAugmenter — backward-compatible composition of
  * ComparisonAugmenter, ArithmeticAugmenter, and LiteralResolver.
  *
  * Also tests the generic augmenters directly with non-RelationValue domains.
  */
class NumericAugmenterSpec extends FunSuite:

  // ==================== Test Fixtures ====================

  /** Minimal KB-like model with RelationValue domain. */
  def kbModel: Model[RelationValue] =
    val domain = Domain(Set[RelationValue](Const("alice"), Const("bob")))
    val fns = Map[String, List[RelationValue] => RelationValue](
      "alice" -> ((_: List[RelationValue]) => Const("alice")),
      "bob"   -> ((_: List[RelationValue]) => Const("bob"))
    )
    val preds = Map[String, List[RelationValue] => Boolean](
      "person" -> { case List(Const(s)) => Set("alice", "bob").contains(s)
                    case _ => false }
    )
    Model(Interpretation(domain, fns, preds))

  /** Apply the numeric augmenter to the KB model. */
  def augmented: Model[RelationValue] = NumericAugmenter.augmenter(kbModel)

  // ==================== Comparison Predicates (RelationValue) ====================

  test("> true (Num)") {
    assert(augmented.interpretation.getPredicate(">")(List(Num(5), Num(3))))
  }

  test("> false (Num)") {
    assert(!augmented.interpretation.getPredicate(">")(List(Num(2), Num(7))))
  }

  test("< true (Num)") {
    assert(augmented.interpretation.getPredicate("<")(List(Num(1), Num(9))))
  }

  test("< false (Num)") {
    assert(!augmented.interpretation.getPredicate("<")(List(Num(9), Num(1))))
  }

  test(">= boundary (Num)") {
    assert(augmented.interpretation.getPredicate(">=")(List(Num(5), Num(5))))
    assert(augmented.interpretation.getPredicate(">=")(List(Num(6), Num(5))))
    assert(!augmented.interpretation.getPredicate(">=")(List(Num(4), Num(5))))
  }

  test("<= boundary (Num)") {
    assert(augmented.interpretation.getPredicate("<=")(List(Num(5), Num(5))))
    assert(augmented.interpretation.getPredicate("<=")(List(Num(4), Num(5))))
    assert(!augmented.interpretation.getPredicate("<=")(List(Num(6), Num(5))))
  }

  test("= numeric equality (Num)") {
    assert(augmented.interpretation.getPredicate("=")(List(Num(3), Num(3))))
    assert(!augmented.interpretation.getPredicate("=")(List(Num(3), Num(4))))
  }

  test("Const comparison: lexicographic") {
    assert(augmented.interpretation.getPredicate("<")(List(Const("alice"), Const("bob"))))
    assert(!augmented.interpretation.getPredicate("<")(List(Const("bob"), Const("alice"))))
  }

  // ==================== Arithmetic Functions (RelationValue) ====================

  test("+ adds two Num values") {
    val result = augmented.interpretation.getFunction("+")(List(Num(3), Num(4)))
    assertEquals(result, Num(7))
  }

  test("- binary subtraction (Num)") {
    val result = augmented.interpretation.getFunction("-")(List(Num(10), Num(3)))
    assertEquals(result, Num(7))
  }

  test("- unary negation (Num)") {
    val result = augmented.interpretation.getFunction("-")(List(Num(5)))
    assertEquals(result, Num(-5))
  }

  test("* multiplies (Num)") {
    val result = augmented.interpretation.getFunction("*")(List(Num(3), Num(4)))
    assertEquals(result, Num(12))
  }

  test("/ divides (integer truncation)") {
    val result = augmented.interpretation.getFunction("/")(List(Num(10), Num(4)))
    assertEquals(result, Num(2))  // integer division: 10/4 = 2
  }

  test("/ division by zero throws") {
    intercept[Exception] {
      augmented.interpretation.getFunction("/")(List(Num(1), Num(0)))
    }
  }

  // ==================== Numeric Literal Resolution (RelationValue) ====================

  test("numeric literal: integer resolves to Num") {
    val result = augmented.interpretation.getFunction("5000000")(Nil)
    assertEquals(result, Num(5000000))
  }

  test("numeric literal: negative") {
    val result = augmented.interpretation.getFunction("-100")(Nil)
    assertEquals(result, Num(-100))
  }

  test("non-numeric string: throws (no fallback)") {
    intercept[Exception] {
      augmented.interpretation.getFunction("xyz")(Nil)
    }
  }

  // ==================== Preservation ====================

  test("augmenter does not clobber existing KB predicates") {
    assert(augmented.interpretation.getPredicate("person")(List(Const("alice"))))
    assert(!augmented.interpretation.getPredicate("person")(List(Const("unknown"))))
  }

  test("augmenter does not clobber existing KB functions (fallback only)") {
    assertEquals(augmented.interpretation.getFunction("alice")(Nil), Const("alice"))
    assertEquals(augmented.interpretation.getFunction("bob")(Nil), Const("bob"))
  }

  // ==================== Generic ComparisonAugmenter[Double] ====================

  test("ComparisonAugmenter[Double]: >, <, >=, <=, =") {
    val domain = Domain(Set(1.0, 2.0, 3.0))
    val model = Model[Double](Interpretation(domain, Map.empty, Map.empty))
    val aug = ComparisonAugmenter.augmenter[Double](model)

    assert(aug.interpretation.getPredicate(">")(List(3.0, 1.0)))
    assert(!aug.interpretation.getPredicate(">")(List(1.0, 3.0)))
    assert(aug.interpretation.getPredicate("=")(List(2.0, 2.0)))
  }

  // ==================== Generic ArithmeticAugmenter[Double] ====================

  test("ArithmeticAugmenter[Double]: +, -, *, /") {
    val domain = Domain(Set(1.0, 2.0, 3.0))
    val model = Model[Double](Interpretation(domain, Map.empty, Map.empty))
    val aug = ArithmeticAugmenter.augmenter[Double](model)

    assertEquals(aug.interpretation.getFunction("+")(List(3.0, 4.0)), 7.0)
    assertEquals(aug.interpretation.getFunction("-")(List(10.0, 3.0)), 7.0)
    assertEquals(aug.interpretation.getFunction("-")(List(5.0)), -5.0)
    assertEquals(aug.interpretation.getFunction("*")(List(3.0, 4.0)), 12.0)
    assertEquals(aug.interpretation.getFunction("/")(List(10.0, 4.0)), 2.5)
  }

  test("ArithmeticAugmenter[Double]: division by zero throws") {
    val domain = Domain(Set(1.0))
    val model = Model[Double](Interpretation(domain, Map.empty, Map.empty))
    val aug = ArithmeticAugmenter.augmenter[Double](model)
    intercept[Exception] {
      aug.interpretation.getFunction("/")(List(1.0, 0.0))
    }
  }

  // ==================== Generic LiteralResolver[RelationValue] ====================

  test("LiteralResolver[RelationValue]: integer literal resolves") {
    val domain = Domain(Set[RelationValue](Const("a")))
    val model = Model[RelationValue](Interpretation(domain,
      Map("a" -> ((_: List[RelationValue]) => Const("a"))), Map.empty))
    val aug = LiteralResolver.augmenter[RelationValue](model)

    assertEquals(aug.interpretation.getFunction("42")(Nil), Num(42))
  }

  test("LiteralResolver[RelationValue]: non-numeric falls through") {
    val domain = Domain(Set[RelationValue](Const("a")))
    val model = Model[RelationValue](Interpretation(domain,
      Map("a" -> ((_: List[RelationValue]) => Const("a"))), Map.empty))
    val aug = LiteralResolver.augmenter[RelationValue](model)

    // "a" is already a constant in the model, should resolve to Const("a")
    assertEquals(aug.interpretation.getFunction("a")(Nil), Const("a"))
    // "xyz" is neither a constant nor numeric — throws
    intercept[Exception] {
      aug.interpretation.getFunction("xyz")(Nil)
    }
  }
