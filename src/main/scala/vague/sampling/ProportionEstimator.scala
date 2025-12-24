package vague.sampling

import scala.reflect.ClassTag

/** Result of proportion estimation with confidence interval.
  * 
  * @param proportion Estimated proportion (p̂) in [0, 1]
  * @param sampleSize Number of samples used (n)
  * @param confidenceInterval (lower, upper) bounds at specified confidence level
  * @param marginOfError Half-width of confidence interval (ε)
  * @param params Sampling parameters used
  */
case class ProportionEstimate(
  proportion: Double,
  sampleSize: Int,
  confidenceInterval: (Double, Double),
  marginOfError: Double,
  params: SamplingParams
):
  require(proportion >= 0 && proportion <= 1, 
    s"Proportion must be in [0,1], got $proportion")
  require(sampleSize >= 0, 
    s"Sample size must be non-negative, got $sampleSize")
  require(confidenceInterval._1 <= proportion && proportion <= confidenceInterval._2,
    s"Proportion $proportion not in confidence interval ${confidenceInterval}")
  
  /** Lower bound of confidence interval. */
  def lower: Double = confidenceInterval._1
  
  /** Upper bound of confidence interval. */
  def upper: Double = confidenceInterval._2
  
  /** Check if estimate meets desired error tolerance.
    * 
    * @param epsilon Desired error bound
    * @return true if margin of error ≤ epsilon
    */
  def meetsErrorBound(epsilon: Double): Boolean = 
    marginOfError <= epsilon
  
  /** Check if target proportion falls within confidence interval.
    * 
    * @param target Target proportion to test
    * @return true if target ∈ [lower, upper]
    */
  def contains(target: Double): Boolean =
    lower <= target && target <= upper
  
  /** Check if confidence interval overlaps with given range.
    * 
    * @param min Lower bound of range
    * @param max Upper bound of range
    * @return true if intervals overlap
    */
  def overlaps(min: Double, max: Double): Boolean =
    !(upper < min || lower > max)
  
  /** Format estimate as human-readable string. */
  override def toString: String =
    f"$proportion%.3f ± $marginOfError%.3f " +
    f"(95%% CI: [$lower%.3f, $upper%.3f], n=$sampleSize)"

/** Estimator for proportions using statistical sampling.
  * 
  * Estimates the proportion of elements satisfying a predicate
  * with confidence intervals based on sample statistics.
  */
