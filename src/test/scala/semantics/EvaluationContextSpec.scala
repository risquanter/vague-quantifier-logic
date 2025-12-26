package semantics

import munit.FunSuite
import logic.{FOL, Formula, Term}
import semantics.{Domain, Interpretation, Model, Valuation, EvaluationContext, FOLSemantics}
import vague.datastore.RelationValue

/** Test suite for EvaluationContext
  * 
  * Tests the evaluation context wrapper that simplifies FOL formula evaluation.
  * EvaluationContext delegates to FOLSemantics, so we focus on:
  * - Context operations (withBinding, holds)
  * - Extension methods (holdsWithRelationValue)
  * - Factory methods
  * - Integration with FOLSemantics
  */
class EvaluationContextSpec extends FunSuite:
  
  // ==================== Test Setup ====================
  
  /** Simple integer arithmetic model for testing */
  val intModel: Model[Int] = FOLSemantics.integerModel(-5 to 5)
  
  /** Simple model with string domain */
  def stringModel: Model[Any] =
    val domain = Domain(Set[Any]("alice", "bob", "charlie"))
    val functions = Map[String, List[Any] => Any](
      "alice" -> ((args: List[Any]) => "alice"),
      "bob" -> ((args: List[Any]) => "bob"),
      "charlie" -> ((args: List[Any]) => "charlie")
    )
    val predicates = Map[String, List[Any] => Boolean](
      "person" -> ((args: List[Any]) => args match
        case List(name: String) => Set("alice", "bob", "charlie").contains(name)
        case _ => false
      ),
      "knows" -> ((args: List[Any]) => args match
        case List("alice", "bob") => true
        case List("bob", "charlie") => true
        case _ => false
      )
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
  
  // ==================== holdsWithRelationValue Extension ====================
  
  test("holdsWithRelationValue: Const value") {
    val ctx = EvaluationContext.empty(stringModel)
    val formula = Formula.Atom(FOL("person", List(Term.Var("x"))))
    
    assert(ctx.holdsWithRelationValue(formula, "x", RelationValue.Const("alice")))
    assert(ctx.holdsWithRelationValue(formula, "x", RelationValue.Const("bob")))
    assert(!ctx.holdsWithRelationValue(formula, "x", RelationValue.Const("unknown")))
  }
  
  test("holdsWithRelationValue: Num value") {
    // Need Model[Any] for extension method to work
    val anyModel = Model[Any](intModel.interpretation.asInstanceOf[Interpretation[Any]])
    val ctx = EvaluationContext.empty(anyModel)
    val formula = Formula.Atom(FOL("=", List(Term.Var("x"), Term.Const("5"))))
    
    assert(ctx.holdsWithRelationValue(formula, "x", RelationValue.Num(5)))
    assert(!ctx.holdsWithRelationValue(formula, "x", RelationValue.Num(3)))
  }
  
  test("holdsWithRelationValue: binary relation") {
    val ctx = EvaluationContext(stringModel, Map("y" -> "bob"))
    val formula = Formula.Atom(FOL("knows", List(Term.Var("x"), Term.Var("y"))))
    
    // alice knows bob
    assert(ctx.holdsWithRelationValue(formula, "x", RelationValue.Const("alice")))
    // charlie doesn't know bob
    assert(!ctx.holdsWithRelationValue(formula, "x", RelationValue.Const("charlie")))
  }
  
  test("holdsWithRelationValue: preserves existing bindings") {
    val ctx = EvaluationContext(stringModel, Map("y" -> "charlie"))
    val formula = Formula.Atom(FOL("knows", List(Term.Var("x"), Term.Var("y"))))
    
    // bob knows charlie (y already bound)
    assert(ctx.holdsWithRelationValue(formula, "x", RelationValue.Const("bob")))
  }
  
  // ==================== holdsWithRelationValues Extension ====================
  
  test("holdsWithRelationValues: multiple bindings") {
    val ctx = EvaluationContext.empty(stringModel)
    val formula = Formula.Atom(FOL("knows", List(Term.Var("x"), Term.Var("y"))))
    
    val bindings = Map(
      "x" -> RelationValue.Const("alice"),
      "y" -> RelationValue.Const("bob")
    )
    
    assert(ctx.holdsWithRelationValues(formula, bindings))
  }
  
  test("holdsWithRelationValues: empty bindings") {
    val ctx = EvaluationContext.empty(stringModel)
    val formula = Formula.Atom(FOL("person", List(Term.Const("alice"))))
    
    assert(ctx.holdsWithRelationValues(formula, Map.empty))
  }
  
  test("holdsWithRelationValues: mixed Const and Num") {
    // Need Model[Any] for extension method to work
    val anyModel = Model[Any](intModel.interpretation.asInstanceOf[Interpretation[Any]])
    val ctx = EvaluationContext.empty(anyModel)
    val formula = Formula.And(
      Formula.Atom(FOL("=", List(Term.Var("x"), Term.Const("3")))),
      Formula.Atom(FOL("=", List(Term.Var("y"), Term.Const("5"))))
    )
    
    val bindings = Map(
      "x" -> RelationValue.Num(3),
      "y" -> RelationValue.Num(5)
    )
    
    assert(ctx.holdsWithRelationValues(formula, bindings))
  }
  
  // ==================== Integration Tests ====================
  
  test("integration: ScopeEvaluator pattern") {
    // Simulate how ScopeEvaluator uses EvaluationContext
    val ctx = EvaluationContext(stringModel, Map.empty)
    val scopeFormula = Formula.Atom(FOL("person", List(Term.Var("x"))))
    
    val rangeElements = Set(
      RelationValue.Const("alice"),
      RelationValue.Const("bob"),
      RelationValue.Const("unknown")
    )
    
    val satisfying = rangeElements.filter { elem =>
      ctx.holdsWithRelationValue(scopeFormula, "x", elem)
    }
    
    assertEquals(satisfying.size, 2)  // alice and bob are persons
  }
  
  test("integration: quantifier evaluation pattern") {
    // Simulate evaluating ∀x. x > 0 over small domain
    val ctx = EvaluationContext.empty(intModel)
    val formula = Formula.Atom(FOL("<", List(Term.Const("0"), Term.Var("x"))))
    
    // Check all positive numbers
    val positiveDomain = Set(1, 2, 3, 4, 5)
    val allPositive = positiveDomain.forall { d =>
      ctx.withBinding("x", d).holds(formula)
    }
    
    assert(allPositive)
  }
  
  test("integration: context chaining for nested quantifiers") {
    // Simulate ∀x. ∃y. x < y
    val ctx = EvaluationContext.empty(intModel)
    val formula = Formula.Atom(FOL("<", List(Term.Var("x"), Term.Var("y"))))
    
    val testX = 2
    val ctxWithX = ctx.withBinding("x", testX)
    
    // For this x, find a y where x < y
    val existsY = intModel.domain.elements.exists { y =>
      ctxWithX.withBinding("y", y).holds(formula)
    }
    
    assert(existsY)  // There exist values greater than 2
  }
  
  test("integration: compare with direct FOLSemantics") {
    // Verify EvaluationContext gives same results as FOLSemantics
    val valuation = Valuation(Map("x" -> 3, "y" -> 5))
    val formula = Formula.Or(
      Formula.Atom(FOL("=", List(Term.Var("x"), Term.Var("y")))),
      Formula.Atom(FOL("<", List(Term.Var("x"), Term.Var("y"))))
    )
    
    val contextResult = EvaluationContext(intModel, valuation).holds(formula)
    val directResult = FOLSemantics.holds(formula, intModel, valuation)
    
    assertEquals(contextResult, directResult)
  }
