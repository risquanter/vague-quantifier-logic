package vague.sampling

import munit.FunSuite

/** Test suite for Sampling Infrastructure
  * 
  * Based on Fermüller, Hofer, and Ortiz (2017).
  * "Querying with Vague Quantifiers Using Probabilistic Semantics"
  * 
  * Tests:
  * - Sampling parameters and validation
  * - Sample size calculation with FPC
  * - HDR sampling via Fisher-Yates
  * - Proportion estimation with confidence intervals
  */
class SamplingSpec extends FunSuite:
  
  // ==================== SamplingParams Tests ====================
  
  test("default sampling params") {
    val params = SamplingParams.default
    assertEquals(params.epsilon, 0.1)
    assertEquals(params.alpha, 0.05)
  }
  
  test("conservative sampling params") {
    val params = SamplingParams.conservative
    assertEquals(params.epsilon, 0.05)
    assertEquals(params.alpha, 0.01)
  }
  
  test("fast sampling params") {
    val params = SamplingParams.fast
    assertEquals(params.epsilon, 0.15)
    assertEquals(params.alpha, 0.10)
  }
  
  test("HDRConfig defaults") {
    val config = HDRConfig.default
    assertEquals(config.entityId, 0L)
    assertEquals(config.varId, 0L)
    assertEquals(config.seed3, 0L)
    assertEquals(config.seed4, 0L)
  }
  
  test("params reject invalid epsilon") {
    intercept[IllegalArgumentException] {
      SamplingParams(epsilon = 0.0)
    }
    intercept[IllegalArgumentException] {
      SamplingParams(epsilon = 1.0)
    }
    intercept[IllegalArgumentException] {
      SamplingParams(epsilon = -0.1)
    }
  }
  
  test("params reject invalid alpha") {
    intercept[IllegalArgumentException] {
      SamplingParams(alpha = 0.0)
    }
    intercept[IllegalArgumentException] {
      SamplingParams(alpha = 1.0)
    }
  }
  
  test("z-score for common confidence levels") {
    val params95 = SamplingParams(alpha = 0.05)
    assert(math.abs(params95.zScore - 1.96) < 0.001, 
      s"95% z-score ${params95.zScore} should be close to 1.96")
    
    val params99 = SamplingParams(alpha = 0.01)
    assert(math.abs(params99.zScore - 2.576) < 0.001, 
      s"99% z-score ${params99.zScore} should be close to 2.576")
    
    val params90 = SamplingParams(alpha = 0.10)
    assert(math.abs(params90.zScore - 1.645) < 0.001, 
      s"90% z-score ${params90.zScore} should be close to 1.645")
  }
  
  // ==================== SampleSizeCalculator Tests ====================
  
  test("calculate sample size for large population") {
    val params = SamplingParams(epsilon = 0.1, alpha = 0.05)
    val size = SampleSizeCalculator.calculateSampleSize(10000, params)
    
    // For large N, should approximate n₀ = z²/(4ε²) = 1.96²/(4*0.1²) ≈ 96
    assert(size >= 90 && size <= 100, s"Expected ~96, got $size")
  }
  
  test("calculate sample size for small population") {
    val params = SamplingParams(epsilon = 0.1, alpha = 0.05)
    val size = SampleSizeCalculator.calculateSampleSize(50, params)
    
    // With FPC, should be less than n₀
    assert(size < 96, s"Expected < 96 with FPC, got $size")
    assert(size <= 50, s"Sample size should not exceed population")
  }
  
  test("sample size of 1 for population of 1") {
    val params = SamplingParams.default
    val size = SampleSizeCalculator.calculateSampleSize(1, params)
    assertEquals(size, 1)
  }
  
  test("sample size increases with smaller epsilon") {
    val params1 = SamplingParams(epsilon = 0.1, alpha = 0.05)
    val params2 = SamplingParams(epsilon = 0.05, alpha = 0.05)
    
    val size1 = SampleSizeCalculator.calculateSampleSize(1000, params1)
    val size2 = SampleSizeCalculator.calculateSampleSize(1000, params2)
    
    assert(size2 > size1, "Smaller epsilon should require larger sample")
  }
  
  test("sample size increases with smaller alpha") {
    val params1 = SamplingParams(epsilon = 0.1, alpha = 0.10)
    val params2 = SamplingParams(epsilon = 0.1, alpha = 0.01)
    
    val size1 = SampleSizeCalculator.calculateSampleSize(1000, params1)
    val size2 = SampleSizeCalculator.calculateSampleSize(1000, params2)
    
    assert(size2 > size1, "Smaller alpha should require larger sample")
  }
  
  test("confidence interval for proportion") {
    val params = SamplingParams(alpha = 0.05)
    val (lower, upper) = SampleSizeCalculator.confidenceInterval(
      proportion = 0.5,
      sampleSize = 100,
      params = params
    )
    
    // For n=100, p=0.5, 95% CI should be approximately [0.40, 0.60]
    assert(lower >= 0.38 && lower <= 0.42, s"Lower bound $lower out of range")
    assert(upper >= 0.58 && upper <= 0.62, s"Upper bound $upper out of range")
    assert(lower < 0.5 && 0.5 < upper, "CI should contain proportion")
  }
  
  test("confidence interval bounds are in [0,1]") {
    val params = SamplingParams.default
    val (lower, upper) = SampleSizeCalculator.confidenceInterval(
      proportion = 0.95,
      sampleSize = 20,
      params = params
    )
    
    assert(lower >= 0.0 && lower <= 1.0, s"Lower bound $lower out of range")
    assert(upper >= 0.0 && upper <= 1.0, s"Upper bound $upper out of range")
  }
  
  test("margin of error") {
    val params = SamplingParams(epsilon = 0.1, alpha = 0.05)
    val moe = SampleSizeCalculator.marginOfError(
      proportion = 0.5,
      sampleSize = 100,
      params = params
    )
    
    // For designed sample size, MOE should be close to epsilon
    assert(moe >= 0.08 && moe <= 0.12, s"MOE $moe not close to epsilon")
  }
  
  // ==================== Sampler Tests ====================
  
  test("HDR sampler with reproducible seed") {
    val config = HDRConfig(seed3 = 42)
    val sampler = HDRSampler[Int](config)
    
    val population = (1 to 100).toSet
    val sample1 = sampler.sample(population, 10)
    
    // Reset and sample again
    val sampler2 = sampler.reset()
    val sample2 = sampler2.sample(population, 10)
    
    assertEquals(sample1, sample2, "Same config should produce same sample")
  }
  
  test("sampling without replacement") {
    val config = HDRConfig(seed3 = 42)
    val sampler = HDRSampler[Int](config)
    
    val population = (1 to 100).toSet
    val sample = sampler.sample(population, 10)
    
    assertEquals(sample.size, 10, "Should sample exactly 10 elements")
    assert(sample.subsetOf(population), "Sample should be subset of population")
  }
  
  test("sampling more than population returns entire population") {
    val config = HDRConfig(seed3 = 42)
    val sampler = HDRSampler[Int](config)
    
    val population = (1 to 10).toSet
    val sample = sampler.sample(population, 100)
    
    assertEquals(sample, population, "Should return entire population")
  }
  
  test("sampling empty population") {
    val config = HDRConfig(seed3 = 42)
    val sampler = HDRSampler[String](config)
    
    val sample = sampler.sample(Set.empty, 10)
    assert(sample.isEmpty, "Sample of empty population should be empty")
  }
  
  test("sampling zero elements") {
    val config = HDRConfig(seed3 = 42)
    val sampler = HDRSampler[Int](config)
    
    val population = (1 to 100).toSet
    val sample = sampler.sample(population, 0)
    
    assert(sample.isEmpty, "Sample of size 0 should be empty")
  }
  
  test("sample with predicate") {
    val config = HDRConfig(seed3 = 42)
    val sampler = HDRSampler[Int](config)
    
    val population = (1 to 100).toSet
    val (sample, successes) = sampler.sampleWithPredicate(
      population, 
      20, 
      x => x % 2 == 0  // Even numbers
    )
    
    assertEquals(sample.size, 20)
    assert(successes <= 20, "Successes should not exceed sample size")
    assert(successes >= 0, "Successes should be non-negative")
  }
  
  test("auto sample calculates size automatically") {
    val params = SamplingParams.default
    val population = (1 to 1000).toSet
    
    val (sample, sampleSize) = Sampler.autoSample(population, params)
    
    assert(sample.size == sampleSize, "Actual sample size should match reported size")
    assert(sampleSize > 0 && sampleSize <= 1000, "Sample size should be in valid range")
  }
  
  // ==================== ProportionEstimator Tests ====================
  
  test("estimate proportion from sample") {
    val params = SamplingParams.default
    val sample = (1 to 100).toSet
    val predicate = (x: Int) => x <= 50  // 50% of elements
    
    val estimate = ProportionEstimator.estimate(sample, predicate, params)
    
    assertEquals(estimate.proportion, 0.5, 0.01)
    assertEquals(estimate.sampleSize, 100)
    assert(estimate.lower < 0.5 && 0.5 < estimate.upper, "CI should contain true proportion")
  }
  
  test("estimate proportion with sampling") {
    val params = SamplingParams.default
    val population = (1 to 1000).toSet
    val predicate = (x: Int) => x <= 500  // 50% of elements
    
    val estimate = ProportionEstimator.estimateWithSampling(
      population, 
      predicate, 
      params
    )
    
    // Should be close to 0.5
    assert(estimate.proportion >= 0.4 && estimate.proportion <= 0.6, 
      s"Estimate ${estimate.proportion} not close to 0.5")
    assert(estimate.sampleSize < 1000, "Should use sampling, not full enumeration")
  }
  
  test("exact estimate uses full population") {
    val params = SamplingParams.default
    val population = (1 to 100).toSet
    val predicate = (x: Int) => x <= 30  // 30% of elements
    
    val estimate = ProportionEstimator.exactEstimate(
      population, 
      predicate, 
      params
    )
    
    assertEquals(estimate.proportion, 0.3, 0.001)
    assertEquals(estimate.sampleSize, 100)
  }
  
  test("estimate empty population") {
    val params = SamplingParams.default
    val estimate = ProportionEstimator.estimateWithSampling(
      Set.empty[Int], 
      x => true, 
      params
    )
    
    assertEquals(estimate.sampleSize, 0)
    assertEquals(estimate.proportion, 0.0)
  }
  
  test("proportion estimate meets error bound") {
    val params = SamplingParams(epsilon = 0.1, alpha = 0.05)
    val population = (1 to 1000).toSet
    val predicate = (x: Int) => x <= 500
    
    val estimate = ProportionEstimator.estimateWithSampling(
      population, 
      predicate, 
      params
    )
    
    // MOE might be slightly above epsilon due to sampling variance
    // but should be reasonably close
    assert(estimate.marginOfError <= params.epsilon * 1.5, 
      s"MOE ${estimate.marginOfError} exceeds epsilon tolerance")
  }
  
  test("proportion estimate contains target") {
    val params = SamplingParams.default
    val sample = (1 to 100).toSet
    val predicate = (x: Int) => x <= 50
    
    val estimate = ProportionEstimator.estimate(sample, predicate, params)
    
    assert(estimate.contains(0.5), "CI should contain true proportion 0.5")
    assert(!estimate.contains(0.9), "CI should not contain 0.9")
  }
  
  test("proportion estimate overlaps range") {
    val params = SamplingParams.default
    val sample = (1 to 100).toSet
    val predicate = (x: Int) => x <= 50
    
    val estimate = ProportionEstimator.estimate(sample, predicate, params)
    
    assert(estimate.overlaps(0.4, 0.6), "CI should overlap [0.4, 0.6]")
    assert(!estimate.overlaps(0.8, 1.0), "CI should not overlap [0.8, 1.0]")
  }
  
  test("adaptive estimate refines if needed") {
    val params = SamplingParams(epsilon = 0.05, alpha = 0.05)
    val population = (1 to 1000).toSet
    val predicate = (x: Int) => x <= 500
    
    val estimate = ProportionEstimator.adaptiveEstimate(
      population, 
      predicate, 
      params,
      maxIterations = 3
    )
    
    // Adaptive should eventually meet error bound or exhaust iterations
    assert(estimate.sampleSize > 0, "Should have sampled some elements")
  }
  
  test("batch estimate uses single sample") {
    val params = SamplingParams.default
    val population = (1 to 1000).toSet
    
    val predicates = Map(
      "even" -> ((x: Int) => x % 2 == 0),
      "divisible_by_3" -> ((x: Int) => x % 3 == 0),
      "greater_than_500" -> ((x: Int) => x > 500)
    )
    
    val estimates = ProportionEstimator.batchEstimate(
      population, 
      predicates, 
      params
    )
    
    assertEquals(estimates.size, 3)
    assert(estimates.contains("even"))
    assert(estimates.contains("divisible_by_3"))
    assert(estimates.contains("greater_than_500"))
    
    // All estimates should use same sample size
    val sampleSizes = estimates.values.map(_.sampleSize).toSet
    assertEquals(sampleSizes.size, 1, "All estimates should use same sample")
  }
  
  test("significant difference detection") {
    val params = SamplingParams.default
    
    val estimate1 = ProportionEstimate(
      proportion = 0.3,
      sampleSize = 100,
      confidenceInterval = (0.21, 0.39),
      marginOfError = 0.09,
      params = params
    )
    
    val estimate2 = ProportionEstimate(
      proportion = 0.7,
      sampleSize = 100,
      confidenceInterval = (0.61, 0.79),
      marginOfError = 0.09,
      params = params
    )
    
    val estimate3 = ProportionEstimate(
      proportion = 0.35,
      sampleSize = 100,
      confidenceInterval = (0.26, 0.44),
      marginOfError = 0.09,
      params = params
    )
    
    assert(
      ProportionEstimator.significantDifference(estimate1, estimate2, 0.05),
      "Large difference should be significant"
    )
    
    assert(
      !ProportionEstimator.significantDifference(estimate1, estimate3, 0.05),
      "Small difference should not be significant"
    )
  }
  
  // ==================== Integration Tests ====================
  
  test("end-to-end sampling pipeline") {
    val params = SamplingParams.default
    val config = HDRConfig(seed3 = 123)
    val population = (1 to 500).toSet
    val predicate = (x: Int) => x <= 250  // True proportion: 0.5
    
    // Calculate sample size
    val sampleSize = SampleSizeCalculator.calculateSampleSize(
      population.size, 
      params
    )
    
    // Sample using HDR
    val sampler = HDRSampler[Int](config)
    val sample = sampler.sample(population, sampleSize)
    
    // Estimate
    val estimate = ProportionEstimator.estimate(sample, predicate, params)
    
    // Verify
    assert(estimate.proportion >= 0.4 && estimate.proportion <= 0.6,
      s"Estimate ${estimate.proportion} not close to true 0.5")
    assert(estimate.contains(0.5), "CI should contain true proportion")
    assertEquals(estimate.sampleSize, sampleSize)
  }
