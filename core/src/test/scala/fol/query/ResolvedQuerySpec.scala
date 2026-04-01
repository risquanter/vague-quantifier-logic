package fol.query

import fol.datastore.{KnowledgeBase, KnowledgeSource, Relation, RelationName, RelationValue, RelationTuple}
import fol.quantifier.VagueQuantifier
import fol.sampling.{SamplingParams, HDRConfig}
import fol.result.{VagueQueryResult, EvaluationOutput}
import fol.error.QueryError

class ResolvedQuerySpec extends munit.FunSuite, fol.RelationValueFixtures:

  val countries: Set[RelationValue] = Set(
    const("France"), const("Germany"), const("Italy"), const("Spain"),
    const("Luxembourg"), const("Switzerland"), const("Belgium"), const("Austria")
  )

  val largeCountries: Set[RelationValue] = Set(
    const("France"), const("Germany"), const("Italy"), const("Spain")
  )

  def isLarge(rv: RelationValue): Boolean = largeCountries.contains(rv)

  // ---------- evaluate() ----------

  test("evaluate returns VagueQueryResult with correct statistics"):
    val rq = ResolvedQuery(
      quantifier = VagueQuantifier.most,
      elements = countries,
      predicate = isLarge,
      params = SamplingParams.exact
    )

    val result = rq.evaluate()

    assertEquals(result.domainSize, 8)
    assertEquals(result.sampleSize, 8)
    assertEquals(result.satisfyingCount, 4)
    assertEquals(result.proportion, 0.5)
    // "most" ≈ 0.7 — 0.5 does not satisfy "most"
    assertEquals(result.satisfied, false)

  test("evaluate on empty elements returns unsatisfied with zero proportion"):
    val rq = ResolvedQuery(
      quantifier = VagueQuantifier.most,
      elements = Set.empty,
      predicate = isLarge,
      params = SamplingParams.exact
    )

    val result = rq.evaluate()

    assertEquals(result.domainSize, 0)
    assertEquals(result.sampleSize, 0)
    assertEquals(result.satisfyingCount, 0)
    assertEquals(result.proportion, 0.0)
    assertEquals(result.satisfied, false)

  test("evaluate with all satisfying yields proportion 1.0"):
    val rq = ResolvedQuery(
      quantifier = VagueQuantifier.most,
      elements = largeCountries,
      predicate = isLarge,          // all pass
      params = SamplingParams.exact
    )

    val result = rq.evaluate()

    assertEquals(result.proportion, 1.0)
    assertEquals(result.satisfyingCount, 4)
    assertEquals(result.satisfied, true) // 1.0 satisfies "most"

  test("evaluate with none satisfying yields proportion 0.0"):
    val rq = ResolvedQuery(
      quantifier = VagueQuantifier.few,
      elements = countries,
      predicate = _ => false,
      params = SamplingParams.exact
    )

    val result = rq.evaluate()

    assertEquals(result.proportion, 0.0)
    assertEquals(result.satisfyingCount, 0)

  // ---------- evaluateWithOutput() ----------

  test("evaluateWithOutput returns element sets and result"):
    val rq = ResolvedQuery(
      quantifier = VagueQuantifier.aboutHalf,
      elements = countries,
      predicate = isLarge,
      params = SamplingParams.exact
    )

    val output: EvaluationOutput[RelationValue] = rq.evaluateWithOutput()

    // Statistics
    assertEquals(output.result.proportion, 0.5)
    assertEquals(output.result.domainSize, 8)
    assertEquals(output.result.satisfyingCount, 4)
    // Element sets
    assertEquals(output.rangeElements, countries)
    assertEquals(output.satisfyingElements, largeCountries)
    // Subset invariant
    assert(output.satisfyingElements.subsetOf(output.rangeElements))

  test("evaluateWithOutput empty elements returns empty sets"):
    val rq = ResolvedQuery(
      quantifier = VagueQuantifier.most,
      elements = Set.empty,
      predicate = isLarge,
      params = SamplingParams.exact
    )

    val output = rq.evaluateWithOutput()

    assertEquals(output.rangeElements, Set.empty[RelationValue])
    assertEquals(output.satisfyingElements, Set.empty[RelationValue])
    assertEquals(output.result.domainSize, 0)
    assertEquals(output.satisfied, false)

  test("evaluateWithOutput satisfying is exact subset when using exact params"):
    val rq = ResolvedQuery(
      quantifier = VagueQuantifier.aboutHalf,
      elements = countries,
      predicate = isLarge,
      params = SamplingParams.exact
    )

    val output = rq.evaluateWithOutput()

    // With exact params, all elements evaluated — satisfying should contain
    // exactly the elements that pass the predicate
    val expectedSatisfying = countries.filter(isLarge)
    assertEquals(output.satisfyingElements, expectedSatisfying)

  test("evaluateWithOutput convenience delegates match result fields"):
    val rq = ResolvedQuery(
      quantifier = VagueQuantifier.aboutHalf,
      elements = countries,
      predicate = isLarge,
      params = SamplingParams.exact
    )

    val output = rq.evaluateWithOutput()

    assertEquals(output.satisfied, output.result.satisfied)
    assertEquals(output.proportion, output.result.proportion)

  // ---------- evaluate / evaluateWithOutput consistency ----------

  test("evaluate and evaluateWithOutput produce the same statistics"):
    val rq = ResolvedQuery(
      quantifier = VagueQuantifier.most,
      elements = countries,
      predicate = isLarge,
      params = SamplingParams.exact
    )

    val result = rq.evaluate()
    val output = rq.evaluateWithOutput()

    assertEquals(output.result.satisfied, result.satisfied)
    assertEquals(output.result.proportion, result.proportion)
    assertEquals(output.result.domainSize, result.domainSize)
    assertEquals(output.result.satisfyingCount, result.satisfyingCount)
    assertEquals(output.result.sampleSize, result.sampleSize)

  // ══════════════════════════════════════════════════════════════════
  //  fromRelation factory
  // ══════════════════════════════════════════════════════════════════

  // Fixtures for fromRelation tests

  def createCountryKB(): KnowledgeBase[RelationValue] =
    KnowledgeBase.builder[RelationValue]
      .withRelation(Relation(RelationName("country"), 1))
      .withRelation(Relation(RelationName("large"), 1))
      .withRelation(Relation(RelationName("coastal"), 1))
      .withRelation(Relation(RelationName("wealthy"), 1))
      .withRelation(Relation(RelationName("borders"), 2))
      .withFacts("country", Set(
        "France", "Germany", "Italy", "Spain",
        "Luxembourg", "Switzerland", "Belgium", "Austria"
      ).map(unary))
      .withFacts("large", Set("France", "Germany", "Italy", "Spain").map(unary))
      .withFacts("coastal", Set("France", "Italy", "Spain").map(unary))
      .withFacts("wealthy", Set("Luxembourg", "Switzerland").map(unary))
      .withFacts("borders", Set(
        binary("France", "Germany"),
        binary("France", "Belgium"),
        binary("France", "Luxembourg"),
        binary("France", "Switzerland"),
        binary("France", "Italy"),
        binary("France", "Spain"),
        binary("Germany", "Austria"),
        binary("Germany", "Switzerland"),
        binary("Germany", "Belgium"),
        binary("Germany", "Luxembourg"),
        binary("Italy", "Austria"),
        binary("Italy", "Switzerland"),
        binary("Belgium", "Luxembourg")
      ))
      .build()

  def rvSource: KnowledgeSource[RelationValue] =
    KnowledgeSource.fromKnowledgeBase(createCountryKB())

  def createStringKB(): KnowledgeBase[String] =
    KnowledgeBase.builder[String]
      .withRelation(Relation(RelationName("employee"), 1))
      .withRelation(Relation(RelationName("satisfied"), 1))
      .withRelation(Relation(RelationName("works_in"), 2))
      .withFacts("employee", Set("Alice", "Bob", "Charlie", "Diana", "Eve",
                                 "Frank", "Grace", "Hank", "Ivy", "Jack").map(e => RelationTuple(List(e))))
      .withFacts("satisfied", Set("Alice", "Bob", "Charlie", "Diana", "Eve",
                                  "Frank", "Grace").map(e => RelationTuple(List(e))))
      .withFacts("works_in", Set(
        ("Alice", "Engineering"), ("Bob", "Engineering"), ("Charlie", "Engineering"),
        ("Diana", "Sales"), ("Eve", "Sales"),
        ("Frank", "HR"), ("Grace", "HR"),
        ("Hank", "Engineering"), ("Ivy", "Sales"), ("Jack", "HR")
      ).map((e, d) => RelationTuple(List(e, d))))
      .build()

  def strSource: KnowledgeSource[String] =
    KnowledgeSource.fromKnowledgeBase(createStringKB())

  test("fromRelation with valid relation returns Right"):
    val result = ResolvedQuery.fromRelation(
      source = rvSource,
      relationName = RelationName("country"),
      quantifier = VagueQuantifier.most,
      predicate = (rv: RelationValue) => Set("France", "Germany", "Italy", "Spain").map(const).contains(rv)
    )
    assert(result.isRight, s"Expected Right, got $result")
    val rq = result.toOption.get
    assertEquals(rq.elements.size, 8)
    assertEquals(rq.quantifier, VagueQuantifier.most)
    assertEquals(rq.params, SamplingParams.exact)

  test("fromRelation with unknown relation returns Left(RelationNotFoundError)"):
    val result = ResolvedQuery.fromRelation(
      source = rvSource,
      relationName = RelationName("nonexistent"),
      quantifier = VagueQuantifier.most,
      predicate = (_: RelationValue) => true
    )
    result match
      case Left(err: QueryError.RelationNotFoundError) =>
        assertEquals(err.relationName, RelationName("nonexistent"))
        assert(err.availableRelations.contains(RelationName("country")))
      case Left(other) =>
        fail(s"Expected RelationNotFoundError, got $other")
      case Right(_) =>
        fail("Expected Left for nonexistent relation")

  test("fromRelation with empty-but-existing relation returns Right (vacuously-false)"):
    val kb = KnowledgeBase.builder[RelationValue]
      .withRelation(Relation(RelationName("empty_rel"), 1))
      .build()
    val src = KnowledgeSource.fromKnowledgeBase(kb)

    val result = ResolvedQuery.fromRelation(
      source = src,
      relationName = RelationName("empty_rel"),
      quantifier = VagueQuantifier.most,
      predicate = (_: RelationValue) => true
    )
    assert(result.isRight, s"Expected Right, got $result")
    val rq = result.toOption.get
    assertEquals(rq.elements.size, 0)
    val evalResult = rq.evaluate()
    assertEquals(evalResult.satisfied, false)
    assertEquals(evalResult.proportion, 0.0)

  test("fromRelation with non-default position"):
    val result = ResolvedQuery.fromRelation(
      source = rvSource,
      relationName = RelationName("borders"),
      position = 1,
      quantifier = VagueQuantifier.most,
      predicate = (_: RelationValue) => true
    )
    assert(result.isRight)
    val elements = result.toOption.get.elements
    assert(elements.nonEmpty, "borders position 1 should have elements")

  test("fromRelation on String domain"):
    val result = ResolvedQuery.fromRelation(
      source = strSource,
      relationName = RelationName("employee"),
      quantifier = VagueQuantifier.most,
      predicate = (name: String) => Set("Alice", "Bob", "Charlie", "Diana",
                                         "Eve", "Frank", "Grace").contains(name)
    )
    assert(result.isRight)
    val rq = result.toOption.get
    assertEquals(rq.elements.size, 10)
    val evalResult = rq.evaluate()
    assertEquals(evalResult.proportion, 0.7)
    assertEquals(evalResult.satisfied, true)

  test("fromRelation with sampling params propagates correctly"):
    val params = SamplingParams(epsilon = 0.05, alpha = 0.01)
    val config = HDRConfig(seed3 = 123)

    val result = ResolvedQuery.fromRelation(
      source = rvSource,
      relationName = RelationName("country"),
      quantifier = VagueQuantifier.most,
      predicate = (rv: RelationValue) => Set("France", "Germany", "Italy", "Spain").map(const).contains(rv),
      params = params,
      hdrConfig = config
    )
    assert(result.isRight)
    val rq = result.toOption.get
    assertEquals(rq.params, params)
    assertEquals(rq.hdrConfig, config)

  // ══════════════════════════════════════════════════════════════════
  //  Translated integration tests (from DSL spec)
  // ══════════════════════════════════════════════════════════════════

  test("predicate with closure over external state"):
    var threshold = 5
    val pred: String => Boolean = name => name.length >= threshold

    val result1 = ResolvedQuery.fromRelation(
      source = strSource,
      relationName = RelationName("employee"),
      quantifier = VagueQuantifier.most,
      predicate = pred
    )
    assert(result1.isRight)
    val prop1 = result1.toOption.get.evaluate().proportion

    threshold = 10
    val result2 = ResolvedQuery.fromRelation(
      source = strSource,
      relationName = RelationName("employee"),
      quantifier = VagueQuantifier.most,
      predicate = pred
    )
    assert(result2.isRight)
    assert(result2.toOption.get.evaluate().proportion <= prop1,
      "Higher threshold should yield fewer matching elements")

  test("combining predicates with logical operators"):
    val largeSet = Set("France", "Germany", "Italy", "Spain").map(const)
    val coastalSet = Set("France", "Italy", "Spain").map(const)

    val result = ResolvedQuery.fromRelation(
      source = rvSource,
      relationName = RelationName("country"),
      quantifier = VagueQuantifier.aboutQuarter,
      predicate = (rv: RelationValue) => largeSet.contains(rv) && coastalSet.contains(rv)
    )
    assert(result.isRight)
    val evalResult = result.toOption.get.evaluate()
    // Large ∩ Coastal = {France, Italy, Spain} → 3/8 = 0.375
    assertEquals(evalResult.proportion, 0.375)
    assertEquals(evalResult.satisfied, false) // aboutQuarter accepts [0.15, 0.35]

  test("negated predicate"):
    val wealthySet = Set("Luxembourg", "Switzerland").map(const)

    val result = ResolvedQuery.fromRelation(
      source = rvSource,
      relationName = RelationName("country"),
      quantifier = VagueQuantifier.most,
      predicate = (rv: RelationValue) => !wealthySet.contains(rv)
    )
    assert(result.isRight)
    val evalResult = result.toOption.get.evaluate()
    assertEquals(evalResult.proportion, 0.75)
    assertEquals(evalResult.satisfied, true)

  test("multiple queries over same source"):
    val src = rvSource
    val largeSet = Set("France", "Germany", "Italy", "Spain").map(const)
    val coastalSet = Set("France", "Italy", "Spain").map(const)
    val wealthySet = Set("Luxembourg", "Switzerland").map(const)

    val large = ResolvedQuery.fromRelation(src, RelationName("country"),
      quantifier = VagueQuantifier.aboutHalf,
      predicate = (rv: RelationValue) => largeSet.contains(rv)).toOption.get.evaluate()
    val coastal = ResolvedQuery.fromRelation(src, RelationName("country"),
      quantifier = VagueQuantifier.several,
      predicate = (rv: RelationValue) => coastalSet.contains(rv)).toOption.get.evaluate()
    val wealthy = ResolvedQuery.fromRelation(src, RelationName("country"),
      quantifier = VagueQuantifier.few,
      predicate = (rv: RelationValue) => wealthySet.contains(rv)).toOption.get.evaluate()

    assertEquals(large.satisfied, true)   // 4/8 = 0.5, aboutHalf
    assertEquals(coastal.satisfied, true)  // 3/8 = 0.375, several ≥ 30%
    assertEquals(wealthy.satisfied, true)  // 2/8 = 0.25, few ≤ 30%

  test("fromRelation evaluateWithOutput satisfying elements are correct on String domain"):
    val worksInHR: String => Boolean =
      d => strSource.query(RelationName("works_in"), List(Some(d), Some("HR"))).exists(_.nonEmpty)

    val result = ResolvedQuery.fromRelation(
      source = strSource,
      relationName = RelationName("employee"),
      quantifier = VagueQuantifier.few,
      predicate = worksInHR
    )
    assert(result.isRight)
    val output = result.toOption.get.evaluateWithOutput()
    assertEquals(output.satisfyingElements, Set("Frank", "Grace", "Jack"))
    assertEquals(output.rangeElements.size, 10)
    assertEquals(output.proportion, 0.3)
