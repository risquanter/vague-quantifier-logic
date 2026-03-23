package examples

import fol.quantifier.VagueQuantifier
import fol.query.{Query, Predicates, execute, UnresolvedQuery}
import fol.sampling.{SamplingParams, HDRConfig}
import fol.datastore.{KnowledgeBase, KnowledgeSource, RiskDomain, RelationValue}
import fol.result.VagueQueryResult
import fol.bridge.{toModel, holds}
import logic.Formula
import parser.FOLParser
import semantics.{Valuation, FOLSemantics}

/** Interactive Playground for Vague Quantifier Queries
  * 
  * This playground demonstrates the complete integration:
  * - KnowledgeBase (relational data storage)
  * - KnowledgeSource (sampling-friendly access)
  * - Query DSL (fluent vague quantifier queries)
  * - FOL Model Theory (semantic evaluation)
  * - Statistical Sampling (scalable proportion estimation)
  * 
  * Phase 13 Integration Goals:
  * - Phase 13.1: KnowledgeBase ✓
  * - Phase 13.2: Statistical Sampling ✓
  * - Phase 13.3: Vague Quantifiers ✓
  * - Phase 13.4: Query DSL ✓
  * - Phase 13.5: This Playground ✓
  */
@main def VagueQueryPlayground(): Unit =
  println("=" * 80)
  println("Vague Quantifier Query Playground")
  println("=" * 80)
  println()
  
  // Run all demos
  basicQueryDemo()
  println()
  
  predicateQueryDemo()
  println()
  
  folIntegrationDemo()
  println()
  
  samplingScalabilityDemo()
  println()
  
  println("=" * 80)
  println("Playground Complete!")
  println("=" * 80)

/** Basic vague quantifier queries using the DSL. */
def basicQueryDemo(): Unit =
  println("1. BASIC QUERY DSL")
  println("-" * 80)
  
  // Create knowledge base with risk management data
  val kb = RiskDomain.createKnowledgeBase
  val source = KnowledgeSource.fromKnowledgeBase(kb)
  
  println("RiskDomain Knowledge Base:")
  println(s"  ${kb.stats}")
  println()
  
  // Query 1: "Most components are low-risk"
  println("Query 1: Are MOST components low-risk?")
  
  val criticalComponents = Set("auth_module", "payment_processor", "database")
  val isLowRisk = (comp: String) => !criticalComponents.contains(comp)
  
  val q1 = Query
    .quantifier(VagueQuantifier.most)  // ≥70%
    .over("component")                 // domain: all components
    .whereConst(isLowRisk)             // predicate: not critical
  
  val result1 = source.execute(q1) match
    case Right(r) => r
    case Left(e) => println(s"Error: ${e.formatted}"); return
  println(s"  ${result1.summary}")
  println(s"  Satisfied: ${result1.satisfied}")
  println()
  
  // Query 2: "Few risks are high severity"
  println("Query 2: Are FEW risks high severity?")
  
  val highSeverityRisks = Set("auth_bypass", "sql_injection", "data_breach")
  val isHighSeverity = (risk: String) => highSeverityRisks.contains(risk)
  
  val q2 = Query
    .quantifier(VagueQuantifier.few)  // ≤30%
    .over("risk")                      // domain: all risks
    .whereConst(isHighSeverity)       // predicate: high severity
  
  val result2 = source.execute(q2) match
    case Right(r) => r
    case Left(e) => println(s"Error: ${e.formatted}"); return
  println(s"  ${result2.summary}")
  println(s"  Satisfied: ${result2.satisfied}")
  println()
  
  // Query 3: "About half of components have mitigations"
  println("Query 3: Do ABOUT HALF of components have mitigations?")
  
  // Get components that have at least one mitigation
  val componentsWithMitigations = kb.getDomain("has_mitigation", 0)
    .collect { case RelationValue.Const(name) => name }
  
  val hasMitigation = (comp: String) => componentsWithMitigations.contains(comp)
  
  val q3 = Query
    .quantifier(VagueQuantifier.aboutHalf)  // 50% ±10%
    .over("component")
    .whereConst(hasMitigation)
  
  val result3 = source.execute(q3) match
    case Right(r) => r
    case Left(e) => println(s"Error: ${e.formatted}"); return
  println(s"  ${result3.summary}")
  println(s"  Satisfied: ${result3.satisfied}")
  println()

