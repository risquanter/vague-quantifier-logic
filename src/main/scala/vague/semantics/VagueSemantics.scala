package vague.semantics

import logic.FOL
import semantics.FOLSemantics
import vague.datastore.{KnowledgeBase, KnowledgeSource, RelationValue}
import vague.logic.{Quantifier, VagueQuery}
import vague.bridge.{KnowledgeBaseModel, KnowledgeSourceModel}
import scala.util.Random

/** Result of evaluating a vague quantifier query
  * 
  * @param satisfied Whether the query is satisfied
  * @param actualProportion The computed Prop_D value
  * @param rangeSize Size of the extracted range D_R
  * @param sampleSize Size of the sample used (may equal rangeSize for exact evaluation)
  * @param satisfyingCount Number of elements in sample that satisfy the scope formula
  */
case class VagueResult(
  satisfied: Boolean,
  actualProportion: Double,
  rangeSize: Int,
  sampleSize: Int,
  satisfyingCount: Int
)

/** Parameters for query evaluation
  * 
  * @param useSampling If false, use entire D_R as sample (exact evaluation)
  * @param sampleSize If useSampling is true, how many elements to sample
  * @param randomSeed Optional seed for reproducible sampling
  */
case class EvaluationParams(
  useSampling: Boolean = false,
  sampleSize: Option[Int] = None,
  randomSeed: Option[Long] = None
)

/** Vague quantifier semantics evaluation
  * 
  * Implements the complete vague quantifier evaluation from the paper:
  * 
  * 1. Extract range D_R using range predicate R
  * 2. Select sample S ⊆ D_R (or use entire D_R for exact evaluation)
  * 3. Calculate Prop_D(S, φ(x,c)) = |{x ∈ S | D ⊨ φ(x,c)}| / |S|
  * 4. Check quantifier condition Q[op]^{k/n} against Prop_D
  * 
  * Paper reference: Section 3, Definition 2
  * OCaml lineage: vague_semantics.ml
  */
