package fol.query

import fol.quantifier.VagueQuantifier
import fol.sampling.{SamplingParams, HDRConfig, HDRSampler, SampleSizeCalculator, ProportionEstimator}
import fol.result.{VagueQueryResult, EvaluationOutput}
import scala.reflect.ClassTag

/** Resolved vague quantifier query — the shared IL.
  *
  * Both entry points converge here:
  *   - String path: ParsedQuery → compile → ResolvedQuery
  *   - Typed DSL:   UnresolvedQuery → resolve → ResolvedQuery
  *
  * All fields are materialized — no KnowledgeSource dependency.
  *
  * '''One code path — no boolean toggle (D11).'''
  * `SamplingParams` controls whether evaluation is exact or sampled.
  * With `SamplingParams.exact` (ε = 1e-6), the sample size calculator
  * returns n = N, the sampler short-circuits to return the full set,
  * and the result is identical to exhaustive enumeration. "Exact" IS
  * the degenerate case of "sampled" per Definition 2 of the paper.
  *
  * See docs/ADR-001.md §Decision.
  *
  * @tparam D           Domain element type
  * @param quantifier   The vague quantifier to evaluate
  * @param elements     Materialized domain (D_R from the paper)
  * @param predicate    Scope predicate bound over elements
  * @param params       Sampling parameters — controls precision.
  *                     Use `SamplingParams.exact` for full enumeration,
  *                     `SamplingParams.default` for statistical sampling.
  * @param hdrConfig    HDR PRNG configuration
  */
case class ResolvedQuery[D: ClassTag](
  quantifier: VagueQuantifier,
  elements: Set[D],
  predicate: D => Boolean,
  params: SamplingParams = SamplingParams.exact,
  hdrConfig: HDRConfig = HDRConfig.default
):

  /** Evaluate this query (statistics only).
    *
    * One code path: `ProportionEstimator.estimateWithSampling` handles
    * both exact and sampled modes. When `params` forces n = N
    * (e.g. `SamplingParams.exact`), the sampler returns the full
    * population and the result is exact. No boolean toggle needed.
    *
    * @return VagueQueryResult with satisfaction, proportion, CI
    */
  def evaluate(): VagueQueryResult =
    if elements.isEmpty then
      ResolvedQuery.emptyResult(quantifier)
    else
      val estimate = ProportionEstimator.estimateWithSampling(
        elements, predicate, params, hdrConfig
      )
      VagueQueryResult.fromEstimate(quantifier, estimate, elements.size)

  /** Evaluate this query with element sets (for tree highlighting).
    *
    * Single code path matching `evaluate()`:
    * 1. Calculator determines n from params (n = N when ε is tiny)
    * 2. HDR sampler draws n elements (short-circuits to full set when n ≥ N)
    * 3. Filter sample by predicate → satisfying elements
    * 4. Build estimate from count (no re-iteration)
    *
    * When n = N, `satisfyingElements` is the complete set of matches.
    * When n < N, `satisfyingElements` contains sample-only matches (D2B).
    *
    * @return EvaluationOutput with result + element sets
    */
  def evaluateWithOutput(): EvaluationOutput[D] =
    if elements.isEmpty then
      EvaluationOutput(
        result = ResolvedQuery.emptyResult(quantifier),
        rangeElements = Set.empty,
        satisfyingElements = Set.empty
      )
    else
      // Step 1: calculator decides sample size from params
      val n = SampleSizeCalculator.calculateSampleSize(elements.size, params)

      // Step 2: sampler draws n elements (returns full set when n >= N)
      val sampler = HDRSampler[D](hdrConfig)
      val sample = sampler.sample(elements, n)

      // Step 3: filter by predicate
      val satisfying = sample.filter(predicate)

      // Step 4: build estimate from pre-counted integers
      val estimate = ProportionEstimator.estimateFromCount(
        successes = satisfying.size,
        sampleSize = sample.size,
        params = params
      )
      val result = VagueQueryResult.fromEstimate(quantifier, estimate, elements.size)

      EvaluationOutput(
        result = result,
        rangeElements = elements,
        satisfyingElements = satisfying
      )

object ResolvedQuery:

  /** Create an empty result for queries against empty ranges. */
  private def emptyResult(quantifier: VagueQuantifier): VagueQueryResult =
    import fol.sampling.ProportionEstimate
    VagueQueryResult(
      satisfied = false,
      proportion = 0.0,
      confidenceInterval = (0.0, 1.0),
      quantifier = quantifier,
      domainSize = 0,
      sampleSize = 0,
      satisfyingCount = 0,
      estimate = ProportionEstimate.empty()
    )
