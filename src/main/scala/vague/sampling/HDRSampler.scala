package vague.sampling

import com.risquanter.hdr.HDR
import scala.reflect.ClassTag

/** HDR-based sampler using Hubbard Decision Research PRNG.
  *
  * The sole [[Sampler]] implementation. Uses Fisher-Yates shuffle
  * powered by HDR counter-based PRNG — no `scala.util.Random` anywhere.
  *
  * Based on Hubbard (2019). "A Multi-Dimensional, Counter-Based Pseudo Random
  * Number Generator as a Standard for Monte Carlo Simulations"
  * Proceedings of the 2019 Winter Simulation Conference.
  *
  * Uses the pure-Scala hdr-rng library (cross-compiled JVM+JS),
  * matching the Excel reference implementation exactly.
  *
  * Benefits over standard PRNG:
  * - Counter-based: Direct access to any trial without computing prior values
  * - Multi-dimensional: Independent sequences for different entities/variables
  * - Reproducible: Same across all platforms and languages
  * - Parallel-friendly: No shared state
  * - Tested: Passes 10k exact-match reference values from Excel
  *
  * '''Separation of concerns:'''
  * - HDR generates deterministic, reproducible random doubles in [0, 1)
  * - Fisher-Yates uses those doubles to shuffle/select elements
  *
  * @param config HDR PRNG configuration (4-layer seed hierarchy per ADR-003)
  * @param ct     ClassTag for creating Array[A] in Fisher-Yates shuffle
  */
class HDRSampler[A](config: HDRConfig)(using ct: ClassTag[A]) extends Sampler[A]:

  // HDR generator configured with the 4-layer seed hierarchy
  private val hdr = HDR(
    entityId = config.entityId,
    varId    = config.varId,
    seed3    = config.seed3,
    seed4    = config.seed4
  )

  /** Sample n elements using Fisher-Yates shuffle powered by HDR.
    *
    * Algorithm: partial Fisher-Yates — only shuffle first n positions.
    * For each position i in [0, n), swap with random position j in [i, N),
    * where j is determined by HDR trial(i).
    *
    * Time complexity: O(min(n, |population|))
    * Random values consumed: exactly n
    *
    * @param population Set of elements to sample from
    * @param n          Number of elements to sample
    * @return Sampled elements (size = min(n, |population|))
    */
  def sample(population: Set[A], n: Int): Set[A] =
    require(n >= 0, s"Sample size must be non-negative, got $n")

    if n == 0 || population.isEmpty then
      return Set.empty

    val populationSize = population.size
    if n >= populationSize then
      return population

    // Convert to mutable array for in-place Fisher-Yates
    val elements = population.toArray

    // Partial Fisher-Yates: shuffle first n positions
    // Each HDR trial(i) produces a reproducible random double in [0, 1)
    for i <- 0 until n do
      val remaining = populationSize - i
      val j = i + (hdr.trial(i.toLong) * remaining).toInt
      // Swap elements(i) and elements(j)
      val temp = elements(i)
      elements(i) = elements(j)
      elements(j) = temp

    // Return first n elements as immutable Set
    elements.take(n).toSet

  /** Create a new sampler with same configuration.
    *
    * HDR is deterministic: same config always produces the same sequence.
    * To get different results, change entityId, varId, seed3, or seed4
    * in the [[HDRConfig]].
    */
  def reset(): HDRSampler[A] =
    new HDRSampler[A](config)

object HDRSampler:
  /** Create HDR sampler with given configuration. */
  def apply[A](config: HDRConfig = HDRConfig.default)(using ClassTag[A]): HDRSampler[A] =
    new HDRSampler[A](config)

  /** Create HDR sampler for specific entity and variable IDs.
    *
    * Convenience factory when seed3/seed4 come from defaults.
    */
  def forEntityVar[A](
    entityId: Long,
    varId: Long,
    seed3: Long = 0L,
    seed4: Long = 0L
  )(using ClassTag[A]): HDRSampler[A] =
    new HDRSampler[A](HDRConfig(entityId, varId, seed3, seed4))
