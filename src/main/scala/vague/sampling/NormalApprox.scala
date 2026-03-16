package vague.sampling

/** Pure-Scala standard normal distribution approximations.
  *
  * Provides inverse CDF (quantile function) and CDF without
  * any JVM-only dependencies, enabling cross-compilation to JS.
  *
  * Both algorithms are well-known, peer-reviewed, and widely used
  * in statistical computing libraries.
  */
object NormalApprox:

  /** Inverse standard normal CDF (quantile function).
    *
    * Acklam rational approximation.
    * Maximum relative error < 1.15 × 10⁻⁹ for p ∈ (0, 1).
    *
    * Reference: Peter J. Acklam, "An algorithm for computing the
    * inverse normal cumulative distribution function" (2010).
    *
    * @param p probability in (0, 1)
    * @return z such that Φ(z) = p
    */
  def inverseCDF(p: Double): Double =
    require(p > 0.0 && p < 1.0, s"p must be in (0,1), got $p")

    // Coefficients for rational approximation
    val a1 = -3.969683028665376e+01
    val a2 =  2.209460984245205e+02
    val a3 = -2.759285104469687e+02
    val a4 =  1.383577518672690e+02
    val a5 = -3.066479806614716e+01
    val a6 =  2.506628277459239e+00

    val b1 = -5.447609879822406e+01
    val b2 =  1.615858368580409e+02
    val b3 = -1.556989798598866e+02
    val b4 =  6.680131188771972e+01
    val b5 = -1.328068155288572e+01

    val c1 = -7.784894002430293e-03
    val c2 = -3.223964580411365e-01
    val c3 = -2.400758277161838e+00
    val c4 = -2.549732539343734e+00
    val c5 =  4.374664141464968e+00
    val c6 =  2.938163982698783e+00

    val d1 =  7.784695709041462e-03
    val d2 =  3.224671290700398e-01
    val d3 =  2.445134137142996e+00
    val d4 =  3.754408661907416e+00

    // Break-points
    val pLow  = 0.02425
    val pHigh = 1.0 - pLow

    if p < pLow then
      // Rational approximation for lower region
      val q = math.sqrt(-2.0 * math.log(p))
      (((((c1 * q + c2) * q + c3) * q + c4) * q + c5) * q + c6) /
        ((((d1 * q + d2) * q + d3) * q + d4) * q + 1.0)
    else if p <= pHigh then
      // Rational approximation for central region
      val q = p - 0.5
      val r = q * q
      (((((a1 * r + a2) * r + a3) * r + a4) * r + a5) * r + a6) * q /
        (((((b1 * r + b2) * r + b3) * r + b4) * r + b5) * r + 1.0)
    else
      // Rational approximation for upper region
      val q = math.sqrt(-2.0 * math.log(1.0 - p))
      -(((((c1 * q + c2) * q + c3) * q + c4) * q + c5) * q + c6) /
        ((((d1 * q + d2) * q + d3) * q + d4) * q + 1.0)
  end inverseCDF

  /** Standard normal cumulative distribution function Φ(x).
    *
    * Abramowitz & Stegun rational approximation (formula 26.2.17).
    * Maximum absolute error < 7.5 × 10⁻⁸.
    *
    * Reference: Abramowitz, M. and Stegun, I.A. (1964).
    * Handbook of Mathematical Functions. National Bureau of Standards.
    *
    * @param x value
    * @return Φ(x) = P(Z ≤ x) for Z ~ N(0,1)
    */
  def cdf(x: Double): Double =
    // Constants from Abramowitz & Stegun 26.2.17
    val p  = 0.2316419
    val b1 = 0.319381530
    val b2 = -0.356563782
    val b3 = 1.781477937
    val b4 = -1.821255978
    val b5 = 1.330274429

    val absX = math.abs(x)
    val t = 1.0 / (1.0 + p * absX)
    val t2 = t * t
    val t3 = t2 * t
    val t4 = t3 * t
    val t5 = t4 * t

    // Standard normal PDF at absX
    val phi = math.exp(-0.5 * absX * absX) / math.sqrt(2.0 * math.Pi)

    val result = 1.0 - phi * (b1 * t + b2 * t2 + b3 * t3 + b4 * t4 + b5 * t5)

    if x >= 0.0 then result else 1.0 - result
  end cdf
