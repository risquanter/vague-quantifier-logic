package fol.examples

import fol.datastore.*
import fol.logic.*
import fol.quantifier.Quantifier
import fol.parser.VagueQueryParser
import fol.semantics.VagueSemantics

/** Executable examples demonstrating vague quantifiers on cybersecurity data
  * 
  * This demonstrates the paper's example queries adapted to a cybersecurity
  * risk management domain:
  * 
  * - Example 1 (Boolean): "At least 3/4 of assets have critical risks"
  * - Example 2 (Unary): "About half of risks have mitigations" (returns risks)
  * - Example 3 (Complex): "At most 1/3 of critical assets have unmitigated risks"
  * 
  * All examples use the parser to demonstrate end-to-end functionality from
  * paper syntax → evaluation → results.
  */
object CyberSecurityExamples:
  
  val domain = CyberSecurityDomain.kb
  
  // ==================== Example 1: Boolean Query ====================
  
  /** Q[≥]^{3/4} x (asset(x), ∃r. (has_risk(x,r) ∧ critical_risk(r)))
    * 
    * Question: "Do at least 3/4 of our assets have critical security risks?"
    * 
    * This is a Boolean query (no answer variables) checking if the proportion
    * of assets with critical risks meets the ≥3/4 threshold with ε=0.1 tolerance.
    * 
    * Expected: SATISFIED (domain has 18/24 = 75% assets with critical risks)
    */
  def example1_CriticalRiskCoverage(): Unit =
    println("\n" + "="*70)
    println("EXAMPLE 1: Critical Risk Coverage (Boolean Query)")
    println("="*70)
    
    val queryStr = """Q[>=]^{3/4} x (asset(x), exists r . (has_risk(x, r) /\ critical_risk(r)))"""
    
    println(s"\nQuery (paper syntax):")
    println(s"  $queryStr")
    
    println("\nEnglish:")
    println("  'At least 3/4 of assets have critical risks'")
    
    // Parse query
    val query = VagueQueryParser.parse(queryStr) match
      case Right(q) => q
      case Left(e) => println(s"Parse error: ${e.formatted}"); return
    
    println("\nParsed structure:")
    println(s"  Quantifier: ${query.quantifier}")
    println(s"  Variable: ${query.variable}")
    println(s"  Range: ${query.range}")
    println(s"  Scope: ${query.scope}")
    println(s"  Answer vars: ${query.answerVars}")
    println(s"  Boolean query: ${query.isBoolean}")
    
    // Evaluate with exact semantics
    val source = KnowledgeSource.fromKnowledgeBase(domain)
    val result = VagueSemantics.holds(query, source) match
      case Right(r) => r
      case Left(e) => println(s"Evaluation error: ${e.formatted}"); return
    
    println("\nEvaluation:")
    println(s"  Range size: ${result.domainSize}")
    println(s"  Satisfying: ${result.satisfyingCount}")
    println(s"  Proportion: ${result.proportion}")
    println(s"  Target: ${Quantifier.targetProportion(query.quantifier)}")
    println(s"  Tolerance: ±${query.quantifier match { case q: Quantifier.AtLeast => q.tolerance; case _ => 0.0 }}")
    println(s"  Satisfied: ${result.satisfied}")
    
    println("\nInterpretation:")
    if result.satisfied then
      println(s"  ✓ YES: ${(result.proportion * 100).round}% of assets (${result.satisfyingCount}/${result.domainSize}) have critical risks")
      println(s"     This meets the ≥75% threshold (with 10% tolerance)")
    else
      println(s"  ✗ NO: Only ${(result.proportion * 100).round}% of assets have critical risks")
      println(s"     This falls below the ≥75% threshold")
    
    println("\n" + "-"*70)
  
  // ==================== Example 2: Unary Query ====================
  
  /** Q[~]^{1/2} x (risk(x), exists m . has_mitigation(x, m))(x)
    * 
    * Question: "Which risks have mitigations, where about half of all risks do?"
    * 
    * This is a unary query returning the list of risks that satisfy the scope
    * (have mitigations) when about half of all risks satisfy it.
    * 
    * Expected: SATISFIED with list of ~10 mitigated risks
    */
  def example2_MitigatedRisks(): Unit =
    println("\n" + "="*70)
    println("EXAMPLE 2: Mitigated Risks (Unary Query)")
    println("="*70)
    
    val queryStr = """Q[~]^{1/2} x (risk(x), exists m . has_mitigation(x, m))(x)"""
    
    println(s"\nQuery (paper syntax):")
    println(s"  $queryStr")
    
    println("\nEnglish:")
    println("  'About half of risks have mitigations - which ones?'")
    
    // Parse query
    val query = VagueQueryParser.parse(queryStr) match
      case Right(q) => q
      case Left(e) => println(s"Parse error: ${e.formatted}"); return
    
    println("\nParsed structure:")
    println(s"  Quantifier: ${query.quantifier}")
    println(s"  Variable: ${query.variable}")
    println(s"  Range: ${query.range}")
    println(s"  Scope: ${query.scope}")
    println(s"  Answer vars: ${query.answerVars}")
    println(s"  Unary query: ${query.isUnary}")
    
    // Evaluate
    val source = KnowledgeSource.fromKnowledgeBase(domain)
    val result = VagueSemantics.holds(query, source) match
      case Right(r) => r
      case Left(e) => println(s"Evaluation error: ${e.formatted}"); return
    
    println("\nEvaluation:")
    println(s"  Range size: ${result.domainSize}")
    println(s"  Satisfying: ${result.satisfyingCount}")
    println(s"  Proportion: ${result.proportion}")
    println(s"  Target: ${Quantifier.targetProportion(query.quantifier)} (about half)")
    println(s"  Satisfied: ${result.satisfied}")
    
    println("\nInterpretation:")
    if result.satisfied then
      println(s"  ✓ YES: ${(result.proportion * 100).round}% of risks (${result.satisfyingCount}/${result.domainSize}) have mitigations")
      println(s"     This is approximately half (target 50% ± 10%)")
    else
      println(s"  ✗ NO: ${(result.proportion * 100).round}% is not close enough to half")
    
    // For unary queries, we can extract the answer set
    if query.isUnary then
      println("\n  Mitigated risks (answer set):")
      
      // Extract risks that have mitigations
      val mitigatedRisks = domain.query(RelationName("has_mitigation"), List(None, None))
        .getOrElse(Set.empty)
        .map(_.values(0).toString)
        .toList
        .sorted
      
      mitigatedRisks.zipWithIndex.foreach { case (risk, idx) =>
        val mitigations = domain.query(RelationName("has_mitigation"), List(Some(RelationValue.Const(risk)), None))
          .getOrElse(Set.empty)
          .map(_.values(1).toString)
        println(s"    ${idx+1}. $risk")
        mitigations.foreach(m => println(s"       → $m"))
      }
      
      println(s"\n  Total: ${mitigatedRisks.size} risks with mitigations")
    
    println("\n" + "-"*70)
  
  // ==================== Example 3: Complex Query ====================
  
  /** Q[<=]^{1/3} x (critical_asset(x), ∃r. (has_risk(x,r) ∧ ¬∃m. has_mitigation(r,m)))
    * 
    * Question: "At most 1/3 of critical assets have unmitigated risks?"
    * 
    * This is a complex Boolean query with nested quantifiers checking if the
    * proportion of critical assets with unmitigated risks is ≤1/3.
    * 
    * Expected: Depends on data distribution
    */
  def example3_UnmitigatedCriticalRisks(): Unit =
    println("\n" + "="*70)
    println("EXAMPLE 3: Unmitigated Critical Asset Risks (Complex Query)")
    println("="*70)
    
    val queryStr = """Q[<=]^{1/3} x (critical_asset(x), 
                                      exists r . (has_risk(x, r) /\ 
                                                  ~(exists m . has_mitigation(r, m))))"""
    
    println(s"\nQuery (paper syntax):")
    println(s"  ${queryStr.replaceAll("\\s+", " ")}")
    
    println("\nEnglish:")
    println("  'At most 1/3 of critical assets have unmitigated risks'")
    
    // Parse query
    val query = VagueQueryParser.parse(queryStr) match
      case Right(q) => q
      case Left(e) => println(s"Parse error: ${e.formatted}"); return
    
    println("\nParsed structure:")
    println(s"  Quantifier: ${query.quantifier}")
    println(s"  Variable: ${query.variable}")
    println(s"  Range: ${query.range}")
    println(s"  Scope: ${query.scope}")
    println(s"  Answer vars: ${query.answerVars}")
    
    // Evaluate
    val source = KnowledgeSource.fromKnowledgeBase(domain)
    val result = VagueSemantics.holds(query, source) match
      case Right(r) => r
      case Left(e) => println(s"Evaluation error: ${e.formatted}"); return
    
    println("\nEvaluation:")
    println(s"  Critical assets: ${result.domainSize}")
    println(s"  With unmitigated risks: ${result.satisfyingCount}")
    println(s"  Proportion: ${result.proportion}")
    println(s"  Target: ≤${Quantifier.targetProportion(query.quantifier)} (at most 1/3)")
    println(s"  Satisfied: ${result.satisfied}")
    
    println("\nInterpretation:")
    if result.satisfied then
      println(s"  ✓ YES: ${(result.proportion * 100).round}% of critical assets (${result.satisfyingCount}/${result.domainSize})")
      println(s"     have unmitigated risks - this is at most 33%")
      println(s"     Security posture is acceptable.")
    else
      println(s"  ✗ NO: ${(result.proportion * 100).round}% of critical assets have unmitigated risks")
      println(s"     This exceeds the 33% threshold - SECURITY ISSUE!")
    
    // Show which critical assets have unmitigated risks
    println("\n  Critical assets with unmitigated risks:")
    
    domain.query(RelationName("critical_asset"), List(None))
      .getOrElse(Set.empty)
      .map(_.values(0))
      .filter { asset =>
        domain.query(RelationName("has_risk"), List(Some(asset), None))
          .getOrElse(Set.empty)
          .exists { tuple =>
            val risk = tuple.values(1)
            domain.query(RelationName("has_mitigation"), List(Some(risk), None)).getOrElse(Set.empty).isEmpty
          }
      }
      .zipWithIndex
      .foreach { case (asset, idx) =>
        println(s"    ${idx+1}. $asset")
        val unmitigatedRisks = domain.query(RelationName("has_risk"), List(Some(asset), None))
          .getOrElse(Set.empty)
          .map(_.values(1))
          .filter(risk => domain.query(RelationName("has_mitigation"), List(Some(risk), None)).getOrElse(Set.empty).isEmpty)
        unmitigatedRisks.foreach(r => println(s"       ⚠ $r (no mitigation)"))
      }
    
    println("\n" + "-"*70)
  
  // ==================== Additional Example: High-Value Asset Protection ====================
  
  /** Q[>=]^{9/10} x (high_value(x), exists r m . (has_risk(x,r) /\ has_mitigation(r,m) /\ patched(m)))
    * 
    * Question: "At least 90% of high-value assets have patched mitigations?"
    * 
    * This checks if our most valuable assets are well-protected with deployed patches.
    */
  def example4_HighValueProtection(): Unit =
    println("\n" + "="*70)
    println("EXAMPLE 4: High-Value Asset Protection (Boolean Query)")
    println("="*70)
    
    val queryStr = """Q[>=]^{9/10} x (high_value(x), 
                                       exists r m . (has_risk(x, r) /\ 
                                                     has_mitigation(r, m) /\ 
                                                     patched(m)))"""
    
    println(s"\nQuery (paper syntax):")
    println(s"  ${queryStr.replaceAll("\\s+", " ")}")
    
    println("\nEnglish:")
    println("  'At least 90% of high-value assets have risks with deployed patches'")
    
    // Parse query
    val query = VagueQueryParser.parse(queryStr) match
      case Right(q) => q
      case Left(e) => println(s"Parse error: ${e.formatted}"); return
    
    // Evaluate
    val source = KnowledgeSource.fromKnowledgeBase(domain)
    val result = VagueSemantics.holds(query, source) match
      case Right(r) => r
      case Left(e) => println(s"Evaluation error: ${e.formatted}"); return
    
    println("\nEvaluation:")
    println(s"  High-value assets: ${result.domainSize}")
    println(s"  With patched mitigations: ${result.satisfyingCount}")
    println(s"  Proportion: ${result.proportion}")
    println(s"  Target: ≥${Quantifier.targetProportion(query.quantifier)} (at least 90%)")
    println(s"  Satisfied: ${result.satisfied}")
    
    println("\nInterpretation:")
    if result.satisfied then
      println(s"  ✓ YES: ${(result.proportion * 100).round}% of high-value assets are protected")
      println(s"     High-value assets have strong patch coverage.")
    else
      println(s"  ✗ NO: Only ${(result.proportion * 100).round}% have patched mitigations")
      println(s"     Need to improve patch deployment for critical assets!")
    
    println("\n" + "-"*70)
  
  // ==================== Main Demo ====================
  
  def runAll(): Unit =
    CyberSecurityDomain.printSummary()
    example1_CriticalRiskCoverage()
    example2_MitigatedRisks()
    example3_UnmitigatedCriticalRisks()
    example4_HighValueProtection()
    
    println("\n" + "="*70)
    println("SUMMARY: Vague Quantifier Evaluation Complete")
    println("="*70)
    println("\nDemonstrated capabilities:")
    println("  ✓ Parser: Paper syntax → ParsedQuery objects")
    println("  ✓ Boolean queries: Satisfaction checking")
    println("  ✓ Unary queries: Answer set extraction")
    println("  ✓ Complex queries: Nested quantifiers and negation")
    println("  ✓ Domain: Realistic cybersecurity risk management")
    println("\n")
  
  @main def demo(): Unit =
    runAll()

end CyberSecurityExamples
