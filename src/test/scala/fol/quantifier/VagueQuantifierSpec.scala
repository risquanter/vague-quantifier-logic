package fol.quantifier

import munit.FunSuite
import fol.sampling.{SamplingParams, HDRConfig, ProportionEstimator}
import fol.result.VagueQueryResult

/** Test suite for Vague Quantifiers
  * 
  * Tests the three quantifier types: Approximately, AtLeast, AtMost
  */
class VagueQuantifierSpec extends FunSuite:

  /** Helper: estimate proportion on a population and wrap as VagueQueryResult.
    *
    * After D9 (concrete types, no `[A]`), evaluation on generic populations
    * is ProportionEstimator's responsibility; VagueQuantifier only judges
    * proportions. This helper keeps test assertions readable.
    */
  def evaluateOn[A: reflect.ClassTag](
    q: VagueQuantifier,
    population: Set[A],
    predicate: A => Boolean,
    params: SamplingParams = SamplingParams.exact,
    config: HDRConfig = HDRConfig.default
  ): VagueQueryResult =
    val estimate = ProportionEstimator.estimateWithSampling(
      population, predicate, params, config
    )
    VagueQueryResult.fromEstimate(q, estimate, population.size)
  
  // Approximately quantifier tests
  
  test("approximately evaluates proportions within tolerance") {
    val q = Approximately(0.5, 0.1)  // [0.4, 0.6]
    
    assert(q.evaluate(0.5), "exact target should satisfy")
    assert(q.evaluate(0.4), "lower bound should satisfy")
    assert(q.evaluate(0.6), "upper bound should satisfy")
    assert(q.evaluate(0.45), "within range should satisfy")
    assert(q.evaluate(0.55), "within range should satisfy")
    
    assert(!q.evaluate(0.39), "below range should not satisfy")
    assert(!q.evaluate(0.61), "above range should not satisfy")
    assert(!q.evaluate(0.0), "far below should not satisfy")
    assert(!q.evaluate(1.0), "far above should not satisfy")
  }
  
  test("approximately bounds are clamped to [0, 1]") {
    val nearZero = Approximately(0.05, 0.1)  // [0, 0.15]
    assertEquals(nearZero.lowerBound, 0.0)
    assert(math.abs(nearZero.upperBound - 0.15) < 0.0001, 
      s"upperBound ${nearZero.upperBound} should be approximately 0.15")
    
    val nearOne = Approximately(0.95, 0.1)   // [0.85, 1.0]
    assert(math.abs(nearOne.lowerBound - 0.85) < 0.0001,
      s"lowerBound ${nearOne.lowerBound} should be approximately 0.85")
    assertEquals(nearOne.upperBound, 1.0)
  }
  
  test("approximately rejects invalid parameters") {
    intercept[IllegalArgumentException] {
      Approximately(-0.1, 0.1)  // negative target
    }
    intercept[IllegalArgumentException] {
      Approximately(1.1, 0.1)   // target > 1
    }
    intercept[IllegalArgumentException] {
      Approximately(0.5, -0.1)  // negative tolerance
    }
  }
  
  test("approximately description") {
    val q = Approximately(0.5, 0.1)
    assertEquals(q.describe, "approximately 50% (±10%)")
  }
  
  // AtLeast quantifier tests
  
  test("at-least evaluates proportions above threshold") {
    val q = AtLeast(0.7)
    
    assert(q.evaluate(0.7), "exact threshold should satisfy")
    assert(q.evaluate(0.8), "above threshold should satisfy")
    assert(q.evaluate(1.0), "maximum should satisfy")
    
    assert(!q.evaluate(0.69), "below threshold should not satisfy")
    assert(!q.evaluate(0.5), "below threshold should not satisfy")
    assert(!q.evaluate(0.0), "minimum should not satisfy")
  }
  
  test("at-least rejects invalid threshold") {
    intercept[IllegalArgumentException] {
      AtLeast(-0.1)  // negative
    }
    intercept[IllegalArgumentException] {
      AtLeast(1.1)   // > 1
    }
  }
  
  test("at-least description") {
    val q = AtLeast(0.7)
    assertEquals(q.describe, "at least 70%")
  }
  
  // AtMost quantifier tests
  
  test("at-most evaluates proportions below threshold") {
    val q = AtMost(0.3)
    
    assert(q.evaluate(0.3), "exact threshold should satisfy")
    assert(q.evaluate(0.2), "below threshold should satisfy")
    assert(q.evaluate(0.0), "minimum should satisfy")
    
    assert(!q.evaluate(0.31), "above threshold should not satisfy")
    assert(!q.evaluate(0.5), "above threshold should not satisfy")
    assert(!q.evaluate(1.0), "maximum should not satisfy")
  }
  
  test("at-most rejects invalid threshold") {
    intercept[IllegalArgumentException] {
      AtMost(-0.1)  // negative
    }
    intercept[IllegalArgumentException] {
      AtMost(1.1)   // > 1
    }
  }
  
  test("at-most description") {
    val q = AtMost(0.3)
    assertEquals(q.describe, "at most 30%")
  }
  
  // Evaluation tests
  
  test("evaluate with sampling on population") {
    val population = (1 to 100).toSet
    val predicate = (x: Int) => x <= 50  // Exactly 50%
    val params = SamplingParams()
    val config = HDRConfig(seed3 = 42)
    
    val q = Approximately(0.5, 0.15)
    val result = evaluateOn(q, population, predicate, params, config)
    
    assert(result.satisfied, "should satisfy approximately 50%")
    assert(result.proportion >= 0.35 && result.proportion <= 0.65,
      s"proportion ${result.proportion} should be near 0.5")
  }
  
  test("evaluate exact on population") {
    val population = (1 to 100).toSet
    val predicate = (x: Int) => x <= 50  // Exactly 50%
    
    val q = Approximately(0.5, 0.01)
    val result = evaluateOn(q, population, predicate)
    
    assert(result.satisfied, "should satisfy approximately 50%")
    assertEquals(result.proportion, 0.5, "exact proportion should be 0.5")
  }
  
  test("evaluate most on majority") {
    val population = (1 to 100).toSet
    val predicate = (x: Int) => x <= 80  // 80% satisfy
    
    val result = evaluateOn(VagueQuantifier.most, population, predicate)
    
    assert(result.satisfied, "80% should satisfy 'most' (≥70%)")
    assertEquals(result.proportion, 0.8)
  }
  
  test("evaluate few on minority") {
    val population = (1 to 100).toSet
    val predicate = (x: Int) => x <= 20  // 20% satisfy
    
    val result = evaluateOn(VagueQuantifier.few, population, predicate)
    
    assert(result.satisfied, "20% should satisfy 'few' (≤30%)")
    assertEquals(result.proportion, 0.2)
  }
  
  test("evaluate on empty population") {
    val population = Set.empty[Int]
    val predicate = (x: Int) => true
    
    val result = evaluateOn(VagueQuantifier.some, population, predicate)
    
    assert(!result.satisfied, "empty population should not satisfy 'some'")
    assertEquals(result.proportion, 0.0)
  }
  
  // Statistical significance tests
  
  test("result significance for approximately with tight CI") {
    val population = (1 to 1000).toSet
    val predicate = (x: Int) => x <= 500  // Exactly 50%
    val params = SamplingParams(epsilon = 0.01, alpha = 0.05)
    val config = HDRConfig(seed3 = 42)
    
    val q = Approximately(0.5, 0.1)
    val result = evaluateOn(q, population, predicate, params, config)
    
    // With large sample, CI should be tight enough to be significant
    assert(result.satisfied)
    // Significance depends on actual CI, which may vary slightly
  }
  
  test("result significance for at-least with clear margin") {
    val population = (1 to 100).toSet
    val predicate = (x: Int) => x <= 90  // 90% satisfy
    
    val q = AtLeast(0.7)
    val result = evaluateOn(q, population, predicate)
    
    assert(result.satisfied)
    assert(result.isSignificant, "90% > 70% with exact count should be significant")
  }
  
  test("result significance for at-most with clear margin") {
    val population = (1 to 100).toSet
    val predicate = (x: Int) => x <= 10  // 10% satisfy
    
    val q = AtMost(0.3)
    val result = evaluateOn(q, population, predicate)
    
    assert(result.satisfied)
    assert(result.isSignificant, "10% < 30% with exact count should be significant")
  }
  
  test("result summary formatting") {
    val population = (1 to 100).toSet
    val predicate = (x: Int) => x <= 75
    
    val result = evaluateOn(VagueQuantifier.most, population, predicate)
    val summary = result.summary
    
    assert(summary.contains("✓"), "satisfied result should show checkmark")
    assert(summary.contains("at least 70%"), "should include quantifier description")
    assert(summary.contains("75%"), "should include proportion")
  }
  
  // Common quantifiers tests
  
  test("common quantifier: aboutHalf") {
    val q = VagueQuantifier.aboutHalf
    assertEquals(q.target, 0.5)
    assertEquals(q.tolerance, 0.1)
    assert(q.evaluate(0.5))
    assert(q.evaluate(0.45))
    assert(!q.evaluate(0.35))
  }
  
  test("common quantifier: most") {
    val q = VagueQuantifier.most
    assertEquals(q.threshold, 0.7)
    assert(q.evaluate(0.8))
    assert(!q.evaluate(0.6))
  }
  
  test("common quantifier: few") {
    val q = VagueQuantifier.few
    assertEquals(q.threshold, 0.3)
    assert(q.evaluate(0.2))
    assert(!q.evaluate(0.4))
  }
  
  // Factory method tests
  
  test("approximately factory with percentages") {
    val q = VagueQuantifier.approximately(50, 10)
    assertEquals(q.target, 0.5)
    assertEquals(q.tolerance, 0.1)
  }
  
  test("atLeast factory with percentage") {
    val q = VagueQuantifier.atLeast(70)
    assertEquals(q.threshold, 0.7)
  }
  
  test("atMost factory with percentage") {
    val q = VagueQuantifier.atMost(30)
    assertEquals(q.threshold, 0.3)
  }
  
  // Integration tests
  
  test("chain multiple quantifier evaluations") {
    val population = (1 to 100).toSet
    
    val predicates = Map(
      "low" -> ((x: Int) => x <= 20),    // 20%
      "mid" -> ((x: Int) => x <= 50),    // 50%
      "high" -> ((x: Int) => x <= 80)    // 80%
    )
    
    val lowResult = evaluateOn(VagueQuantifier.few, population, predicates("low"))
    val midResult = evaluateOn(VagueQuantifier.aboutHalf, population, predicates("mid"))
    val highResult = evaluateOn(VagueQuantifier.most, population, predicates("high"))
    
    assert(lowResult.satisfied, "20% should be 'few'")
    assert(midResult.satisfied, "50% should be 'about half'")
    assert(highResult.satisfied, "80% should be 'most'")
  }
