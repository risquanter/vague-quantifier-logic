package fol.result

import fol.quantifier.VagueQuantifier
import fol.sampling.ProportionEstimate
import fol.logic.{Quantifier => LogicQuantifier}

/** Unified result type for all vague quantifier evaluations.
  * 
  * Replaces the previous split between:
  * - `fol.semantics.QueryResult` (flat 5-field, no CI)
  * - `fol.query.QueryResult` (wrapper around QuantifierResult)
  * - `fol.quantifier.QuantifierResult` (inner result type)
  * 
  * This single type is returned by all evaluation modes:
  * - Exact evaluation (full population enumeration)
  * - Sampled evaluation (statistical sampling with HDR PRNG)
  * - String-parsed evaluation (via VagueSemantics facade)
  * 
  * See docs/ADR-001.md §Decision.
  * 
  * @param satisfied   Whether the quantifier accepts the observed proportion
  * @param proportion  Observed or estimated proportion in [0, 1]
  * @param confidenceInterval  Wilson score CI (exact mode: degenerate [p, p])
  * @param quantifier  The vague quantifier that was evaluated
  * @param domainSize  Total population/domain size
  * @param sampleSize  Number of elements actually examined (= domainSize for exact)
  * @param satisfyingCount  Number of elements satisfying the predicate
  * @param estimate    Full ProportionEstimate with margin of error and params
  */
case class VagueQueryResult(
  satisfied: Boolean,
  proportion: Double,
  confidenceInterval: (Double, Double),
  quantifier: VagueQuantifier,
  domainSize: Int,
  sampleSize: Int,
  satisfyingCount: Int,
  estimate: ProportionEstimate
):
  /** Check if result is statistically significant.
    * 
    * Result is significant when the entire confidence interval
    * falls within (if satisfied) or outside (if not satisfied)
    * the quantifier's acceptance range.
    * 
    * For exact evaluation (sampleSize == domainSize), 
    * the CI is degenerate so this always returns true.
    */
  def isSignificant: Boolean =
    val (qLower, qUpper) = LogicQuantifier.acceptanceRange(quantifier.toQuantifier)
    val (lower, upper) = confidenceInterval
    
    if satisfied then
      // Entire CI must be inside acceptance range
      lower >= qLower && upper <= qUpper
    else
      // Entire CI must be outside acceptance range
      upper < qLower || lower > qUpper
  
  /** Whether this was an exact (non-sampled) evaluation. */
  def isExact: Boolean = sampleSize == domainSize
  
  /** Human-readable summary. */
  def summary: String =
    val significanceNote = if isSignificant then "" else " (not statistically significant)"
    val modeNote = if isExact then "exact" else s"sampled $sampleSize of $domainSize"
    s"${if satisfied then "✓" else "✗"} ${quantifier.describe}: " +
    s"${(proportion * 100).toInt}% " +
    s"[${(confidenceInterval._1 * 100).toInt}%-${(confidenceInterval._2 * 100).toInt}%] " +
    s"($modeNote)$significanceNote"

object VagueQueryResult:
  
  /** Create from an existing ProportionEstimate (sampling or exact).
    *
    * Uses `estimate.successes` for `satisfyingCount` — no rounding needed.
    *
    * @param quantifier  The vague quantifier to evaluate against
    * @param estimate    Proportion estimate (carries successes, CI, etc.)
    * @param domainSize  Total population size (may differ from sampleSize)
    */
  def fromEstimate(
    quantifier: VagueQuantifier,
    estimate: ProportionEstimate,
    domainSize: Int
  ): VagueQueryResult =
    VagueQueryResult(
      satisfied = quantifier.evaluate(estimate.proportion),
      proportion = estimate.proportion,
      confidenceInterval = estimate.confidenceInterval,
      quantifier = quantifier,
      domainSize = domainSize,
      sampleSize = estimate.sampleSize,
      satisfyingCount = estimate.successes,
      estimate = estimate
    )
