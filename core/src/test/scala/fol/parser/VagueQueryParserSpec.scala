package fol.parser

import fol.logic.{ParsedQuery, Quantifier}
import logic.{FOL, Formula, Term}

class VagueQueryParserSpec extends munit.FunSuite:
  import VagueQueryParser.*
  
  // Helper: extract Right or fail test
  def parseOk(input: String): ParsedQuery =
    parse(input) match
      case Right(q) => q
      case Left(e)  => fail(s"Parse failed: ${e.formatted}")
  
  // Helper to check query structure
  def assertQueryStructure(
    query: ParsedQuery,
    expectedVar: String,
    expectedRangePred: String,
    expectedAnswerVars: List[String]
  ): Unit =
    assertEquals(query.variable, expectedVar)
    assertEquals(query.range.predicate, expectedRangePred)
    assertEquals(query.answerVars, expectedAnswerVars)
  
  // ==================== Basic Parsing Tests ====================
  
  test("parse simple Boolean query with About quantifier") {
    val q = parseOk("Q[~]^{1/2} x (country(x), large(x))")
    
    assertQueryStructure(q, "x", "country", Nil)
    q.quantifier match
      case Quantifier.About(k, n, tol) =>
        assertEquals(k, 1)
        assertEquals(n, 2)
        assertEquals(tol, 0.1)  // Default tolerance
      case _ => fail("Expected About quantifier")
    
    q.scope match
      case Formula.Atom(fol) => assertEquals(fol.predicate, "large")
      case _ => fail("Expected simple atom scope")
  }
  
  test("parse query with AtLeast quantifier") {
    val q = parseOk("Q[>=]^{3/4} x (country(x), large(x))")
    
    q.quantifier match
      case Quantifier.AtLeast(k, n, _) =>
        assertEquals(k, 3)
        assertEquals(n, 4)
      case _ => fail("Expected AtLeast quantifier")
  }
  
  test("parse query with AtMost quantifier") {
    val q = parseOk("Q[<=]^{1/3} x (city(x), populous(x))")
    
    q.quantifier match
      case Quantifier.AtMost(k, n, _) =>
        assertEquals(k, 1)
        assertEquals(n, 3)
      case _ => fail("Expected AtMost quantifier")
  }
  
  test("parse query with custom tolerance") {
    val q = parseOk("Q[~]^{1/2}[0.05] x (country(x), large(x))")
    
    q.quantifier match
      case Quantifier.About(k, n, tol) =>
        assertEquals(tol, 0.05)
      case _ => fail("Expected About quantifier")
  }
  
  test("parse query with single answer variable") {
    val q = parseOk("Q[~]^{1/2} x (capital(x, y), large(x))(y)")
    
    assertQueryStructure(q, "x", "capital", List("y"))
    
    // Verify range has two terms
    assertEquals(q.range.terms.length, 2)
  }
  
  test("parse query with multiple answer variables") {
    val q = parseOk("Q[>=]^{2/3} x (borders(x, y, z), conflict(x))(y, z)")
    
    assertQueryStructure(q, "x", "borders", List("y", "z"))
    assertEquals(q.range.terms.length, 3)
  }
  
  // ==================== Complex Formula Parsing ====================
  
  test("parse query with conjunction in scope") {
    val q = parseOk("Q[~]^{1/2} x (country(x), large(x) /\\ coastal(x))")
    
    q.scope match
      case Formula.And(_, _) => // Success
      case _ => fail("Expected conjunction in scope")
  }
  
  test("parse query with disjunction in scope") {
    val q = parseOk("Q[>=]^{3/4} x (country(x), large(x) \\/ populous(x))")
    
    q.scope match
      case Formula.Or(_, _) => // Success
      case _ => fail("Expected disjunction in scope")
  }
  
  test("parse query with negation in scope") {
    val q = parseOk("Q[<=]^{1/4} x (country(x), ~large(x))")
    
    q.scope match
      case Formula.Not(_) => // Success
      case _ => fail("Expected negation in scope")
  }
  
  test("parse query with existential quantifier in scope") {
    val q = parseOk("Q[>=]^{3/4} x (country(x), exists y . (hasGDP(x, y) /\\ y < 20))")
    
    q.scope match
      case Formula.Exists(y, _) =>
        assertEquals(y, "y")
      case _ => fail("Expected existential quantifier in scope")
  }
  
  test("parse query with universal quantifier in scope") {
    val q = parseOk("Q[~]^{1/2} x (country(x), forall y . (city(y) ==> inCountry(y, x)))")
    
    q.scope match
      case Formula.Forall(y, _) =>
        assertEquals(y, "y")
      case _ => fail("Expected universal quantifier in scope")
  }
  
  test("parse query with implication in scope") {
    val q = parseOk("Q[>=]^{2/3} x (country(x), large(x) ==> wealthy(x))")
    
    q.scope match
      case Formula.Imp(_, _) => // Success
      case _ => fail("Expected implication in scope")
  }
  
  test("parse query with biconditional in scope") {
    val q = parseOk("Q[~]^{1/2} x (country(x), large(x) <=> populous(x))")
    
    q.scope match
      case Formula.Iff(_, _) => // Success
      case _ => fail("Expected biconditional in scope")
  }
  
  // ==================== Paper Examples ====================
  
  test("paper example q1: Boolean query with existential") {
    val q = parseOk("""Q[>=]^{3/4} x (country(x), exists y . (hasGDP_agr(x, y) /\ y <= 20))""")
    
    assertQueryStructure(q, "x", "country", Nil)
    assert(q.isBoolean)
    
    q.quantifier match
      case Quantifier.AtLeast(k, n, _) =>
        assertEquals(k, 3)
        assertEquals(n, 4)
      case _ => fail("Expected AtLeast quantifier")
    
    q.scope match
      case Formula.Exists(y, Formula.And(_, _)) =>
        assertEquals(y, "y")
      case _ => fail("Expected exists y (... /\\ ...)")
  }
  
  test("paper example q3 skeleton: unary query with answer variable") {
    val q = parseOk("Q[~]^{1/2} x (capital(x, y), large(x))(y)")
    
    assertQueryStructure(q, "x", "capital", List("y"))
    assert(q.isUnary)
    
    q.quantifier match
      case Quantifier.About(k, n, _) =>
        assertEquals(k, 1)
        assertEquals(n, 2)
      case _ => fail("Expected About quantifier")
  }
  
  // ==================== Alternative Operator Syntax ====================
  
  test("parse with ~# operator (alternative About syntax)") {
    val q = parseOk("Q[~#]^{1/2} x (country(x), large(x))")
    
    q.quantifier match
      case Quantifier.About(_, _, _) => // Success
      case _ => fail("Expected About quantifier")
  }
  
  test("parse with Unicode >= operator") {
    val q = parseOk("Q[≥]^{3/4} x (country(x), large(x))")
    
    q.quantifier match
      case Quantifier.AtLeast(_, _, _) => // Success
      case _ => fail("Expected AtLeast quantifier")
  }
  
  test("parse with Unicode <= operator") {
    val q = parseOk("Q[≤]^{1/4} x (country(x), large(x))")
    
    q.quantifier match
      case Quantifier.AtMost(_, _, _) => // Success
      case _ => fail("Expected AtMost quantifier")
  }
  
  // ==================== Edge Cases and Validation ====================
  
  test("parse query with True scope") {
    val q = parseOk("Q[~]^{1/2} x (country(x), true)")
    
    q.scope match
      case Formula.True => // Success
      case _ => fail("Expected True formula")
  }
  
  test("parse query with False scope") {
    val q = parseOk("Q[~]^{1/2} x (country(x), false)")
    
    q.scope match
      case Formula.False => // Success
      case _ => fail("Expected False formula")
  }
  
  test("parse rejects invalid quantifier operator") {
    assert(parse("Q[*]^{1/2} x (country(x), large(x))").isLeft)
  }
  
  test("parse rejects missing quantifier") {
    assert(parse("x (country(x), large(x))").isLeft)
  }
  
  test("parse rejects missing variable") {
    assert(parse("Q[~]^{1/2} (country(x), large(x))").isLeft)
  }
  
  test("parse rejects missing range") {
    assert(parse("Q[~]^{1/2} x (, large(x))").isLeft)
  }
  
  test("parse rejects missing scope") {
    assert(parse("Q[~]^{1/2} x (country(x), )").isLeft)
  }
  
  test("parse rejects unmatched parentheses") {
    assert(parse("Q[~]^{1/2} x (country(x), large(x)").isLeft)
  }
  
  test("parse rejects extra tokens after query") {
    assert(parse("Q[~]^{1/2} x (country(x), large(x)) extra").isLeft)
  }
  
  // ==================== Query Validation ====================
  
  test("parser validates quantified variable in range") {
    // This should fail validation in ParsedQuery.mk
    assert(parse("Q[~]^{1/2} x (country(y), large(x))").isLeft)
  }
  
  test("parser validates range variables subset of answer vars") {
    // y appears in range but not in answer variables
    assert(parse("Q[~]^{1/2} x (capital(x, y), large(x))").isLeft)
  }
  
  test("parser accepts when range vars are answer vars") {
    // y appears in range and in answer variables - should succeed
    val q = parseOk("Q[~]^{1/2} x (capital(x, y), large(x))(y)")
    assertQueryStructure(q, "x", "capital", List("y"))
  }
  
  // ==================== Complex Nested Formulas ====================
  
  test("parse deeply nested formula") {
    val q = parseOk("""Q[>=]^{2/3} x (country(x), 
                     (large(x) /\ coastal(x)) \/ 
                     (populous(x) /\ wealthy(x)))""")
    
    q.scope match
      case Formula.Or(Formula.And(_, _), Formula.And(_, _)) => // Success
      case _ => fail("Expected complex nested formula")
  }
  
  test("parse query with multiple quantifiers in scope") {
    val q = parseOk("""Q[~]^{1/2} x (country(x), 
                     forall y . (city(y) ==> exists z . (serves(z, y) /\ inCountry(z, x))))""")
    
    q.scope match
      case Formula.Forall(_, Formula.Imp(_, Formula.Exists(_, _))) => // Success
      case _ => fail("Expected nested quantifiers")
  }
  
  // ==================== Whitespace Handling ====================
  
  test("parse handles extra whitespace") {
    val q = parseOk("""Q[~]^{1/2}  x  ( country(x) ,  large(x) )""")
    assertQueryStructure(q, "x", "country", Nil)
  }
  
  test("parse handles no whitespace around operators") {
    val q = parseOk("Q[~]^{1/2} x(country(x),large(x))")
    assertQueryStructure(q, "x", "country", Nil)
  }
  
  test("parse handles multiline input") {
    val q = parseOk("""Q[>=]^{3/4} x 
                    (country(x), 
                     exists y . (hasGDP_agr(x, y) /\ y <= 20))""")
    assertQueryStructure(q, "x", "country", Nil)
  }
  
  // ==================== Either API Tests ====================
  
  test("parse succeeds on valid query") {
    val result = parse("Q[~]^{1/2} x (country(x), large(x))")
    
    result match
      case Right(query) =>
        assertQueryStructure(query, "x", "country", Nil)
        query.quantifier match
          case Quantifier.About(1, 2, 0.1) => // Success
          case _ => fail("Expected About(1, 2, 0.1)")
      case Left(error) =>
        fail(s"Expected success, got error: ${error.formatted}")
  }
  
  test("parse returns error on invalid quantifier operator") {
    val result = parse("Q[==]^{1/2} x (country(x), large(x))")
    
    result match
      case Left(error) =>
        assert(error.message.contains("Invalid quantifier operator"))
        assert(error.message.contains("=="))
      case Right(_) =>
        fail("Expected parse error")
  }
  
  test("parse returns error on unexpected tokens") {
    val result = parse("Q[~]^{1/2} x (country(x), large(x)) extra tokens")
    
    result match
      case Left(error) =>
        assert(error.message.contains("Unexpected tokens"))
        assert(error.message.contains("extra"))
      case Right(_) =>
        fail("Expected parse error")
  }