/** Using predicate builders for relational queries. */
def predicateQueryDemo(): Unit =
  println("2. PREDICATE BUILDERS FOR RELATIONAL QUERIES")
  println("-" * 80)
  
  val kb = RiskDomain.createKnowledgeBase
  val source = KnowledgeSource.fromKnowledgeBase(kb)
  
  // Query 1: "Many components have risks"
  println("Query 1: Do MANY components have risks?")
  
  // Use predicate builder to check relation
  val componentHasRisk = (comp: String) =>
    val pattern = List(Some(RelationValue.Const(comp)), None)
    source.query("has_risk", pattern).nonEmpty
  
  val q1 = Query
    .quantifier(VagueQuantifier.many)  // ≥50%
    .over("component")
    .whereConst(componentHasRisk)
  
  val result1 = source.execute(q1) match
    case Right(r) => r
    case Left(e) => println(s"Error: ${e.formatted}"); return
  println(s"  ${result1.summary}")
  println()
  
  // Query 2: "Several mitigations address multiple risks"
  println("Query 2: Do SEVERAL mitigations have associated risks?")
  
  // Check if mitigations have at least one risk they mitigate
  val mitigations = kb.getDomain("mitigation", 0)
    .collect { case RelationValue.Const(name) => name }
  
  val hasRisks = (mit: String) =>
    val risksAddressed = kb.query(
      "mitigates",
      List(Some(RelationValue.Const(mit)), None)
    )
    risksAddressed.nonEmpty
  
  val q2 = Query
    .quantifier(VagueQuantifier.several)  // ≥30%
    .over("mitigation")
    .whereConst(hasRisks)
  
  val result2 = source.execute(q2) match
    case Right(r) => r
    case Left(e) => println(s"Error: ${e.formatted}"); return
  println(s"  ${result2.summary}")
  println()

/** Integration with FOL model theory. */
def folIntegrationDemo(): Unit =
  println("3. FOL MODEL THEORY INTEGRATION")
  println("-" * 80)
  
  val kb = RiskDomain.createKnowledgeBase
  
  println("Translating KnowledgeBase → FOL Model...")
  val model = kb.toModel
  println(s"  Domain size: ${model.interpretation.domain.elements.size}")
  println(s"  Functions: ${model.interpretation.funcInterp.keys.size} constants")
  println(s"  Predicates: ${model.interpretation.predInterp.keys.mkString(", ")}")
  println()
  
  // Debug: check if auth_module is in the domain
  val hasAuthModule = model.interpretation.domain.elements.contains("auth_module")
  println(s"  'auth_module' in domain: $hasAuthModule")
  if !hasAuthModule then
    val sample = model.interpretation.domain.elements.take(10).mkString(", ")
    println(s"  Domain sample: $sample")
  println()
  
  // Query 1: Evaluate FOL formula directly
  println("FOL Formula Evaluation:")
  
  // Note: FOL parser treats - as minus, so use programmatic construction for hyphenated names
  // But RiskDomain uses underscores, so we can use either method
  import logic.{Term, FOL}
  
  // component(auth_module) ∧ risk(sql_injection)
  val formula1 = Formula.And(
    Formula.Atom(FOL("component", List(Term.Const("auth_module")))),
    Formula.Atom(FOL("risk", List(Term.Const("sql_injection"))))
  )
  val holds1 = FOLSemantics.holds(formula1, model, Valuation(Map.empty))
  println(s"  component(auth_module) ∧ risk(sql_injection): ${holds1}")
  
  // has_risk(auth_module, sql_injection)
  val formula2 = Formula.Atom(FOL("has_risk", List(
    Term.Const("auth_module"),
    Term.Const("sql_injection")
  )))
  val holds2 = FOLSemantics.holds(formula2, model, Valuation(Map.empty))
  println(s"  has_risk(auth_module, sql_injection): ${holds2}")
  
  // ∃r. has_risk(database, r)
  val formula3 = Formula.Exists("r", Formula.Atom(FOL("has_risk", List(
    Term.Const("database"),
    Term.Var("r")
  ))))
  val holds3 = FOLSemantics.holds(formula3, model, Valuation(Map.empty))
  println(s"  ∃r. has_risk(database, r): ${holds3}")
  println()
  
  // Query 2: Combine FOL with vague quantifiers
  println("Vague Query with FOL Predicate:")
  
  // "Most components satisfy ∃r. has_risk(c, r) ∧ ∃m. has_mitigation(c, m)"
  // Translation: Most components have both a risk and a mitigation
  val source = KnowledgeSource.fromKnowledgeBase(kb)
  val components = kb.getDomain("component", 0)
    .collect { case RelationValue.Const(name) => name }
  
  val hasRiskAndMitigation = (comp: String) =>
    // Check: ∃r. has_risk(comp, r)
    val hasRisk = kb.query(
      "has_risk",
      List(Some(RelationValue.Const(comp)), None)
    ).nonEmpty
    
    // Check: ∃m. has_mitigation(comp, m)
    val hasMit = kb.query(
      "has_mitigation",
      List(Some(RelationValue.Const(comp)), None)
    ).nonEmpty
    
    hasRisk && hasMit
  
  val q = Query
    .quantifier(VagueQuantifier.most)
    .over("component")
    .whereConst(hasRiskAndMitigation)
  
  val result = source.execute(q) match
    case Right(r) => r
    case Left(e) => println(s"Error: ${e.formatted}"); return
  println(s"  Do MOST components have both risks and mitigations?")
  println(s"  ${result.summary}")
  println()

