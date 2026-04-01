package fol.sampling

import munit.FunSuite

/** Thorough test suite for Fisher-Yates shuffle powered by HDR PRNG.
  *
  * Properties tested:
  * 1.  Correctness — samples are valid subsets of the population
  * 2.  Without replacement — no duplicates (guaranteed by Fisher-Yates)
  * 3.  Exact size — always returns exactly n elements when n ≤ N
  * 4.  Determinism — same HDRConfig → same sample, every time
  * 5.  Entity isolation — different entityId → different sample
  * 6.  Variable isolation — different varId → different sample
  * 7.  Seed3 isolation — different seed3 → different sample
  * 8.  Seed4 isolation — different seed4 → different sample
  * 9.  Uniformity — over many configs, selection frequency is approximately flat
  * 10. Boundary cases — n=0, n=1, n=N-1, n=N, n>N, empty, singleton
  * 11. Index safety — Fisher-Yates j never falls outside [i, N)
  * 12. Type polymorphism — Int, String, case class
  * 13. sampleWithPredicate correctness
  * 14. Stability across reset
  * 15. No correlation between adjacent config values
  */
class FisherYatesHDRSpec extends FunSuite:

  // ==================== 1. Correctness (subset) ====================

  test("sample is always a subset of the population") {
    val config = HDRConfig(entityId = 1, varId = 2, seed3 = 3, seed4 = 4)
    val sampler = HDRSampler[Int](config)
    val population = (1 to 200).toSet

    for n <- List(1, 5, 10, 50, 100, 199) do
      val sample = sampler.sample(population, n)
      assert(sample.subsetOf(population),
        s"Sample of size $n is not a subset of population")
  }

  // ==================== 2. Without replacement ====================

  test("Fisher-Yates guarantees no duplicates") {
    // The Set representation ensures uniqueness, but let's verify via
    // array length == set size to catch any Fisher-Yates indexing bug.
    val config = HDRConfig(seed3 = 42)
    val sampler = HDRSampler[Int](config)
    val population = (1 to 500).toSet

    for n <- List(1, 10, 50, 100, 250, 499) do
      val sample = sampler.sample(population, n)
      assertEquals(sample.size, n,
        s"Expected exactly $n unique elements, got ${sample.size}")
  }

  // ==================== 3. Exact size ====================

  test("sample size is exactly n when n < N") {
    val config = HDRConfig.default
    val sampler = HDRSampler[Int](config)
    val population = (1 to 1000).toSet

    for n <- List(1, 2, 10, 100, 500, 999) do
      assertEquals(sampler.sample(population, n).size, n)
  }

  test("sample size is N when n >= N") {
    val config = HDRConfig.default
    val sampler = HDRSampler[Int](config)
    val population = (1 to 50).toSet

    assertEquals(sampler.sample(population, 50).size, 50)
    assertEquals(sampler.sample(population, 51).size, 50)
    assertEquals(sampler.sample(population, 1000).size, 50)
  }

  // ==================== 4. Determinism ====================

  test("same HDRConfig always produces the same sample") {
    val config = HDRConfig(entityId = 7, varId = 13, seed3 = 99, seed4 = 1001)
    val population = (1 to 100).toSet

    // Create 5 independent sampler instances — all should agree
    val samples = (1 to 5).map { _ =>
      HDRSampler[Int](config).sample(population, 20)
    }

    samples.tail.foreach { s =>
      assertEquals(s, samples.head,
        "Independent HDRSampler instances with same config must produce identical samples")
    }
  }

  test("reset produces identical sample") {
    val config = HDRConfig(seed3 = 42)
    val sampler = HDRSampler[Int](config)
    val population = (1 to 100).toSet

    val sample1 = sampler.sample(population, 15)
    val sample2 = sampler.reset().sample(population, 15)

    assertEquals(sample1, sample2)
  }

  // ==================== 5. Entity isolation ====================

  test("different entityId produces different samples") {
    val population = (1 to 100).toSet
    val config1 = HDRConfig(entityId = 1)
    val config2 = HDRConfig(entityId = 2)

    val s1 = HDRSampler[Int](config1).sample(population, 20)
    val s2 = HDRSampler[Int](config2).sample(population, 20)

    assert(s1 != s2, "entityId=1 and entityId=2 should produce different samples")
  }

  test("many different entityIds all produce distinct samples") {
    val population = (1 to 100).toSet
    val samples = (0L until 20L).map { eid =>
      HDRSampler[Int](HDRConfig(entityId = eid)).sample(population, 15)
    }.toSet

    // With 20 different entityIds and 15 out of 100, collisions are astronomically unlikely
    assert(samples.size >= 18,
      s"Expected ≥18 distinct samples from 20 entityIds, got ${samples.size}")
  }

  // ==================== 6. Variable isolation ====================

  test("different varId produces different samples") {
    val population = (1 to 100).toSet
    val config1 = HDRConfig(varId = 100)
    val config2 = HDRConfig(varId = 200)

    val s1 = HDRSampler[Int](config1).sample(population, 20)
    val s2 = HDRSampler[Int](config2).sample(population, 20)

    assert(s1 != s2, "varId=100 and varId=200 should produce different samples")
  }

  test("ADR-003 variable offset pattern works (occurrence +1000, loss +2000)") {
    // Mimics the register pattern: same entity, different variable offsets
    val population = (1 to 100).toSet
    val riskHash = 42L

    val occurrenceConfig = HDRConfig(entityId = riskHash, varId = riskHash + 1000L)
    val lossConfig       = HDRConfig(entityId = riskHash, varId = riskHash + 2000L)

    val occurrenceSample = HDRSampler[Int](occurrenceConfig).sample(population, 20)
    val lossSample       = HDRSampler[Int](lossConfig).sample(population, 20)

    assert(occurrenceSample != lossSample,
      "Occurrence and loss variable streams should be independent")
  }

  // ==================== 7. Seed3 isolation ====================

  test("different seed3 produces different samples") {
    val population = (1 to 100).toSet
    val config1 = HDRConfig(seed3 = 0L)
    val config2 = HDRConfig(seed3 = 12345L)

    val s1 = HDRSampler[Int](config1).sample(population, 20)
    val s2 = HDRSampler[Int](config2).sample(population, 20)

    assert(s1 != s2, "Different seed3 should produce different samples")
  }

  // ==================== 8. Seed4 isolation ====================

  test("different seed4 produces different samples") {
    val population = (1 to 100).toSet
    val config1 = HDRConfig(seed4 = 0L)
    val config2 = HDRConfig(seed4 = 99999L)

    val s1 = HDRSampler[Int](config1).sample(population, 20)
    val s2 = HDRSampler[Int](config2).sample(population, 20)

    assert(s1 != s2, "Different seed4 should produce different samples")
  }

  // ==================== 9. Uniformity ====================

  test("selection frequency is approximately uniform across many HDR configs") {
    // For each config, sample 10 out of 20.
    // Over 1000 configs, each element should be selected ~500 times (50%).
    // We accept within [400, 600] (±20% of expected).
    val population = (1 to 20).toSet
    val n = 10
    val numTrials = 1000

    val counts = scala.collection.mutable.Map.empty[Int, Int].withDefaultValue(0)

    for trial <- 0 until numTrials do
      val config = HDRConfig(entityId = trial.toLong, seed3 = 7)
      val sample = HDRSampler[Int](config).sample(population, n)
      sample.foreach(elem => counts(elem) += 1)

    val expected = numTrials * n.toDouble / population.size  // 500
    val tolerance = expected * 0.2  // ±100

    population.foreach { elem =>
      val count = counts(elem)
      assert(count >= (expected - tolerance).toInt && count <= (expected + tolerance).toInt,
        s"Element $elem selected $count times, expected ≈${expected.toInt} ±${tolerance.toInt}")
    }
  }

  test("pairwise selection frequency is approximately uniform") {
    // For a fair shuffle, each pair (i, j) should appear together at roughly
    // C(n,2)/C(N,2) * numTrials times. We just check that no pair is grossly
    // over- or under-represented compared to others.
    val population = (1 to 10).toSet
    val n = 5
    val numTrials = 2000

    val pairCounts = scala.collection.mutable.Map.empty[(Int, Int), Int].withDefaultValue(0)

    for trial <- 0 until numTrials do
      val config = HDRConfig(entityId = trial.toLong, varId = 1L)
      val sample = HDRSampler[Int](config).sample(population, n).toVector.sorted
      for
        i <- sample.indices
        j <- i + 1 until sample.size
      do
        pairCounts((sample(i), sample(j))) += 1

    val counts = pairCounts.values.toVector
    val mean = counts.sum.toDouble / counts.size
    val max = counts.max
    val min = counts.min

    // No pair should be more than 3× the frequency of the rarest pair
    assert(max.toDouble / min.toDouble < 3.0,
      s"Pair frequency ratio too high: max=$max, min=$min, mean=${mean.toInt}")
  }

  // ==================== 10. Boundary cases ====================

  test("sample 0 from non-empty population") {
    val sampler = HDRSampler[Int](HDRConfig.default)
    val sample = sampler.sample((1 to 100).toSet, 0)
    assert(sample.isEmpty)
  }

  test("sample from empty population") {
    val sampler = HDRSampler[String](HDRConfig.default)
    val sample = sampler.sample(Set.empty[String], 10)
    assert(sample.isEmpty)
  }

  test("sample 0 from empty population") {
    val sampler = HDRSampler[Int](HDRConfig.default)
    val sample = sampler.sample(Set.empty[Int], 0)
    assert(sample.isEmpty)
  }

  test("sample 1 from singleton population") {
    val sampler = HDRSampler[Int](HDRConfig.default)
    val sample = sampler.sample(Set(42), 1)
    assertEquals(sample, Set(42))
  }

  test("sample 1 from large population") {
    val sampler = HDRSampler[Int](HDRConfig(seed3 = 42))
    val sample = sampler.sample((1 to 10000).toSet, 1)
    assertEquals(sample.size, 1)
    assert(sample.head >= 1 && sample.head <= 10000)
  }

  test("sample N-1 from population of N") {
    val population = (1 to 10).toSet
    val sampler = HDRSampler[Int](HDRConfig(seed3 = 7))
    val sample = sampler.sample(population, 9)
    assertEquals(sample.size, 9)
    assert(sample.subsetOf(population))
    // Exactly one element should be missing
    assertEquals((population -- sample).size, 1)
  }

  test("sample N from population of N returns the full population") {
    val population = (1 to 10).toSet
    val sampler = HDRSampler[Int](HDRConfig.default)
    assertEquals(sampler.sample(population, 10), population)
  }

  test("sample N+1 from population of N returns the full population") {
    val population = (1 to 10).toSet
    val sampler = HDRSampler[Int](HDRConfig.default)
    assertEquals(sampler.sample(population, 11), population)
  }

  test("negative sample size throws") {
    val sampler = HDRSampler[Int](HDRConfig.default)
    intercept[IllegalArgumentException] {
      sampler.sample((1 to 10).toSet, -1)
    }
  }

  test("population of size 2, sample 1 — both elements reachable") {
    // Over many configs, element 'a' and 'b' should both appear
    val population = Set("a", "b")
    val results = (0L until 100L).map { eid =>
      HDRSampler[String](HDRConfig(entityId = eid)).sample(population, 1)
    }

    val allSelected = results.flatten.toSet
    assertEquals(allSelected, Set("a", "b"),
      "Both elements of a 2-element population must be reachable with n=1")
  }

  // ==================== 11. Index safety ====================

  test("Fisher-Yates j is always in [i, N) — stress test with many sizes") {
    // If j ever fell outside bounds, toArray access would throw ArrayIndexOutOfBoundsException.
    // We test many population sizes and sample sizes to stress the index arithmetic.
    for N <- List(1, 2, 3, 5, 10, 50, 100, 1000) do
      val population = (1 to N).toSet
      for n <- List(0, 1, N / 2, N - 1, N, N + 1).filter(_ >= 0) do
        val config = HDRConfig(entityId = N.toLong, varId = n.toLong, seed3 = 999)
        val sampler = HDRSampler[Int](config)
        val sample = sampler.sample(population, n)
        assert(sample.subsetOf(population),
          s"N=$N, n=$n: sample not a subset of population")
  }

  // ==================== 12. Type polymorphism ====================

  test("works with String elements") {
    val population = Set("alpha", "bravo", "charlie", "delta", "echo",
                         "foxtrot", "golf", "hotel", "india", "juliet")
    val sampler = HDRSampler[String](HDRConfig(seed3 = 42))
    val sample = sampler.sample(population, 5)

    assertEquals(sample.size, 5)
    assert(sample.subsetOf(population))
  }

  test("works with case class elements") {
    case class Point(x: Int, y: Int)
    val population = (for x <- 0 to 4; y <- 0 to 4 yield Point(x, y)).toSet  // 25 points
    val sampler = HDRSampler[Point](HDRConfig(seed3 = 42))
    val sample = sampler.sample(population, 10)

    assertEquals(sample.size, 10)
    assert(sample.subsetOf(population))
  }

  test("works with Long elements") {
    val population = (1L to 100L).toSet
    val sampler = HDRSampler[Long](HDRConfig(seed3 = 42))
    val sample = sampler.sample(population, 20)

    assertEquals(sample.size, 20)
    assert(sample.subsetOf(population))
  }

  test("works with Double elements") {
    val population = (1 to 50).map(_ * 0.1).toSet
    val sampler = HDRSampler[Double](HDRConfig(seed3 = 42))
    val sample = sampler.sample(population, 10)

    assertEquals(sample.size, 10)
    assert(sample.subsetOf(population))
  }

  // ==================== 13. sampleWithPredicate ====================

  test("sampleWithPredicate counts successes correctly") {
    val config = HDRConfig(seed3 = 42)
    val sampler = HDRSampler[Int](config)
    val population = (1 to 100).toSet
    val predicate: Int => Boolean = _ % 2 == 0  // even numbers

    val (sample, successes) = sampler.sampleWithPredicate(population, 20, predicate)

    assertEquals(sample.size, 20)
    assertEquals(successes, sample.count(predicate),
      "Reported successes must match actual count of predicate-satisfying elements")
    assert(successes >= 0 && successes <= 20)
  }

  test("sampleWithPredicate with always-true predicate") {
    val sampler = HDRSampler[Int](HDRConfig(seed3 = 1))
    val population = (1 to 50).toSet
    val (sample, successes) = sampler.sampleWithPredicate(population, 20, _ => true)

    assertEquals(successes, sample.size)
  }

  test("sampleWithPredicate with always-false predicate") {
    val sampler = HDRSampler[Int](HDRConfig(seed3 = 1))
    val population = (1 to 50).toSet
    val (sample, successes) = sampler.sampleWithPredicate(population, 20, _ => false)

    assertEquals(successes, 0)
  }

  // ==================== 14. Stability across reset ====================

  test("multiple resets all produce identical results") {
    val config = HDRConfig(entityId = 5, varId = 10, seed3 = 100, seed4 = 200)
    val population = (1 to 100).toSet
    val sampler = HDRSampler[Int](config)

    val original = sampler.sample(population, 25)
    for _ <- 1 to 10 do
      val fresh = sampler.reset()
      assertEquals(fresh.sample(population, 25), original)
  }

  // ==================== 15. No correlation between adjacent configs ====================

  test("sequential entityIds produce uncorrelated samples") {
    // Verify that entityId=n and entityId=n+1 don't share an unusually
    // high overlap. Expected overlap for two random 20-of-100 samples is
    // ~4 elements (hypergeometric mean = n²/N). We flag if overlap > 12.
    val population = (1 to 100).toSet
    val n = 20
    val maxExpectedOverlap = 12  // generous: E[overlap] ≈ 4, this is 3× that

    for eid <- 0L until 50L do
      val s1 = HDRSampler[Int](HDRConfig(entityId = eid)).sample(population, n)
      val s2 = HDRSampler[Int](HDRConfig(entityId = eid + 1)).sample(population, n)
      val overlap = (s1 intersect s2).size
      assert(overlap <= maxExpectedOverlap,
        s"entityId=$eid and ${eid + 1} have overlap=$overlap (max=$maxExpectedOverlap)")
  }

  test("sequential seed3 values produce uncorrelated samples") {
    val population = (1 to 100).toSet
    val n = 20
    val maxExpectedOverlap = 12

    for s3 <- 0L until 50L do
      val s1 = HDRSampler[Int](HDRConfig(seed3 = s3)).sample(population, n)
      val s2 = HDRSampler[Int](HDRConfig(seed3 = s3 + 1)).sample(population, n)
      val overlap = (s1 intersect s2).size
      assert(overlap <= maxExpectedOverlap,
        s"seed3=$s3 and ${s3 + 1} have overlap=$overlap (max=$maxExpectedOverlap)")
  }

  // ==================== 16. Large population stress ====================

  test("Fisher-Yates handles large population (100k) without error") {
    val population = (1 to 100_000).toSet
    val config = HDRConfig(entityId = 42, seed3 = 7)
    val sampler = HDRSampler[Int](config)

    val sample = sampler.sample(population, 500)
    assertEquals(sample.size, 500)
    assert(sample.subsetOf(population))
  }

  test("sampling 1 from large population is fast and correct") {
    val population = (1 to 100_000).toSet
    val config = HDRConfig(seed3 = 42)
    val sampler = HDRSampler[Int](config)

    val t0 = System.nanoTime()
    val sample = sampler.sample(population, 1)
    val elapsed = (System.nanoTime() - t0) / 1_000_000  // ms

    assertEquals(sample.size, 1)
    // Fisher-Yates partial: should only do 1 swap, but toArray is O(N).
    // Just verify it completes in reasonable time (< 5 seconds).
    assert(elapsed < 5000, s"Sampling 1 from 100k took ${elapsed}ms")
  }

  // ==================== 17. forEntityVar factory ====================

  test("forEntityVar produces same result as manual HDRConfig") {
    val population = (1 to 100).toSet

    val viaFactory = HDRSampler.forEntityVar[Int](
      entityId = 10, varId = 20, seed3 = 30, seed4 = 40
    ).sample(population, 15)

    val viaConfig = HDRSampler[Int](
      HDRConfig(entityId = 10, varId = 20, seed3 = 30, seed4 = 40)
    ).sample(population, 15)

    assertEquals(viaFactory, viaConfig)
  }

  test("forEntityVar defaults seed3/seed4 to 0") {
    val population = (1 to 100).toSet

    val withDefaults = HDRSampler.forEntityVar[Int](entityId = 10, varId = 20)
      .sample(population, 15)
    val explicit = HDRSampler[Int](HDRConfig(entityId = 10, varId = 20, seed3 = 0, seed4 = 0))
      .sample(population, 15)

    assertEquals(withDefaults, explicit)
  }

  // ==================== 18. Permutation order not trivial ====================

  test("sample is not just the first n elements of the population") {
    // If Fisher-Yates weren't shuffling, we'd get the iteration order of Set.
    // Verify we're actually shuffling.
    val population = (1 to 100).toSet
    val config = HDRConfig(seed3 = 42)
    val sample = HDRSampler[Int](config).sample(population, 10)

    // The first 10 elements of Set iteration are implementation-dependent,
    // but if the sample exactly matches them, that's suspicious.
    val iterationOrder = population.take(10)
    assert(sample != iterationOrder,
      "Sample should not be the trivial first-n elements of Set iteration order")
  }

  // ==================== 19. Chi-squared goodness of fit ====================

  test("chi-squared test for uniform selection (p > 0.01)") {
    // Each element of a 20-element population should be selected with
    // probability n/N = 10/20 = 0.5 over many trials.
    // Chi-squared test with 19 degrees of freedom.
    val N = 20
    val n = 10
    val numTrials = 5000
    val expected = numTrials.toDouble * n / N  // 2500

    val counts = Array.fill(N)(0)

    for trial <- 0 until numTrials do
      val config = HDRConfig(entityId = trial.toLong)
      val sample = HDRSampler[Int](config).sample((1 to N).toSet, n)
      sample.foreach(elem => counts(elem - 1) += 1)

    // Chi-squared statistic: Σ (observed - expected)² / expected
    val chiSq = counts.map { observed =>
      val diff = observed - expected
      diff * diff / expected
    }.sum

    // Critical value for df=19, α=0.01 is ~36.19
    // If chiSq < 36.19, the distribution is consistent with uniform at 1% significance
    assert(chiSq < 36.19,
      s"Chi-squared = $chiSq exceeds critical value 36.19 (df=19, α=0.01). " +
      s"Selection may not be uniform. Counts: ${counts.mkString(", ")}")
  }
