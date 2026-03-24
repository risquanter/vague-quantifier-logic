package fol.query

import fol.quantifier.VagueQuantifier
import fol.datastore.{KnowledgeSource, RelationValue}
import fol.sampling.{SamplingParams, HDRConfig}
import fol.result.{VagueQueryResult, EvaluationOutput}
import fol.error.{QueryError, QueryException}
import scala.util.control.NonFatal

/** Query DSL for vague quantifier queries over knowledge bases.
  * 
  * Enables natural-language-like queries such as:
  * ```scala
  * // "Most components are critical"
  * Query
  *   .quantifier(VagueQuantifier.most)
  *   .over("component")
  *   .whereConst(name => isCritical(name))
  *   .resolve(source)
  *   .evaluate()
  * 
  * // "About half of employees are satisfied"
  * Query
  *   .quantifier(VagueQuantifier.aboutHalf)
  *   .over("employee")
  *   .whereConst(name => name == "satisfied")
  *   .evaluate(source)   // convenience: resolve + evaluate
  * ```
  * 
  * The DSL builds an [[UnresolvedQuery]] which is then resolved
  * against a [[KnowledgeSource]] to produce a [[ResolvedQuery]]
  * (the shared IL). All evaluation flows through `ResolvedQuery`.
  */

/** Unresolved vague quantifier query — domain not yet fetched.
  *
  * Created by the typed DSL builder. Elements are always `RelationValue`
  * (no type parameter — see Decision D9).
  *
  * Call `.resolve(source)` to produce a [[ResolvedQuery]], or
  * `.evaluate(source)` as a convenience that chains resolve + evaluate.
  *
  * @param quantifier  The vague quantifier
  * @param domain      Where to fetch elements (strategy, not data)
  * @param predicate   Scope predicate over RelationValue elements
  * @param params      Sampling parameters
  * @param hdrConfig   HDR PRNG configuration
  */
case class UnresolvedQuery(
  quantifier: VagueQuantifier,
  domain: DomainSpec,
  predicate: RelationValue => Boolean,
  params: SamplingParams = SamplingParams.exact,
  hdrConfig: HDRConfig = HDRConfig.default
):
  /** Resolve this query against a knowledge source.
    *
    * Fetches domain elements from the source and produces a
    * [[ResolvedQuery]] — the shared IL with everything sealed in.
    *
    * @param source Knowledge source to query
    * @return Either error or resolved query ready for evaluation
    */
  def resolve(source: KnowledgeSource[RelationValue]): Either[QueryError, ResolvedQuery] =
    try
      val elements: Set[RelationValue] = domain match
        case DomainSpec.Relation(relationName, position) =>
          source.getDomain(relationName, position)
        case DomainSpec.ActiveDomain =>
          source.activeDomain

      if elements.isEmpty then
        val relName = domain match
          case DomainSpec.Relation(name, _) => name
          case DomainSpec.ActiveDomain => "active_domain"
        Left(QueryError.EmptyRangeError(
          relName,
          domain.toString,
          Some("Check that the relation exists and has data")
        ))
      else
        Right(ResolvedQuery(quantifier, elements, predicate, params, hdrConfig))
    catch
      case e: QueryException => Left(e.error)
      case NonFatal(e) =>
        Left(QueryError.EvaluationError(
          s"Error resolving query domain: ${e.getMessage}",
          "domain_resolution",
          Some(e),
          Map("domain" -> domain.toString)
        ))

  /** Convenience: resolve and evaluate in one step.
    *
    * The `params` stored in this query control evaluation mode:
    * `SamplingParams.exact` for full enumeration,
    * `SamplingParams.default` for statistical sampling.
    * No boolean toggle — see D11.
    *
    * @param source Knowledge source to query
    * @return Either error or VagueQueryResult
    */
  def evaluate(source: KnowledgeSource[RelationValue]): Either[QueryError, VagueQueryResult] =
    resolve(source).map(_.evaluate())

  /** Convenience: resolve and evaluate with element sets in one step.
    *
    * Returns both statistical results and the concrete element sets
    * needed for tree highlighting in register.
    *
    * @param source Knowledge source to query
    * @return Either error or EvaluationOutput with result + element sets
    */
  def evaluateWithOutput(source: KnowledgeSource[RelationValue]): Either[QueryError, EvaluationOutput] =
    resolve(source).map(_.evaluateWithOutput())

/** Specification of the domain to query over. */
enum DomainSpec:
  /** Domain from a specific position of a relation.
    * 
    * Example: Relation("component", 0) = first argument of "component" relation
    */
  case Relation(name: String, position: Int)
  
  /** Domain from all constants in the knowledge base.
    * 
    * This is useful for open-domain queries where the entity type
    * is not restricted to a specific relation.
    */
  case ActiveDomain

