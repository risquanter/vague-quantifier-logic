package fol.query

import fol.datastore.RelationValue
import fol.quantifier.VagueQuantifier
import fol.sampling.{SamplingParams, HDRConfig}
import fol.result.{VagueQueryResult, EvaluationOutput}

class ResolvedQuerySpec extends munit.FunSuite:

  // Helper to create RelationValue constants
  def const(s: String): RelationValue = RelationValue.Const(s)

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

    val output: EvaluationOutput = rq.evaluateWithOutput()

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
