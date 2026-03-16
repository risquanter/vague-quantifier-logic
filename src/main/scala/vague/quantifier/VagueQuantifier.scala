package vague.quantifier

import vague.sampling.{ProportionEstimator, ProportionEstimate, SamplingParams, HDRConfig}
import scala.reflect.ClassTag

/** Vague quantifier for proportional reasoning over populations.
  * 
  * Based on Fermüller, Hofer, and Ortiz (2017).
  * "Querying with Vague Quantifiers Using Probabilistic Semantics"
  * Section 5.2: Vague Quantifiers and Their Semantics
  * 
  * Implements Definition 2 (Sampled Answer Semantics):
  * A quantifier Q evaluates to true if the estimated proportion
  * of elements satisfying the predicate falls within Q's acceptance range.
  * 
  * Three types of vague quantifiers:
  * - Approximately (Q[~#]): proportion ≈ target (e.g., "about half")
  * - AtLeast (Q[≥]): proportion ≥ threshold (e.g., "most", "many")
  * - AtMost (Q[≤]): proportion ≤ threshold (e.g., "few", "hardly any")
  */
sealed trait VagueQuantifier:
  
  /** Evaluate whether the proportion satisfies this quantifier.
    * 
    * @param proportion Estimated proportion in [0, 1]
    * @return true if proportion falls within quantifier's acceptance range
    */
  def evaluate(proportion: Double): Boolean
  
  /** Evaluate quantifier on a population with sampling.
    * 
    * @param population Full population
    * @param predicate Condition to test
    * @param params Sampling parameters
    * @return Evaluation result with proportion estimate
    */
  def evaluateWithSampling[A](
    population: Set[A],
    predicate: A => Boolean,
    params: SamplingParams = SamplingParams(),
    config: HDRConfig = HDRConfig.default
  )(using ClassTag[A]): QuantifierResult =
    val estimate = ProportionEstimator.estimateWithSampling(
      population,
      predicate,
      params,
      config
    )
    
    QuantifierResult(
      satisfied = evaluate(estimate.proportion),
      proportion = estimate.proportion,
      confidenceInterval = estimate.confidenceInterval,
      quantifier = this,
      estimate = estimate
    )
  
  /** Evaluate quantifier on exact proportion (no sampling).
    * 
    * @param population Full population
    * @param predicate Condition to test
    * @return Evaluation result with exact proportion
    */
  def evaluateExact[A](
    population: Set[A],
    predicate: A => Boolean
  ): QuantifierResult =
    val estimate = ProportionEstimator.exactEstimate(
      population,
      predicate,
      SamplingParams()
    )
    
    QuantifierResult(
      satisfied = evaluate(estimate.proportion),
      proportion = estimate.proportion,
      confidenceInterval = estimate.confidenceInterval,
      quantifier = this,
      estimate = estimate
    )
  
  /** Human-readable description of this quantifier. */
  def describe: String

/** Approximately quantifier Q[~#].
  * 
  * Accepts proportions close to a target value within tolerance.
  * Example: "about half" = Approximately(0.5, 0.1) accepts [0.4, 0.6]
  * 
  * @param target Target proportion in [0, 1]
  * @param tolerance Acceptable deviation from target (default: 0.1 = ±10%)
  */
case class Approximately(target: Double, tolerance: Double = 0.1) extends VagueQuantifier:
  require(target >= 0.0 && target <= 1.0, s"Target must be in [0, 1], got $target")
  require(tolerance >= 0.0 && tolerance <= 1.0, s"Tolerance must be in [0, 1], got $tolerance")
  
  /** Acceptance range [target - tolerance, target + tolerance] ∩ [0, 1] */
  val lowerBound: Double = math.max(0.0, target - tolerance)
  val upperBound: Double = math.min(1.0, target + tolerance)
  
  def evaluate(proportion: Double): Boolean =
    proportion >= lowerBound && proportion <= upperBound
  
  def describe: String = 
    s"approximately ${(target * 100).toInt}% (±${(tolerance * 100).toInt}%)"

/** At-least quantifier Q[≥].
  * 
  * Accepts proportions greater than or equal to threshold.
  * Example: "most" = AtLeast(0.7) accepts [0.7, 1.0]
  * 
  * @param threshold Minimum acceptable proportion in [0, 1]
  */
