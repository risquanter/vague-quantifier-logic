package vague.sampling

import scala.math.*
import org.apache.commons.math3.distribution.NormalDistribution

/** Calculator for determining sample sizes in vague quantifier evaluation.
  * 
  * Implements sample size calculation for proportion estimation:
  * 
  * For sampling without replacement from finite population:
  *   n = n₀ / (1 + n₀/N)
  * 
  * where n₀ is the initial sample size from infinite population formula:
  *   n₀ = (z²/4ε²)
  * 
  * and z is the z-score for significance level α.
  */
object SampleSizeCalculator:
  
  /** Calculate required sample size for proportion estimation.
    * 
    * @param populationSize Size of the finite population (N)
    * @param params Sampling parameters (ε, α)
    * @return Required sample size (n), clamped to [1, N]
    */
  def calculateSampleSize(
    populationSize: Int,
    params: SamplingParams
  ): Int =
    require(populationSize > 0, s"Population size must be positive, got $populationSize")
    
    if populationSize == 1 then
      // Special case: population of 1 requires full enumeration
      return 1
    
    // Get z-score for confidence level
    val z = params.zScore
    val epsilon = params.epsilon
    
    // Calculate initial sample size for infinite population
    // n₀ = z² / (4ε²)
    // This comes from the margin of error formula for proportions:
    // ε = z * √(p(1-p)/n)
    // Worst case: p = 0.5 (maximum variance)
    // Solving for n: n = z² / (4ε²)
    val n0 = (z * z) / (4.0 * epsilon * epsilon)
    
    // Apply finite population correction (FPC)
    // Adjusts sample size downward when sampling significant fraction of population
    // n = n₀ / (1 + n₀/N)
    val n = n0 / (1.0 + n0 / populationSize.toDouble)
    
    // Round up to ensure we meet error bounds
    val sampleSize = ceil(n).toInt
    
    // Clamp to valid range [1, N]
    // If calculated sample size exceeds population, use full enumeration
    max(1, min(sampleSize, populationSize))
  
  /** Calculate confidence interval for a proportion estimate.
    * 
    * Uses Wilson score interval, which is more accurate than normal approximation
    * for extreme proportions and small samples.
    * 
    * @param proportion Observed proportion (p̂) in [0, 1]
    * @param sampleSize Number of samples (n)
    * @param params Sampling parameters (α for z-score)
    * @return (lower bound, upper bound) of confidence interval
    */
  def confidenceInterval(
    proportion: Double,
    sampleSize: Int,
    params: SamplingParams
  ): (Double, Double) =
    require(proportion >= 0 && proportion <= 1, 
      s"Proportion must be in [0,1], got $proportion")
    require(sampleSize > 0, 
      s"Sample size must be positive, got $sampleSize")
    
    if sampleSize == 1 then
      // With single sample, CI is just the observed value
      return (proportion, proportion)
    
    val z = params.zScore
    val n = sampleSize.toDouble
    val p = proportion
    
    // Wilson score interval (more robust than normal approximation)
    // Center: (p̂ + z²/2n) / (1 + z²/n)
    // Width: z * √(p̂(1-p̂)/n + z²/4n²) / (1 + z²/n)
    
    val z2 = z * z
    val denominator = 1.0 + z2 / n
    val center = (p + z2 / (2.0 * n)) / denominator
    val width = z * sqrt(p * (1.0 - p) / n + z2 / (4.0 * n * n)) / denominator
    
    val lower = max(0.0, center - width)
    val upper = min(1.0, center + width)
    
    (lower, upper)
  
  /** Calculate margin of error for given sample size and proportion.
    * 
    * Returns the half-width of the confidence interval.
    * 
    * @param proportion Observed proportion
    * @param sampleSize Number of samples
    * @param params Sampling parameters
    * @return Margin of error (half-width of CI)
    */
  def marginOfError(
    proportion: Double,
    sampleSize: Int,
    params: SamplingParams
  ): Double =
    val (lower, upper) = confidenceInterval(proportion, sampleSize, params)
    (upper - lower) / 2.0
  
  /** Check if sample size is sufficient for desired error bound.
    * 
    * @param sampleSize Actual sample size
    * @param proportion Observed proportion
    * @param params Sampling parameters (ε threshold)
    * @return true if margin of error ≤ ε
    */
  def isSufficient(
    sampleSize: Int,
    proportion: Double,
    params: SamplingParams
  ): Boolean =
    marginOfError(proportion, sampleSize, params) <= params.epsilon
  
  /** Calculate statistical power for detecting a difference from target proportion.
    * 
    * Power = probability of correctly rejecting null hypothesis when it's false.
    * 
    * @param sampleSize Sample size
    * @param targetProportion Target/expected proportion
    * @param actualProportion True proportion in population
    * @param params Sampling parameters
    * @return Statistical power in [0, 1]
    */
  def power(
    sampleSize: Int,
    targetProportion: Double,
    actualProportion: Double,
    params: SamplingParams
  ): Double =
    // Simplified power calculation for proportion tests
    // Power depends on effect size (difference between proportions)
    val z = params.zScore
    val n = sampleSize.toDouble
    val delta = abs(actualProportion - targetProportion)
    val se = sqrt(targetProportion * (1.0 - targetProportion) / n)
    
    if se == 0.0 then return 1.0
    
    // Z-score for alternative hypothesis
    val zAlt = delta / se - z
    
    // Power is probability Z > -zAlt
    // Using normal CDF approximation
    standardNormalCDF(zAlt)
  
  /** Standard normal cumulative distribution function (CDF).
    * 
    * Uses Apache Commons Math for precise calculation.
    */
  private def standardNormalCDF(x: Double): Double =
    val standardNormal = new NormalDistribution(0.0, 1.0)
    standardNormal.cumulativeProbability(x)