/** Demonstrate sampling scalability. */
def samplingScalabilityDemo(): Unit =
  println("4. SAMPLING FOR SCALABILITY")
  println("-" * 80)
  
  // Create large knowledge base
  println("Creating large synthetic knowledge base...")
  val builder = KnowledgeBase.builder
    .withUnaryRelation("entity")
  
  // Add 10,000 entities
  val largeN = 10000
  for i <- 1 to largeN do
    builder.withFact("entity", s"e$i")
  
  val largeKB = builder.build()
  val source = KnowledgeSource.fromKnowledgeBase(largeKB)
  
  println(s"  Created KB with ${largeKB.count("entity")} entities")
  println()
  
  // Define predicate: first 7000 entities satisfy (70%)
  val satisfiesPredicate = (entity: String) =>
    val num = entity.drop(1).toIntOption.getOrElse(0)
    num <= 7000
  
  // Query WITH sampling (fast, approximate)
  println("Query WITH sampling (target: 1000 samples):")
  val paramsWithSampling = SamplingParams(
    epsilon = 0.03,  // 3% error margin
    alpha = 0.05     // 95% confidence
  )
  val hdrConfig = HDRConfig(seed3 = 42)
  
  val t1 = System.currentTimeMillis()
  val q1 = Query
    .quantifier(VagueQuantifier.most)  // ≥70%
    .over("entity")
    .whereConst(satisfiesPredicate, paramsWithSampling)
  
  val result1 = source.execute(q1) match
    case Right(r) => r
    case Left(e) => println(s"Error: ${e.formatted}"); return
  val elapsed1 = System.currentTimeMillis() - t1
  
  println(s"  ${result1.summary}")
  println(s"  Satisfied: ${result1.satisfied}")
  println(s"  Time: ${elapsed1}ms")
  println()
  
  // Query WITHOUT sampling (slower, exact)
  println("Query WITHOUT sampling (exact, all 10,000 elements):")
  // Use exact evaluation method instead
  val exactQ = Query
    .quantifier(VagueQuantifier.most)
    .over("entity")
    .whereConst(satisfiesPredicate)
  
  val t2 = System.currentTimeMillis()
  
  // Evaluate with full domain (no sampling) via the Query DSL
  val result2 = source.execute(exactQ) match
    case Right(r) => r
    case Left(e) => println(s"Error: ${e.formatted}"); return
  val elapsed2 = System.currentTimeMillis() - t2
  
  println(s"  ${result2.summary}")
  println(s"  Satisfied: ${result2.satisfied}")
  println(s"  Time: ${elapsed2}ms")
  println()
  
  // Compare results
  val error = math.abs(result1.proportion - result2.proportion) * 100
  println("Comparison:")
  println(s"  Sampling proportion: ${(result1.proportion * 100).toInt}%")
  println(s"  Exact proportion:    ${(result2.proportion * 100).toInt}%")
  println(f"  Error: $error%.1f%%")
  val speedup = elapsed2.toDouble / elapsed1
  println(f"  Speedup: $speedup%.1fx")
  println()
  
  println("KEY INSIGHT:")
  println("  Sampling provides near-identical results with much better performance.")
  println("  For SQL backends with millions of rows, sampling is essential!")
  println("  Use: SELECT * FROM table ORDER BY RANDOM() LIMIT 1000")
