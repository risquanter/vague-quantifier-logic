package fol.query

import munit.FunSuite
import fol.datastore.{KnowledgeBase, KnowledgeSource, Relation, RelationName, RelationValue, RelationTuple, DomainElement}
import fol.quantifier.{VagueQuantifier, Approximately, AtLeast, AtMost}
import fol.sampling.{SamplingParams, HDRConfig}
import fol.result.{VagueQueryResult, EvaluationOutput}
import fol.error.QueryError
import scala.reflect.ClassTag
import fol.RelationValueFixtures

/** Comprehensive test suite for the Query DSL.
  *
  * Covers the full DSL surface:
  *   - Builder chain: Query.quantifier().over().where[D]() / whereConst() / satisfying[D]()
  *   - DomainSpec variants: Relation(name, position), ActiveDomain
  *   - Predicate builders: Predicates.hasRelation, inRelation, relatedTo
  *   - Extension methods: source.execute(), source.executeWithOutput()
  *   - Generic typing: D = RelationValue, D = String
  *   - All built-in quantifiers
  *   - Sampling parameter propagation
  *   - Error paths
  *
  * Intended audience: register project integration. These tests serve
  * as executable usage documentation for programmatic ("hard-coded")
  * queries using the typed DSL — as opposed to the string API
  * (VagueQueryParser + VagueSemantics.holds) used when a user
  * phrases a query via the GUI.
  */
