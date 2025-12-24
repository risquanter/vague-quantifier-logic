package vague.sampling

import munit.FunSuite

/** Test suite for HDR Sampler - Phase 13.2
  * 
  * Tests the HDR (Hubbard Decision Research) PRNG integration.
  */
class HDRSamplerSpec extends FunSuite:
  
  test("HDR sampler with reproducible results") {
    val params = SamplingParams(seed = Some(42))
    val sampler = Sampler.hdr[Int](params)
    
    val population = (1 to 100).toSet
    val sample1 = sampler.sample(population, 10)
    
    // Reset and sample again with same seed
    val sampler2 = sampler.reset()
    val sample2 = sampler2.sample(population, 10)
    
    assertEquals(sample1, sample2, "Same seed should produce same sample")
  }
  
  test("HDR sampler with different entity IDs produces different sequences") {
    val params1 = SamplingParams(hdrEntityId = 1, seed = Some(42))
    val params2 = SamplingParams(hdrEntityId = 2, seed = Some(42))
    
    val sampler1 = Sampler.hdr[Int](params1)
    val sampler2 = Sampler.hdr[Int](params2)
    
    val population = (1 to 100).toSet
    val sample1 = sampler1.sample(population, 10)
    val sample2 = sampler2.sample(population, 10)
    
    assert(sample1 != sample2, "Different entity IDs should produce different samples")
  }
  
  test("HDR sampler with different variable IDs produces different sequences") {
    val params1 = SamplingParams(hdrVarId = 100, seed = Some(42))
    val params2 = SamplingParams(hdrVarId = 200, seed = Some(42))
    
    val sampler1 = Sampler.hdr[Int](params1)
    val sampler2 = Sampler.hdr[Int](params2)
    
    val population = (1 to 100).toSet
    val sample1 = sampler1.sample(population, 10)
    val sample2 = sampler2.sample(population, 10)
    
    assert(sample1 != sample2, "Different variable IDs should produce different samples")
  }
  
  test("HDR sampling without replacement") {
    val params = SamplingParams(seed = Some(42))
    val sampler = Sampler.hdr[Int](params)
    
    val population = (1 to 100).toSet
    val sample = sampler.sample(population, 10)
    
    assertEquals(sample.size, 10, "Should sample exactly 10 elements")
    assert(sample.subsetOf(population), "Sample should be subset of population")
  }
  
  test("HDR sampling more than population returns entire population") {
    val params = SamplingParams(seed = Some(42))
    val sampler = Sampler.hdr[Int](params)
    
    val population = (1 to 10).toSet
    val sample = sampler.sample(population, 100)
    
    assertEquals(sample, population, "Should return entire population")
  }
  
  test("HDR sampling empty population") {
    val params = SamplingParams(seed = Some(42))
    val sampler = Sampler.hdr[String](params)
    
    val sample = sampler.sample(Set.empty, 10)
    assert(sample.isEmpty, "Sample of empty population should be empty")
  }
  
  test("HDR sampling zero elements") {
    val params = SamplingParams(seed = Some(42))
    val sampler = Sampler.hdr[Int](params)
    
    val population = (1 to 100).toSet
    val sample = sampler.sample(population, 0)
    
    assert(sample.isEmpty, "Sample of size 0 should be empty")
  }
  
  test("HDR forEntityVar creates sampler with correct IDs") {
    val sampler = HDRSampler.forEntityVar[Int](
      entityId = 123,
      varId = 456,
      params = SamplingParams(seed = Some(42))
    )
    
    val population = (1 to 100).toSet
    val sample = sampler.sample(population, 10)
    
    assertEquals(sample.size, 10)
  }
  
  test("HDR integration with ProportionEstimator") {
    val params = SamplingParams(
      hdrEntityId = 1,
      hdrVarId = 100,
      seed = Some(42)
    )
    
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
  }
  
  test("HDR produces consistent results across multiple samples") {
    val params = SamplingParams(
      hdrEntityId = 999,
      hdrVarId = 888,
      seed = Some(12345)
    )
    
    val population = (1 to 100).toSet
    
    // Sample 3 times with same parameters
    val sampler1 = Sampler.hdr[Int](params)
    val sample1 = sampler1.sample(population, 20)
    
    val sampler2 = Sampler.hdr[Int](params)
    val sample2 = sampler2.sample(population, 20)
    
    val sampler3 = Sampler.hdr[Int](params)
    val sample3 = sampler3.sample(population, 20)
    
    assertEquals(sample1, sample2)
    assertEquals(sample2, sample3)
  }
