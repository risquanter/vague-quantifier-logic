package fol.semantics

import logic.{FOL, Formula, Term}
import fol.datastore.{KnowledgeBase, KnowledgeSource, Relation, RelationName, RelationValue, RelationTuple}
import fol.logic.{Quantifier, ParsedQuery}
import fol.result.{EvaluationOutput, VagueQueryResult}
import fol.error.QueryError
import fol.sampling.SamplingParams

class VagueSemanticsSpec extends munit.FunSuite:

  // Helper to convert KnowledgeBase to KnowledgeSource for backward compatibility
  extension (kb: KnowledgeBase[RelationValue])
    def asSource: KnowledgeSource[RelationValue] = KnowledgeSource.fromKnowledgeBase(kb)

  // Helper: Extract Right or fail the test
  def ok[A](result: Either[QueryError, A]): A =
    result.fold(e => fail(s"Expected Right, got Left(${e.message})"), identity)

  // Helper to create unary tuple
  def unary(value: String): RelationTuple[RelationValue] = 
    RelationTuple(List(RelationValue.Const(value)))

  // Test knowledge base with countries
  def createCountryKB(): KnowledgeBase[RelationValue] =
    KnowledgeBase[RelationValue](Map.empty, Map.empty)
      .addRelation(Relation(RelationName("country"), 1))
      .addRelation(Relation(RelationName("large"), 1))
      .addRelation(Relation(RelationName("coastal"), 1))
      .addRelation(Relation(RelationName("wealthy"), 1))
      .addFacts(RelationName("country"), Set(
        unary("France"), unary("Germany"), unary("Italy"), unary("Spain"),
        unary("Luxembourg"), unary("Switzerland"), unary("Belgium"), unary("Austria")
      ))
      .addFacts(RelationName("large"), Set(
        unary("France"), unary("Germany"), unary("Italy"), unary("Spain")
      ))
      .addFacts(RelationName("coastal"), Set(
        unary("France"), unary("Italy"), unary("Spain")
      ))
      .addFacts(RelationName("wealthy"), Set(
        unary("Luxembourg"), unary("Switzerland")
      ))

  test("evaluate simple query: about half of countries are large (exact)"):
    val kb = createCountryKB()
    
    // Q[~#]^{1/2} x (country(x), large(x))
    val query = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.1),
      variable = "x",
      range = FOL("country", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("large", List(Term.Var("x"))))
    )
    
    val result = ok(VagueSemantics.holds(query, kb.asSource))
    
    // 4 out of 8 countries are large = 0.5
    assertEquals(result.domainSize, 8)
    assertEquals(result.sampleSize, 8) // Exact evaluation uses all
    assertEquals(result.satisfyingCount, 4)
    assertEquals(result.proportion, 0.5)
    assertEquals(result.satisfied, true) // 0.5 is within [0.4, 0.6]

  test("evaluate query: at least half are large"):
    val kb = createCountryKB()
    
    // Q[≥]^{1/2} x (country(x), large(x))
    val query = ParsedQuery(
      quantifier = Quantifier.AtLeast(1, 2, 0.1),
      variable = "x",
      range = FOL("country", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("large", List(Term.Var("x"))))
    )
    
    val result = ok(VagueSemantics.holds(query, kb.asSource))
    
    assertEquals(result.proportion, 0.5)
    assertEquals(result.satisfied, true) // 0.5 >= 0.5 - 0.1 = 0.4

  test("evaluate query: at most half are large"):
    val kb = createCountryKB()
    
    // Q[≤]^{1/2} x (country(x), large(x))
    val query = ParsedQuery(
      quantifier = Quantifier.AtMost(1, 2, 0.1),
      variable = "x",
      range = FOL("country", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("large", List(Term.Var("x"))))
    )
    
    val result = ok(VagueSemantics.holds(query, kb.asSource))
    
    assertEquals(result.proportion, 0.5)
    assertEquals(result.satisfied, true) // 0.5 <= 0.5 + 0.1 = 0.6

  test("query not satisfied: about three-quarters are large"):
    val kb = createCountryKB()
    
    // Q[~#]^{3/4} x (country(x), large(x))
    val query = ParsedQuery(
      quantifier = Quantifier.About(3, 4, 0.1),
      variable = "x",
      range = FOL("country", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("large", List(Term.Var("x"))))
    )
    
    val result = ok(VagueSemantics.holds(query, kb.asSource))
    
    // 0.5 is not within [0.65, 0.85]
    assertEquals(result.proportion, 0.5)
    assertEquals(result.satisfied, false)

  test("query satisfied: about three-quarters are coastal"):
    val kb = createCountryKB()
    
    // Q[~#]^{3/4} x (large(x), coastal(x))
    // Range: large countries (4), Scope: coastal (3 out of 4)
    val query = ParsedQuery(
      quantifier = Quantifier.About(3, 4, 0.1),
      variable = "x",
      range = FOL("large", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("coastal", List(Term.Var("x"))))
    )
    
    val result = ok(VagueSemantics.holds(query, kb.asSource))
    
    // 3 out of 4 large countries are coastal = 0.75
    assertEquals(result.domainSize, 4)
    assertEquals(result.satisfyingCount, 3)
    assertEquals(result.proportion, 0.75)
    assertEquals(result.satisfied, true) // 0.75 is within [0.65, 0.85]

  test("all elements satisfy scope formula"):
    val kb = createCountryKB()
    
    // Q[~#]^{1/1} x (country(x), country(x))
    // All countries are countries
    val query = ParsedQuery(
      quantifier = Quantifier.About(1, 1, 0.01),
      variable = "x",
      range = FOL("country", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("country", List(Term.Var("x"))))
    )
    
    val result = ok(VagueSemantics.holds(query, kb.asSource))
    
    assertEquals(result.proportion, 1.0)
    assertEquals(result.satisfyingCount, 8)
    assertEquals(result.satisfied, true)

  test("no elements satisfy scope formula"):
    val kb = createCountryKB()
    
    // Q[~#]^{0/1} x (large(x), wealthy(x))
    // No large country is wealthy (wealthy are Luxembourg, Switzerland)
    val query = ParsedQuery(
      quantifier = Quantifier.About(0, 1, 0.01),
      variable = "x",
      range = FOL("large", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("wealthy", List(Term.Var("x"))))
    )
    
    val result = ok(VagueSemantics.holds(query, kb.asSource))
    
    assertEquals(result.proportion, 0.0)
    assertEquals(result.satisfyingCount, 0)
    assertEquals(result.satisfied, true) // 0.0 is within [-0.01, 0.01]

  test("empty range returns unsatisfied with zero proportion"):
    val kb = createCountryKB()
    
    // Q[~#]^{1/2} x (nonexistent(x), large(x))
    val query = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.1),
      variable = "x",
      range = FOL("nonexistent", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("large", List(Term.Var("x"))))
    )
    
    val result = ok(VagueSemantics.holds(query, kb.asSource))
    
    assertEquals(result.domainSize, 0)
    assertEquals(result.sampleSize, 0)
    assertEquals(result.satisfyingCount, 0)
    assertEquals(result.proportion, 0.0)
    assertEquals(result.satisfied, false)

  test("conjunction in scope formula"):
    val kb = createCountryKB()
    
    // Q[~#]^{1/2} x (country(x), large(x) ∧ coastal(x))
    val query = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.2), // Wider tolerance for 0.375
      variable = "x",
      range = FOL("country", List(Term.Var("x"))),
      scope = Formula.And(
        Formula.Atom(FOL("large", List(Term.Var("x")))),
        Formula.Atom(FOL("coastal", List(Term.Var("x"))))
      )
    )
    
    val result = ok(VagueSemantics.holds(query, kb.asSource))
    
    // 3 out of 8 countries are both large and coastal
    // France, Italy, Spain
    assertEquals(result.satisfyingCount, 3)
    assertEquals(result.proportion, 3.0 / 8.0) // 0.375
    assertEquals(result.satisfied, true) // 0.375 is within [0.3, 0.7]

  test("disjunction in scope formula"):
    val kb = createCountryKB()
    
    // Q[≥]^{1/2} x (country(x), large(x) ∨ wealthy(x))
    val query = ParsedQuery(
      quantifier = Quantifier.AtLeast(1, 2, 0.1),
      variable = "x",
      range = FOL("country", List(Term.Var("x"))),
      scope = Formula.Or(
        Formula.Atom(FOL("large", List(Term.Var("x")))),
        Formula.Atom(FOL("wealthy", List(Term.Var("x"))))
      )
    )
    
    val result = ok(VagueSemantics.holds(query, kb.asSource))
    
    // Large: France, Germany, Italy, Spain (4)
    // Wealthy: Luxembourg, Switzerland (2)
    // Union: 6 out of 8
    assertEquals(result.satisfyingCount, 6)
    assertEquals(result.proportion, 0.75)
    assertEquals(result.satisfied, true) // 0.75 >= 0.4

  test("negation in scope formula"):
    val kb = createCountryKB()
    
    // Q[~#]^{3/4} x (country(x), ¬wealthy(x))
    val query = ParsedQuery(
      quantifier = Quantifier.About(3, 4, 0.1),
      variable = "x",
      range = FOL("country", List(Term.Var("x"))),
      scope = Formula.Not(Formula.Atom(FOL("wealthy", List(Term.Var("x")))))
    )
    
    val result = ok(VagueSemantics.holds(query, kb.asSource))
    
    // 6 out of 8 are not wealthy (only Luxembourg, Switzerland are wealthy)
    assertEquals(result.satisfyingCount, 6)
    assertEquals(result.proportion, 0.75)
    assertEquals(result.satisfied, true) // 0.75 is within [0.65, 0.85]

  test("sampling evaluation produces reproducible result via HDR PRNG"):
    val kb = createCountryKB()
    
    val query = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.2), // Wider tolerance for sampling
      variable = "x",
      range = FOL("country", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("large", List(Term.Var("x"))))
    )
    
    import fol.sampling.{SamplingParams, HDRConfig}
    val result = ok(VagueSemantics.holds(
      query, kb.asSource,
      samplingParams = SamplingParams(),
      hdrConfig = HDRConfig(entityId = 1, varId = 1, seed3 = 0, seed4 = 0)
    ))
    
    // Small population (8) — SampleSizeCalculator uses all elements
    assertEquals(result.domainSize, 8)
    assertEquals(result.sampleSize, 8)
    assertEquals(result.proportion, 0.5)
    assertEquals(result.satisfied, true)

  test("sampling with small population uses entire range"):
    val kb = createCountryKB()
    
    val query = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.1),
      variable = "x",
      range = FOL("country", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("large", List(Term.Var("x"))))
    )
    
    import fol.sampling.{SamplingParams, HDRConfig}
    val result = ok(VagueSemantics.holds(
      query, kb.asSource,
      samplingParams = SamplingParams(),
      hdrConfig = HDRConfig.default
    ))
    
    // Population of 8 is small enough that SampleSizeCalculator uses all
    assertEquals(result.domainSize, 8)
    assertEquals(result.sampleSize, 8)
    assertEquals(result.proportion, 0.5)

  test("convenience method: holds with exact params"):
    val kb = createCountryKB()
    
    val query = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.1),
      variable = "x",
      range = FOL("country", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("large", List(Term.Var("x"))))
    )
    
    val result = ok(VagueSemantics.holds(query, kb.asSource))
    
    // Should use entire range (no sampling)
    assertEquals(result.sampleSize, result.domainSize)

  test("paper example (simplified): large European countries"):
    // Simplified version of query q₁ from paper Section 5.2
    // Q[~#]^{1/2} x (european_country(x), large(x))
    
    val kb = KnowledgeBase[RelationValue](Map.empty, Map.empty)
      .addRelation(Relation(RelationName("european_country"), 1))
      .addRelation(Relation(RelationName("large"), 1))
      .addFacts(RelationName("european_country"), Set(
        unary("France"), unary("Germany"), unary("Spain"), unary("Italy"), 
        unary("Poland"), unary("Romania"), unary("Luxembourg"), unary("Belgium"), 
        unary("Netherlands"), unary("Portugal")
      ))
      .addFacts(RelationName("large"), Set(
        unary("France"), unary("Germany"), unary("Spain"), unary("Italy"), unary("Poland")
      ))
    
    val query = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.1),
      variable = "x",
      range = FOL("european_country", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("large", List(Term.Var("x"))))
    )
    
    val result = ok(VagueSemantics.holds(query, kb.asSource))
    
    // 5 out of 10 = 0.5
    assertEquals(result.domainSize, 10)
    assertEquals(result.satisfyingCount, 5)
    assertEquals(result.proportion, 0.5)
    assertEquals(result.satisfied, true)

  // ---------- evaluate() → EvaluationOutput (T1b.21) ----------

  test("evaluate returns EvaluationOutput with element sets"):
    val kb = createCountryKB()

    val query = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.1),
      variable = "x",
      range = FOL("country", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("large", List(Term.Var("x"))))
    )

    val output = ok(VagueSemantics.evaluate(query, kb.asSource))

    // Statistics match holds()
    assertEquals(output.result.proportion, 0.5)
    assertEquals(output.result.satisfied, true)
    assertEquals(output.result.domainSize, 8)
    assertEquals(output.result.satisfyingCount, 4)
    // Convenience delegates
    assertEquals(output.satisfied, true)
    assertEquals(output.proportion, 0.5)
    // Element sets
    assertEquals(output.rangeElements.size, 8)
    assertEquals(output.satisfyingElements.size, 4)
    // Satisfying is a subset of range
    assert(output.satisfyingElements.subsetOf(output.rangeElements))

  test("evaluate returns empty element sets for empty range"):
    val kb = createCountryKB()

    val query = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.1),
      variable = "x",
      range = FOL("nonexistent", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("large", List(Term.Var("x"))))
    )

    val output = ok(VagueSemantics.evaluate(query, kb.asSource))

    assertEquals(output.rangeElements, Set.empty[RelationValue])
    assertEquals(output.satisfyingElements, Set.empty[RelationValue])
    assertEquals(output.result.domainSize, 0)
    assertEquals(output.satisfied, false)

  test("evaluate satisfying elements match scope predicate"):
    val kb = createCountryKB()

    // "about three-quarters of large countries are coastal"
    val query = ParsedQuery(
      quantifier = Quantifier.About(3, 4, 0.1),
      variable = "x",
      range = FOL("large", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("coastal", List(Term.Var("x"))))
    )

    val output = ok(VagueSemantics.evaluate(query, kb.asSource))

    // Range = {France, Germany, Italy, Spain}
    assertEquals(output.rangeElements.size, 4)
    // Satisfying = {France, Italy, Spain} — the coastal large countries
    assertEquals(output.satisfyingElements.size, 3)
    assert(output.satisfyingElements.subsetOf(output.rangeElements))

  test("evaluate with sampling params matches holds"):
    val kb = createCountryKB()

    val query = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.2),
      variable = "x",
      range = FOL("country", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("large", List(Term.Var("x"))))
    )

    import fol.sampling.HDRConfig
    val params = SamplingParams()
    val config = HDRConfig(entityId = 1, varId = 1, seed3 = 0, seed4 = 0)
    val output = ok(VagueSemantics.evaluate(query, kb.asSource, samplingParams = params, hdrConfig = config))
    val result = ok(VagueSemantics.holds(query, kb.asSource, samplingParams = params, hdrConfig = config))

    // Results should be consistent
    assertEquals(output.result.proportion, result.proportion)
    assertEquals(output.result.satisfied, result.satisfied)
    assertEquals(output.result.domainSize, result.domainSize)

  // ---------- Cross-API consistency ----------

  test("holds and evaluate produce consistent results"):
    val kb = createCountryKB()

    val query = ParsedQuery(
      quantifier = Quantifier.About(3, 4, 0.1),
      variable = "x",
      range = FOL("large", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("coastal", List(Term.Var("x"))))
    )

    val holdsResult = ok(VagueSemantics.holds(query, kb.asSource))
    val evalOutput = ok(VagueSemantics.evaluate(query, kb.asSource))

    // Both paths see the same statistics
    assertEquals(evalOutput.result.proportion, holdsResult.proportion)
    assertEquals(evalOutput.result.satisfied, holdsResult.satisfied)
