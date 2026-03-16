package vague.sampling

import scala.collection.immutable.Set
import scala.reflect.ClassTag

/** Trait for sampling elements from a population.
  *
  * The sole implementation is [[HDRSampler]], which uses Fisher-Yates
  * shuffle powered by HDR counter-based PRNG. No `scala.util.Random`
  * is used anywhere — all randomness comes from HDR per ADR-003.
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

object Sampler:
  /** Create an HDR-based sampler.
    *
    * Uses Fisher-Yates shuffle with HDR PRNG for reproducibility.
    * This is the primary (and only) sampler factory.
    *
    * @param config HDR PRNG configuration (4-layer seed hierarchy)
    */
  def apply[A](config: HDRConfig = HDRConfig.default)(using ClassTag[A]): HDRSampler[A] =
    HDRSampler[A](config)

  /** Sample with automatic sample size calculation.
    *
    * Convenience method that combines SampleSizeCalculator and HDRSampler.
    *
    * @param population Population to sample from
    * @param params Statistical precision parameters (ε, α)
    * @param config HDR PRNG configuration
    * @return (sample, sample size)
    */
  def autoSample[A: ClassTag](
    population: Set[A],
    params: SamplingParams = SamplingParams(),
    config: HDRConfig = HDRConfig.default
  ): (Set[A], Int) =
    val sampleSize = SampleSizeCalculator.calculateSampleSize(
      population.size,
      params
    )
    val sampler = HDRSampler[A](config)
    val sample = sampler.sample(population, sampleSize)
    (sample, sampleSize)
