package vague.sampling

import org.apache.commons.math3.distribution.NormalDistribution

/** Parameters for statistical sampling in vague quantifier evaluation.
  * 
  * Based on Fermüller, Hofer, and Ortiz (2017).
  * "Querying with Vague Quantifiers Using Probabilistic Semantics"
  * Implements sampling-based semantics for proportional quantifiers.
  * 
  * Uses HDR (Hubbard Decision Research) PRNG from:
  * Hubbard (2019). "A Multi-Dimensional, Counter-Based Pseudo Random Number
  * Generator as a Standard for Monte Carlo Simulations"
  * 
  * @param epsilon Maximum error tolerance (ε) for proportion estimates.
  *                Smaller values require larger sample sizes.
  *                Default: 0.1 (10% error bound)
  * 
  * @param alpha Significance level (α) for confidence intervals.
  *              Probability that true proportion falls outside confidence interval.
  *              Default: 0.05 (95% confidence)
  * 
  * @param seed Random seed for reproducible sampling.
  *             Use Some(seed) for deterministic results (testing).
  *             Use None for true randomness (production).
  *             
  * @param hdrEntityId Entity ID for HDR PRNG (default: 0).
  *                    Can represent organization or domain context.
  *                    
  * @param hdrVarId Variable ID for HDR PRNG (default: 0).
  *                 Can represent specific variable/relation being sampled.
  */
case class SamplingParams(
  epsilon: Double = 0.1,
  alpha: Double = 0.05,
  seed: Option[Long] = None,
  hdrEntityId: Long = 0L,
  hdrVarId: Long = 0L
):
  require(epsilon > 0 && epsilon < 1, s"Epsilon must be in (0,1), got $epsilon")
  require(alpha > 0 && alpha < 1, s"Alpha must be in (0,1), got $alpha")
  
  /** Z-score for the given significance level.
    * 
    * Uses Apache Commons Math for inverse normal CDF.
    * 
    * For two-tailed confidence intervals:
    * - α = 0.05 → z = 1.96 (95% confidence)
    * - α = 0.01 → z = 2.576 (99% confidence)
    * - α = 0.10 → z = 1.645 (90% confidence)
    */
  def zScore: Double =
    val standardNormal = new NormalDistribution(0.0, 1.0)
    val p = 1.0 - alpha / 2.0
    standardNormal.inverseCumulativeProbability(p)
end SamplingParams

object SamplingParams:
  /** Default sampling parameters.
    * - 10% error tolerance
    * - 95% confidence
    * - Random seed (non-deterministic)
    */
  val default: SamplingParams = SamplingParams()
  
  /** Conservative parameters for higher precision.
    * - 5% error tolerance
    * - 99% confidence
    */
  val conservative: SamplingParams = SamplingParams(
    epsilon = 0.05,
    alpha = 0.01
  )
  
  /** Fast parameters for exploratory queries.
    * - 15% error tolerance
    * - 90% confidence
    */
  val fast: SamplingParams = SamplingParams(
    epsilon = 0.15,
    alpha = 0.10
  )
  
  /** Testing parameters with fixed seed for reproducibility. */
  def forTesting(seed: Long = 42): SamplingParams = SamplingParams(
    seed = Some(seed)
  )
