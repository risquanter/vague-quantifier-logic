package semantics

import munit.FunSuite
import logic.{FOL, Formula, Term}
import semantics.FOLSemantics.*
import parser.FOLParser

/** Test suite for FOL Semantics - Phase 12
  * 
  * Tests the formal semantics of first-order logic:
  * - Domain and interpretation setup
  * - Term evaluation in models
  * - Formula satisfaction (Tarski semantics)
  * - Quantifier semantics
  * - Semantic entailment
  * 
  * Uses integer arithmetic as the primary test domain.
  */
class FOLSemanticsSpec extends FunSuite:
  
  // ==================== Helper: Setup Test Model ====================
  
  val intModel: Model[Int] = integerModel(-5 to 5)
  val emptyVal: Valuation[Int] = Valuation(Map.empty)
  
  // ==================== Domain Tests ====================
  
  test("domain must be non-empty") {
    intercept[IllegalArgumentException] {
      Domain(Set.empty[Int])
    }
  }
  
  test("domain contains elements") {
    val domain = Domain(Set(1, 2, 3))
    assert(domain.elements.contains(1))
    assert(domain.elements.contains(2))
    assert(domain.elements.contains(3))
  }
  
  // ==================== Valuation Tests ====================
  
  test("valuation lookup") {
    val v = Valuation(Map("x" -> 5, "y" -> 7))
    assertEquals(v("x"), 5)
    assertEquals(v("y"), 7)
  }
  
  test("valuation update") {
    val v = Valuation(Map("x" -> 5))
    val v2 = v.updated("x", 10)
    assertEquals(v2("x"), 10)
    assertEquals(v("x"), 5)  // Original unchanged
  }
  
  test("valuation unbound variable throws") {
    val v = Valuation(Map("x" -> 5))
    intercept[Exception] {
      v("y")
    }
  }
  
  // ==================== Term Evaluation Tests ====================
  
  test("eval variable") {
    val v = Valuation(Map("x" -> 5))
    val term = Term.Var("x")
    assertEquals(evalTerm(term, intModel.interpretation, v), 5)
  }
  
  test("eval constant") {
    val term = Term.Const("1")
    assertEquals(evalTerm(term, intModel.interpretation, emptyVal), 1)
  }
  
  test("eval function application") {
    val v = Valuation(Map("x" -> 3, "y" -> 4))
    val term = Term.Fn("+", List(Term.Var("x"), Term.Var("y")))
    assertEquals(evalTerm(term, intModel.interpretation, v), 7)
  }
  
  test("eval nested function") {
    val v = Valuation(Map("x" -> 2, "y" -> 3))
    // (x + y) * 2
    val term = Term.Fn("*", List(
      Term.Fn("+", List(Term.Var("x"), Term.Var("y"))),
      Term.Const("2")
    ))
    assertEquals(evalTerm(term, intModel.interpretation, v), 10)
  }
  
  test("eval complex arithmetic") {
    val v = Valuation(Map("x" -> 3))
    // x * x - 1
    val term = Term.Fn("-", List(
      Term.Fn("*", List(Term.Var("x"), Term.Var("x"))),
      Term.Const("1")
    ))
    assertEquals(evalTerm(term, intModel.interpretation, v), 8)
  }
  
  // ==================== Atomic Formula Tests ====================
  
  test("holds: true") {
    assert(holds(Formula.True, intModel, emptyVal))
  }
  
  test("holds: false") {
    assert(!holds(Formula.False, intModel, emptyVal))
  }
  
  test("holds: equality true") {
    val v = Valuation(Map("x" -> 5))
    val fm = FOLParser.parse("x = 5")
    assert(holds(fm, intModel, v))
  }
  
  test("holds: equality false") {
    val v = Valuation(Map("x" -> 5))
    val fm = FOLParser.parse("x = 7")
    assert(!holds(fm, intModel, v))
  }
  
  test("holds: less than true") {
    val v = Valuation(Map("x" -> 3, "y" -> 5))
    val fm = FOLParser.parse("x < y")
    assert(holds(fm, intModel, v))
  }
  
  test("holds: less than false") {
    val v = Valuation(Map("x" -> 5, "y" -> 3))
    val fm = FOLParser.parse("x < y")
    assert(!holds(fm, intModel, v))
  }
  
  test("holds: unary predicate") {
    val v = Valuation(Map("x" -> 4))
    val fm = FOLParser.parse("even(x)")
    assert(holds(fm, intModel, v))
  }
  
  test("holds: unary predicate false") {
    val v = Valuation(Map("x" -> 3))
    val fm = FOLParser.parse("even(x)")
    assert(!holds(fm, intModel, v))
  }
  
  // ==================== Connective Tests ====================
  
  test("holds: negation") {
    val v = Valuation(Map("x" -> 5))
    val fm = FOLParser.parse("~(x < 3)")
    assert(holds(fm, intModel, v))
  }
  
  test("holds: conjunction true") {
    val v = Valuation(Map("x" -> 4))
    val fm = FOLParser.parse("x > 0 /\\ even(x)")
    assert(holds(fm, intModel, v))
  }
  
  test("holds: conjunction false") {
    val v = Valuation(Map("x" -> 3))
    val fm = FOLParser.parse("x > 0 /\\ even(x)")
    assert(!holds(fm, intModel, v))
  }
  
  test("holds: disjunction true") {
    val v = Valuation(Map("x" -> 3))
    val fm = FOLParser.parse("even(x) \\/ odd(x)")
    assert(holds(fm, intModel, v))
  }
  
  test("holds: implication true (consequent true)") {
    val v = Valuation(Map("x" -> 4))
    val fm = FOLParser.parse("even(x) ==> x > 0")
    assert(holds(fm, intModel, v))
  }
  
  test("holds: implication true (antecedent false)") {
    val v = Valuation(Map("x" -> 3))
    val fm = FOLParser.parse("even(x) ==> x < 0")
    assert(holds(fm, intModel, v))  // Vacuously true
  }
  
  test("holds: implication false") {
    val v = Valuation(Map("x" -> -2))
    val fm = FOLParser.parse("even(x) ==> x > 0")
    assert(!holds(fm, intModel, v))
  }
  
  test("holds: iff true") {
    val v = Valuation(Map("x" -> 4))
    val fm = FOLParser.parse("even(x) <=> ~odd(x)")
    assert(holds(fm, intModel, v))
  }
  
  test("holds: iff false") {
    val v = Valuation(Map("x" -> 3))
    val fm = FOLParser.parse("even(x) <=> odd(x)")
    assert(!holds(fm, intModel, v))
  }
  
  // ==================== Quantifier Tests ====================
  
  test("holds: forall true") {
    // forall x. x = x (everything equals itself)
    val fm = FOLParser.parse("forall x. x = x")
    assert(holds(fm, intModel, emptyVal))
  }
  
  test("holds: forall false") {
    // forall x. x > 0 (not all numbers are positive in [-5, 5])
    val fm = FOLParser.parse("forall x. x > 0")
    assert(!holds(fm, intModel, emptyVal))
  }
  
  test("holds: exists true") {
    // exists x. x > 3 (there exists a number greater than 3)
    val fm = FOLParser.parse("exists x. x > 3")
    assert(holds(fm, intModel, emptyVal))
  }
  
  test("holds: exists false") {
    // exists x. x > 10 (no number in [-5, 5] is greater than 10)
    val fm = FOLParser.parse("exists x. x > 10")
    assert(!holds(fm, intModel, emptyVal))
  }
  
  test("holds: nested quantifiers") {
    // forall x. exists y. y > x (for every x, there's a larger y)
    val fm = FOLParser.parse("forall x. exists y. y > x")
    assert(!holds(fm, intModel, emptyVal))  // False: 5 is max in our domain
  }
  
  test("holds: nested quantifiers 2") {
    // exists x. forall y. x <= y (exists a minimum)
    val fm = FOLParser.parse("exists x. forall y. x <= y")
    assert(holds(fm, intModel, emptyVal))  // True: -5 is minimum
  }
  
  test("holds: quantifier with conjunction") {
    // forall x. even(x) ==> x + 1 = x + 1
    val fm = FOLParser.parse("forall x. even(x) ==> x + 1 = x + 1")
    assert(holds(fm, intModel, emptyVal))
  }
  
  test("holds: existential with bound variable") {
    // exists x. x > 0 /\\ even(x)
    val fm = FOLParser.parse("exists x. x > 0 /\\ even(x)")
    assert(holds(fm, intModel, emptyVal))  // 2, 4 satisfy this
  }
  
  // ==================== Complex Formula Tests ====================
  
  test("holds: arithmetic properties") {
    // forall x. x + 0 = x
    val fm = FOLParser.parse("forall x. x + 0 = x")
    assert(holds(fm, intModel, emptyVal))
  }
  
  test("holds: commutativity") {
    // forall x. forall y. x + y = y + x
    val fm = FOLParser.parse("forall x. forall y. x + y = y + x")
    assert(holds(fm, intModel, emptyVal))
  }
  
  test("holds: transitivity of less than") {
    // forall x y z. (x < y /\\ y < z) ==> x < z
    val fm = FOLParser.parse("forall x. forall y. forall z. (x < y /\\ y < z) ==> x < z")
    assert(holds(fm, intModel, emptyVal))
  }
  
  test("holds: complex predicate logic") {
    // exists x. (x > 0 /\\ even(x) /\\ x < 5)
    val fm = FOLParser.parse("exists x. (x > 0 /\\ even(x) /\\ x < 5)")
    assert(holds(fm, intModel, emptyVal))  // x = 2 or x = 4
  }
  
  test("holds: de Morgan's law (semantic version)") {
    val v = Valuation(Map("x" -> 3))
    val fm1 = FOLParser.parse("~(even(x) /\\ x > 5)")
    val fm2 = FOLParser.parse("~even(x) \\/ ~(x > 5)")
    assertEquals(holds(fm1, intModel, v), holds(fm2, intModel, v))
  }
  
  // ==================== Entailment Tests ====================
  
  test("entailment: simple case") {
    // P(x), P(x) ==> Q(x) ⊨ Q(x)
    val p1 = FOLParser.parse("even(x)")
    val p2 = FOLParser.parse("even(x) ==> x > -10")
    val conclusion = FOLParser.parse("x > -10")
    
    val v = Valuation(Map("x" -> 4))
    val model = Model(intModel.interpretation)
    
    // Check that when premises hold, conclusion holds
    assert(holds(p1, model, v))
    assert(holds(p2, model, v))
    assert(holds(conclusion, model, v))
  }
  
  test("entailment: modus ponens") {
    // If we have P and P ==> Q, then we can derive Q
    val v = Valuation(Map("x" -> 4))
    val premises = List(
      FOLParser.parse("x = 4"),
      FOLParser.parse("x = 4 ==> even(x)")
    )
    val conclusion = FOLParser.parse("even(x)")
    
    // Check manually that premises imply conclusion
    assert(holds(premises(0), intModel, v))
    assert(holds(premises(1), intModel, v))
    assert(holds(conclusion, intModel, v))
  }
  
  // ==================== Integer Model Tests ====================
  
  test("integer model: basic operations") {
    val v = Valuation(Map("x" -> 3, "y" -> 4))
    
    assertEquals(
      evalTerm(Term.Fn("+", List(Term.Var("x"), Term.Var("y"))),
        intModel.interpretation, v),
      7
    )
    
    assertEquals(
      evalTerm(Term.Fn("*", List(Term.Var("x"), Term.Var("y"))),
        intModel.interpretation, v),
      12
    )
  }
  
  test("integer model: predicates") {
    val v = Valuation(Map("x" -> 4))
    
    assert(holds(FOLParser.parse("even(x)"), intModel, v))
    assert(!holds(FOLParser.parse("odd(x)"), intModel, v))
    assert(holds(FOLParser.parse("positive(x)"), intModel, v))
  }
  
  // ==================== Boolean Model Tests ====================
  
  test("boolean model: basic") {
    val boolModel = booleanModel()
    val v = Valuation(Map("x" -> true, "y" -> false))
    
    // Test that true is interpreted correctly
    val trueTerm = Term.Const("true")
    assertEquals(evalTerm(trueTerm, boolModel.interpretation, v), true)
  }
  
  // ==================== Edge Cases ====================
  
  test("holds: empty conjunction (vacuously true)") {
    // This tests the base case behavior
    assert(holds(Formula.True, intModel, emptyVal))
  }
  
  test("quantifier over single element domain") {
    val singleDomain = Domain(Set(42))
    val singleInterp = Interpretation(
      singleDomain,
      Map("c" -> (_ => 42)),
      Map("P" -> { case List(x) => x == 42 })
    )
    val singleModel = Model(singleInterp)
    
    val fm = FOLParser.parse("forall x. P(x)")
    assert(holds(fm, singleModel, emptyVal))
  }
  
  test("term evaluation with nested operators") {
    val v = Valuation(Map("x" -> 2))
    // ((x + 1) * (x - 1))
    val term = Term.Fn("*", List(
      Term.Fn("+", List(Term.Var("x"), Term.Const("1"))),
      Term.Fn("-", List(Term.Var("x"), Term.Const("1")))
    ))
    // (2 + 1) * (2 - 1) = 3 * 1 = 3
    assertEquals(evalTerm(term, intModel.interpretation, v), 3)
  }
  
  test("formula with mixed bound and free variables") {
    val v = Valuation(Map("y" -> 3))
    // exists x. x + y = 5
    val fm = FOLParser.parse("exists x. x + y = 5")
    assert(holds(fm, intModel, v))  // x = 2, y = 3 satisfies this
  }
  
  test("double negation") {
    val v = Valuation(Map("x" -> 4))
    val fm = FOLParser.parse("~(~even(x))")
    assertEquals(holds(fm, intModel, v), holds(FOLParser.parse("even(x)"), intModel, v))
  }