class QueryDSLSpec extends FunSuite, RelationValueFixtures:

  // ══════════════════════════════════════════════════════════════════
  //  Fixtures — RelationValue domain
  // ══════════════════════════════════════════════════════════════════

  val countries: Set[String] = Set(
    "France", "Germany", "Italy", "Spain",
    "Luxembourg", "Switzerland", "Belgium", "Austria"
  )

  val largeCountries: Set[String] = Set("France", "Germany", "Italy", "Spain")
  val coastalCountries: Set[String] = Set("France", "Italy", "Spain")
  val wealthyCountries: Set[String] = Set("Luxembourg", "Switzerland")

  def createCountryKB(): KnowledgeBase[RelationValue] =
    KnowledgeBase[RelationValue](Map.empty, Map.empty)
      .addRelation(Relation(RelationName("country"), 1))
      .addRelation(Relation(RelationName("large"), 1))
      .addRelation(Relation(RelationName("coastal"), 1))
      .addRelation(Relation(RelationName("wealthy"), 1))
      .addRelation(Relation(RelationName("borders"), 2))
      .addFacts(RelationName("country"), countries.map(unary))
      .addFacts(RelationName("large"), largeCountries.map(unary))
      .addFacts(RelationName("coastal"), coastalCountries.map(unary))
      .addFacts(RelationName("wealthy"), wealthyCountries.map(unary))
      .addFacts(RelationName("borders"), Set(
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

  def rvSource: KnowledgeSource[RelationValue] =
    KnowledgeSource.fromKnowledgeBase(createCountryKB())

  // ══════════════════════════════════════════════════════════════════
  //  Fixtures — String domain (generic typing)
  // ══════════════════════════════════════════════════════════════════

  def createStringKB(): KnowledgeBase[String] =
    KnowledgeBase[String](Map.empty, Map.empty)
      .addRelation(Relation(RelationName("employee"), 1))
      .addRelation(Relation(RelationName("satisfied"), 1))
      .addRelation(Relation(RelationName("department"), 1))
      .addRelation(Relation(RelationName("works_in"), 2))
      .addFacts(RelationName("employee"), Set("Alice", "Bob", "Charlie", "Diana", "Eve",
                                 "Frank", "Grace", "Hank", "Ivy", "Jack").map(e => RelationTuple(List(e))))
      .addFacts(RelationName("satisfied"), Set("Alice", "Bob", "Charlie", "Diana", "Eve",
                                  "Frank", "Grace").map(e => RelationTuple(List(e))))
      .addFacts(RelationName("department"), Set("Engineering", "Sales", "HR").map(d => RelationTuple(List(d))))
      .addFacts(RelationName("works_in"), Set(
        ("Alice", "Engineering"), ("Bob", "Engineering"), ("Charlie", "Engineering"),
        ("Diana", "Sales"), ("Eve", "Sales"),
        ("Frank", "HR"), ("Grace", "HR"),
        ("Hank", "Engineering"), ("Ivy", "Sales"), ("Jack", "HR")
      ).map((e, d) => RelationTuple(List(e, d))))

  def strSource: KnowledgeSource[String] =
    KnowledgeSource.fromKnowledgeBase(createStringKB())

  // ══════════════════════════════════════════════════════════════════
  //  Section 1: Builder chain — quantifier + over + where
  // ══════════════════════════════════════════════════════════════════

  test("DSL: quantifier().over().whereConst() builds UnresolvedQuery[RelationValue]"):
    val q: UnresolvedQuery[RelationValue] = Query
      .quantifier(VagueQuantifier.most)
      .over("country")
      .whereConst(largeCountries.contains)

    assertEquals(q.quantifier, VagueQuantifier.most)
    assertEquals(q.domain, DomainSpec.Relation(RelationName("country"), 0))
    assertEquals(q.params, SamplingParams.exact)
    assertEquals(q.hdrConfig, HDRConfig.default)

  test("DSL: quantifier().over().where[D]() builds UnresolvedQuery[D]"):
    val q: UnresolvedQuery[RelationValue] = Query
      .quantifier(VagueQuantifier.few)
      .over("country")
      .where[RelationValue](rv => rv == const("Luxembourg"))

    val result = q.evaluate(rvSource)
    assert(result.isRight)
    // 1/8 = 0.125 satisfies "few" (≤ 30%)
    assertEquals(result.toOption.get.proportion, 0.125)
    assertEquals(result.toOption.get.satisfied, true)

  test("DSL: where[String] with String-typed KnowledgeSource"):
    val q: UnresolvedQuery[String] = Query
      .quantifier(VagueQuantifier.most)
      .over("employee")
      .where[String](name => Set("Alice", "Bob", "Charlie", "Diana",
                                  "Eve", "Frank", "Grace").contains(name))

    val result = q.evaluate(strSource)
    assert(result.isRight)
    // 7/10 = 0.7 — satisfies "most" (≥ 70%)
    assertEquals(result.toOption.get.proportion, 0.7)
    assertEquals(result.toOption.get.satisfied, true)

  test("DSL: satisfying[D] is alias for where[D]"):
    val qWhere: UnresolvedQuery[RelationValue] = Query
      .quantifier(VagueQuantifier.most)
      .over("country")
      .where[RelationValue](largeCountries.map(const).contains)

    val qSatisfying: UnresolvedQuery[RelationValue] = Query
      .quantifier(VagueQuantifier.most)
      .over("country")
      .satisfying[RelationValue](largeCountries.map(const).contains)

    val rWhere = qWhere.evaluate(rvSource)
    val rSatisfying = qSatisfying.evaluate(rvSource)

    assert(rWhere.isRight && rSatisfying.isRight)
    assertEquals(rWhere.toOption.get.proportion, rSatisfying.toOption.get.proportion)
    assertEquals(rWhere.toOption.get.satisfied, rSatisfying.toOption.get.satisfied)

  // ══════════════════════════════════════════════════════════════════
  //  Section 2: DomainSpec variants
  // ══════════════════════════════════════════════════════════════════

  test("DSL: over(relation, position) uses non-default position"):
    // borders is binary — position 1 gives the "to" countries
    val q = Query
      .quantifier(VagueQuantifier.most)
      .over("borders", 1)
      .whereConst(largeCountries.contains)

    assertEquals(q.domain, DomainSpec.Relation(RelationName("borders"), 1))
    val result = q.resolve(rvSource)
    assert(result.isRight)
    // Position 1 of borders contains the border targets
    val elements = result.toOption.get.elements
    assert(elements.nonEmpty, "borders position 1 should have elements")

  test("DSL: overActiveDomain queries all constants"):
    val q = Query
      .quantifier(VagueQuantifier.some)
      .overActiveDomain
      .whereConst(largeCountries.contains)

    assertEquals(q.domain, DomainSpec.ActiveDomain)
    val result = q.resolve(rvSource)
    assert(result.isRight)
    // Active domain includes all constants from all relations (country, large, coastal, wealthy, borders)
    assert(result.toOption.get.elements.size >= 8)

  test("DSL: overActiveDomain with String domain"):
    val q = Query
      .quantifier(VagueQuantifier.most)
      .overActiveDomain
      .where[String](name => name.length <= 5)

    val result = q.evaluate(strSource)
    assert(result.isRight)
    // Active domain includes employees + departments + all values

  // ══════════════════════════════════════════════════════════════════
  //  Section 3: All built-in quantifiers
  // ══════════════════════════════════════════════════════════════════

  // 4 large out of 8 countries = 50%

  test("DSL quantifier: most (≥ 70%) — 50% does NOT satisfy"):
    val result = Query.quantifier(VagueQuantifier.most).over("country")
      .whereConst(largeCountries.contains).evaluate(rvSource)
    assertEquals(result.toOption.get.satisfied, false)

  test("DSL quantifier: many (≥ 50%) — 50% satisfies"):
    val result = Query.quantifier(VagueQuantifier.many).over("country")
      .whereConst(largeCountries.contains).evaluate(rvSource)
    assertEquals(result.toOption.get.satisfied, true)

  test("DSL quantifier: several (≥ 30%) — 50% satisfies"):
    val result = Query.quantifier(VagueQuantifier.several).over("country")
      .whereConst(largeCountries.contains).evaluate(rvSource)
    assertEquals(result.toOption.get.satisfied, true)

  test("DSL quantifier: some (≥ 10%) — 50% satisfies"):
    val result = Query.quantifier(VagueQuantifier.some).over("country")
      .whereConst(largeCountries.contains).evaluate(rvSource)
    assertEquals(result.toOption.get.satisfied, true)

  test("DSL quantifier: almostAll (≥ 90%) — 50% does NOT satisfy"):
    val result = Query.quantifier(VagueQuantifier.almostAll).over("country")
      .whereConst(largeCountries.contains).evaluate(rvSource)
    assertEquals(result.toOption.get.satisfied, false)

  test("DSL quantifier: few (≤ 30%) — 50% does NOT satisfy"):
    val result = Query.quantifier(VagueQuantifier.few).over("country")
      .whereConst(largeCountries.contains).evaluate(rvSource)
    assertEquals(result.toOption.get.satisfied, false)

  test("DSL quantifier: hardlyAny (≤ 10%) — 50% does NOT satisfy"):
    val result = Query.quantifier(VagueQuantifier.hardlyAny).over("country")
      .whereConst(largeCountries.contains).evaluate(rvSource)
    assertEquals(result.toOption.get.satisfied, false)

  test("DSL quantifier: almostNone (≤ 5%) — 50% does NOT satisfy"):
    val result = Query.quantifier(VagueQuantifier.almostNone).over("country")
      .whereConst(largeCountries.contains).evaluate(rvSource)
    assertEquals(result.toOption.get.satisfied, false)

  test("DSL quantifier: notMany (≤ 40%) — 50% does NOT satisfy"):
    val result = Query.quantifier(VagueQuantifier.notMany).over("country")
      .whereConst(largeCountries.contains).evaluate(rvSource)
    assertEquals(result.toOption.get.satisfied, false)

  test("DSL quantifier: aboutHalf (~50% ±10%) — 50% satisfies"):
    val result = Query.quantifier(VagueQuantifier.aboutHalf).over("country")
      .whereConst(largeCountries.contains).evaluate(rvSource)
    assertEquals(result.toOption.get.proportion, 0.5)
    assertEquals(result.toOption.get.satisfied, true)

  test("DSL quantifier: aboutQuarter (~25% ±10%) — 50% does NOT satisfy"):
    val result = Query.quantifier(VagueQuantifier.aboutQuarter).over("country")
      .whereConst(largeCountries.contains).evaluate(rvSource)
    assertEquals(result.toOption.get.satisfied, false)

  test("DSL quantifier: aboutThreeQuarters (~75% ±10%) — 50% does NOT satisfy"):
    val result = Query.quantifier(VagueQuantifier.aboutThreeQuarters).over("country")
      .whereConst(largeCountries.contains).evaluate(rvSource)
    assertEquals(result.toOption.get.satisfied, false)

  // ── Custom quantifiers ────────────────────────────────────────────

  test("DSL quantifier: custom approximately(60%, ±15%)"):
    val q = VagueQuantifier.approximately(60, 15) // accepts [45%, 75%]
    val result = Query.quantifier(q).over("country")
      .whereConst(largeCountries.contains).evaluate(rvSource)
    // 50% in [45%, 75%] → satisfied
    assertEquals(result.toOption.get.satisfied, true)

  test("DSL quantifier: custom atLeast(45%)"):
    val q = VagueQuantifier.atLeast(45)
    val result = Query.quantifier(q).over("country")
      .whereConst(largeCountries.contains).evaluate(rvSource)
    // 50% ≥ 45% → satisfied
    assertEquals(result.toOption.get.satisfied, true)

  test("DSL quantifier: custom atMost(60%)"):
    val q = VagueQuantifier.atMost(60)
    val result = Query.quantifier(q).over("country")
      .whereConst(largeCountries.contains).evaluate(rvSource)
    // 50% ≤ 60% → satisfied
    assertEquals(result.toOption.get.satisfied, true)

  // ══════════════════════════════════════════════════════════════════
  //  Section 4: whereConst specifics
  // ══════════════════════════════════════════════════════════════════

  test("DSL: whereConst unwraps Const names"):
    val q = Query
      .quantifier(VagueQuantifier.aboutHalf)
      .over("country")
      .whereConst(name => name.startsWith("S") || name.startsWith("F"))

    val result = q.evaluate(rvSource)
    assert(result.isRight)
    // France, Spain, Switzerland = 3/8 = 0.375 — within [0.4, 0.6]? No → 0.375 < 0.4
    assertEquals(result.toOption.get.proportion, 0.375)
    assertEquals(result.toOption.get.satisfied, false)

  test("DSL: whereConst rejects non-Const values"):
    // Build a KB with a Num in the facts — whereConst only unwraps Const
    val kb = KnowledgeBase[RelationValue](Map.empty, Map.empty)
      .addRelation(Relation(RelationName("test"), 1))
      .addFacts(RelationName("test"), Set(
        RelationTuple(List(const("a"))),
        RelationTuple(List(const("b"))),
        RelationTuple(List(RelationValue.Num(99)))
      ))
    val src: KnowledgeSource[RelationValue] = KnowledgeSource.fromKnowledgeBase(kb)

    // whereConst predicate: everything passes for Const, fails for Num
    val q = Query
      .quantifier(VagueQuantifier.most)
      .over("test")
      .whereConst(_ => true)

    val result = q.evaluate(src)
    assert(result.isRight)
    // 2 Const pass, 1 Num fails → 2/3 ≈ 0.667 < 0.7 → not "most"
    assertEqualsDouble(result.toOption.get.proportion, 2.0 / 3.0, 0.001)
    assertEquals(result.toOption.get.satisfied, false)

  // ══════════════════════════════════════════════════════════════════
  //  Section 5: Sampling parameter propagation
  // ══════════════════════════════════════════════════════════════════

  test("DSL: where[D] with sampling params propagates correctly"):
    val params = SamplingParams(epsilon = 0.05, alpha = 0.01)
    val config = HDRConfig(seed3 = 123)

    val q = Query
      .quantifier(VagueQuantifier.most)
      .over("country")
      .where[RelationValue](largeCountries.map(const).contains, params, config)

    assertEquals(q.params, params)
    assertEquals(q.hdrConfig, config)

    val result = q.evaluate(rvSource)
    assert(result.isRight)
    // With only 8 elements the sampler uses all — proportion stays 0.5
    assertEquals(result.toOption.get.proportion, 0.5)

  test("DSL: whereConst with sampling params propagates correctly"):
    val params = SamplingParams(epsilon = 0.1, alpha = 0.05)
    val config = HDRConfig(seed3 = 42)

    val q = Query
      .quantifier(VagueQuantifier.few)
      .over("country")
      .whereConst(wealthyCountries.contains, params, config)

    assertEquals(q.params, params)
    assertEquals(q.hdrConfig, config)

  test("DSL: where[String] with sampling params on String domain"):
    val params = SamplingParams(epsilon = 0.1, alpha = 0.05)

    val q = Query
      .quantifier(VagueQuantifier.most)
      .over("employee")
      .where[String](_ => true, params)

    assertEquals(q.params, params)
    val result = q.evaluate(strSource)
    assert(result.isRight)
    assertEquals(result.toOption.get.proportion, 1.0)

  // ══════════════════════════════════════════════════════════════════
  //  Section 6: Predicates object
  // ══════════════════════════════════════════════════════════════════

  test("Predicates.inRelation[RelationValue] checks unary relation membership"):
    val pred = Predicates.inRelation[RelationValue](rvSource, "large")

    assert(pred(const("France")), "France is in large")
    assert(pred(const("Germany")), "Germany is in large")
    assert(!pred(const("Luxembourg")), "Luxembourg is NOT in large")
    assert(!pred(const("Belgium")), "Belgium is NOT in large")

  test("Predicates.inRelation as DSL predicate"):
    val q = Query
      .quantifier(VagueQuantifier.aboutHalf)
      .over("country")
      .where[RelationValue](Predicates.inRelation(rvSource, "large"))

    val result = q.evaluate(rvSource)
    assert(result.isRight)
    assertEquals(result.toOption.get.proportion, 0.5)
    assertEquals(result.toOption.get.satisfied, true)

  test("Predicates.inRelation[String] on String domain"):
    val pred = Predicates.inRelation[String](strSource, "satisfied")

    assert(pred("Alice"), "Alice is satisfied")
    assert(!pred("Hank"), "Hank is NOT satisfied")

  test("Predicates.inRelation[String] used in full DSL chain"):
    val q = Query
      .quantifier(VagueQuantifier.most)
      .over("employee")
      .where[String](Predicates.inRelation(strSource, "satisfied"))

    val result = q.evaluate(strSource)
    assert(result.isRight)
    // 7/10 = 0.7 — satisfies "most" (≥ 70%)
    assertEquals(result.toOption.get.proportion, 0.7)
    assertEquals(result.toOption.get.satisfied, true)

  test("Predicates.hasRelation[RelationValue] checks relation via arg mapper"):
    // Check which countries have borders(entity, Germany) — directed relation
    val pred = Predicates.hasRelation[RelationValue](
      rvSource,
      "borders",
      entity => List(entity, const("Germany"))
    )

    // borders(France, Germany) exists → true
    assert(pred(const("France")), "France borders→Germany exists")
    // borders(Spain, Germany) does NOT exist → false (directed: only (France, Germany) etc.)
    assert(!pred(const("Spain")), "Spain borders→Germany does NOT exist")
    // borders(Belgium, Germany) does NOT exist (only Germany→Belgium does)
    assert(!pred(const("Belgium")), "Belgium→Germany not stored; only Germany→Belgium")

  test("Predicates.hasRelation as DSL predicate"):
    // "Most countries that border Germany are large"
    // (we query over borders position 0 and filter with hasRelation)
    val bordersGermany = Predicates.hasRelation[RelationValue](
      rvSource, "borders", entity => List(entity, const("Germany"))
    )
    val q = Query
      .quantifier(VagueQuantifier.most)
      .over("country")
      .where[RelationValue](rv => bordersGermany(rv) && largeCountries.map(const).contains(rv))

    val result = q.evaluate(rvSource)
    assert(result.isRight)
    // Countries bordering Germany AND large: France, (Germany→Austria: not large in domain)
    // Result depends on actual data

  test("Predicates.hasRelation[String] on String domain"):
    val worksInEngineering = Predicates.hasRelation[String](
      strSource,
      "works_in",
      emp => List(emp, "Engineering")
    )

    assert(worksInEngineering("Alice"), "Alice works in Engineering")
    assert(worksInEngineering("Bob"), "Bob works in Engineering")
    assert(!worksInEngineering("Diana"), "Diana does NOT work in Engineering")

  test("Predicates.relatedTo[RelationValue] checks binary relation"):
    val bordersItaly = Predicates.relatedTo[RelationValue](
      rvSource,
      "borders",
      const("Italy")
    )

    // France borders Italy
    assert(bordersItaly(const("France")), "France borders Italy")
    // Italy → Italy? Depends on data
    assert(!bordersItaly(const("Spain")), "Spain does NOT border Italy in our data")

  test("Predicates.relatedTo[String] on String domain"):
    val worksInSales = Predicates.relatedTo[String](
      strSource,
      "works_in",
      "Sales"
    )

    assert(worksInSales("Diana"), "Diana works in Sales")
    assert(worksInSales("Eve"), "Eve works in Sales")
    assert(!worksInSales("Alice"), "Alice does NOT work in Sales")

  test("Predicates.relatedTo used in full DSL chain"):
    // "Few employees work in HR"
    val q = Query
      .quantifier(VagueQuantifier.few)
      .over("employee")
      .where[String](Predicates.relatedTo(strSource, "works_in", "HR"))

    val result = q.evaluate(strSource)
    assert(result.isRight)
    // Frank, Grace, Jack in HR → 3/10 = 0.3 — satisfies "few" (≤ 30%)
    assertEquals(result.toOption.get.proportion, 0.3)
    assertEquals(result.toOption.get.satisfied, true)

  // ══════════════════════════════════════════════════════════════════
  //  Section 7: Extension methods — source.execute / executeWithOutput
  // ══════════════════════════════════════════════════════════════════

  test("source.execute[RelationValue] evaluates query"):
    val q = Query
      .quantifier(VagueQuantifier.aboutHalf)
      .over("country")
      .whereConst(largeCountries.contains)

    val result: Either[QueryError, VagueQueryResult] = rvSource.execute(q)
    assert(result.isRight)
    assertEquals(result.toOption.get.proportion, 0.5)
    assertEquals(result.toOption.get.satisfied, true)

  test("source.execute[String] evaluates query on String domain"):
    val q = Query
      .quantifier(VagueQuantifier.most)
      .over("employee")
      .where[String](Predicates.inRelation(strSource, "satisfied"))

    val result: Either[QueryError, VagueQueryResult] = strSource.execute(q)
    assert(result.isRight)
    assertEquals(result.toOption.get.proportion, 0.7)

  test("source.executeWithOutput returns element sets"):
    val q = Query
      .quantifier(VagueQuantifier.aboutHalf)
      .over("country")
      .whereConst(largeCountries.contains)

    val result: Either[QueryError, EvaluationOutput[RelationValue]] =
      rvSource.executeWithOutput(q)
    assert(result.isRight)

    val output = result.toOption.get
    assertEquals(output.rangeElements.size, 8)
    assertEquals(output.satisfyingElements.size, 4)
    assert(output.satisfyingElements.subsetOf(output.rangeElements))
    assertEquals(output.proportion, 0.5)

  test("source.executeWithOutput[String] returns typed element sets"):
    val q = Query
      .quantifier(VagueQuantifier.few)
      .over("employee")
      .where[String](Predicates.relatedTo(strSource, "works_in", "HR"))

    val result: Either[QueryError, EvaluationOutput[String]] =
      strSource.executeWithOutput(q)
    assert(result.isRight)

    val output = result.toOption.get
    assertEquals(output.rangeElements.size, 10)
    assertEquals(output.satisfyingElements, Set("Frank", "Grace", "Jack"))
    assertEquals(output.proportion, 0.3)

  // ══════════════════════════════════════════════════════════════════
  //  Section 8: Edge cases
  // ══════════════════════════════════════════════════════════════════

  test("DSL: predicate that matches ALL elements → proportion 1.0"):
    val q = Query
      .quantifier(VagueQuantifier.almostAll)
      .over("country")
      .whereConst(_ => true)

    val result = q.evaluate(rvSource)
    assert(result.isRight)
    assertEquals(result.toOption.get.proportion, 1.0)
    assertEquals(result.toOption.get.satisfied, true)

  test("DSL: predicate that matches NO elements → proportion 0.0"):
    val q = Query
      .quantifier(VagueQuantifier.almostNone)
      .over("country")
      .whereConst(_ => false)

    val result = q.evaluate(rvSource)
    assert(result.isRight)
    assertEquals(result.toOption.get.proportion, 0.0)
    assertEquals(result.toOption.get.satisfied, true)

  test("DSL: query over nonexistent relation returns Left"):
    val q = Query
      .quantifier(VagueQuantifier.most)
      .over("nonexistent")
      .whereConst(_ => true)

    val result = q.evaluate(rvSource)
    assert(result.isLeft)

  test("DSL: query over nonexistent relation returns Left for String domain"):
    val q = Query
      .quantifier(VagueQuantifier.most)
      .over("nonexistent")
      .where[String](_ => true)

    val result = q.evaluate(strSource)
    assert(result.isLeft)

  test("DSL: resolve error propagates through evaluate"):
    val q = Query
      .quantifier(VagueQuantifier.most)
      .over("nonexistent")
      .whereConst(_ => true)

    val resolveResult = q.resolve(rvSource)
    val evalResult = q.evaluate(rvSource)
    val outputResult = q.evaluateWithOutput(rvSource)

    assert(resolveResult.isLeft)
    assert(evalResult.isLeft)
    assert(outputResult.isLeft)

  test("DSL: predicate with closure over external state"):
    var threshold = 5
    val q = Query
      .quantifier(VagueQuantifier.most)
      .over("employee")
      .where[String](name => name.length >= threshold)

    val result1 = q.evaluate(strSource)
    assert(result1.isRight)
    val prop1 = result1.toOption.get.proportion

    // Closure captures the var — changing threshold changes results
    // Note: the UnresolvedQuery captures the function reference,
    // so this tests that predicates are evaluated lazily at resolve time
    threshold = 10
    val result2 = q.evaluate(strSource)
    assert(result2.isRight)
    assert(result2.toOption.get.proportion <= prop1,
      "Higher threshold should yield fewer matching elements")

  // ══════════════════════════════════════════════════════════════════
  //  Section 9: evaluate vs evaluateWithOutput consistency
  // ══════════════════════════════════════════════════════════════════

  test("DSL: evaluate and evaluateWithOutput produce consistent results"):
    val q = Query
      .quantifier(VagueQuantifier.many)
      .over("country")
      .whereConst(coastalCountries.contains)

    val evalResult = q.evaluate(rvSource)
    val outputResult = q.evaluateWithOutput(rvSource)

    assert(evalResult.isRight)
    assert(outputResult.isRight)

    val eval = evalResult.toOption.get
    val output = outputResult.toOption.get

    assertEquals(output.result.proportion, eval.proportion)
    assertEquals(output.result.satisfied, eval.satisfied)
    assertEquals(output.result.domainSize, eval.domainSize)
    assertEquals(output.result.satisfyingCount, eval.satisfyingCount)

  test("DSL: evaluateWithOutput satisfying set matches predicate"):
    val q = Query
      .quantifier(VagueQuantifier.aboutHalf)
      .over("country")
      .whereConst(largeCountries.contains)

    val output = q.evaluateWithOutput(rvSource).toOption.get

    // Every element in satisfyingElements should pass the predicate
    output.satisfyingElements.foreach { elem =>
      elem match
        case RelationValue.Const(name) =>
          assert(largeCountries.contains(name),
            s"$name in satisfying but not in largeCountries")
        case other => fail(s"Unexpected element type: $other")
    }

    // Every element in range NOT in satisfying should fail the predicate
    (output.rangeElements -- output.satisfyingElements).foreach { elem =>
      elem match
        case RelationValue.Const(name) =>
          assert(!largeCountries.contains(name),
            s"$name not in satisfying but is in largeCountries")
        case _ => () // Var/Func would fail the predicate → not in satisfying
    }

  test("DSL: evaluateWithOutput on String domain — satisfying elements are correct"):
    val q = Query
      .quantifier(VagueQuantifier.several)
      .over("employee")
      .where[String](Predicates.relatedTo(strSource, "works_in", "Engineering"))

    val output = q.evaluateWithOutput(strSource).toOption.get
    assertEquals(output.satisfyingElements, Set("Alice", "Bob", "Charlie", "Hank"))
    assertEquals(output.rangeElements.size, 10)

  // ══════════════════════════════════════════════════════════════════
  //  Section 10: Composing multiple queries
  // ══════════════════════════════════════════════════════════════════

  test("DSL: multiple queries over same source"):
    val src = rvSource

    val large = Query.quantifier(VagueQuantifier.aboutHalf).over("country")
      .whereConst(largeCountries.contains).evaluate(src)
    val coastal = Query.quantifier(VagueQuantifier.several).over("country")
      .whereConst(coastalCountries.contains).evaluate(src)
    val wealthy = Query.quantifier(VagueQuantifier.few).over("country")
      .whereConst(wealthyCountries.contains).evaluate(src)

    assert(large.isRight && coastal.isRight && wealthy.isRight)
    // 4/8 = 0.5 — about half ✓
    assertEquals(large.toOption.get.satisfied, true)
    // 3/8 = 0.375 — several (≥ 30%) ✓
    assertEquals(coastal.toOption.get.satisfied, true)
    // 2/8 = 0.25 — few (≤ 30%) ✓
    assertEquals(wealthy.toOption.get.satisfied, true)

  test("DSL: combining predicates with logical operators"):
    // "About half of countries are large AND coastal"
    val q = Query
      .quantifier(VagueQuantifier.aboutQuarter)
      .over("country")
      .whereConst(name => largeCountries.contains(name) && coastalCountries.contains(name))

    val result = q.evaluate(rvSource)
    assert(result.isRight)
    // Large ∩ Coastal = {France, Italy, Spain} → 3/8 = 0.375
    // aboutQuarter accepts [0.15, 0.35] → 0.375 NOT in range
    assertEquals(result.toOption.get.proportion, 0.375)
    assertEquals(result.toOption.get.satisfied, false)

  test("DSL: negated predicate"):
    // "Most countries are NOT wealthy"
    val q = Query
      .quantifier(VagueQuantifier.most)
      .over("country")
      .whereConst(name => !wealthyCountries.contains(name))

    val result = q.evaluate(rvSource)
    assert(result.isRight)
    // 6/8 = 0.75 — satisfies "most" (≥ 70%) ✓
    assertEquals(result.toOption.get.proportion, 0.75)
    assertEquals(result.toOption.get.satisfied, true)

  // ══════════════════════════════════════════════════════════════════
  //  Section 11: Full pipeline — resolve then evaluate
  // ══════════════════════════════════════════════════════════════════

  test("DSL: explicit resolve().evaluate() pipeline"):
    val q = Query
      .quantifier(VagueQuantifier.many)
      .over("employee")
      .where[String](Predicates.inRelation(strSource, "satisfied"))

    val resolved = q.resolve(strSource)
    assert(resolved.isRight, s"resolve failed: $resolved")

    val rq = resolved.toOption.get
    assertEquals(rq.elements.size, 10)
    assertEquals(rq.quantifier, VagueQuantifier.many)

    val result = rq.evaluate()
    assertEquals(result.proportion, 0.7)
    assertEquals(result.satisfied, true) // 0.7 ≥ 0.5

  test("DSL: explicit resolve().evaluateWithOutput() pipeline"):
    val q = Query
      .quantifier(VagueQuantifier.many)
      .over("employee")
      .where[String](Predicates.inRelation(strSource, "satisfied"))

    val resolved = q.resolve(strSource).toOption.get
    val output = resolved.evaluateWithOutput()

    assertEquals(output.satisfyingElements.size, 7)
    assertEquals(output.rangeElements.size, 10)
    assert(output.satisfyingElements.subsetOf(output.rangeElements))

  // ══════════════════════════════════════════════════════════════════
  //  Section 12: VagueQueryResult field access
  // ══════════════════════════════════════════════════════════════════

  test("DSL: VagueQueryResult exposes all statistics"):
    val result = Query
      .quantifier(VagueQuantifier.aboutHalf)
      .over("country")
      .whereConst(largeCountries.contains)
      .evaluate(rvSource)
      .toOption.get

    assertEquals(result.domainSize, 8)
    assertEquals(result.sampleSize, 8) // exact params → all sampled
    assertEquals(result.satisfyingCount, 4)
    assertEquals(result.proportion, 0.5)
    assertEquals(result.satisfied, true)

  test("DSL: EvaluationOutput convenience delegates"):
    val output = Query
      .quantifier(VagueQuantifier.aboutHalf)
      .over("country")
      .whereConst(largeCountries.contains)
      .evaluateWithOutput(rvSource)
      .toOption.get

    // Convenience accessors delegate to result
    assertEquals(output.satisfied, output.result.satisfied)
    assertEquals(output.proportion, output.result.proportion)
