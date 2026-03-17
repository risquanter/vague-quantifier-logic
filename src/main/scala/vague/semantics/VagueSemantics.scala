package vague.semantics

import vague.datastore.{KnowledgeSource, RelationValue}
import vague.logic.{Quantifier, VagueQuery}
import vague.quantifier.VagueQuantifier
import vague.bridge.FOLBridge
import vague.result.VagueQueryResult
import vague.sampling.{SamplingParams, HDRConfig}
import vague.error.{VagueError, VagueException}
import vague.result.VagueResult as VagueResultMonad
import scala.util.control.NonFatal

/** Vague quantifier semantics evaluation — thin facade.
  * 
  * Evaluates string-parsed vague queries by delegating to the unified
  * typed-DSL pipeline via [[FOLBridge]]:
  * 
  * 1. Extract range D_R using range predicate R  (RangeExtractor)
  * 2. Convert scope formula to typed predicate    (FOLBridge.scopeToPredicate)
  * 3. Convert Quantifier → VagueQuantifier        (VagueQuantifier.fromQuantifier)
  * 4. Evaluate via VagueQuantifier.evaluateExact/evaluateWithSampling
  * 5. Return VagueQueryResult (unified result type)
  * 
  * Paper reference: Section 3, Definition 2
  * See also: docs/EVALUATION-PATH-UNIFICATION.md (Decision D4)
  */
object VagueSemantics:

  /** Evaluate a vague quantifier query (primary API).
    * 
    * Evaluates: D ⊨ Q[op]^{k/n} x (R, φ(x,c))
    * 
    * Works with any KnowledgeSource implementation (in-memory, SQL, RDF, etc.).
    * 
    * @param query The string-parsed vague query
    * @param source The knowledge source to evaluate against
    * @param answerTuple Substitution for answer variables in φ
    * @param useSampling If true, sample from D_R using HDR PRNG; if false, exact
    * @param samplingParams Statistical precision parameters (when sampling)
    * @param hdrConfig HDR PRNG configuration (when sampling)
    * @return VagueQueryResult with satisfaction, proportion, CI, and metadata
    */
  def holds(
    query: VagueQuery,
    source: KnowledgeSource,
    answerTuple: Map[String, RelationValue] = Map.empty,
    useSampling: Boolean = false,
    samplingParams: SamplingParams = SamplingParams(),
    hdrConfig: HDRConfig = HDRConfig.default
  ): VagueQueryResult =
    
    // Step 1: Extract range D_R using range predicate R
    val rangeElements = RangeExtractor.extractRange(source, query, answerTuple)
    
    if rangeElements.isEmpty then
      emptyResult(query.quantifier)
    else
      // Step 2: Convert scope formula to typed predicate via FOLBridge
      val predicate = FOLBridge.scopeToPredicate(
        query.scope, query.variable, source, answerTuple
      )
      
      // Step 3: Convert ratio-based Quantifier → percentage-based VagueQuantifier
      val vq = VagueQuantifier.fromQuantifier(query.quantifier)
      
      // Step 4: Evaluate using the unified pipeline
      if useSampling then
        vq.evaluateWithSampling(rangeElements, predicate, samplingParams, hdrConfig)
      else
        vq.evaluateExact(rangeElements, predicate)
  
  /** Safe internal implementation returning Either. */
  private def holdsInternal(
    query: VagueQuery,
    source: KnowledgeSource,
    answerTuple: Map[String, RelationValue],
    useSampling: Boolean,
    samplingParams: SamplingParams,
    hdrConfig: HDRConfig
  ): Either[VagueError, VagueQueryResult] =
    try
      val rangeResult = RangeExtractor.extractRangeSafe(source, query, answerTuple)
      
      rangeResult.flatMap { rangeElements =>
        if rangeElements.isEmpty then
          Left(VagueError.EmptyRangeError(
            query.range.predicate,
            s"${query.variable} with substitution ${answerTuple}",
            Some("Check that the relation exists and has data matching the criteria")
          ))
        else
          val predicate = FOLBridge.scopeToPredicate(
            query.scope, query.variable, source, answerTuple
          )
          val vq = VagueQuantifier.fromQuantifier(query.quantifier)
          
          val result =
            if useSampling then
              vq.evaluateWithSampling(rangeElements, predicate, samplingParams, hdrConfig)
            else
              vq.evaluateExact(rangeElements, predicate)
          
          Right(result)
      }
    catch
      case e: VagueException => Left(e.error)
      case NonFatal(e) =>
        Left(VagueError.EvaluationError(
          s"Unexpected error during query evaluation: ${e.getMessage}",
          "query_evaluation",
          Some(e),
          Map("query" -> query.toString)
        ))
  
  /** Either API for structured error handling. */
  def holdsEither(
    query: VagueQuery,
    source: KnowledgeSource,
    answerTuple: Map[String, RelationValue] = Map.empty,
    useSampling: Boolean = false,
    samplingParams: SamplingParams = SamplingParams(),
    hdrConfig: HDRConfig = HDRConfig.default
  ): Either[VagueError, VagueQueryResult] =
    holdsInternal(query, source, answerTuple, useSampling, samplingParams, hdrConfig)
  
  /** VagueResult monad API for functional composition. */
  def holdsSafe(
    query: VagueQuery,
    source: KnowledgeSource,
    answerTuple: Map[String, RelationValue] = Map.empty,
    useSampling: Boolean = false,
    samplingParams: SamplingParams = SamplingParams(),
    hdrConfig: HDRConfig = HDRConfig.default
  ): VagueResultMonad[VagueQueryResult] =
    VagueResultMonad.fromEither(
      holdsInternal(query, source, answerTuple, useSampling, samplingParams, hdrConfig)
    )
  
  /** Convenience: exact evaluation (no sampling). */
  def holdsExact(
    query: VagueQuery,
    source: KnowledgeSource,
    answerTuple: Map[String, RelationValue] = Map.empty
  ): VagueQueryResult =
    holds(query, source, answerTuple, useSampling = false)

  /** Convenience: sampling evaluation with HDR PRNG. */
  def holdsWithSampling(
    query: VagueQuery,
    source: KnowledgeSource,
    answerTuple: Map[String, RelationValue] = Map.empty,
    samplingParams: SamplingParams = SamplingParams(),
    hdrConfig: HDRConfig = HDRConfig.default
  ): VagueQueryResult =
    holds(query, source, answerTuple, useSampling = true, samplingParams, hdrConfig)
  
  /** Create an empty result for queries against empty ranges. */
  private def emptyResult(quantifier: Quantifier): VagueQueryResult =
    import vague.sampling.ProportionEstimate
    val vq = VagueQuantifier.fromQuantifier(quantifier)
    VagueQueryResult(
      satisfied = false,
      proportion = 0.0,
      confidenceInterval = (0.0, 1.0),
      quantifier = vq,
      domainSize = 0,
      sampleSize = 0,
      satisfyingCount = 0,
      estimate = ProportionEstimate.empty()
    )
