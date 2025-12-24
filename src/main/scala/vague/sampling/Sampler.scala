package vague.sampling

import scala.util.Random
import scala.collection.immutable.Set
import scala.reflect.ClassTag

/** Trait for sampling elements from a population.
  * 
  * Supports different sampling strategies (uniform, stratified, etc.)
  * with reproducibility via seed control.
  * 
  * @tparam A Type of elements being sampled
  */
trait Sampler[A]:
  /** Sample n elements from the given population.
    * 
    * @param population Set of elements to sample from
    * @param n Number of elements to sample
    * @return Sampled elements (size may be less than n if population is small)
    */
  def sample(population: Set[A], n: Int): Set[A]
  
  /** Sample elements and apply a predicate, counting successes.
    * 
    * @param population Set of elements to sample from
    * @param n Number of elements to sample
    * @param predicate Function to test each sampled element
    * @return (sampled elements, number of successes)
    */
  def sampleWithPredicate(
    population: Set[A], 
    n: Int, 
    predicate: A => Boolean
  ): (Set[A], Int) =
    val sampled = sample(population, n)
    val successes = sampled.count(predicate)
    (sampled, successes)

/** Uniform random sampling without replacement.
  * 
  * Each element has equal probability of being selected.
  * Once selected, element is not eligible for re-selection.
  * 
  * Uses Fisher-Yates shuffle algorithm for efficiency.
  * 
  * @param params Sampling configuration (seed, epsilon, alpha)
  * @param ct ClassTag for creating Array[A] in Fisher-Yates shuffle
  */
class UniformSampler[A](params: SamplingParams)(using ct: ClassTag[A]) extends Sampler[A]:
  
  // Initialize random number generator with seed if provided
  private val rng: Random = params.seed match
    case Some(seed) => new Random(seed)
    case None => new Random()
  
  /** Sample n elements uniformly without replacement.
    * 
    * Algorithm: Fisher-Yates shuffle on first n positions
    * Time complexity: O(min(n, |population|))
    * 
    * Rationale for Fisher-Yates in codebase:
    * - HDR PRNG (counter-based) handles deterministic random value generation
    * - Fisher-Yates handles the actual shuffling logic for collections
    * - Separation of concerns: HDR = number generation, Fisher-Yates = sampling
    * - Fisher-Yates is simple (~15 lines), optimal O(n), and well-understood
    * - Requires ClassTag[A] to create mutable Array[A] for in-place shuffling
    * - ClassTag provides runtime type information needed by JVM for array creation
    * - Without ClassTag, we'd need boxing/unboxing or manifest patterns (deprecated)
    * 
    * @param population Set of elements to sample from
    * @param n Desired sample size
    * @return Sampled elements (size = min(n, |population|))
    */
  def sample(population: Set[A], n: Int): Set[A] =
    require(n >= 0, s"Sample size must be non-negative, got $n")
    
    if n == 0 || population.isEmpty then
      return Set.empty
    
    val populationSize = population.size
    if n >= populationSize then
      // Sample size exceeds population - return entire population
      return population
    
    // Convert to indexed sequence for efficient random access
    val elements = population.toVector
    
    // Create mutable array for shuffling
    // ClassTag[A] enables Array[A] creation without knowing A at runtime
    val shuffled = elements.toArray
    
    // Fisher-Yates shuffle: only shuffle first n positions
    // For each position i, swap with random position j in [i, populationSize)
    for i <- 0 until n do
      val j = i + rng.nextInt(populationSize - i)
      val temp = shuffled(i)
      shuffled(i) = shuffled(j)
      shuffled(j) = temp
    
    // Return first n elements as immutable Set
    shuffled.take(n).toSet
  
  /** Create a new sampler with the same parameters but fresh random state.
    * 
    * Useful for independent sampling runs.
    */
  def reset(): UniformSampler[A] = 
    new UniformSampler[A](params)(using ct)

/** Stratified sampling that preserves subgroup proportions.
  * 
  * Divides population into strata and samples proportionally from each.
  * Reduces variance when strata have different characteristics.
  * 
  * @param params Sampling parameters
  * @param stratify Function to assign elements to strata
  */
class StratifiedSampler[A: ClassTag, S](
  params: SamplingParams,
  stratify: A => S
) extends Sampler[A]:
  
  private val rng: Random = params.seed match
    case Some(seed) => new Random(seed)
    case None => new Random()
  
  def sample(population: Set[A], n: Int): Set[A] =
    require(n >= 0, s"Sample size must be non-negative, got $n")
    
    if n == 0 || population.isEmpty then
      return Set.empty
    
    if n >= population.size then
      return population
    
    // Group population by strata
    val strata = population.groupBy(stratify)
    val populationSize = population.size
    
    // Calculate proportional sample sizes for each stratum
    var sampledElements = Set.empty[A]
    var remainingSamples = n
    
    strata.foreach { case (stratum, elements) =>
      val stratumSize = elements.size
      val proportion = stratumSize.toDouble / populationSize.toDouble
      
      // Allocate samples proportionally
      val stratumSamples = if remainingSamples > 0 then
        val allocated = math.round(n * proportion).toInt
        math.min(allocated, math.min(stratumSize, remainingSamples))
      else
        0
      
      if stratumSamples > 0 then
        // Use uniform sampling within stratum
        val uniformSampler = new UniformSampler[A](params)(using summon[ClassTag[A]])
        val stratumSample = uniformSampler.sample(elements, stratumSamples)
        sampledElements = sampledElements ++ stratumSample
        remainingSamples -= stratumSample.size
    }
    
    sampledElements

object Sampler:
  /** Create a uniform sampler with given parameters.
    * 
    * Uses Fisher-Yates shuffle with Scala's default Random.
    */
  def uniform[A](params: SamplingParams = SamplingParams())(using ClassTag[A]): UniformSampler[A] =
    new UniformSampler[A](params)
  
  /** Create HDR-based sampler with given parameters.
    * 
    * Recommended for reproducibility and parallel processing.
    * Based on Hubbard (2019) counter-based PRNG.
    */
  def hdr[A](params: SamplingParams = SamplingParams())(using ClassTag[A]): HDRSampler[A] =
    new HDRSampler[A](params)
  
  /** Create a stratified sampler with given parameters and stratification function. */
  def stratified[A, S](
    params: SamplingParams = SamplingParams()
  )(stratify: A => S)(using ClassTag[A]): StratifiedSampler[A, S] =
    new StratifiedSampler[A, S](params, stratify)
  
  /** Sample with automatic sample size calculation.
    * 
    * Convenience method that combines SampleSizeCalculator and Sampler.
    * 
    * @param population Population to sample from
    * @param params Sampling parameters
    * @return (sample, sample size)
    */
  def autoSample[A: ClassTag](
    population: Set[A], 
    params: SamplingParams = SamplingParams()
  ): (Set[A], Int) =
    val sampleSize = SampleSizeCalculator.calculateSampleSize(
      population.size, 
      params
    )
    val sampler = uniform[A](params)
    val sample = sampler.sample(population, sampleSize)
    (sample, sampleSize)