/** Fluent query builder. */
object Query:
  
  /** Start building a query with a vague quantifier.
    * 
    * @param q Vague quantifier (e.g., VagueQuantifier.most)
    * @return Query builder
    */
  def quantifier(q: VagueQuantifier): QuantifierBuilder =
    new QuantifierBuilder(q)
  
  /** Query builder after quantifier is specified. */
  class QuantifierBuilder(q: VagueQuantifier):
    
    /** Specify the domain to query over (a relation).
      * 
      * @param relationName Name of the relation
      * @param position Position in the relation (default: 0)
      * @return Domain builder
      */
    def over(relationName: String, position: Int = 0): DomainBuilder =
      new DomainBuilder(q, DomainSpec.Relation(relationName, position))
    
    /** Specify the domain as the full active domain. */
    def overActiveDomain: DomainBuilder =
      new DomainBuilder(q, DomainSpec.ActiveDomain)
  
  /** Query builder after domain is specified. */
  class DomainBuilder(q: VagueQuantifier, domain: DomainSpec):
    
    /** Specify the predicate over RelationValue elements.
      * 
      * @param pred Predicate function on RelationValue
      * @return Complete unresolved query
      */
    def where(pred: RelationValue => Boolean): UnresolvedQuery =
      UnresolvedQuery(q, domain, pred)

    /** Specify the predicate over unwrapped Const names.
      *
      * Convenience for the common case where elements are
      * `RelationValue.Const(name)` and the predicate operates
      * on the String name. Non-Const values fail the predicate.
      *
      * @param pred Predicate function on the unwrapped String name
      * @return Complete unresolved query
      */
    def whereConst(pred: String => Boolean): UnresolvedQuery =
      val rvPred: RelationValue => Boolean = {
        case RelationValue.Const(name) => pred(name)
        case _ => false
      }
      UnresolvedQuery(q, domain, rvPred)
    
    /** Alias for where. */
    def satisfying(pred: RelationValue => Boolean): UnresolvedQuery =
      where(pred)
    
    /** Specify predicate with sampling parameters.
      * 
      * @param pred Predicate function
      * @param params Sampling parameters
      * @param config HDR PRNG configuration
      * @return Complete unresolved query
      */
    def where(pred: RelationValue => Boolean, params: SamplingParams, config: HDRConfig = HDRConfig.default): UnresolvedQuery =
      UnresolvedQuery(q, domain, pred, params, config)

    /** Specify predicate over Const names with sampling parameters. */
    def whereConst(pred: String => Boolean, params: SamplingParams, config: HDRConfig = HDRConfig.default): UnresolvedQuery =
      val rvPred: RelationValue => Boolean = {
        case RelationValue.Const(name) => pred(name)
        case _ => false
      }
      UnresolvedQuery(q, domain, rvPred, params, config)

/** Extension methods for convenient querying. */
extension (source: KnowledgeSource[RelationValue])
  
  /** Execute a vague query against this knowledge source.
    * 
    * @param query Query to execute
    * @return Either error or VagueQueryResult
    */
  def execute(query: UnresolvedQuery): Either[QueryError, VagueQueryResult] =
    query.evaluate(source)

  /** Execute with element sets (for tree highlighting).
    *
    * @param query Query to execute
    * @return Either error or EvaluationOutput with result + element sets
    */
  def executeWithOutput(query: UnresolvedQuery): Either[QueryError, EvaluationOutput] =
    query.evaluateWithOutput(source)

/** Predicate builders for common patterns. */
object Predicates:
  
  /** Predicate that checks if a relation holds.
    * 
    * Example: hasRelation(kb, "high_severity", rv => List(rv))
    * 
    * @param source Knowledge source to query
    * @param relationName Name of the relation
    * @param argMapper Function to map entity to relation arguments
    * @return Predicate function
    */
  def hasRelation(
    source: KnowledgeSource[RelationValue],
    relationName: String,
    argMapper: RelationValue => List[RelationValue]
  ): RelationValue => Boolean =
    (entity: RelationValue) =>
      val args = argMapper(entity)
      val pattern = args.map(Some(_))
      source.query(relationName, pattern).nonEmpty
  
  /** Predicate that checks if entity appears in a unary relation.
    * 
    * Example: inRelation(kb, "critical_component")
    * 
    * @param source Knowledge source to query
    * @param relationName Name of the unary relation
    * @return Predicate function
    */
  def inRelation(
    source: KnowledgeSource[RelationValue],
    relationName: String
  ): RelationValue => Boolean =
    (entity: RelationValue) =>
      val tuple = fol.datastore.RelationTuple(List(entity))
      source.contains(relationName, tuple)
  
  /** Predicate that checks if a binary relation holds for an entity.
    * 
    * Example: relatedTo(kb, "has_risk", "R1")  // entities that have risk R1
    * 
    * @param source Knowledge source to query
    * @param relationName Name of the binary relation
    * @param secondArg The second argument value
    * @return Predicate function
    */
  def relatedTo(
    source: KnowledgeSource[RelationValue],
    relationName: String,
    secondArg: String
  ): RelationValue => Boolean =
    (entity: RelationValue) =>
      val pattern = List(
        Some(entity),
        Some(RelationValue.Const(secondArg))
      )
      source.query(relationName, pattern).nonEmpty
