package vague.logic

import munit.FunSuite
import logic.{FOL, Formula, Term}

class VagueQuerySpec extends FunSuite:
  import VagueQuery.*, Quantifier.*, Formula.*, Term.*
  
  // ==================== Constructor Tests ====================
  
  test("create simple Boolean query") {
    val q = VagueQuery(
      quantifier = mkAbout(1, 2),
      variable = "x",
      range = FOL("country", List(Var("x"))),
      scope = Atom(FOL("large", List(Var("x")))),
      answerVars = Nil
    )
    assert(q.isBoolean)
    assert(!q.isUnary)
    assertEquals(q.variable, "x")
  }
  
  test("create unary query with answer variable") {
    val q = VagueQuery(
      quantifier = mkAbout(1, 2),
      variable = "x",
      range = FOL("city", List(Var("x"), Var("y"))),
      scope = Atom(FOL("large", List(Var("x")))),
      answerVars = List("y")
    )
    assert(!q.isBoolean)
    assert(q.isUnary)
    assertEquals(q.answerVars, List("y"))
  }
  
  test("create query with complex scope formula") {
    val q = VagueQuery(
      quantifier = mkAtLeast(3, 4),
      variable = "x",
      range = FOL("country", List(Var("x"))),
      scope = Exists("y", And(
        Atom(FOL("hasGDP", List(Var("x"), Var("y")))),
        Atom(FOL("<", List(Var("y"), Const("20"))))
      ))
    )
    assertEquals(q.quantifier, mkAtLeast(3, 4))
  }
  
  // ==================== Variable Extraction Tests ====================
  
  test("extract range variables from unary relation") {
    val q = VagueQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = FOL("country", List(Var("x"))),
      scope = True
    )
    assertEquals(q.rangeVars, Set("x"))
  }
  
  test("extract range variables from binary relation") {
    val q = VagueQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = FOL("city_of", List(Var("x"), Var("y"))),
      scope = True,
      answerVars = List("y")
    )
    assertEquals(q.rangeVars, Set("x", "y"))
  }
  
  test("extract range variables with function terms") {
    val q = VagueQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = FOL("relation", List(
        Fn("f", List(Var("x"))),
        Var("y")
      )),
      scope = True,
      answerVars = List("y")
    )
    assertEquals(q.rangeVars, Set("x", "y"))
  }
  
  test("extract scope variables from simple atom") {
    val q = VagueQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = FOL("country", List(Var("x"))),
      scope = Atom(FOL("large", List(Var("x"))))
    )
    assert(q.scopeVars.contains("x"))
  }
  
  test("extract scope variables from complex formula") {
    val q = example1  // Uses ∃y (hasGDP_agr(x,y) ∧ y≤20)
    assert(q.scopeVars.contains("x"))
    assert(q.scopeVars.contains("y"))
  }
  
  test("extract scope variables from nested quantifiers") {
    val q = VagueQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = FOL("country", List(Var("x"))),
      scope = Forall("y", Exists("z", 
        Atom(FOL("P", List(Var("x"), Var("y"), Var("z"))))
      ))
    )
    assertEquals(q.scopeVars, Set("x", "y", "z"))
  }
  
  test("handle constants in scope") {
    val q = VagueQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = FOL("country", List(Var("x"))),
      scope = Atom(FOL("capital", List(Const("Paris"))))
    )
    // Constants don't contribute to variables
    assert(!q.scopeVars.contains("Paris"))
  }
  
  // ==================== Validation Tests ====================
  
  test("mk validates quantified variable in range") {
    val error = intercept[IllegalArgumentException] {
      mk(
        mkAbout(1, 2),
        "x",
        FOL("country", List(Var("y"))),  // x not in range!
        True
      )
    }
    assert(error.getMessage.contains("must appear in range"))
  }
  
  test("mk validates range variables subset of answer vars") {
    val error = intercept[IllegalArgumentException] {
      mk(
        mkAbout(1, 2),
        "x",
        FOL("city_of", List(Var("x"), Var("y"))),  // y in range
        True,
        Nil  // but y not in answer vars!
      )
    }
    assert(error.getMessage.contains("must be subset of answer variables"))
  }
  
  test("mk accepts valid binary range with answer var") {
    val q = mk(
      mkAbout(1, 2),
      "x",
      FOL("city_of", List(Var("x"), Var("y"))),
      Atom(FOL("large", List(Var("x")))),
      List("y")
    )
    assertEquals(q.rangeVars, Set("x", "y"))
  }
  
  test("mk accepts unary range with no answer vars") {
    val q = mk(
      mkAbout(1, 2),
      "x",
      FOL("country", List(Var("x"))),
      Atom(FOL("large", List(Var("x"))))
    )
    assert(q.isBoolean)
  }
  
  // ==================== Paper Examples Tests ====================
  
  test("example1: Boolean query structure") {
    val q1 = example1
    
    // Verify structure matches paper
    assertEquals(q1.variable, "x")
    assertEquals(q1.range.predicate, "country")
    assert(q1.isBoolean)
    
    // Verify quantifier
    assertEquals(Quantifier.targetProportion(q1.quantifier), 0.75)
    
    // Verify scope is ∃y (...)
    q1.scope match
      case Exists(y, And(_, _)) => 
        assertEquals(y, "y")
      case _ => 
        fail("Expected ∃y (... ∧ ...)")
  }
  
  test("example1: variable extraction") {
    val q1 = example1
    assertEquals(q1.rangeVars, Set("x"))
    assert(q1.scopeVars.contains("x"))
    assert(q1.scopeVars.contains("y"))
  }
  
  test("example3Skeleton: unary query structure") {
    val q3 = example3Skeleton
    
    assertEquals(q3.variable, "x")
    assert(q3.isUnary)
    assertEquals(q3.answerVars, List("y"))
    
    // Verify quantifier is "about half"
    assertEquals(Quantifier.targetProportion(q3.quantifier), 0.5)
  }
  
  test("simpleExample: basic structure") {
    val q = simpleExample
    
    assert(q.isBoolean)
    assertEquals(q.variable, "x")
    assertEquals(Quantifier.targetProportion(q.quantifier), 0.5)
    
    // Verify scope is simple atom
    q.scope match
      case Atom(fol) => 
        assertEquals(fol.predicate, "large")
      case _ => 
        fail("Expected Atom(...)")
  }
  
  // ==================== Query Properties Tests ====================
  
  test("isBoolean returns true when no answer vars") {
    val q = VagueQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = FOL("country", List(Var("x"))),
      scope = True
    )
    assert(q.isBoolean)
  }
  
  test("isBoolean returns false when answer vars present") {
    val q = VagueQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = FOL("city", List(Var("x"), Var("y"))),
      scope = True,
      answerVars = List("y")
    )
    assert(!q.isBoolean)
  }
  
  test("isUnary returns true for single answer var") {
    val q = VagueQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = FOL("city", List(Var("x"), Var("y"))),
      scope = True,
      answerVars = List("y")
    )
    assert(q.isUnary)
  }
  
  test("isUnary returns false for multiple answer vars") {
    val q = VagueQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = FOL("relation", List(Var("x"), Var("y"), Var("z"))),
      scope = True,
      answerVars = List("y", "z")
    )
    assert(!q.isUnary)
  }
  
  test("isUnary returns false for no answer vars") {
    val q = simpleExample
    assert(!q.isUnary)
  }
  
  // ==================== Complex Query Tests ====================
  
  test("query with nested logical operators") {
    val q = VagueQuery(
      quantifier = mkAtLeast(2, 3),
      variable = "x",
      range = FOL("country", List(Var("x"))),
      scope = And(
        Atom(FOL("large", List(Var("x")))),
        Or(
          Atom(FOL("wealthy", List(Var("x")))),
          Atom(FOL("educated", List(Var("x"))))
        )
      )
    )
    
    assert(q.scopeVars.contains("x"))
    assertEquals(q.rangeVars, Set("x"))
  }
  
  test("query with implication in scope") {
    val q = VagueQuery(
      quantifier = almostAll,
      variable = "x",
      range = FOL("country", List(Var("x"))),
      scope = Imp(
        Atom(FOL("large", List(Var("x")))),
        Atom(FOL("diverse", List(Var("x"))))
      )
    )
    
    assert(q.isBoolean)
    assertEquals(Quantifier.targetProportion(q.quantifier), 1.0)
  }
  
  test("query with multiple quantifiers in scope") {
    val q = VagueQuery(
      quantifier = aboutTwoThirds,
      variable = "x",
      range = FOL("country", List(Var("x"))),
      scope = Exists("y", Forall("z",
        Atom(FOL("relation", List(Var("x"), Var("y"), Var("z"))))
      ))
    )
    
    val vars = q.scopeVars
    assert(vars.contains("x"))
    assert(vars.contains("y"))
    assert(vars.contains("z"))
  }
  
  // ==================== Edge Cases ====================
  
  test("query with False scope") {
    val q = VagueQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = FOL("country", List(Var("x"))),
      scope = False
    )
    assertEquals(q.scopeVars, Set.empty)
  }
  
  test("query with True scope") {
    val q = VagueQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = FOL("country", List(Var("x"))),
      scope = True
    )
    assertEquals(q.scopeVars, Set.empty)
  }
  
  test("query with empty range (constant-only terms)") {
    val q = VagueQuery(
      quantifier = aboutHalf,
      variable = "x",
      range = FOL("relation", List(Var("x"), Const("USA"))),
      scope = True
    )
    assertEquals(q.rangeVars, Set("x"))
  }
