package fol.sampling

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
  successes: Int,
  confidenceInterval: (Double, Double),
  marginOfError: Double,
  params: SamplingParams
):
  require(proportion >= 0 && proportion <= 1, 
    s"Proportion must be in [0,1], got $proportion")
  require(sampleSize >= 0, 
    s"Sample size must be non-negative, got $sampleSize")
  require(successes >= 0 && successes <= sampleSize,
    s"Successes must be in [0, sampleSize], got $successes (sampleSize=$sampleSize)")
  // Allow small floating point tolerance for CI bounds
  require(confidenceInterval._1 - 0.0001 <= proportion && proportion <= confidenceInterval._2 + 0.0001,
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

object ProportionEstimate:
  /** Empty estimate for zero-element populations/samples. */
  def empty(params: SamplingParams = SamplingParams()): ProportionEstimate =
    ProportionEstimate(
      proportion = 0.0,
      sampleSize = 0,
      successes = 0,
      confidenceInterval = (0.0, 1.0),
      marginOfError = 0.5,
      params = params
    )

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
      ProportionEstimate.empty(params)
    else
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
        successes = successes,
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
    * @param params Statistical precision parameters (ε, α)
    * @param config HDR PRNG configuration (4-layer seed hierarchy)
    * @param sampler Override sampling strategy (default: HDR)
    * @return Proportion estimate with confidence interval
    */
  def estimateWithSampling[A](
    population: Set[A],
    predicate: A => Boolean,
    params: SamplingParams = SamplingParams(),
    config: HDRConfig = HDRConfig.default,
    sampler: Option[Sampler[A]] = None
  )(using ClassTag[A]): ProportionEstimate =
    
    if population.isEmpty then
      ProportionEstimate.empty(params)
    else
      // Calculate required sample size
      val sampleSize = SampleSizeCalculator.calculateSampleSize(
        population.size, 
        params
      )
      
      // Perform sampling - default to HDR for reproducibility
      val actualSampler = sampler.getOrElse(HDRSampler[A](config))
      val sample = actualSampler.sample(population, sampleSize)
      
      // Estimate proportion from sample
      estimate(sample, predicate, params)
  
  /** Build a ProportionEstimate from pre-counted integers.
    * 
    * Use when successes and sampleSize are already known (e.g. after
    * a `.filter(predicate)` / `.size` pass) to avoid re-iterating.
    * 
    * @param successes Number of elements that satisfied the predicate
    * @param sampleSize Total number of elements examined
    * @param params Sampling parameters (for CI calculation)
    * @return Proportion estimate with confidence interval
    */
  def estimateFromCount(
    successes: Int,
    sampleSize: Int,
    params: SamplingParams = SamplingParams()
  ): ProportionEstimate =
    if sampleSize == 0 then
      ProportionEstimate.empty(params)
    else
      val proportion = successes.toDouble / sampleSize.toDouble
      val ci = SampleSizeCalculator.confidenceInterval(proportion, sampleSize, params)
      val moe = (ci._2 - ci._1) / 2.0
      ProportionEstimate(
        proportion = proportion,
        sampleSize = sampleSize,
        successes = successes,
        confidenceInterval = ci,
        marginOfError = moe,
        params = params
      )
  
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
    config: HDRConfig = HDRConfig.default,
    maxIterations: Int = 5
  )(using ClassTag[A]): ProportionEstimate =

    if population.isEmpty then
      ProportionEstimate.empty(params)
    else
      val sampler = HDRSampler[A](config)
      val initialSize = SampleSizeCalculator.calculateSampleSize(population.size, params)
      val initialEstimate = estimateWithSampling(population, predicate, params, config, Some(sampler))

      @scala.annotation.tailrec
      def refine(sampleSize: Int, est: ProportionEstimate, iter: Int): ProportionEstimate =
        if est.meetsErrorBound(params.epsilon) || sampleSize >= population.size || iter >= maxIterations then
          est
        else
          val nextSize = math.min((sampleSize * 1.5).toInt, population.size)
          val sample = sampler.sample(population, nextSize)
          val nextEst = ProportionEstimator.estimate(sample, predicate, params)
          refine(nextSize, nextEst, iter + 1)

      refine(initialSize, initialEstimate, 0)
  
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
    
    if n1 == 0 || n2 == 0 then false
    else
      // Pooled proportion
      val pooled = (n1 * p1 + n2 * p2) / (n1 + n2)
      
      // Standard error
      val se = math.sqrt(pooled * (1.0 - pooled) * (1.0 / n1 + 1.0 / n2))
      
      if se == 0.0 then p1 != p2
      else
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
    params: SamplingParams = SamplingParams(),
    config: HDRConfig = HDRConfig.default
  )(using ClassTag[A]): Map[String, ProportionEstimate] =
    
    if population.isEmpty then
      predicates.map { case (name, _) =>
        name -> ProportionEstimate.empty(params)
      }
    else
      // Calculate sample size once
      val sampleSize = SampleSizeCalculator.calculateSampleSize(
        population.size, 
        params
      )
      
      // Take single sample using HDR
      val sampler = HDRSampler[A](config)
      val sample = sampler.sample(population, sampleSize)
      
      // Evaluate all predicates on same sample
      predicates.map { case (name, predicate) =>
        name -> estimate(sample, predicate, params)
      }
