package fol.sampling

import munit.FunSuite

/** Test suite for HDR Sampler
  * 
  * Tests the HDR (Hubbard Decision Research) PRNG integration
  * with the Fisher-Yates sampler and 4-layer seed hierarchy.
  */
class HDRSamplerSpec extends FunSuite:
  
  test("HDR sampler with reproducible results") {
    val config = HDRConfig(seed3 = 42)
    val sampler = HDRSampler[Int](config)
    
    val population = (1 to 100).toSet
    val sample1 = sampler.sample(population, 10)
    
    // Reset and sample again with same config
    val sampler2 = sampler.reset()
    val sample2 = sampler2.sample(population, 10)
    
    assertEquals(sample1, sample2, "Same config should produce same sample")
  }
  
  test("HDR sampler with different entity IDs produces different sequences") {
    val config1 = HDRConfig(entityId = 1, seed3 = 42)
    val config2 = HDRConfig(entityId = 2, seed3 = 42)
    
    val sampler1 = HDRSampler[Int](config1)
    val sampler2 = HDRSampler[Int](config2)
    
    val population = (1 to 100).toSet
    val sample1 = sampler1.sample(population, 10)
    val sample2 = sampler2.sample(population, 10)
    
    assert(sample1 != sample2, "Different entity IDs should produce different samples")
  }
  
  test("HDR sampler with different variable IDs produces different sequences") {
    val config1 = HDRConfig(varId = 100, seed3 = 42)
    val config2 = HDRConfig(varId = 200, seed3 = 42)
    
    val sampler1 = HDRSampler[Int](config1)
    val sampler2 = HDRSampler[Int](config2)
    
    val population = (1 to 100).toSet
    val sample1 = sampler1.sample(population, 10)
    val sample2 = sampler2.sample(population, 10)
    
    assert(sample1 != sample2, "Different variable IDs should produce different samples")
  }
  
  test("HDR sampling without replacement") {
    val config = HDRConfig(seed3 = 42)
    val sampler = HDRSampler[Int](config)
    
    val population = (1 to 100).toSet
    val sample = sampler.sample(population, 10)
    
    assertEquals(sample.size, 10, "Should sample exactly 10 elements")
    assert(sample.subsetOf(population), "Sample should be subset of population")
  }
  
  test("HDR sampling more than population returns entire population") {
    val config = HDRConfig(seed3 = 42)
    val sampler = HDRSampler[Int](config)
    
    val population = (1 to 10).toSet
    val sample = sampler.sample(population, 100)
    
    assertEquals(sample, population, "Should return entire population")
  }
  
  test("HDR sampling empty population") {
    val config = HDRConfig(seed3 = 42)
    val sampler = HDRSampler[String](config)
    
    val sample = sampler.sample(Set.empty, 10)
    assert(sample.isEmpty, "Sample of empty population should be empty")
  }
  
  test("HDR sampling zero elements") {
    val config = HDRConfig(seed3 = 42)
    val sampler = HDRSampler[Int](config)
    
    val population = (1 to 100).toSet
    val sample = sampler.sample(population, 0)
    
    assert(sample.isEmpty, "Sample of size 0 should be empty")
  }
  
  test("HDR forEntityVar creates sampler with correct IDs") {
    val sampler = HDRSampler.forEntityVar[Int](
      entityId = 123,
      varId = 456,
      seed3 = 42
    )
    
    val population = (1 to 100).toSet
    val sample = sampler.sample(population, 10)
    
    assertEquals(sample.size, 10)
  }
  
  test("HDR integration with ProportionEstimator") {
    val config = HDRConfig(entityId = 1, varId = 100, seed3 = 42)
    
    val population = (1 to 1000).toSet
    val predicate = (x: Int) => x <= 500  // 50% of elements
    
    val estimate = ProportionEstimator.estimateWithSampling(
      population,
      predicate,
      config = config
    )
    
    // Should be close to 0.5
    assert(estimate.proportion >= 0.4 && estimate.proportion <= 0.6,
      s"Estimate ${estimate.proportion} not close to 0.5")
  }
  
  test("HDR produces consistent results across multiple samples") {
    val config = HDRConfig(entityId = 999, varId = 888, seed3 = 12345)
    
    val population = (1 to 100).toSet
    
    // Sample 3 times with same config
    val sampler1 = HDRSampler[Int](config)
    val sample1 = sampler1.sample(population, 20)
    
    val sampler2 = HDRSampler[Int](config)
    val sample2 = sampler2.sample(population, 20)
    
    val sampler3 = HDRSampler[Int](config)
    val sample3 = sampler3.sample(population, 20)
    
    assertEquals(sample1, sample2)
    assertEquals(sample2, sample3)
  }
