package fol.semantics

import fol.datastore.{KnowledgeSource, DomainElement, DomainCodec}
import fol.logic.{Quantifier, ParsedQuery}
import fol.quantifier.VagueQuantifier
import fol.bridge.FOLBridge
import fol.query.ResolvedQuery
import fol.result.{VagueQueryResult, EvaluationOutput}
import fol.sampling.{SamplingParams, HDRConfig}
import fol.error.{QueryError, QueryException}
import fol.typed.{TypeCatalog, BoundQuery, QueryBinder, TypeCheckError, RuntimeModel, RuntimeModelError, Value, TypedSemantics}
import semantics.ModelAugmenter
import scala.util.control.NonFatal
import scala.reflect.ClassTag

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
  * See also: [[fol.typed.TypedSemantics]] for the canonical typed evaluation path (ADR-001)
  */
object VagueSemantics:

  /** Bind a parsed query into the typed IL (type-checks variable sorts against the catalog).
    *
    * Phase-1 canonical entrypoint. Runtime evaluation follows in evaluateTyped.
    */
  def bindTyped(
    query: ParsedQuery,
    catalog: TypeCatalog
  ): Either[QueryError, BoundQuery] =
    QueryBinder
      .bind(query, catalog)
      .left
      .map(errors => QueryError.BindError(errors = renderTypeErrors(errors)))

  private def renderTypeErrors(errors: List[TypeCheckError]): List[String] =
    errors.map {
      case TypeCheckError.UnknownPredicate(name)                   => s"unknown predicate: $name"
      case TypeCheckError.UnknownFunction(name)                    => s"unknown function: $name"
      case TypeCheckError.ArityMismatch(symbol, expected, actual)  => s"arity mismatch for '$symbol': expected $expected, actual $actual"
      case TypeCheckError.UnknownConstantOrLiteral(name)           => s"unknown constant or literal: $name"
      case TypeCheckError.TypeMismatch(expected, actual, context)  => s"type mismatch in $context: expected ${expected.value}, actual ${actual.value}"
      case TypeCheckError.UnboundAnswerVar(name)                   => s"unbound answer variable: $name"
      case TypeCheckError.UnconstrainedVar(name)                   => s"unconstrained quantifier variable: $name"
      case TypeCheckError.ConflictingTypes(name, left, right)      => s"conflicting inferred types for '$name': ${left.value} vs ${right.value}"
    }

  private def renderModelErrors(errors: List[RuntimeModelError]): List[String] =
    errors.map {
      case RuntimeModelError.MissingFunctionImplementation(n)  => s"missing function: ${n.value}"
      case RuntimeModelError.MissingPredicateImplementation(n) => s"missing predicate: ${n.value}"
    }

  /** Evaluate a parsed query through the typed pipeline.
    *
    * Canonical flow: ParsedQuery -> bindTyped -> model validation -> TypedSemantics.evaluate
    */
  def evaluateTyped(
    query: ParsedQuery,
    catalog: TypeCatalog,
    model: RuntimeModel,
    answerTuple: Map[String, Value] = Map.empty,
    samplingParams: SamplingParams = SamplingParams.exact,
    hdrConfig: HDRConfig = HDRConfig.default
  ): Either[QueryError, EvaluationOutput[Value]] =
    for
      bound <- bindTyped(query, catalog)
      _ <- model.validateAgainst(catalog).left.map { errors =>
        QueryError.ModelValidationError(errors = renderModelErrors(errors))
      }
      output <- TypedSemantics.evaluate(
        query = bound,
        model = model,
        answerTuple = answerTuple,
        samplingParams = samplingParams,
        hdrConfig = hdrConfig
      )
    yield output

  /** Compile a string-parsed query into a [[ResolvedQuery]].
    *
    * Private — returns Either.  Composes the Either from RangeExtractor
    * with the remaining (throwing) compilation steps inside map.
    */
  private def toResolved[D: DomainElement: DomainCodec: ClassTag](
    query: ParsedQuery,
    source: KnowledgeSource[D],
    answerTuple: Map[String, D],
    samplingParams: SamplingParams,
    hdrConfig: HDRConfig,
    modelAugmenter: ModelAugmenter[D] = ModelAugmenter.identity[D]
  ): Either[QueryError, ResolvedQuery[D]] =
    RangeExtractor.extractRange(source, query, answerTuple).map { rangeElements =>
      val predicate = FOLBridge.scopeToPredicate(
        query.scope, query.variable, source, answerTuple, modelAugmenter
      )
      val vq = VagueQuantifier.fromQuantifier(query.quantifier)
      ResolvedQuery(vq, rangeElements, predicate, samplingParams, hdrConfig)
    }

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
  def holds[D: DomainElement: DomainCodec: ClassTag](
    query: ParsedQuery,
    source: KnowledgeSource[D],
    answerTuple: Map[String, D] = Map.empty[String, D],
    samplingParams: SamplingParams = SamplingParams.exact,
    hdrConfig: HDRConfig = HDRConfig.default,
    modelAugmenter: ModelAugmenter[D] = ModelAugmenter.identity[D]
  ): Either[QueryError, VagueQueryResult] =
    try
      toResolved(query, source, answerTuple, samplingParams, hdrConfig, modelAugmenter)
        .map(_.evaluate())
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
  def evaluate[D: DomainElement: DomainCodec: ClassTag](
    query: ParsedQuery,
    source: KnowledgeSource[D],
    answerTuple: Map[String, D] = Map.empty[String, D],
    samplingParams: SamplingParams = SamplingParams.exact,
    hdrConfig: HDRConfig = HDRConfig.default,
    modelAugmenter: ModelAugmenter[D] = ModelAugmenter.identity[D]
  ): Either[QueryError, EvaluationOutput[D]] =
    try
      toResolved(query, source, answerTuple, samplingParams, hdrConfig, modelAugmenter)
        .map(_.evaluateWithOutput())
    catch
      case e: QueryException => Left(e.error)
      case NonFatal(e) =>
        Left(QueryError.EvaluationError(
          s"Unexpected error during query evaluation: ${e.getMessage}",
          "query_evaluation",
          Some(e),
          Map("query" -> query.toString)
        ))