case class AtLeast(threshold: Double) extends VagueQuantifier:
  require(threshold >= 0.0 && threshold <= 1.0, s"Threshold must be in [0, 1], got $threshold")
  
  def evaluate(proportion: Double): Boolean =
    proportion >= threshold
  
  def describe: String = 
    s"at least ${(threshold * 100).toInt}%"

/** At-most quantifier Q[≤].
  * 
  * Accepts proportions less than or equal to threshold.
  * Example: "few" = AtMost(0.3) accepts [0, 0.3]
  * 
  * @param threshold Maximum acceptable proportion in [0, 1]
  */
case class AtMost(threshold: Double) extends VagueQuantifier:
  require(threshold >= 0.0 && threshold <= 1.0, s"Threshold must be in [0, 1], got $threshold")
  
  def evaluate(proportion: Double): Boolean =
    proportion <= threshold
  
  def describe: String = 
    s"at most ${(threshold * 100).toInt}%"

/** Result of evaluating a vague quantifier. */
case class QuantifierResult(
  satisfied: Boolean,
  proportion: Double,
  confidenceInterval: (Double, Double),
  quantifier: VagueQuantifier,
  estimate: ProportionEstimate
):
  /** Check if result is statistically significant.
    * 
    * Result is significant if the entire confidence interval
    * falls within (satisfied) or outside (not satisfied) the
    * quantifier's acceptance range.
    */
  def isSignificant: Boolean = quantifier match
    case Approximately(target, tolerance) =>
      val (lower, upper) = confidenceInterval
      val qLower = math.max(0.0, target - tolerance)
      val qUpper = math.min(1.0, target + tolerance)
      
      if satisfied then
        // Entire CI must be within acceptance range
        lower >= qLower && upper <= qUpper
      else
        // Entire CI must be outside acceptance range
        upper < qLower || lower > qUpper
    
    case AtLeast(threshold) =>
      val (lower, upper) = confidenceInterval
      if satisfied then
        lower >= threshold  // Lower bound meets threshold
      else
        upper < threshold   // Upper bound below threshold
    
    case AtMost(threshold) =>
      val (lower, upper) = confidenceInterval
      if satisfied then
        upper <= threshold  // Upper bound meets threshold
      else
        lower > threshold   // Lower bound exceeds threshold
  
  /** Human-readable summary of result. */
  def summary: String =
    val significanceNote = if isSignificant then "" else " (not statistically significant)"
    s"${if satisfied then "✓" else "✗"} ${quantifier.describe}: " +
    s"${(proportion * 100).toInt}% " +
    s"[${(confidenceInterval._1 * 100).toInt}%-${(confidenceInterval._2 * 100).toInt}%]" +
    significanceNote

/** Common vague quantifiers with sensible defaults. */
object VagueQuantifier:
  
  // Approximately quantifiers (Q[~#])
  val aboutHalf: Approximately = Approximately(0.5, 0.1)          // [0.4, 0.6]
  val aboutQuarter: Approximately = Approximately(0.25, 0.1)      // [0.15, 0.35]
  val aboutThreeQuarters: Approximately = Approximately(0.75, 0.1) // [0.65, 0.85]
  
  // At-least quantifiers (Q[≥])
  val most: AtLeast = AtLeast(0.7)           // ≥ 70%
  val many: AtLeast = AtLeast(0.5)           // ≥ 50%
  val several: AtLeast = AtLeast(0.3)        // ≥ 30%
  val some: AtLeast = AtLeast(0.1)           // ≥ 10%
  val almostAll: AtLeast = AtLeast(0.9)      // ≥ 90%
  
  // At-most quantifiers (Q[≤])
  val few: AtMost = AtMost(0.3)              // ≤ 30%
  val hardlyAny: AtMost = AtMost(0.1)        // ≤ 10%
  val almostNone: AtMost = AtMost(0.05)      // ≤ 5%
  val notMany: AtMost = AtMost(0.4)          // ≤ 40%
  
  /** Create custom approximately quantifier. */
  def approximately(percent: Double, tolerancePercent: Double = 10.0): Approximately =
    Approximately(percent / 100.0, tolerancePercent / 100.0)
  
  /** Create custom at-least quantifier. */
  def atLeast(percent: Double): AtLeast =
    AtLeast(percent / 100.0)
  
  /** Create custom at-most quantifier. */
  def atMost(percent: Double): AtMost =
    AtMost(percent / 100.0)
