package fol.semantics

import munit.FunSuite
import logic.{FOL, Formula, Term}
import semantics.{Domain, Interpretation, Model, Valuation, FOLSemantics}
// EvaluationContext and holdsWithBinding are in the same package (fol.semantics)
import fol.datastore.RelationValue
import fol.datastore.RelationValue.{Const, Num}

/** Test suite for EvaluationContext
  *
  * Tests the evaluation context wrapper that simplifies FOL formula evaluation.
  * EvaluationContext delegates to FOLSemantics, so we focus on:
  * - Context operations (withBinding, holds)
  * - Extension method (holdsWithBinding)
  * - Factory methods
  * - Integration with FOLSemantics
  */
class EvaluationContextSpec extends FunSuite:

  // ==================== Test Setup ====================

  /** Simple integer arithmetic model for testing */
  val intModel: Model[Int] = FOLSemantics.integerModel(-5 to 5)

  /** Simple model with RelationValue domain for holdsWithBinding tests. */
  def rvModel: Model[RelationValue] =
    val domain = Domain(Set[RelationValue](Const("alice"), Const("bob"), Const("charlie")))
    val functions = Map[String, List[RelationValue] => RelationValue](
      "alice"   -> ((_: List[RelationValue]) => Const("alice")),
      "bob"     -> ((_: List[RelationValue]) => Const("bob")),
      "charlie" -> ((_: List[RelationValue]) => Const("charlie"))
    )
    val predicates = Map[String, List[RelationValue] => Boolean](
      "person" -> {
        case List(Const(name)) => Set("alice", "bob", "charlie").contains(name)
        case _ => false
      },
      "knows" -> {
        case List(Const("alice"), Const("bob")) => true
        case List(Const("bob"), Const("charlie")) => true
        case _ => false
      }
    )
    Model(Interpretation(domain, functions, predicates))

  // ==================== Basic Context Operations ====================

  test("create context with empty valuation") {
    val ctx = EvaluationContext.empty(intModel)
    assert(ctx.model == intModel)
    assert(ctx.valuation == Valuation(Map.empty[String, Int]))
  }

  test("create context with bindings") {
    val ctx = EvaluationContext(intModel, Map("x" -> 3, "y" -> 5))
    assert(ctx.isBound("x"))
    assert(ctx.isBound("y"))
    assert(!ctx.isBound("z"))
  }

  test("access domain from context") {
    val ctx = EvaluationContext.empty(intModel)
    assertEquals(ctx.domain, intModel.domain)
    assert(ctx.domain.elements.contains(0))
    assert(ctx.domain.elements.contains(5))
  }

  // ==================== withBinding Tests ====================

  test("withBinding: extends valuation") {
    val ctx = EvaluationContext.empty(intModel)
    val extended = ctx.withBinding("x", 3)

    assert(!ctx.isBound("x"))  // Original unchanged
    assert(extended.isBound("x"))
  }

  test("withBinding: chains multiple bindings") {
    val ctx = EvaluationContext.empty(intModel)
      .withBinding("x", 1)
      .withBinding("y", 2)
      .withBinding("z", 3)

    assert(ctx.isBound("x"))
    assert(ctx.isBound("y"))
    assert(ctx.isBound("z"))
  }

  test("withBinding: overwrites existing binding") {
    val ctx = EvaluationContext(intModel, Map("x" -> 1))
    val updated = ctx.withBinding("x", 5)

    // Verify by evaluating a formula that uses x
    val formula = Formula.Atom(FOL("=", List(Term.Var("x"), Term.Const("5"))))
    assert(updated.holds(formula))
  }

  // ==================== holds Tests ====================

  test("holds: evaluate simple atom") {
    val ctx = EvaluationContext(intModel, Map("x" -> 3))
    val formula = Formula.Atom(FOL("=", List(Term.Var("x"), Term.Const("3"))))
    assert(ctx.holds(formula))
  }

  test("holds: evaluate with no free variables") {
    val ctx = EvaluationContext.empty(intModel)
    val formula = Formula.Atom(FOL("=", List(Term.Const("2"), Term.Const("2"))))
    assert(ctx.holds(formula))
  }

  test("holds: conjunction") {
    val ctx = EvaluationContext(intModel, Map("x" -> 3, "y" -> 5))
    val formula = Formula.And(
      Formula.Atom(FOL("<", List(Term.Var("x"), Term.Var("y")))),
      Formula.Atom(FOL("<", List(Term.Const("0"), Term.Var("x"))))
    )
    assert(ctx.holds(formula))
  }

  test("holds: formula not satisfied") {
    val ctx = EvaluationContext(intModel, Map("x" -> 5))
    val formula = Formula.Atom(FOL("<", List(Term.Var("x"), Term.Const("3"))))
    assert(!ctx.holds(formula))
  }

  test("holds: delegates to FOLSemantics") {
    // Verify that context produces same result as direct FOLSemantics call
    val valuation = Valuation(Map("x" -> 3))
    val formula = Formula.Atom(FOL("=", List(Term.Var("x"), Term.Const("3"))))

    val ctx = EvaluationContext(intModel, valuation)
    val ctxResult = ctx.holds(formula)
    val directResult = FOLSemantics.holds(formula, intModel, valuation)

    assertEquals(ctxResult, directResult)
  }

  // ==================== evalTerm Tests ====================

  test("evalTerm: evaluate variable") {
    val ctx = EvaluationContext(intModel, Map("x" -> 42))
    val result = ctx.evalTerm(Term.Var("x"))
    assertEquals(result, 42)
  }

  test("evalTerm: evaluate constant") {
    val ctx = EvaluationContext.empty(intModel)
    val result = ctx.evalTerm(Term.Const("5"))
    assertEquals(result, 5)
  }

  test("evalTerm: evaluate function (arithmetic)") {
    val ctx = EvaluationContext(intModel, Map("x" -> 3, "y" -> 4))
    val term = Term.Fn("+", List(Term.Var("x"), Term.Var("y")))
    val result = ctx.evalTerm(term)
    assertEquals(result, 7)
  }

  // ==================== holdsWithBinding Extension ====================

  test("holdsWithBinding: Const value") {
    val ctx = EvaluationContext.empty(rvModel)
    val formula = Formula.Atom(FOL("person", List(Term.Var("x"))))

    assert(ctx.holdsWithBinding(formula, "x", Const("alice")))
    assert(ctx.holdsWithBinding(formula, "x", Const("bob")))
    assert(!ctx.holdsWithBinding(formula, "x", Const("unknown")))
  }

  test("holdsWithBinding: Int domain") {
    val ctx = EvaluationContext.empty(intModel)
    val formula = Formula.Atom(FOL("=", List(Term.Var("x"), Term.Const("5"))))

    assert(ctx.holdsWithBinding(formula, "x", 5))
    assert(!ctx.holdsWithBinding(formula, "x", 3))
  }

  test("holdsWithBinding: binary relation") {
    val ctx = EvaluationContext(rvModel, Map("y" -> Const("bob")))
    val formula = Formula.Atom(FOL("knows", List(Term.Var("x"), Term.Var("y"))))

    assert(ctx.holdsWithBinding(formula, "x", Const("alice")))
    assert(!ctx.holdsWithBinding(formula, "x", Const("charlie")))
  }

  test("holdsWithBinding: preserves existing bindings") {
    val ctx = EvaluationContext(rvModel, Map("y" -> Const("charlie")))
    val formula = Formula.Atom(FOL("knows", List(Term.Var("x"), Term.Var("y"))))

    assert(ctx.holdsWithBinding(formula, "x", Const("bob")))
  }

  // ==================== Integration Tests ====================

  test("integration: ScopeEvaluator pattern") {
    val ctx = EvaluationContext(rvModel, Map.empty[String, RelationValue])
    val scopeFormula = Formula.Atom(FOL("person", List(Term.Var("x"))))

    val rangeElements = Set[RelationValue](
      Const("alice"),
      Const("bob"),
      Const("unknown")
    )

    val satisfying = rangeElements.filter { elem =>
      ctx.holdsWithBinding(scopeFormula, "x", elem)
    }

    assertEquals(satisfying.size, 2)
  }

  test("integration: quantifier evaluation pattern") {
    val ctx = EvaluationContext.empty(intModel)
    val formula = Formula.Atom(FOL("<", List(Term.Const("0"), Term.Var("x"))))

    val positiveDomain = Set(1, 2, 3, 4, 5)
    val allPositive = positiveDomain.forall { d =>
      ctx.withBinding("x", d).holds(formula)
    }

    assert(allPositive)
  }

  test("integration: context chaining for nested quantifiers") {
    val ctx = EvaluationContext.empty(intModel)
    val formula = Formula.Atom(FOL("<", List(Term.Var("x"), Term.Var("y"))))

    val testX = 2
    val ctxWithX = ctx.withBinding("x", testX)

    val existsY = intModel.domain.elements.exists { y =>
      ctxWithX.withBinding("y", y).holds(formula)
    }

    assert(existsY)
  }

  test("integration: compare with direct FOLSemantics") {
    val valuation = Valuation(Map("x" -> 3, "y" -> 5))
    val formula = Formula.Or(
      Formula.Atom(FOL("=", List(Term.Var("x"), Term.Var("y")))),
      Formula.Atom(FOL("<", List(Term.Var("x"), Term.Var("y"))))
    )

    val contextResult = EvaluationContext(intModel, valuation).holds(formula)
    val directResult = FOLSemantics.holds(formula, intModel, valuation)

    assertEquals(contextResult, directResult)
  }
