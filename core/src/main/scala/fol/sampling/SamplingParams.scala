package fol.sampling

/** Statistical precision parameters for proportion estimation.
  *
  * Controls sample-size calculation and confidence intervals.
  * Separated from PRNG configuration (see [[HDRConfig]]) following
  * the single-responsibility principle.
  *
  * Based on Fermüller, Hofer, and Ortiz (2017).
  * "Querying with Vague Quantifiers Using Probabilistic Semantics"
  *
  * @param epsilon Maximum error tolerance (ε) for proportion estimates.
  *                Smaller values require larger sample sizes.
  *                Default: 0.1 (10% error bound)
  *
  * @param alpha Significance level (α) for confidence intervals.
  *              Probability that true proportion falls outside confidence interval.
  *              Default: 0.05 (95% confidence)
  */
case class SamplingParams(
  epsilon: Double = 0.1,
  alpha: Double = 0.05
):
  require(epsilon > 0 && epsilon < 1, s"Epsilon must be in (0,1), got $epsilon")
  require(alpha > 0 && alpha < 1, s"Alpha must be in (0,1), got $alpha")

  /** Z-score for the given significance level.
    *
    * Pure-Scala Acklam rational approximation of inverse normal CDF.
    * Maximum relative error < 1.15 × 10⁻⁹ for p ∈ (0, 1).
    *
    * For two-tailed confidence intervals:
    * - α = 0.05 → z ≈ 1.960 (95% confidence)
    * - α = 0.01 → z ≈ 2.576 (99% confidence)
    * - α = 0.10 → z ≈ 1.645 (90% confidence)
    *
    * Reference: Peter J. Acklam, "An algorithm for computing the
    * inverse normal cumulative distribution function" (2010).
    */
  def zScore: Double =
    val p = 1.0 - alpha / 2.0
    NormalApprox.inverseCDF(p)
end SamplingParams

object SamplingParams:
  /** Default: 10% error, 95% confidence. */
  val default: SamplingParams = SamplingParams()

  /** Conservative: 5% error, 99% confidence. */
  val conservative: SamplingParams = SamplingParams(
    epsilon = 0.05,
    alpha = 0.01
  )

  /** Fast: 15% error, 90% confidence. */
  val fast: SamplingParams = SamplingParams(
    epsilon = 0.15,
    alpha = 0.10
  )

  /** Full-population evaluation — the degenerate case of sampling.
    *
    * With ε = 1e-6 and α = 0.01 (z ≈ 2.576), the sample size formula
    * computes n₀ = z² / (4ε²) ≈ 1.66 × 10¹². After FPC correction,
    * n = N for any population under ~1.66 trillion elements.
    *
    * The HDR sampler short-circuits when n ≥ |population|, returning
    * the full set. No separate "exact" code path is needed — this
    * IS exact evaluation via the paper's Sampled Answer Semantics
    * (Definition 2) with n = N.
    *
    * '''Service-level usage (register):'''
    * {{{val params = if exactMode then SamplingParams.exact
    *               else SamplingParams.default
    * val output = VagueSemantics.evaluate(query, source, answerTuple, params, config)}}}
    *
    * See EVALUATION-PATH-UNIFICATION.md §D11 for design rationale.
    */
  val exact: SamplingParams = SamplingParams(
    epsilon = 1e-6,
    alpha = 0.01
  )

/** HDR PRNG configuration — the 4-layer seed hierarchy from ADR-003.
  *
  * Maps directly to the HDR generate call:
  * {{{
  * HDR.generate(counter, entityId, varId, seed3, seed4)
  * }}}
  *
  * Different use cases drive different random streams by varying
  * entity/variable IDs:
  *
  * '''Register simulation (ADR-003):'''
  * {{{
  * // Layer 2: entity isolation — each risk node gets its own stream
  * entityId = leaf.id.hashCode.toLong
  *
  * // Layer 3: variable isolation — occurrence vs loss are uncorrelated
  * occurrenceVarId = riskHash + 1000L
  * lossVarId       = riskHash + 2000L
  *
  * // Layer 4: global seeds from SimulationConfig
  * seed3 = config.defaultSeed3
  * seed4 = config.defaultSeed4
  * }}}
  *
  * '''Vague quantifier evaluation:'''
  * {{{
  * // Layer 2: entity isolation — each tree/workspace gets its own stream
  * entityId = treeId.hashCode.toLong
  *
  * // Layer 3: variable isolation — each query gets its own stream
  * varId = queryHash
  *
  * // Layer 4: global seeds from SimulationConfig
  * seed3 = config.defaultSeed3
  * seed4 = config.defaultSeed4
  * }}}
  *
  * @param entityId Entity identifier — isolates random streams per entity.
  *                 In register: hash of risk node ID.
  *                 In query evaluation: hash of tree/workspace ID.
  * @param varId    Variable identifier — isolates streams per use case.
  *                 In register: occurrence (+1000) vs loss (+2000).
  *                 In query evaluation: hash of the query.
  * @param seed3    Global seed 3 — from application config (REGISTER_SEED3).
  *                 Changing this changes ALL random streams.
  * @param seed4    Global seed 4 — from application config (REGISTER_SEED4).
  *                 Second global axis for additional variation.
  */
case class HDRConfig(
  entityId: Long = 0L,
  varId: Long = 0L,
  seed3: Long = 0L,
  seed4: Long = 0L
)

object HDRConfig:
  /** Default: all zeros. Suitable for tests or when caller doesn't need
    * entity/variable isolation. */
  val default: HDRConfig = new HDRConfig()

  /** Testing config — deterministic, all zeros. */
  val forTesting: HDRConfig = new HDRConfig()

