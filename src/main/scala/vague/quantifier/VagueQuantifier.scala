package vague.quantifier

import vague.sampling.{ProportionEstimator, ProportionEstimate, SamplingParams, HDRConfig}
import vague.logic.{Quantifier => LogicQuantifier}
import vague.result.VagueQueryResult
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
  * 
  * This is the ergonomic Scala API (percentage-based). Internally,
  * acceptance checking delegates to [[vague.logic.Quantifier.accepts]]
  * which implements the paper's Definition 2 with ratio-based notation.
  * Use [[toQuantifier]] to convert to the canonical ratio form.
  */
sealed trait VagueQuantifier:
  
  /** Convert to the canonical ratio-based [[vague.logic.Quantifier]].
    * 
    * The ratio form (k/n) is what the parser produces and what the paper
    * uses. This conversion enables the typed DSL and the string-parsed
    * path to share a single acceptance-checking implementation.
    */
  def toQuantifier: LogicQuantifier
  
  /** Evaluate whether the proportion satisfies this quantifier.
    * 
    * Delegates to [[vague.logic.Quantifier.accepts]] using the
    * quantifier's own tolerance as epsilon.
    * 
    * @param proportion Estimated proportion in [0, 1]
    * @return true if proportion falls within quantifier's acceptance range
    */
  def evaluate(proportion: Double): Boolean =
    val q = toQuantifier
    LogicQuantifier.accepts(q, proportion, LogicQuantifier.tolerance(q))
  
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
  )(using ClassTag[A]): VagueQueryResult =
    val estimate = ProportionEstimator.estimateWithSampling(
      population, predicate, params, config
    )
    VagueQueryResult.fromEstimate(this, estimate, population.size)
  
  /** Evaluate quantifier on exact proportion (no sampling).
    * 
    * @param population Full population
    * @param predicate Condition to test
    * @return Evaluation result with exact proportion
    */
  def evaluateExact[A](
    population: Set[A],
    predicate: A => Boolean
  ): VagueQueryResult =
    val estimate = ProportionEstimator.exactEstimate(
      population, predicate, SamplingParams()
    )
    VagueQueryResult.fromEstimate(this, estimate, population.size)
  
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
  
  def toQuantifier: LogicQuantifier =
    // Convert percentage target to ratio k/n with denominator 1000 for precision
    val k = math.round(target * 1000).toInt
    LogicQuantifier.About(k, 1000, tolerance)
  
  def describe: String = 
    s"approximately ${(target * 100).toInt}% (±${(tolerance * 100).toInt}%)"

/** At-least quantifier Q[≥].
  * 
  * Accepts proportions greater than or equal to threshold.
  * Example: "most" = AtLeast(0.7) accepts [0.7, 1.0]
  * 
  * @param threshold Minimum acceptable proportion in [0, 1]
  */
case class AtLeast(threshold: Double, tolerance: Double = 0.0) extends VagueQuantifier:
  require(threshold >= 0.0 && threshold <= 1.0, s"Threshold must be in [0, 1], got $threshold")
  require(tolerance >= 0.0 && tolerance <= 1.0, s"Tolerance must be in [0, 1], got $tolerance")
  
  def toQuantifier: LogicQuantifier =
    val k = math.round(threshold * 1000).toInt
    LogicQuantifier.AtLeast(k, 1000, tolerance)
  
  def describe: String = 
    if tolerance > 0 then s"at least about ${(threshold * 100).toInt}%"
    else s"at least ${(threshold * 100).toInt}%"

/** At-most quantifier Q[≤].
  * 
  * Accepts proportions less than or equal to threshold.
  * Example: "few" = AtMost(0.3) accepts [0, 0.3]
  * 
  * @param threshold Maximum acceptable proportion in [0, 1]
  */
case class AtMost(threshold: Double, tolerance: Double = 0.0) extends VagueQuantifier:
  require(threshold >= 0.0 && threshold <= 1.0, s"Threshold must be in [0, 1], got $threshold")
  require(tolerance >= 0.0 && tolerance <= 1.0, s"Tolerance must be in [0, 1], got $tolerance")
  
  def toQuantifier: LogicQuantifier =
    val k = math.round(threshold * 1000).toInt
    LogicQuantifier.AtMost(k, 1000, tolerance)
  
  def describe: String = 
    if tolerance > 0 then s"at most about ${(threshold * 100).toInt}%"
    else s"at most ${(threshold * 100).toInt}%"

/** Common vague quantifiers with sensible defaults. */
object VagueQuantifier:
  
  /** Convert from the canonical ratio-based [[vague.logic.Quantifier]]
    * to the ergonomic percentage-based [[VagueQuantifier]].
    * 
    * This is the inverse of [[VagueQuantifier.toQuantifier]].
    * Used by the bridge layer when string-parsed queries need to
    * flow through the typed-DSL evaluation pipeline.
    */
  def fromQuantifier(q: LogicQuantifier): VagueQuantifier = q match
    case LogicQuantifier.About(k, n, tol) =>
      Approximately(k.toDouble / n.toDouble, tol)
    case LogicQuantifier.AtLeast(k, n, tol) =>
      AtLeast(k.toDouble / n.toDouble, tol)
    case LogicQuantifier.AtMost(k, n, tol) =>
      AtMost(k.toDouble / n.toDouble, tol)
  
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
