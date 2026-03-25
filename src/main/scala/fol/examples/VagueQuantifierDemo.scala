package fol.examples

import fol.quantifier.*
import fol.sampling.{SamplingParams, HDRConfig, ProportionEstimator}
import fol.datastore.{KnowledgeBase, RelationName, RiskDomain, RelationValue}
import fol.result.VagueQueryResult

/** Demonstration of Vague Quantifier Usage
  * 
  * This demo shows how to use vague quantifiers for proportional reasoning
  * over populations. Vague quantifiers allow expressing statements like:
  * - "Most employees are satisfied" (at least 70%)
  * - "About half the components are critical" (approximately 50% ±10%)
  * - "Few vulnerabilities are exploitable" (at most 30%)
  * 
  * The demo focuses on the API usage of vague quantifiers,
  * but runs on dummy data for illustration purposes only.
  * 
  * 
  * Based on the paper:
  * Fermüller, Hofer, and Ortiz (2017).
  * "Querying with Vague Quantifiers Using Probabilistic Semantics"
  * 
  * The system uses statistical sampling to evaluate these quantifiers
  * efficiently on large populations, with configurable error bounds
  * and confidence levels.
  */
object VagueQuantifierDemo:

  /** Helper: estimate proportion on a population and wrap as VagueQueryResult. */
  private def evaluateOn[A: reflect.ClassTag](
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

  def main(args: Array[String]): Unit =
    println("=" * 70)
    println("Vague Quantifier Demo")
    println("=" * 70)
    println()
    
    // Run all demo sections
    basicQuantifierDemo()
    println()
    
    samplingDemo()
    println()
    
    customQuantifierDemo()
    println()
    
    knowledgeBaseDemo()
    println()
    
    statisticalSignificanceDemo()
    println()
    
    println("=" * 70)
    println("Demo Complete!")
    println("=" * 70)
  
  /** Basic demonstration of the three quantifier types. */
  def basicQuantifierDemo(): Unit =
    println("1. BASIC QUANTIFIER TYPES")
    println("-" * 70)
    
    // Example population: survey responses from 100 employees
    val employees = (1 to 100).toSet
    
    // Scenario 1: "Most employees are satisfied" (75% satisfaction)
    // We define "satisfied" as employees with ID ≤ 75
    val satisfiedPredicate = (id: Int) => id <= 75
    
    println("Scenario: Employee Satisfaction Survey (100 employees)")
    println(s"  Satisfied employees: 75 out of 100")
    println()
    
    // Q[≥]: "At Least" quantifier - checks if proportion ≥ threshold
    // "most" is defined as ≥70%
    val mostResult = evaluateOn(
      VagueQuantifier.most,
      employees,
      satisfiedPredicate
    )
    println(s"  Q1: Are 'most' employees satisfied? (≥70%)")
    println(s"      ${mostResult.summary}")
    println(s"      Satisfied: ${mostResult.satisfied}")
    println()
    
    // Scenario 2: "About half of the budget is spent" (48% spent)
    val budgetItems = (1 to 100).toSet
    val spentPredicate = (item: Int) => item <= 48
    
    println("Scenario: Budget Tracking (100 line items)")
    println(s"  Items spent: 48 out of 100")
    println()
    
    // Q[~#]: "Approximately" quantifier - checks if proportion ≈ target ± tolerance
    // "aboutHalf" accepts proportions in range [0.4, 0.6] (50% ±10%)
    val aboutHalfResult = evaluateOn(
      VagueQuantifier.aboutHalf,
      budgetItems,
      spentPredicate
    )
    println(s"  Q2: Is 'about half' the budget spent? (50% ±10%)")
    println(s"      ${aboutHalfResult.summary}")
    println(s"      Satisfied: ${aboutHalfResult.satisfied}")
    println()
    
    // Scenario 3: "Few security vulnerabilities are critical" (12% critical)
    val vulnerabilities = (1 to 100).toSet
    val criticalPredicate = (vuln: Int) => vuln <= 12
    
    println("Scenario: Security Vulnerability Assessment (100 vulnerabilities)")
    println(s"  Critical vulnerabilities: 12 out of 100")
    println()
    
    // Q[≤]: "At Most" quantifier - checks if proportion ≤ threshold
    // "few" is defined as ≤30%
    val fewResult = evaluateOn(
      VagueQuantifier.few,
      vulnerabilities,
      criticalPredicate
    )
    println(s"  Q3: Are 'few' vulnerabilities critical? (≤30%)")
    println(s"      ${fewResult.summary}")
    println(s"      Satisfied: ${fewResult.satisfied}")
  
  /** Demonstrates sampling for large populations. */
  def samplingDemo(): Unit =
    println("2. SAMPLING FOR LARGE POPULATIONS")
    println("-" * 70)
    
    // For very large populations, we use statistical sampling
    // instead of enumerating all elements
    val largePopulation = (1 to 10000).toSet
    val largePredicate = (x: Int) => x <= 7000  // 70% satisfy
    
    println("Scenario: Large Dataset Analysis (10,000 records)")
    println(s"  Records meeting criteria: 7,000 out of 10,000 (70%)")
    println()
    
    // Sampling parameters control precision vs. speed tradeoff
    // - epsilon (ε): Maximum error tolerance (default: 0.1 = ±10%)
    // - alpha (α): Significance level (default: 0.05 = 95% confidence)
    // HDRConfig controls the PRNG seed hierarchy (ADR-003)
    val params = SamplingParams(
      epsilon = 0.05,   // ±5% error bound
      alpha = 0.05      // 95% confidence
    )
    val hdrConfig = HDRConfig(seed3 = 42)  // Reproducible random sampling
    
    println(s"  Sampling parameters:")
    println(s"    Error tolerance (ε): ${params.epsilon * 100}%")
    println(s"    Confidence level: ${(1 - params.alpha) * 100}%")
    println(s"    HDR seed3: ${hdrConfig.seed3}")
    println()
    
    // HDR (Hubbard Decision Research) PRNG is used for sampling
    // It's counter-based, deterministic, and platform-independent
    val result = evaluateOn(
      VagueQuantifier.most,
      largePopulation,
      largePredicate,
      params,
      hdrConfig
    )
    
    println(s"  Q: Do 'most' records meet criteria? (≥70%)")
    println(s"      ${result.summary}")
    println(s"      Proportion estimate: ${(result.proportion * 100).toInt}%")
    println(s"      Confidence interval: [${(result.confidenceInterval._1 * 100).toInt}%, ${(result.confidenceInterval._2 * 100).toInt}%]")
    println(s"      Sample size used: ${result.estimate.sampleSize}")
    println(s"      Satisfied: ${result.satisfied}")
  
  /** Shows how to create custom quantifiers. */
  def customQuantifierDemo(): Unit =
    println("3. CUSTOM QUANTIFIER DEFINITIONS")
    println("-" * 70)
    
    val testScores = (1 to 100).toSet
    
    // Scenario 1: Custom "approximately" quantifier
    // "About 80% of students passed" with ±5% tolerance
    val passingPredicate = (score: Int) => score <= 82  // 82% passed
    
    println("Scenario: Student Test Scores (100 students)")
    println(s"  Passing students: 82 out of 100")
    println()
    
    // Create custom approximately quantifier: 80% ±5% = [75%, 85%]
    val aboutEighty = VagueQuantifier.approximately(80, 5)
    val result1 = evaluateOn(aboutEighty, testScores, passingPredicate)
    
    println(s"  Q1: Did 'about 80%' pass? (80% ±5%)")
    println(s"      Range: [${aboutEighty.lowerBound * 100}%, ${aboutEighty.upperBound * 100}%]")
    println(s"      ${result1.summary}")
    println()
    
    // Scenario 2: Custom "at least" quantifier
    // "At least 90% compliance required" (strict requirement)
    val compliancePredicate = (item: Int) => item <= 92  // 92% compliant
    
    println("Scenario: Regulatory Compliance (100 controls)")
    println(s"  Compliant controls: 92 out of 100")
    println()
    
    // Create custom at-least quantifier: ≥90%
    val highCompliance = VagueQuantifier.atLeast(90)
    val result2 = evaluateOn(highCompliance, testScores, compliancePredicate)
    
    println(s"  Q2: Do we have 'at least 90%' compliance? (≥90%)")
    println(s"      ${result2.summary}")
    println()
    
    // Scenario 3: Custom "at most" quantifier
    // "At most 5% error rate acceptable"
    val errorPredicate = (item: Int) => item <= 3  // 3% errors
    
    println("Scenario: Manufacturing Quality (100 units)")
    println(s"  Defective units: 3 out of 100")
    println()
    
    // Create custom at-most quantifier: ≤5%
    val lowError = VagueQuantifier.atMost(5)
    val result3 = evaluateOn(lowError, testScores, errorPredicate)
    
    println(s"  Q3: Is the error rate 'at most 5%'? (≤5%)")
    println(s"      ${result3.summary}")
  
  /** Demonstrates integration with the knowledge base. */
  def knowledgeBaseDemo(): Unit =
    println("4. KNOWLEDGE BASE INTEGRATION")
    println("-" * 70)
    
    // RiskDomain is a pre-built knowledge base for risk management
    // It contains relations for components, risks, and mitigations
    val kb = RiskDomain.createKnowledgeBase
    
    println("Using RiskDomain knowledge base:")
    println(s"  Relations: ${kb.schema.size}")
    println(s"  Total facts: ${kb.totalFacts}")
    println(s"  Active domain: ${kb.activeDomain.size} unique values")
    println()
    
    // Query 1: What proportion of components are critical?
    // Get all components from the "component" relation (unary relation)
    val components = kb.getDomain(RelationName("component"), 0)
    val componentNames = components.collect { case RelationValue.Const(name) => name }
    
    val criticalComponents = Set("auth-service", "payment-gateway", "database")
    val isCritical = (comp: String) => criticalComponents.contains(comp)
    
    println("Query 1: Component Criticality")
    println(s"  Total components: ${componentNames.size}")
    println(s"  Critical components: ${criticalComponents.size}")
    println()
    
    // Are "many" components critical? (≥50%)
    val manyResult = evaluateOn(VagueQuantifier.many, componentNames, isCritical)
    println(s"  Q: Are 'many' components critical? (≥50%)")
    println(s"      ${manyResult.summary}")
    println()
    
    // Query 2: What proportion of risks are high severity?
    // Get all risks from the "risk" relation (unary relation)
    val risks = kb.getDomain(RelationName("risk"), 0)
    val riskNames = risks.collect { case RelationValue.Const(name) => name }
    
    val highSeverityRisks = Set("auth-bypass", "sql-injection", "data-leak")
    val isHighSeverity = (risk: String) => highSeverityRisks.contains(risk)
    
    println("Query 2: Risk Severity Distribution")
    println(s"  Total risks: ${riskNames.size}")
    println(s"  High severity risks: ${highSeverityRisks.size}")
    println()
    
    // Are "few" risks high severity? (≤30%)
    val fewResult = evaluateOn(VagueQuantifier.few, riskNames, isHighSeverity)
    println(s"  Q: Are 'few' risks high severity? (≤30%)")
    println(s"      ${fewResult.summary}")
  
  /** Demonstrates statistical significance checking. */
  def statisticalSignificanceDemo(): Unit =
    println("5. STATISTICAL SIGNIFICANCE")
    println("-" * 70)
    
    println("When using sampling, results may not be statistically significant")
    println("if the confidence interval overlaps with the quantifier boundary.")
    println()
    
    // Scenario: Close to boundary case
    val population = (1 to 100).toSet
    val borderlinePredicate = (x: Int) => x <= 71  // 71% - close to 70% threshold
    
    println("Scenario: Borderline Case (71% satisfaction, 'most' = ≥70%)")
    println()
    
    // With tight sampling parameters, we can detect significance
    val tightParams = SamplingParams(
      epsilon = 0.01,  // ±1% error
      alpha = 0.05     // 95% confidence
    )
    val tightHdr = HDRConfig(seed3 = 42)
    
    val borderlineResult = evaluateOn(
      VagueQuantifier.most,
      population,
      borderlinePredicate,
      tightParams,
      tightHdr
    )
    
    println(s"  Q: Are 'most' satisfied? (≥70%)")
    println(s"      Proportion: ${(borderlineResult.proportion * 100).toInt}%")
    println(s"      CI: [${(borderlineResult.confidenceInterval._1 * 100).toInt}%, ${(borderlineResult.confidenceInterval._2 * 100).toInt}%]")
    println(s"      Satisfied: ${borderlineResult.satisfied}")
    println(s"      Statistically significant: ${borderlineResult.isSignificant}")
    println()
    
    if borderlineResult.isSignificant then
      println("  ✓ The confidence interval is entirely above 70%")
      println("    Result is statistically significant at 95% confidence")
    else
      println("  ⚠ The confidence interval overlaps with the 70% threshold")
      println("    Result may not be statistically significant")
      println("    Consider: larger sample size or exact enumeration")
    println()
    
    // Compare with exact evaluation
    val exactResult = evaluateOn(
      VagueQuantifier.most,
      population,
      borderlinePredicate
    )
    println(s"  Exact evaluation (no sampling):")
    println(s"      ${exactResult.summary}")
    println(s"      Statistically significant: ${exactResult.isSignificant}")
  
  /** Helper to demonstrate all common quantifiers. */
  def showAllCommonQuantifiers(): Unit =
    println("\nCOMMON QUANTIFIERS REFERENCE")
    println("-" * 70)
    
    println("\nApproximately (Q[~#]) - Proportion ≈ target ± tolerance:")
    println(s"  aboutHalf:          ${VagueQuantifier.aboutHalf.describe}")
    println(s"  aboutQuarter:       ${VagueQuantifier.aboutQuarter.describe}")
    println(s"  aboutThreeQuarters: ${VagueQuantifier.aboutThreeQuarters.describe}")
    
    println("\nAt Least (Q[≥]) - Proportion ≥ threshold:")
    println(s"  almostAll:  ${VagueQuantifier.almostAll.describe}")
    println(s"  most:       ${VagueQuantifier.most.describe}")
    println(s"  many:       ${VagueQuantifier.many.describe}")
    println(s"  several:    ${VagueQuantifier.several.describe}")
    println(s"  some:       ${VagueQuantifier.some.describe}")
    
    println("\nAt Most (Q[≤]) - Proportion ≤ threshold:")
    println(s"  almostNone: ${VagueQuantifier.almostNone.describe}")
    println(s"  hardlyAny:  ${VagueQuantifier.hardlyAny.describe}")
    println(s"  few:        ${VagueQuantifier.few.describe}")
    println(s"  notMany:    ${VagueQuantifier.notMany.describe}")