object ProportionEstimator:
  
  /** Estimate proportion from a sample.
    * 
    * Simple estimation: count successes and compute proportion.
    * 
    * @param sample Sampled elements
    * @param predicate Condition to test
    * @param params Sampling parameters
    * @return Proportion estimate with confidence interval
    */
  def estimate[A](
    sample: Set[A],
    predicate: A => Boolean,
    params: SamplingParams
  ): ProportionEstimate =
    val sampleSize = sample.size
    
    if sampleSize == 0 then
      // Empty sample - undefined proportion, return 0 with zero confidence
      return ProportionEstimate(
        proportion = 0.0,
        sampleSize = 0,
        confidenceInterval = (0.0, 1.0),
        marginOfError = 0.5,
        params = params
      )
    
    val successes = sample.count(predicate)
    val proportion = successes.toDouble / sampleSize.toDouble
    
    val ci = SampleSizeCalculator.confidenceInterval(
      proportion, 
      sampleSize, 
      params
    )
    val moe = (ci._2 - ci._1) / 2.0
    
    ProportionEstimate(
      proportion = proportion,
      sampleSize = sampleSize,
      confidenceInterval = ci,
      marginOfError = moe,
      params = params
    )
  
  /** Estimate proportion with automatic sampling.
    * 
    * Automatically calculates required sample size and performs sampling.
    * 
    * @param population Full population
    * @param predicate Condition to test
    * @param params Sampling parameters
    * @param sampler Sampling strategy (default: HDR for reproducibility)
    * @return Proportion estimate with confidence interval
    */
  def estimateWithSampling[A](
    population: Set[A],
    predicate: A => Boolean,
    params: SamplingParams = SamplingParams(),
    sampler: Option[Sampler[A]] = None
  )(using ClassTag[A]): ProportionEstimate =
    
    if population.isEmpty then
      return ProportionEstimate(
        proportion = 0.0,
        sampleSize = 0,
        confidenceInterval = (0.0, 1.0),
        marginOfError = 0.5,
        params = params
      )
    
    // Calculate required sample size
    val sampleSize = SampleSizeCalculator.calculateSampleSize(
      population.size, 
      params
    )
    
    // Perform sampling - default to HDR for reproducibility
    val actualSampler = sampler.getOrElse(Sampler.hdr[A](params))
    val sample = actualSampler.sample(population, sampleSize)
    
    // Estimate proportion from sample
    estimate(sample, predicate, params)
  
  /** Estimate proportion using exhaustive enumeration (no sampling).
    * 
    * Use when population is small or precision is critical.
    * 
    * @param population Full population
    * @param predicate Condition to test
    * @param params Sampling parameters (for CI calculation)
    * @return Exact proportion with tight confidence interval
    */
  def exactEstimate[A](
    population: Set[A],
    predicate: A => Boolean,
    params: SamplingParams = SamplingParams()
  ): ProportionEstimate =
    estimate(population, predicate, params)
  
  /** Estimate proportion with adaptive sampling.
    * 
    * Starts with small sample and increases if error bound not met.
    * Stops when margin of error ≤ ε or population exhausted.
    * 
    * @param population Full population
    * @param predicate Condition to test
    * @param params Sampling parameters
    * @param maxIterations Maximum refinement iterations
    * @return Proportion estimate with confidence interval
    */
  def adaptiveEstimate[A](
    population: Set[A],
    predicate: A => Boolean,
    params: SamplingParams = SamplingParams(),
    maxIterations: Int = 5
  )(using ClassTag[A]): ProportionEstimate =
    
    if population.isEmpty then
      return ProportionEstimate(
        proportion = 0.0,
        sampleSize = 0,
        confidenceInterval = (0.0, 1.0),
        marginOfError = 0.5,
        params = params
      )
    
    // Start with calculated sample size
    var currentSampleSize = SampleSizeCalculator.calculateSampleSize(
      population.size, 
      params
    )
    
    val sampler = Sampler.hdr[A](params)
    var estimate = estimateWithSampling(population, predicate, params, Some(sampler))
    var iterations = 0
    
    // Refine if needed
    while !estimate.meetsErrorBound(params.epsilon) && 
          currentSampleSize < population.size && 
          iterations < maxIterations do
      
      // Increase sample size by 50%
      currentSampleSize = math.min(
        (currentSampleSize * 1.5).toInt,
        population.size
      )
      
      val sample = sampler.sample(population, currentSampleSize)
      estimate = ProportionEstimator.estimate(sample, predicate, params)
      iterations += 1
    
    estimate
  
  /** Compare two proportion estimates for statistical significance.
    * 
    * Tests if the difference between two proportions is statistically significant.
    * 
    * @param estimate1 First proportion estimate
    * @param estimate2 Second proportion estimate
    * @param alpha Significance level (default: 0.05)
    * @return true if difference is significant at level alpha
    */
  def significantDifference(
    estimate1: ProportionEstimate,
    estimate2: ProportionEstimate,
    alpha: Double = 0.05
  ): Boolean =
    // Two-proportion z-test
    val p1 = estimate1.proportion
    val p2 = estimate2.proportion
    val n1 = estimate1.sampleSize.toDouble
    val n2 = estimate2.sampleSize.toDouble
    
    if n1 == 0 || n2 == 0 then return false
    
    // Pooled proportion
    val pooled = (n1 * p1 + n2 * p2) / (n1 + n2)
    
    // Standard error
    val se = math.sqrt(pooled * (1.0 - pooled) * (1.0 / n1 + 1.0 / n2))
    
    if se == 0.0 then return p1 != p2
    
    // Z-statistic
    val z = math.abs(p1 - p2) / se
    
    // Critical value for two-tailed test
    val zCritical = if math.abs(alpha - 0.05) < 0.0001 then 1.96 
                    else if math.abs(alpha - 0.01) < 0.0001 then 2.576
                    else 1.96  // Default to 95% confidence
    
    z > zCritical
  
  /** Batch estimation for multiple predicates.
    * 
    * Efficiently estimate proportions for multiple predicates using same sample.
    * 
    * @param population Full population
    * @param predicates Multiple conditions to test
    * @param params Sampling parameters
    * @return Map of predicate names to proportion estimates
    */
  def batchEstimate[A](
    population: Set[A],
    predicates: Map[String, A => Boolean],
    params: SamplingParams = SamplingParams()
  )(using ClassTag[A]): Map[String, ProportionEstimate] =
    
    if population.isEmpty then
      return predicates.map { case (name, _) =>
        name -> ProportionEstimate(
          proportion = 0.0,
          sampleSize = 0,
          confidenceInterval = (0.0, 1.0),
          marginOfError = 0.5,
          params = params
        )
      }
    
    // Calculate sample size once
    val sampleSize = SampleSizeCalculator.calculateSampleSize(
      population.size, 
      params
    )
    
    // Take single sample using HDR
    val sampler = Sampler.hdr[A](params)
    val sample = sampler.sample(population, sampleSize)
    
    // Evaluate all predicates on same sample
    predicates.map { case (name, predicate) =>
      name -> estimate(sample, predicate, params)
    }
