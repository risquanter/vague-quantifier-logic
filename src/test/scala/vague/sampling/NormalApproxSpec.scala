package vague.sampling

import munit.FunSuite

/** Comprehensive tests for NormalApprox — pure-Scala normal distribution
  * approximations replacing Apache Commons Math3.
  *
  * Test strategy:
  * 1. Known reference values from statistical tables (NIST, textbooks)
  * 2. Symmetry and boundary properties
  * 3. Roundtrip: inverseCDF(cdf(x)) ≈ x
  * 4. Cross-validation at the exact values the existing SamplingSpec tests
  *    expect (z-scores for α = 0.05, 0.01, 0.10)
  * 5. Monotonicity
  * 6. Error bound verification
  */
class NormalApproxSpec extends FunSuite:

  // --------------- inverseCDF (Acklam) ---------------

  test("inverseCDF — 50th percentile (median) is 0") {
    assertEqualsDouble(NormalApprox.inverseCDF(0.5), 0.0, 1e-10)
  }

  test("inverseCDF — standard z-scores from NIST tables") {
    // p → expected z (from NIST/Sematech e-Handbook, Table A.1)
    val referenceValues = List(
      (0.0010, -3.0902),
      (0.0050, -2.5758),
      (0.0100, -2.3263),
      (0.0250, -1.9600),
      (0.0500, -1.6449),
      (0.1000, -1.2816),
      (0.2000, -0.8416),
      (0.3000, -0.5244),
      (0.4000, -0.2533),
      (0.5000,  0.0000),
      (0.6000,  0.2533),
      (0.7000,  0.5244),
      (0.8000,  0.8416),
      (0.9000,  1.2816),
      (0.9500,  1.6449),
      (0.9750,  1.9600),
      (0.9900,  2.3263),
      (0.9950,  2.5758),
      (0.9990,  3.0902)
    )

    referenceValues.foreach { (p, expected) =>
      val actual = NormalApprox.inverseCDF(p)
      val absDiff = math.abs(actual - expected)
      assert(
        absDiff < 0.0005,
        s"inverseCDF($p): expected $expected, got $actual (diff=$absDiff)"
      )
    }
  }

  test("inverseCDF — z-scores used by existing SamplingSpec tests") {
    // These are the exact values the SamplingSpec z-score tests compare against.
    // Previously produced by Apache Commons Math3; our approximation must match
    // to within the 0.001 tolerance used by those tests.

    // α = 0.05 → p = 0.975 → z ≈ 1.96
    val z95 = NormalApprox.inverseCDF(0.975)
    assert(math.abs(z95 - 1.96) < 0.001, s"95%: $z95")

    // α = 0.01 → p = 0.995 → z ≈ 2.576
    val z99 = NormalApprox.inverseCDF(0.995)
    assert(math.abs(z99 - 2.576) < 0.001, s"99%: $z99")

    // α = 0.10 → p = 0.95 → z ≈ 1.645
    val z90 = NormalApprox.inverseCDF(0.95)
    assert(math.abs(z90 - 1.645) < 0.001, s"90%: $z90")
  }

  test("inverseCDF — symmetry: inverseCDF(p) = -inverseCDF(1-p)") {
    val probabilities = List(0.01, 0.05, 0.10, 0.25, 0.40)
    probabilities.foreach { p =>
      val lower = NormalApprox.inverseCDF(p)
      val upper = NormalApprox.inverseCDF(1.0 - p)
      assertEqualsDouble(lower, -upper, 1e-10,
        s"Symmetry violated at p=$p: inverseCDF($p)=$lower, inverseCDF(${1-p})=$upper")
    }
  }

  test("inverseCDF — monotonically increasing") {
    val probabilities = (1 to 99).map(_ / 100.0)
    val values = probabilities.map(NormalApprox.inverseCDF)
    values.sliding(2).foreach { window =>
      val Seq(a, b) = window
      assert(a < b, s"Not monotonic: $a >= $b")
    }
  }

  test("inverseCDF — extreme tails (near 0 and 1)") {
    // Very low probability
    val zLow = NormalApprox.inverseCDF(0.0001)
    assert(zLow < -3.5 && zLow > -4.0, s"Expected z ∈ (-4, -3.5), got $zLow")

    // Very high probability
    val zHigh = NormalApprox.inverseCDF(0.9999)
    assert(zHigh > 3.5 && zHigh < 4.0, s"Expected z ∈ (3.5, 4), got $zHigh")

    // Symmetry at extremes
    assertEqualsDouble(zLow, -zHigh, 1e-10)
  }

  test("inverseCDF — rejects out-of-range input") {
    intercept[IllegalArgumentException] { NormalApprox.inverseCDF(0.0) }
    intercept[IllegalArgumentException] { NormalApprox.inverseCDF(1.0) }
    intercept[IllegalArgumentException] { NormalApprox.inverseCDF(-0.1) }
    intercept[IllegalArgumentException] { NormalApprox.inverseCDF(1.5) }
  }

  // --------------- cdf (Abramowitz & Stegun) ---------------

  test("cdf — Φ(0) = 0.5") {
    // A&S approximation has tiny bias at 0: ~5e-10
    assertEqualsDouble(NormalApprox.cdf(0.0), 0.5, 1e-8)
  }

  test("cdf — standard reference values from NIST tables") {
    // x → expected Φ(x) (from standard normal table)
    val referenceValues = List(
      (-3.0, 0.001350),
      (-2.5, 0.006210),
      (-2.0, 0.022750),
      (-1.5, 0.066807),
      (-1.0, 0.158655),
      (-0.5, 0.308538),
      ( 0.0, 0.500000),
      ( 0.5, 0.691462),
      ( 1.0, 0.841345),
      ( 1.5, 0.933193),
      ( 2.0, 0.977250),
      ( 2.5, 0.993790),
      ( 3.0, 0.998650)
    )

    referenceValues.foreach { (x, expected) =>
      val actual = NormalApprox.cdf(x)
      val absDiff = math.abs(actual - expected)
      assert(
        absDiff < 1e-4,
        s"cdf($x): expected $expected, got $actual (diff=$absDiff)"
      )
    }
  }

  test("cdf — symmetry: Φ(x) + Φ(-x) = 1") {
    val xValues = List(0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5)
    xValues.foreach { x =>
      val sum = NormalApprox.cdf(x) + NormalApprox.cdf(-x)
      // A&S symmetry error is ≤ 2 × max absolute error ≈ 1.5e-7
      assertEqualsDouble(sum, 1.0, 1.5e-7,
        s"Symmetry violated at x=$x: Φ($x) + Φ(${-x}) = $sum")
    }
  }

  test("cdf — monotonically increasing") {
    val xValues = (-40 to 40).map(_ / 10.0)
    val cdfs = xValues.map(NormalApprox.cdf)
    cdfs.sliding(2).foreach { window =>
      val Seq(a, b) = window
      assert(a <= b, s"Not monotonic: cdf values $a > $b")
    }
  }

  test("cdf — bounds: always in [0, 1]") {
    val xValues = List(-10.0, -5.0, -3.0, 0.0, 3.0, 5.0, 10.0)
    xValues.foreach { x =>
      val c = NormalApprox.cdf(x)
      assert(c >= 0.0 && c <= 1.0, s"cdf($x) = $c out of [0,1]")
    }
  }

  test("cdf — extreme values converge to 0 and 1") {
    assert(NormalApprox.cdf(-8.0) < 1e-12, s"cdf(-8) should be ≈ 0")
    assert(NormalApprox.cdf(8.0) > 1.0 - 1e-12, s"cdf(8) should be ≈ 1")
  }

  test("cdf — maximum absolute error < 7.5e-8 at known test points") {
    // High-precision reference values (15 decimal digits, from Wolfram Alpha)
    val highPrecisionRefs = List(
      (-3.0, 0.001349898031630),
      (-2.0, 0.022750131948179),
      (-1.0, 0.158655253931457),
      ( 0.0, 0.500000000000000),
      ( 1.0, 0.841344746068543),
      ( 2.0, 0.977249868051821),
      ( 3.0, 0.998650101968370)
    )

    highPrecisionRefs.foreach { (x, expected) =>
      val actual = NormalApprox.cdf(x)
      val absDiff = math.abs(actual - expected)
      assert(
        absDiff < 7.5e-8,
        s"cdf($x): error $absDiff exceeds 7.5e-8 bound (actual=$actual, expected=$expected)"
      )
    }
  }

  // --------------- Roundtrip: inverseCDF ∘ cdf ≈ identity ---------------

  test("roundtrip — inverseCDF(cdf(x)) ≈ x for central region") {
    val xValues = List(-2.5, -2.0, -1.5, -1.0, -0.5, 0.0, 0.5, 1.0, 1.5, 2.0, 2.5)
    xValues.foreach { x =>
      val roundtrip = NormalApprox.inverseCDF(NormalApprox.cdf(x))
      // Compound error from both approximations; tightest achievable is ~2e-6
      assertEqualsDouble(roundtrip, x, 5e-6,
        s"roundtrip failed at x=$x: got $roundtrip")
    }
  }

  test("roundtrip — cdf(inverseCDF(p)) ≈ p") {
    val probabilities = List(0.01, 0.05, 0.10, 0.25, 0.50, 0.75, 0.90, 0.95, 0.99)
    probabilities.foreach { p =>
      val roundtrip = NormalApprox.cdf(NormalApprox.inverseCDF(p))
      assertEqualsDouble(roundtrip, p, 1e-6,
        s"roundtrip failed at p=$p: got $roundtrip")
    }
  }

  // --------------- Integration with SamplingParams ---------------

  test("SamplingParams.zScore uses NormalApprox and matches expected values") {
    // These are the values the existing SamplingSpec tests assert
    val params95 = SamplingParams(alpha = 0.05)
    assert(math.abs(params95.zScore - 1.96) < 0.001,
      s"95% z-score ${params95.zScore} should be ≈ 1.96")

    val params99 = SamplingParams(alpha = 0.01)
    assert(math.abs(params99.zScore - 2.576) < 0.001,
      s"99% z-score ${params99.zScore} should be ≈ 2.576")

    val params90 = SamplingParams(alpha = 0.10)
    assert(math.abs(params90.zScore - 1.645) < 0.001,
      s"90% z-score ${params90.zScore} should be ≈ 1.645")
  }
