package fol.semantics

import munit.FunSuite
import logic.{FOL, Formula, Term}
import semantics.{Model, Valuation}
import fol.datastore.{KnowledgeBase, Relation, RelationTuple, RelationValue}
import fol.bridge.KnowledgeBaseModel

class ScopeEvaluatorSpec extends FunSuite:
  import ScopeEvaluator.*
  import RelationValue.{Const as RConst, Num}
  import Formula.*, Term.{Var, Const as TConst}
  
  // ==================== Test Data Setup ====================
  
  // Geography KB with countries and properties
  def geographyKB: KnowledgeBase[RelationValue] =
    val kb = KnowledgeBase[RelationValue](Map.empty, Map.empty)
      .addRelation(Relation("country", 1))
      .addRelation(Relation("city", 1))
      .addRelation(Relation("capital", 2))
      .addRelation(Relation("large", 1))
      .addRelation(Relation("has_pop", 2))
    
    kb.addFacts("country", Set(
      RelationTuple(List(RConst("France"))),
      RelationTuple(List(RConst("Germany"))),
      RelationTuple(List(RConst("Italy"))),
      RelationTuple(List(RConst("Spain"))),
      RelationTuple(List(RConst("Luxembourg")))
    ))
    .addFacts("city", Set(
      RelationTuple(List(RConst("Paris"))),
      RelationTuple(List(RConst("Berlin"))),
      RelationTuple(List(RConst("Rome"))),
      RelationTuple(List(RConst("Madrid")))
    ))
    .addFacts("capital", Set(
      RelationTuple(List(RConst("Paris"), RConst("France"))),
      RelationTuple(List(RConst("Berlin"), RConst("Germany"))),
      RelationTuple(List(RConst("Rome"), RConst("Italy"))),
      RelationTuple(List(RConst("Madrid"), RConst("Spain")))
    ))
    .addFacts("large", Set(
      RelationTuple(List(RConst("France"))),
      RelationTuple(List(RConst("Germany"))),
      RelationTuple(List(RConst("Italy")))
    ))
    .addFacts("has_pop", Set(
      RelationTuple(List(RConst("France"), Num(67))),
      RelationTuple(List(RConst("Germany"), Num(83))),
      RelationTuple(List(RConst("Italy"), Num(60))),
      RelationTuple(List(RConst("Spain"), Num(47))),
      RelationTuple(List(RConst("Luxembourg"), Num(1)))
    ))
  
  // Component/risk KB for numeric tests
  def componentKB: KnowledgeBase[RelationValue] =
    val kb = KnowledgeBase[RelationValue](Map.empty, Map.empty)
      .addRelation(Relation("component", 1))
      .addRelation(Relation("critical", 1))
      .addRelation(Relation("has_severity", 2))
    
    kb.addFacts("component", Set(
      RelationTuple(List(RConst("C1"))),
      RelationTuple(List(RConst("C2"))),
      RelationTuple(List(RConst("C3"))),
      RelationTuple(List(RConst("C4")))
    ))
    .addFacts("critical", Set(
      RelationTuple(List(RConst("C1"))),
      RelationTuple(List(RConst("C2")))
    ))
    .addFacts("has_severity", Set(
      RelationTuple(List(RConst("C1"), Num(8))),
      RelationTuple(List(RConst("C2"), Num(9))),
      RelationTuple(List(RConst("C3"), Num(3))),
      RelationTuple(List(RConst("C4"), Num(7)))
    ))
  
  // ==================== Single Element Evaluation Tests ====================
  
  test("evaluate simple unary predicate: country(x)") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Atom(FOL("country", List(Var("x"))))
    val element = RConst("France")
    
    val result = evaluateForElement(formula, element, "x", model)
    assert(result, "France should be a country")
  }
  
  test("evaluate simple unary predicate: large(x)") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Atom(FOL("large", List(Var("x"))))
    
    // Large countries
    assert(evaluateForElement(formula, RConst("France"), "x", model))
    assert(evaluateForElement(formula, RConst("Germany"), "x", model))
    
    // Not large
    assert(!evaluateForElement(formula, RConst("Luxembourg"), "x", model))
  }
  
  test("evaluate binary predicate: capital(x, y)") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Atom(FOL("capital", List(Var("x"), Var("y"))))
    
    // With substitution y -> France
    val substitution = Map("y" -> RConst("France"))
    val result = evaluateForElement(formula, RConst("Paris"), "x", model, substitution)
    assert(result, "Paris is capital of France")
    
    // Wrong capital
    val result2 = evaluateForElement(formula, RConst("Berlin"), "x", model, substitution)
    assert(!result2, "Berlin is not capital of France")
  }
  
  test("evaluate with numeric relation") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    // Check if country has population data
    val formula = Exists("p", 
      Atom(FOL("has_pop", List(Var("x"), Var("p"))))
    )
    
    // All countries have population
    assert(evaluateForElement(formula, RConst("France"), "x", model))
    assert(evaluateForElement(formula, RConst("Germany"), "x", model))
    assert(evaluateForElement(formula, RConst("Italy"), "x", model))
    assert(evaluateForElement(formula, RConst("Luxembourg"), "x", model))
  }
  
  test("evaluate conjunction: large(x) ∧ country(x)") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = And(
      Atom(FOL("large", List(Var("x")))),
      Atom(FOL("country", List(Var("x"))))
    )
    
    // Large countries
    assert(evaluateForElement(formula, RConst("France"), "x", model))
    assert(evaluateForElement(formula, RConst("Germany"), "x", model))
    
    // Small country
    assert(!evaluateForElement(formula, RConst("Luxembourg"), "x", model))
  }
  
  test("evaluate disjunction: large(x) ∨ has_pop(x, 1)") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Or(
      Atom(FOL("large", List(Var("x")))),
      Atom(FOL("has_pop", List(Var("x"), TConst("1"))))
    )
    
    // Large countries satisfy via first disjunct
    assert(evaluateForElement(formula, RConst("France"), "x", model))
    
    // Luxembourg satisfies via second disjunct
    assert(evaluateForElement(formula, RConst("Luxembourg"), "x", model))
  }
  
  test("evaluate negation: ¬large(x)") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Not(Atom(FOL("large", List(Var("x")))))
    
    // Not large
    assert(evaluateForElement(formula, RConst("Luxembourg"), "x", model))
    assert(evaluateForElement(formula, RConst("Spain"), "x", model))
    
    // Large
    assert(!evaluateForElement(formula, RConst("France"), "x", model))
  }
  
  test("evaluate existential: ∃y capital(x, y)") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Exists("y", 
      Atom(FOL("capital", List(Var("x"), Var("y"))))
    )
    
    // Cities that are capitals
    assert(evaluateForElement(formula, RConst("Paris"), "x", model))
    assert(evaluateForElement(formula, RConst("Berlin"), "x", model))
  }
  
  test("evaluate complex formula from paper: ∃y (capital(x,y) ∧ large(y))") {
    val kb = geographyKB
    // Add relation: large countries
    val kb2 = kb.addRelation(Relation("country_large", 1))
      .addFacts("country_large", Set(
        RelationTuple(List(RConst("France"))),
        RelationTuple(List(RConst("Germany")))
      ))
    
    val model = KnowledgeBaseModel.toModel(kb2)
    
    // Paper-style: ∃y (R(x,y) ∧ φ(y))
    // "Capitals of large countries"
    val formula = Exists("y", And(
      Atom(FOL("capital", List(Var("x"), Var("y")))),
      Atom(FOL("country_large", List(Var("y"))))
    ))
    
    // Capitals of large countries
    assert(evaluateForElement(formula, RConst("Paris"), "x", model))  // France is large
    assert(evaluateForElement(formula, RConst("Berlin"), "x", model)) // Germany is large
    
    // Capitals of non-large countries
    assert(!evaluateForElement(formula, RConst("Madrid"), "x", model)) // Spain not large
  }
  
  test("evaluate True and False") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    assert(evaluateForElement(True, RConst("France"), "x", model))
    assert(!evaluateForElement(False, RConst("France"), "x", model))
  }
  
  // ==================== Proportion Calculation Tests ====================
  
  test("calculate proportion: all satisfy") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Atom(FOL("country", List(Var("x"))))
    val sample = Set(
      RConst("France"),
      RConst("Germany"),
      RConst("Italy")
    )
    
    val prop = calculateProportion(sample, formula, "x", model)
    assertEquals(prop, 1.0, 0.001)
  }
  
  test("calculate proportion: none satisfy") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Atom(FOL("city", List(Var("x"))))
    val sample = Set(
      RConst("France"),
      RConst("Germany"),
      RConst("Italy")
    )
    
    val prop = calculateProportion(sample, formula, "x", model)
    assertEquals(prop, 0.0, 0.001)
  }
  
  test("calculate proportion: half satisfy (large countries)") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Atom(FOL("large", List(Var("x"))))
    val sample = Set(
      RConst("France"),     // large
      RConst("Germany"),    // large
      RConst("Spain"),      // not large
      RConst("Luxembourg")  // not large
    )
    
    val prop = calculateProportion(sample, formula, "x", model)
    assertEquals(prop, 0.5, 0.001)
  }
  
  test("calculate proportion: 3/4 satisfy") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Atom(FOL("large", List(Var("x"))))
    val sample = Set(
      RConst("France"),     // large
      RConst("Germany"),    // large
      RConst("Italy"),      // large
      RConst("Luxembourg")  // not large
    )
    
    val prop = calculateProportion(sample, formula, "x", model)
    assertEquals(prop, 0.75, 0.001)
  }
  
  test("calculate proportion: empty sample") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Atom(FOL("country", List(Var("x"))))
    val sample = Set.empty[RelationValue]
    
    val prop = calculateProportion(sample, formula, "x", model)
    assertEquals(prop, 0.0)
  }
  
  test("calculate proportion: complex formula") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    // large(x) ∨ has_pop(x, 1)
    val formula = Or(
      Atom(FOL("large", List(Var("x")))),
      Atom(FOL("has_pop", List(Var("x"), TConst("1"))))
    )
    
    val sample = Set(
      RConst("France"),     // large: true
      RConst("Luxembourg")  // has_pop(x,1): true
    )
    
    val prop = calculateProportion(sample, formula, "x", model)
    assertEquals(prop, 1.0)
  }
  
  // ==================== Batch Evaluation Tests ====================
  
  test("batch evaluation: partition sample") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Atom(FOL("large", List(Var("x"))))
    val sample = Set(
      RConst("France"),
      RConst("Germany"),
      RConst("Spain"),
      RConst("Luxembourg")
    )
    
    val (satisfying, nonSatisfying) = evaluateSample(sample, formula, "x", model)
    
    assertEquals(satisfying, Set(RConst("France"), RConst("Germany")))
    assertEquals(nonSatisfying, Set(RConst("Spain"), RConst("Luxembourg")))
  }
  
  test("batch evaluation: all satisfy") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Atom(FOL("country", List(Var("x"))))
    val sample = Set(RConst("France"), RConst("Germany"))
    
    val (satisfying, nonSatisfying) = evaluateSample(sample, formula, "x", model)
    
    assertEquals(satisfying.size, 2)
    assertEquals(nonSatisfying.size, 0)
  }
  
  test("batch evaluation: none satisfy") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Atom(FOL("city", List(Var("x"))))
    val sample = Set(RConst("France"), RConst("Germany"))
    
    val (satisfying, nonSatisfying) = evaluateSample(sample, formula, "x", model)
    
    assertEquals(satisfying.size, 0)
    assertEquals(nonSatisfying.size, 2)
  }
  
  // ==================== Element Evaluation Map Tests ====================
  
  test("evaluate elements: return satisfaction map") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Atom(FOL("large", List(Var("x"))))
    val elements = Set(
      RConst("France"),
      RConst("Luxembourg")
    )
    
    val results = evaluateElements(elements, formula, "x", model)
    
    assertEquals(results(RConst("France")), true)
    assertEquals(results(RConst("Luxembourg")), false)
  }
  
  test("evaluate elements: all combinations") {
    val kb = componentKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Atom(FOL("critical", List(Var("x"))))
    val elements = Set(
      RConst("C1"),
      RConst("C2"),
      RConst("C3"),
      RConst("C4")
    )
    
    val results = evaluateElements(elements, formula, "x", model)
    
    assertEquals(results(RConst("C1")), true)   // critical
    assertEquals(results(RConst("C2")), true)   // critical
    assertEquals(results(RConst("C3")), false)  // not critical
    assertEquals(results(RConst("C4")), false)  // not critical
  }
  
  // ==================== Count Satisfying Tests ====================
  
  test("count satisfying: all elements") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Atom(FOL("country", List(Var("x"))))
    val sample = Set(RConst("France"), RConst("Germany"), RConst("Italy"))
    
    val count = countSatisfying(sample, formula, "x", model)
    assertEquals(count, 3)
  }
  
  test("count satisfying: partial") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Atom(FOL("large", List(Var("x"))))
    val sample = Set(
      RConst("France"),     // large
      RConst("Germany"),    // large
      RConst("Luxembourg")  // not large
    )
    
    val count = countSatisfying(sample, formula, "x", model)
    assertEquals(count, 2)
  }
  
  test("count satisfying: none") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    val formula = Atom(FOL("city", List(Var("x"))))
    val sample = Set(RConst("France"), RConst("Germany"))
    
    val count = countSatisfying(sample, formula, "x", model)
    assertEquals(count, 0)
  }
  
  // ==================== Integration Tests ====================
  
  test("paper example q₁: proportion of large countries") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    // Simplified version: check if country is large
    // (In real implementation would be: ∃y (hasGDP_agr(x,y) ∧ y≤20))
    val formula = Atom(FOL("large", List(Var("x"))))
    
    val sample = Set(
      RConst("France"),
      RConst("Germany"),
      RConst("Italy"),
      RConst("Luxembourg")
    )
    
    val prop = calculateProportion(sample, formula, "x", model)
    assertEquals(prop, 0.75, 0.001)  // 3 out of 4 are large
  }
  
  test("with answer variable substitution") {
    val kb = geographyKB
    val model = KnowledgeBaseModel.toModel(kb)
    
    // capital(x, y) where y is bound
    val formula = Atom(FOL("capital", List(Var("x"), Var("y"))))
    
    // For France
    val substitution = Map("y" -> RConst("France"))
    val sample = Set(RConst("Paris"), RConst("Berlin"), RConst("Rome"))
    
    val prop = calculateProportion(sample, formula, "x", model, substitution)
    assertEquals(prop, 1.0/3.0, 0.001)  // Only Paris
  }
