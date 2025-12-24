package vague.sampling

import com.risquanter.simulation.util.rng.HDR
import scala.reflect.ClassTag

/** HDR-based sampler using Hubbard Decision Research PRNG.
  * 
  * Based on Hubbard (2019). "A Multi-Dimensional, Counter-Based Pseudo Random
  * Number Generator as a Standard for Monte Carlo Simulations"
  * Proceedings of the 2019 Winter Simulation Conference.
  * 
  * Benefits over standard PRNG:
  * - Counter-based: Direct access to any trial without computing prior values
  * - Multi-dimensional: Independent sequences for different entities/variables
  * - Reproducible: Same across all platforms and languages
  * - Parallel-friendly: No shared state
  * - Tested: Passes Dieharder randomness tests
  * 
  * @param params Sampling parameters including HDR entity/variable IDs
  * @param ct ClassTag for Set operations (not used for HDR itself, but required by trait)
  */
class HDRSampler[A](params: SamplingParams)(using ct: ClassTag[A]) extends Sampler[A]:
  
  // HDR generator configured with entity and variable IDs
  private val hdr = new HDR(
    params.hdrEntityId,
    params.hdrVarId,
    params.seed.getOrElse(0L),  // seed3
    0L                           // seed4 - reserved for future use
  )
  
  /** Sample n elements using HDR PRNG for selection.
    * 
    * Uses counter-based approach: each index i gets a unique random value
    * from HDR(i), allowing parallel and reproducible sampling.
    * 
    * @param population Set of elements to sample from
    * @param n Number of elements to sample
    * @return Sampled elements (size = min(n, |population|))
    */
  def sample(population: Set[A], n: Int): Set[A] =
    require(n >= 0, s"Sample size must be non-negative, got $n")
    
    if n == 0 || population.isEmpty then
      return Set.empty
    
    val populationSize = population.size
    if n >= populationSize then
      return population
    
    // Convert to indexed sequence
    val elements = population.toVector
    
    // Generate n random indices using HDR
    // Each trial counter produces a unique, reproducible random value
    val indices = (0 until n).map { trial =>
      val randomValue = hdr.trial(trial.toLong)
      (randomValue * populationSize).toInt
    }.toSet
    
    // Handle potential collisions by taking first n unique indices
    val uniqueIndices = scala.collection.mutable.Set.empty[Int]
    var trial = 0L
    while uniqueIndices.size < n && trial < populationSize * 2 do
      val randomValue = hdr.trial(trial)
      val index = (randomValue * populationSize).toInt
      uniqueIndices.add(index)
      trial += 1
    
    uniqueIndices.take(n).map(elements).toSet
  
  /** Create a new sampler with same parameters.
    * 
    * Note: HDR is deterministic, so "resetting" produces the same sequence.
    * To get different results, change hdrEntityId or hdrVarId.
    */
  def reset(): HDRSampler[A] = 
    new HDRSampler[A](params)

object HDRSampler:
  /** Create HDR sampler with given parameters. */
  def apply[A](params: SamplingParams = SamplingParams())(using ClassTag[A]): HDRSampler[A] =
    new HDRSampler[A](params)
  
  /** Create HDR sampler for specific entity and variable IDs. */
  def forEntityVar[A](
    entityId: Long,
    varId: Long,
    params: SamplingParams = SamplingParams()
  )(using ClassTag[A]): HDRSampler[A] =
    new HDRSampler[A](params.copy(hdrEntityId = entityId, hdrVarId = varId))
