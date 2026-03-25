package fol.semantics

import fol.datastore.{KnowledgeSource, RelationValue}
import fol.logic.{Quantifier, ParsedQuery}
import fol.quantifier.VagueQuantifier
import fol.bridge.FOLBridge
import fol.query.ResolvedQuery
import fol.result.{VagueQueryResult, EvaluationOutput}
import fol.sampling.{SamplingParams, HDRConfig}
import fol.error.{QueryError, QueryException}
import semantics.ModelAugmenter
import scala.util.control.NonFatal

/** Vague quantifier semantics evaluation — thin facade.
  *
  * Evaluates string-parsed vague queries by compiling them to
  * [[ResolvedQuery]] (the shared IL) and delegating evaluation:
  *
  * 1. Extract range D_R using range predicate R   (RangeExtractor)
  * 2. Convert scope formula to typed predicate     (FOLBridge.scopeToPredicate)
  * 3. Convert Quantifier → VagueQuantifier         (VagueQuantifier.fromQuantifier)
  * 4. Construct [[ResolvedQuery]] (single code path, D11)
  * 5. Evaluate via ResolvedQuery.evaluate / evaluateWithOutput
  *
  * '''One code path — no boolean toggle (D11).'''
  * `SamplingParams` controls whether evaluation is exact or sampled.
  * Default is `SamplingParams.exact` for backward compatibility.
  *
  * Internal implementation throws on error (OCaml style).
  * Public API returns `Either[QueryError, A]` — single boundary.
  *
  * Paper reference: Section 3, Definition 2
  * See also: docs/EVALUATION-PATH-UNIFICATION.md (Decisions D4, D11)
  */
object VagueSemantics:

  /** Compile a string-parsed query into a [[ResolvedQuery]].
    *
    * Private — throws on error.  The public methods wrap this in Either.
    */
  private def toResolved(
    query: ParsedQuery,
    source: KnowledgeSource[RelationValue],
    answerTuple: Map[String, RelationValue],
    samplingParams: SamplingParams,
    hdrConfig: HDRConfig,
    modelAugmenter: ModelAugmenter[RelationValue] = ModelAugmenter.identity
  ): ResolvedQuery =
    val rangeElements = RangeExtractor.extractRange(source, query, answerTuple) match
      case Right(elems) => elems
      case Left(error) => throw error.toThrowable
    val predicate = FOLBridge.scopeToPredicate(
      query.scope, query.variable, source, answerTuple, modelAugmenter
    )
    val vq = VagueQuantifier.fromQuantifier(query.quantifier)
    ResolvedQuery(vq, rangeElements, predicate, samplingParams, hdrConfig)

  /** Evaluate a vague quantifier query.
    *
    * Evaluates: D ⊨ Q[op]^{k/n} x (R, φ(x,c))
    *
    * Works with any KnowledgeSource implementation (in-memory, SQL, RDF, etc.).
    *
    * @param query          The string-parsed vague query
    * @param source         The knowledge source to evaluate against
    * @param answerTuple    Substitution for answer variables in φ
    * @param samplingParams Controls precision. `SamplingParams.exact` (default)
    *                       for full enumeration; any other for statistical sampling.
    * @param hdrConfig      HDR PRNG configuration (relevant when sampling)
    * @return Either[QueryError, VagueQueryResult]
    */
  def holds(
    query: ParsedQuery,
    source: KnowledgeSource[RelationValue],
    answerTuple: Map[String, RelationValue] = Map.empty,
    samplingParams: SamplingParams = SamplingParams.exact,
    hdrConfig: HDRConfig = HDRConfig.default,
    modelAugmenter: ModelAugmenter[RelationValue] = ModelAugmenter.identity
  ): Either[QueryError, VagueQueryResult] =
    try
      Right(toResolved(query, source, answerTuple, samplingParams, hdrConfig, modelAugmenter).evaluate())
    catch
      case e: QueryException => Left(e.error)
      case NonFatal(e) =>
        Left(QueryError.EvaluationError(
          s"Unexpected error during query evaluation: ${e.getMessage}",
          "query_evaluation",
          Some(e),
          Map("query" -> query.toString)
        ))

  /** Evaluate a vague quantifier query with element sets.
    *
    * Returns both statistical results and the concrete element sets
    * needed for tree highlighting in register.
    *
    * @param query          The string-parsed vague query
    * @param source         The knowledge source to evaluate against
    * @param answerTuple    Substitution for answer variables in φ
    * @param samplingParams Controls precision (see `holds`)
    * @param hdrConfig      HDR PRNG configuration
    * @return Either[QueryError, EvaluationOutput]
    */
  def evaluate(
    query: ParsedQuery,
    source: KnowledgeSource[RelationValue],
    answerTuple: Map[String, RelationValue] = Map.empty,
    samplingParams: SamplingParams = SamplingParams.exact,
    hdrConfig: HDRConfig = HDRConfig.default,
    modelAugmenter: ModelAugmenter[RelationValue] = ModelAugmenter.identity
  ): Either[QueryError, EvaluationOutput] =
    try
      Right(toResolved(query, source, answerTuple, samplingParams, hdrConfig, modelAugmenter).evaluateWithOutput())
    catch
      case e: QueryException => Left(e.error)
      case NonFatal(e) =>
        Left(QueryError.EvaluationError(
          s"Unexpected error during query evaluation: ${e.getMessage}",
          "query_evaluation",
          Some(e),
          Map("query" -> query.toString)
        ))