object VagueSemantics:

  /** Evaluate a vague quantifier query (primary API using KnowledgeSource)
    * 
    * Evaluates: D ⊨ Q[op]^{k/n} x (R, φ(x,c))
    * 
    * This is the main API that works with any KnowledgeSource implementation
    * (in-memory, SQL, RDF, etc.).
    * 
    * @param query The vague query to evaluate
    * @param source The knowledge source to evaluate against
    * @param answerTuple The answer tuple c to substitute for answer variables in φ
    * @param params Evaluation parameters (sampling, etc.)
    * @return Result containing satisfaction and metadata
    * 
    * Example:
    * {{{
    * val kb = KnowledgeBase.builder...build()
    * val source = KnowledgeSource.fromKnowledgeBase(kb)
    * 
    * val query = VagueQuery(
    *   quantifier = Quantifier.About(1, 2, 0.1),
    *   variable = Var("x"),
    *   rangePredicate = Pred("country", List(Var("x"))),
    *   scopeFormula = Pred("large", List(Var("x")))
    * )
    * 
    * val result = VagueSemantics.holds(query, source, Map.empty)
    * println(s"Query satisfied: ${result.satisfied}, proportion: ${result.actualProportion}")
    * }}}
    */
  def holds(
    query: VagueQuery,
    source: KnowledgeSource,
    answerTuple: Map[String, RelationValue],
    params: EvaluationParams = EvaluationParams()
  ): VagueResult =
    
    // Step 1: Extract range D_R using range predicate R
    val rangeElements = RangeExtractor.extractRange(
      source,
      query,
      answerTuple
    )
    
    // Convert source to FOL Model for scope evaluation
    val model = KnowledgeSourceModel.toModel(source)
    
    // Handle empty range
    if rangeElements.isEmpty then
      return VagueResult(
        satisfied = false,
        actualProportion = 0.0,
        rangeSize = 0,
        sampleSize = 0,
        satisfyingCount = 0
      )
    
    // Step 2: Select sample S
    val sample = selectSample(rangeElements, params)
    
    // Step 3: Calculate Prop_D(S, φ(x,c))
    val proportion = ScopeEvaluator.calculateProportion(
      sample,
      query.scope,
      query.variable,
      model,
      answerTuple
    )
    
    // Step 4: Check quantifier condition
    val satisfied = checkQuantifier(query.quantifier, proportion)
    
    // Calculate satisfying count for metadata
    val satisfyingCount = ScopeEvaluator.countSatisfying(
      sample,
      query.scope,
      query.variable,
      model,
      answerTuple
    )
    
    VagueResult(
      satisfied = satisfied,
      actualProportion = proportion,
      rangeSize = rangeElements.size,
      sampleSize = sample.size,
      satisfyingCount = satisfyingCount
    )
  
  /** Backward-compatible wrapper for KnowledgeBase
    * 
    * This method allows existing code using KnowledgeBase directly
    * to continue working without changes. Internally, it wraps the
    * KB in a KnowledgeSource and delegates to the main implementation.
    * 
    * @deprecated Use holds(query, source, ...) with KnowledgeSource instead
    */
  def holdsOnKB(
    query: VagueQuery,
    kb: KnowledgeBase,
    answerTuple: Map[String, RelationValue],
    params: EvaluationParams = EvaluationParams()
  ): VagueResult =
    val source = KnowledgeSource.fromKnowledgeBase(kb)
    holds(query, source, answerTuple, params)

  /** Select sample from range based on evaluation parameters
    * 
    * @param range The full range D_R
    * @param params Evaluation parameters
    * @return Sample S ⊆ D_R (or S = D_R for exact evaluation)
    */
  private def selectSample(
    range: Set[RelationValue],
    params: EvaluationParams
  ): Set[RelationValue] =
    
    if !params.useSampling then
      // Exact evaluation: use entire range
      range
    else
      // Sampling evaluation
      val targetSize = params.sampleSize.getOrElse(
        math.min(100, range.size) // Default: 100 or all if fewer
      )
      
      val actualSize = math.min(targetSize, range.size)
      
      if actualSize >= range.size then
        // If sample size >= range size, just use entire range
        range
      else
        // Random sampling
        val rng = params.randomSeed match
          case Some(seed) => new Random(seed)
          case None => new Random()
        
        rng.shuffle(range.toList).take(actualSize).toSet

  /** Check if quantifier condition is satisfied
    * 
    * Implements the quantifier semantics:
    * - About (≈): |Prop_D - k/n| ≤ tolerance
    * - AtLeast (≥): Prop_D ≥ k/n - tolerance
    * - AtMost (≤): Prop_D ≤ k/n + tolerance
    * 
    * @param quantifier The quantifier Q[op]^{k/n}
    * @param actualProportion The computed Prop_D
    * @return True if quantifier condition satisfied
    */
  private def checkQuantifier(
    quantifier: Quantifier,
    actualProportion: Double
  ): Boolean = quantifier match
    
    case Quantifier.About(k, n, tolerance) =>
      val target = k.toDouble / n.toDouble
      math.abs(actualProportion - target) <= tolerance
    
    case Quantifier.AtLeast(k, n, tolerance) =>
      val target = k.toDouble / n.toDouble
      actualProportion >= (target - tolerance)
    
    case Quantifier.AtMost(k, n, tolerance) =>
      val target = k.toDouble / n.toDouble
      actualProportion <= (target + tolerance)

  /** Convenience method for exact evaluation (no sampling) using KnowledgeSource
    * 
    * @param query The vague query
    * @param source The knowledge source
    * @param answerTuple Answer variable substitution
    * @return Result with exact proportion
    */
  def holdsExact(
    query: VagueQuery,
    source: KnowledgeSource,
    answerTuple: Map[String, RelationValue] = Map.empty
  ): VagueResult =
    holds(query, source, answerTuple, EvaluationParams(useSampling = false))

  /** Convenience method for sampling evaluation with specified sample size using KnowledgeSource
    * 
    * @param query The vague query
    * @param source The knowledge source
    * @param sampleSize Number of elements to sample
    * @param answerTuple Answer variable substitution
    * @param seed Optional random seed
    * @return Result with approximate proportion
    */
  def holdsWithSampling(
    query: VagueQuery,
    source: KnowledgeSource,
    sampleSize: Int,
    answerTuple: Map[String, RelationValue] = Map.empty,
    seed: Option[Long] = None
  ): VagueResult =
    holds(
      query, 
      source, 
      answerTuple, 
      EvaluationParams(useSampling = true, sampleSize = Some(sampleSize), randomSeed = seed)
    )
  
  /** Backward-compatible exact evaluation for KnowledgeBase
    * @deprecated Use holdsExact(query, source, ...) with KnowledgeSource instead
    */
  def holdsExactOnKB(
    query: VagueQuery,
    kb: KnowledgeBase,
    answerTuple: Map[String, RelationValue] = Map.empty
  ): VagueResult =
    holdsExact(query, KnowledgeSource.fromKnowledgeBase(kb), answerTuple)

  /** Backward-compatible sampling evaluation for KnowledgeBase
    * @deprecated Use holdsWithSampling(query, source, ...) with KnowledgeSource instead
    */
  def holdsWithSamplingOnKB(
    query: VagueQuery,
    kb: KnowledgeBase,
    sampleSize: Int,
    answerTuple: Map[String, RelationValue] = Map.empty,
    seed: Option[Long] = None
  ): VagueResult =
    holdsWithSampling(query, KnowledgeSource.fromKnowledgeBase(kb), sampleSize, answerTuple, seed)
