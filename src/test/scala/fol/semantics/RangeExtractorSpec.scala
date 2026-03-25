package fol.semantics

import munit.FunSuite
import scala.language.implicitConversions
import fol.error.{QueryError, QueryException}
import logic.{FOL, Formula, Term}
import fol.logic.{ParsedQuery, Quantifier}
import fol.datastore.{KnowledgeBase, KnowledgeSource, Relation, RelationName, RelationTuple, RelationValue}

class RangeExtractorSpec extends FunSuite:
  import RangeExtractor.*
  import RelationValue.{Const as RConst, Num}
  import Term.{Var, Const as TConst, Fn}
  
  // Helper: Convert KB to KnowledgeSource for testing
  given Conversion[KnowledgeBase[RelationValue], KnowledgeSource[RelationValue]] = KnowledgeSource.fromKnowledgeBase
  
  // Helper: Extract Right or fail the test
  def ok[A](result: Either[QueryError, A]): A =
    result.fold(e => fail(s"Expected Right, got Left(${e.message})"), identity)
  
  // ==================== Test Data Setup ====================
  
  // Simple geography KB
  def geographyKB: KnowledgeBase[RelationValue] =
    val kb = KnowledgeBase[RelationValue](Map.empty, Map.empty)
      .addRelation(Relation(RelationName("country"), 1))
      .addRelation(Relation(RelationName("city"), 1))
      .addRelation(Relation(RelationName("capital"), 2))
      .addRelation(Relation(RelationName("large_country"), 1))
    
    kb.addFacts(RelationName("country"), Set(
      RelationTuple(List(RConst("France"))),
      RelationTuple(List(RConst("Germany"))),
      RelationTuple(List(RConst("Italy"))),
      RelationTuple(List(RConst("Spain")))
    ))
    .addFacts(RelationName("city"), Set(
      RelationTuple(List(RConst("Paris"))),
      RelationTuple(List(RConst("Berlin"))),
      RelationTuple(List(RConst("Rome"))),
      RelationTuple(List(RConst("Madrid")))
    ))
    .addFacts(RelationName("capital"), Set(
      RelationTuple(List(RConst("Paris"), RConst("France"))),
      RelationTuple(List(RConst("Berlin"), RConst("Germany"))),
      RelationTuple(List(RConst("Rome"), RConst("Italy"))),
      RelationTuple(List(RConst("Madrid"), RConst("Spain")))
    ))
    .addFacts(RelationName("large_country"), Set(
      RelationTuple(List(RConst("France"))),
      RelationTuple(List(RConst("Germany")))
    ))
  
  // KB with numeric values
  def numericKB: KnowledgeBase[RelationValue] =
    val kb = KnowledgeBase[RelationValue](Map.empty, Map.empty)
      .addRelation(Relation(RelationName("component"), 1))
      .addRelation(Relation(RelationName("has_severity"), 2))
    
    kb.addFacts(RelationName("component"), Set(
      RelationTuple(List(RConst("C1"))),
      RelationTuple(List(RConst("C2"))),
      RelationTuple(List(RConst("C3")))
    ))
    .addFacts(RelationName("has_severity"), Set(
      RelationTuple(List(RConst("C1"), Num(8))),
      RelationTuple(List(RConst("C2"), Num(3))),
      RelationTuple(List(RConst("C3"), Num(9)))
    ))
  
  // ==================== Unary Range Tests ====================
  
  test("extract range from unary relation (Boolean query)") {
    val kb = geographyKB
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("country", List(Var("x"))),
      Formula.True
    )
    
    val range = ok(extractRangeBoolean(kb, query))
    assertEquals(range, Set(
      RConst("France"), RConst("Germany"), RConst("Italy"), RConst("Spain")
    ))
  }
  
  test("extract range from subset relation") {
    val kb = geographyKB
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("large_country", List(Var("x"))),
      Formula.True
    )
    
    val range = ok(extractRangeBoolean(kb, query))
    assertEquals(range, Set(RConst("France"), RConst("Germany")))
  }
  
  test("extract range from empty relation") {
    val kb = KnowledgeBase[RelationValue](Map.empty, Map.empty)
      .addRelation(Relation(RelationName("empty_rel"), 1))
    
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("empty_rel", List(Var("x"))),
      Formula.True
    )
    
    val range = ok(extractRangeBoolean(kb, query))
    assertEquals(range, Set.empty[RelationValue])
  }
  
  // ==================== Binary Range Tests ====================
  
  test("extract range from binary relation - first position") {
    val kb = geographyKB
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("capital", List(Var("x"), Var("y"))),
      Formula.True,
      answerVars = List("y")
    )
    
    // Extract range for y = "France"
    val range = ok(extractRangeUnary(kb, query, RConst("France")))
    assertEquals(range, Set(RConst("Paris")))
  }
  
  test("extract range from binary relation - second position") {
    val kb = geographyKB
    // Query: Q x (capital(y, x), ...)(y)
    // This means: find countries y such that Q x holds over capitals of y
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("capital", List(Var("y"), Var("x"))),
      Formula.True,
      answerVars = List("y")
    )
    
    // Extract range for y = "Paris" - should give countries
    val range = ok(extractRangeUnary(kb, query, RConst("Paris")))
    assertEquals(range, Set(RConst("France")))
  }
  
  test("extract range with no matching substitution") {
    val kb = geographyKB
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("capital", List(Var("x"), Var("y"))),
      Formula.True,
      answerVars = List("y")
    )
    
    // Extract range for y = "Unknown" - should be empty
    val range = ok(extractRangeUnary(kb, query, RConst("Unknown")))
    assertEquals(range, Set.empty[RelationValue])
  }
  
  // ==================== Substitution Tests ====================
  
  test("extract range with explicit substitution map") {
    val kb = geographyKB
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("capital", List(Var("x"), Var("y"))),
      Formula.True,
      answerVars = List("y")
    )
    
    val subst = Map("y" -> RConst("Germany"))
    val range = ok(extractRange(kb, query, subst))
    assertEquals(range, Set(RConst("Berlin")))
  }
  
  test("extract range with multiple substitution values") {
    val kb = geographyKB
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("capital", List(Var("x"), Var("y"))),
      Formula.True,
      answerVars = List("y")
    )
    
    val countries = Set(RConst("France"), RConst("Italy"))
    val ranges = countries.map { country =>
      country -> ok(extractRange(kb, query, Map("y" -> country)))
    }.toMap
    
    assertEquals(ranges(RConst("France")), Set[RelationValue](RConst("Paris")))
    assertEquals(ranges(RConst("Italy")), Set[RelationValue](RConst("Rome")))
  }
  
  test("extract range with no substitution needed (Boolean)") {
    val kb = geographyKB
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("country", List(Var("x"))),
      Formula.True
    )
    
    val range = ok(extractRange(kb, query, Map.empty))
    assertEquals(range.size, 4)  // All countries
  }
  
  // ==================== Numeric Values Tests ====================
  
  test("extract range from relation with numeric values") {
    val kb = numericKB
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("component", List(Var("x"))),
      Formula.True
    )
    
    val range = ok(extractRangeBoolean(kb, query))
    assertEquals(range, Set(RConst("C1"), RConst("C2"), RConst("C3")))
  }
  
  test("extract range with numeric substitution") {
    val kb = numericKB
    // Query: Q x (has_severity(x, s), ...)(s)
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("has_severity", List(Var("x"), Var("s"))),
      Formula.True,
      answerVars = List("s")
    )
    
    val range = ok(extractRangeUnary(kb, query, Num(8)))
    assertEquals(range, Set(RConst("C1")))
  }
  
  // ==================== extractAllRanges Tests ====================
  
  test("extractAllRanges for Boolean query") {
    val kb = geographyKB
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("country", List(Var("x"))),
      Formula.True
    )
    
    val allRanges = ok(extractAllRanges(kb, query))
    assertEquals(allRanges.size, 1)
    assert(allRanges.contains(Map.empty))
    assertEquals(allRanges(Map.empty).size, 4)
  }
  
  test("extractAllRanges for unary query - size check") {
    val kb = geographyKB
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("capital", List(Var("x"), Var("y"))),
      Formula.True,
      answerVars = List("y")
    )
    
    val allRanges = ok(extractAllRanges(kb, query))
    
    // Should have one entry per active domain element
    // Active domain: 4 countries + 4 cities = 8 elements
    assertEquals(allRanges.size, 8)
  }
  
  test("extractAllRanges for unary query - verify mappings") {
    val kb = geographyKB
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("capital", List(Var("x"), Var("y"))),
      Formula.True,
      answerVars = List("y")
    )
    
    val allRanges = ok(extractAllRanges(kb, query))
    
    // Verify specific substitutions
    val franceSubst = Map("y" -> RConst("France"))
    assert(allRanges.contains(franceSubst))
    assertEquals(allRanges(franceSubst), Set(RConst("Paris")))
    
    val germanySubst = Map("y" -> RConst("Germany"))
    assert(allRanges.contains(germanySubst))
    assertEquals(allRanges(germanySubst), Set(RConst("Berlin")))
  }
  
  // ==================== Edge Cases ====================
  
  test("extract range with constant in range predicate") {
    val kb = geographyKB
    // Range: capital(x, "France") - cities that are capital of France
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("capital", List(Var("x"), TConst("France"))),
      Formula.True
    )
    
    val range = ok(extractRangeBoolean(kb, query))
    assertEquals(range, Set(RConst("Paris")))
  }
  
  test("extractRangeBoolean requires Boolean query") {
    val kb = geographyKB
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("capital", List(Var("x"), Var("y"))),
      Formula.True,
      answerVars = List("y")
    )
    
    assert(extractRangeBoolean(kb, query).isLeft)
  }
  
  test("extractRangeUnary requires unary query") {
    val kb = geographyKB
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("country", List(Var("x"))),
      Formula.True
    )
    
    assert(extractRangeUnary(kb, query, RConst("test")).isLeft)
  }
  
  test("extract range fails if quantified var not in range") {
    val kb = geographyKB
    // Invalid: quantified variable z not in range
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "z",
      FOL("country", List(Var("x"))),
      Formula.True
    )
    
    assert(extractRangeBoolean(kb, query).isLeft)
  }
  
  test("extract range from relation not in KB") {
    val kb = geographyKB
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("nonexistent", List(Var("x"))),
      Formula.True
    )
    
    // Should return empty set (KB.query returns empty for unknown relations)
    val range = ok(extractRangeBoolean(kb, query))
    assertEquals(range, Set.empty[RelationValue])
  }
  
  // ==================== Paper Examples ====================
  
  test("paper example: country range") {
    // From paper: Q[≥]^{3/4} x (country(x), ...)
    val kb = geographyKB
    val query = ParsedQuery(
      Quantifier.mkAtLeast(3, 4),
      "x",
      FOL("country", List(Var("x"))),
      Formula.Exists("y", Formula.And(
        Formula.Atom(FOL("hasGDP_agr", List(Var("x"), Var("y")))),
        Formula.Atom(FOL("<=", List(Var("y"), TConst("20"))))
      ))
    )
    
    // D_R should be all countries
    val range = ok(extractRangeBoolean(kb, query))
    assertEquals(range, Set(
      RConst("France"), RConst("Germany"), RConst("Italy"), RConst("Spain")
    ))
  }
  
  test("paper example: capital range with answer variable") {
    // From paper: Q[~#]^{1/2} x (capital(x), ...)(y)
    val kb = geographyKB
    
    // For simplicity, use capital(x, y) to encode "x is capital of country y"
    val query = ParsedQuery(
      Quantifier.aboutHalf,
      "x",
      FOL("capital", List(Var("x"), Var("y"))),
      Formula.True,  // Simplified scope
      answerVars = List("y")
    )
    
    // For each country y, D_R should be its capital
    val allRanges = ok(extractAllRanges(kb, query))
    
    // Verify key substitutions exist
    val franceRange = allRanges.get(Map("y" -> RConst("France")))
    assert(franceRange.isDefined)
    assertEquals(franceRange.get, Set(RConst("Paris")))
  }
