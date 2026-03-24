package fol.query

import munit.FunSuite
import fol.datastore.{KnowledgeBase, KnowledgeSource, Relation, RelationValue, RelationTuple}
import fol.quantifier.VagueQuantifier
import fol.sampling.{SamplingParams, HDRConfig}
import fol.result.{VagueQueryResult, EvaluationOutput}
import fol.error.QueryError

class UnresolvedQuerySpec extends FunSuite:

  // ---------- Test fixtures ----------

  def const(s: String): RelationValue = RelationValue.Const(s)
  def unary(s: String): RelationTuple[RelationValue] = RelationTuple(List(const(s)))

  val countries: Set[String] = Set(
    "France", "Germany", "Italy", "Spain",
    "Luxembourg", "Switzerland", "Belgium", "Austria"
  )

  val largeCountries: Set[String] = Set("France", "Germany", "Italy", "Spain")

  def createKB(): KnowledgeBase[RelationValue] =
    KnowledgeBase[RelationValue](Map.empty, Map.empty)
      .addRelation(Relation("country", 1))
      .addRelation(Relation("large", 1))
      .addFacts("country", countries.map(unary))
      .addFacts("large", largeCountries.map(unary))

  def source: KnowledgeSource[RelationValue] = KnowledgeSource.fromKnowledgeBase(createKB())

  /** Query: "most countries are large" — 4/8 = 0.5, does NOT satisfy "most" (0.7) */
  def mostCountriesLarge: UnresolvedQuery =
    Query
      .quantifier(VagueQuantifier.most)
      .over("country")
      .whereConst(largeCountries.contains)

  /** Query: "about half of countries are large" — 4/8 = 0.5, satisfies ~50% ±10% */
  def aboutHalfCountriesLarge: UnresolvedQuery =
    Query
      .quantifier(VagueQuantifier.aboutHalf)
      .over("country")
      .whereConst(largeCountries.contains)

  /** Query over a non-existent relation — yields empty range */
  def queryOverNonexistent: UnresolvedQuery =
    Query
      .quantifier(VagueQuantifier.most)
      .over("nonexistent")
      .whereConst(_ => true)

  // ---------- Default params ----------

  test("UnresolvedQuery defaults to SamplingParams.exact"):
    val q = aboutHalfCountriesLarge
    assertEquals(q.params, SamplingParams.exact)

  test("UnresolvedQuery defaults to HDRConfig.default"):
    val q = aboutHalfCountriesLarge
    assertEquals(q.hdrConfig, HDRConfig.default)

  // ---------- resolve ----------

  test("resolve returns Right with correct elements"):
    val result = aboutHalfCountriesLarge.resolve(source)
    assert(result.isRight, s"Expected Right, got $result")
    val resolved = result.toOption.get
    assertEquals(resolved.elements.size, 8)
    assertEquals(resolved.quantifier, VagueQuantifier.aboutHalf)
    assertEquals(resolved.params, SamplingParams.exact)

  test("resolve returns Left(EvaluationError) for unknown relation"):
    val result = queryOverNonexistent.resolve(source)

    result match
      case Left(err: QueryError.EvaluationError) =>
        assert(err.message.contains("nonexistent"))
        assertEquals(err.phase, "domain_resolution")
      case Left(other) =>
        fail(s"Expected EvaluationError, got $other")
      case Right(_) =>
        fail("Expected Left for nonexistent relation")

  // ---------- evaluate ----------

  test("evaluate returns Right(VagueQueryResult)"):
    val result = aboutHalfCountriesLarge.evaluate(source)
    assert(result.isRight, s"Expected Right, got $result")
    assertEquals(result.toOption.get.proportion, 0.5)
    assertEquals(result.toOption.get.satisfied, true) // 0.5 within [0.4, 0.6]

  test("evaluate unsatisfied query"):
    val result = mostCountriesLarge.evaluate(source)
    assert(result.isRight)
    assertEquals(result.toOption.get.proportion, 0.5)
    assertEquals(result.toOption.get.satisfied, false) // 0.5 < 0.7

  test("evaluate returns Left for unknown relation"):
    val result = queryOverNonexistent.evaluate(source)

    result match
      case Left(err: QueryError.EvaluationError) =>
        assert(err.message.contains("nonexistent"))
        assertEquals(err.phase, "domain_resolution")
      case Left(other) =>
        fail(s"Expected EvaluationError, got $other")
      case Right(_) =>
        fail("Expected Left for nonexistent relation")

  // ---------- evaluateWithOutput ----------

  test("evaluateWithOutput returns Right with element sets"):
    val result = aboutHalfCountriesLarge.evaluateWithOutput(source)
    assert(result.isRight, s"Expected Right, got $result")
    val output = result.toOption.get

    assertEquals(output.result.proportion, 0.5)
    assertEquals(output.rangeElements.size, 8)
    assertEquals(output.satisfyingElements.size, 4)
    assert(output.satisfyingElements.subsetOf(output.rangeElements))

  test("evaluateWithOutput convenience delegates match result"):
    val result = aboutHalfCountriesLarge.evaluateWithOutput(source)
    val output = result.toOption.get
    assertEquals(output.satisfied, output.result.satisfied)
    assertEquals(output.proportion, output.result.proportion)

  test("evaluateWithOutput returns Left for unknown relation"):
    val result = queryOverNonexistent.evaluateWithOutput(source)

    result match
      case Left(_: QueryError.EvaluationError) => () // pass
      case Left(other)  => fail(s"Expected EvaluationError, got $other")
      case Right(_)     => fail("Expected Left for nonexistent relation")

  // ---------- KnowledgeSource extension methods ----------

  test("source.execute delegates to evaluate"):
    val query = aboutHalfCountriesLarge
    val direct = query.evaluate(source)
    val ext = source.execute(query)

    assertEquals(ext.isRight, direct.isRight)
    assertEquals(ext.toOption.get.proportion, direct.toOption.get.proportion)
    assertEquals(ext.toOption.get.satisfied, direct.toOption.get.satisfied)

  test("source.execute returns Left for empty range"):
    val query = queryOverNonexistent
    val result = source.execute(query)
    assert(result.isLeft)

  test("source.executeWithOutput delegates to evaluateWithOutput"):
    val query = aboutHalfCountriesLarge
    val direct = query.evaluateWithOutput(source)
    val ext = source.executeWithOutput(query)

    assertEquals(ext.isRight, direct.isRight)
    assertEquals(
      ext.toOption.get.rangeElements,
      direct.toOption.get.rangeElements
    )
    assertEquals(
      ext.toOption.get.satisfyingElements,
      direct.toOption.get.satisfyingElements
    )

  test("source.executeWithOutput returns Left for empty range"):
    val query = queryOverNonexistent
    val result = source.executeWithOutput(query)
    assert(result.isLeft)

  // ---------- Cross-API consistency ----------

  test("evaluate and evaluateWithOutput produce consistent results"):
    val query = aboutHalfCountriesLarge

    val evalResult = query.evaluate(source)
    val outputResult = query.evaluateWithOutput(source)

    assert(evalResult.isRight)
    assert(outputResult.isRight)

    val eval = evalResult.toOption.get
    val output = outputResult.toOption.get

    // Both paths see the same proportion and satisfaction
    assertEquals(output.result.proportion, eval.proportion)
    assertEquals(output.result.satisfied, eval.satisfied)

  test("all API variants produce consistent failures on empty domain"):
    val query = queryOverNonexistent

    val resolveResult = query.resolve(source)
    val evalResult = query.evaluate(source)
    val outputResult = query.evaluateWithOutput(source)

    assert(resolveResult.isLeft)
    assert(evalResult.isLeft)
    assert(outputResult.isLeft)

  // ---------- Builder DSL ----------

  test("Query builder with whereConst"):
    val query = Query
      .quantifier(VagueQuantifier.few)
      .over("country")
      .whereConst(name => name.startsWith("L"))

    val result = query.evaluate(source)
    assert(result.isRight)
    // Luxembourg starts with L → 1/8 = 0.125 which is ≤ 0.3 → satisfies "few"
    assertEquals(result.toOption.get.proportion, 0.125)
    assertEquals(result.toOption.get.satisfied, true)

  test("Query builder with where and RelationValue predicate"):
    val query = Query
      .quantifier(VagueQuantifier.most)
      .over("country")
      .where(rv => largeCountries.contains(rv match
        case RelationValue.Const(n) => n
        case _ => ""
      ))

    val result = query.evaluate(source)
    assert(result.isRight)
    assertEquals(result.toOption.get.proportion, 0.5)

  test("Query builder with custom sampling params"):
    val params = SamplingParams(epsilon = 0.1, alpha = 0.05)
    val config = HDRConfig(seed3 = 42)

    val query = Query
      .quantifier(VagueQuantifier.most)
      .over("country")
      .whereConst(largeCountries.contains, params, config)

    assertEquals(query.params, params)
    assertEquals(query.hdrConfig, config)

    val result = query.evaluate(source)
    assert(result.isRight)
    // Small population (8) — sampler uses all elements anyway
    assertEquals(result.toOption.get.proportion, 0.5)

  test("Query builder overActiveDomain"):
    val query = Query
      .quantifier(VagueQuantifier.most)
      .overActiveDomain
      .whereConst(largeCountries.contains)

    val resolved = query.resolve(source)
    assert(resolved.isRight)
    // Active domain includes all constants from all relations
    assert(resolved.toOption.get.elements.size >= 8)
