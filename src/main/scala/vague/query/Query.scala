package vague.query

import scala.reflect.ClassTag
import vague.quantifier.VagueQuantifier
import vague.datastore.{KnowledgeSource, RelationValue, RelationValueUtil}
import vague.sampling.{SamplingParams, HDRConfig}
import vague.semantics.DomainExtraction
import RelationValueUtil.*

/** Query DSL for vague quantifier queries over knowledge bases.
  * 
  * Enables natural-language-like queries such as:
  * ```scala
  * // "Most components are critical"
  * Query
  *   .quantifier(VagueQuantifier.most)
  *   .over("component")
  *   .where(comp => isCritical(comp))
  *   .evaluate(kb)
  * 
  * // "About half of employees are satisfied"
  * Query
  *   .quantifier(VagueQuantifier.aboutHalf)
  *   .over("employee")
  *   .where(_.hasAttribute("satisfied", "true"))
  *   .evaluate(kb)
  * 
  * // "Few risks are high severity"
  * Query
  *   .quantifier(VagueQuantifier.few)
  *   .over("risk")
  *   .satisfying(risk => kb.hasRelation("high_severity", List(risk)))
  *   .evaluate(kb)
  * ```
  * 
  * The DSL compiles to:
  * 1. Extract domain from knowledge source (with sampling)
  * 2. Evaluate predicate on each sampled element
  * 3. Compute proportion and check against quantifier
  * 
  * Designed for extensibility:
  * - Predicates can be Scala functions (in-memory evaluation)
  * - Future: compile predicates to SQL (database evaluation)
  * - Future: optimize by pushing predicates to knowledge source
  */

/** Query AST representing a vague quantifier query. */
case class VagueQuery[A: ClassTag](
  quantifier: VagueQuantifier,
  domain: DomainSpec,
  predicate: A => Boolean,
  params: SamplingParams = SamplingParams(),
  hdrConfig: HDRConfig = HDRConfig.default
):
  /** Evaluate this query against a knowledge source.
    * 
    * @param source Knowledge source to query
    * @return Query result with satisfaction and proportion
    */
  def evaluate(source: KnowledgeSource): QueryResult =
    domain match
      case DomainSpec.Relation(relationName, position) =>
        // Extract domain from relation - always use full domain for now
        // (sampling happens inside evaluateWithSampling)
        val domainValues = source.getDomain(relationName, position)
        
        // Convert RelationValues to the expected type A (typically String)
        val population: Set[A] = toDomainSetTyped[A](domainValues)
        
        // Evaluate quantifier with sampling
        val result = quantifier.evaluateWithSampling(population, predicate, params, hdrConfig)
        
        QueryResult(
          query = this.asInstanceOf[VagueQuery[Any]],
          result = result,
          sampleSize = result.estimate.sampleSize,
          domainSize = population.size
        )
      
      case DomainSpec.ActiveDomain =>
        // Use full active domain (all constants in KB)
        val domainValues = source.activeDomain
        
        val population: Set[A] = toDomainSetTyped[A](domainValues)
        
        val result = quantifier.evaluateWithSampling(population, predicate, params, hdrConfig)
        
        QueryResult(
          query = this.asInstanceOf[VagueQuery[Any]],
          result = result,
          sampleSize = result.estimate.sampleSize,
          domainSize = population.size
        )

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

/** Result of evaluating a vague query. */
case class QueryResult(
  query: VagueQuery[Any],
  result: vague.quantifier.QuantifierResult,
  sampleSize: Int,
  domainSize: Int
):
  /** Whether the query is satisfied. */
  def satisfied: Boolean = result.satisfied
  
  /** Estimated proportion. */
  def proportion: Double = result.proportion
  
  /** Confidence interval. */
  def confidenceInterval: (Double, Double) = result.confidenceInterval
  
  /** Human-readable summary. */
  def summary: String =
    s"${result.summary} (sampled $sampleSize of $domainSize elements)"
  
  /** Is the result statistically significant? */
  def isSignificant: Boolean = result.isSignificant

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
    def over(relationName: String, position: Int = 0): DomainBuilder[String] =
      new DomainBuilder(q, DomainSpec.Relation(relationName, position))
    
    /** Specify the domain as the full active domain. */
    def overActiveDomain: DomainBuilder[String] =
      new DomainBuilder(q, DomainSpec.ActiveDomain)
  
  /** Query builder after domain is specified. */
  class DomainBuilder[A: ClassTag](q: VagueQuantifier, domain: DomainSpec):
    
    /** Specify the predicate to test.
      * 
      * @param pred Predicate function
      * @return Complete query
      */
    def where(pred: A => Boolean): VagueQuery[A] =
      VagueQuery(q, domain, pred)
    
    /** Alias for where. */
    def satisfying(pred: A => Boolean): VagueQuery[A] =
      where(pred)
    
    /** Specify predicate with sampling parameters.
      * 
      * @param pred Predicate function
      * @param params Sampling parameters
      * @param config HDR PRNG configuration
      * @return Complete query
      */
    def where(pred: A => Boolean, params: SamplingParams, config: HDRConfig = HDRConfig.default): VagueQuery[A] =
      VagueQuery(q, domain, pred, params, config)

/** Extension methods for convenient querying. */
extension (source: KnowledgeSource)
  
  /** Execute a vague query against this knowledge source.
    * 
    * @param query Query to execute
    * @return Query result
    */
  def execute[A](query: VagueQuery[A]): QueryResult =
    query.evaluate(source)

/** Predicate builders for common patterns. */
object Predicates:
  
  /** Predicate that checks if a relation holds.
    * 
    * Example: hasRelation(kb, "high_severity", risk => List(risk))
    * 
    * @param source Knowledge source to query
    * @param relationName Name of the relation
    * @param argMapper Function to map entity to relation arguments
    * @return Predicate function
    */
  def hasRelation[A](
    source: KnowledgeSource,
    relationName: String,
    argMapper: A => List[RelationValue]
  ): A => Boolean =
    (entity: A) =>
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
    source: KnowledgeSource,
    relationName: String
  ): String => Boolean =
    (entityName: String) =>
      val tuple = vague.datastore.RelationTuple.fromConstants(entityName)
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
    source: KnowledgeSource,
    relationName: String,
    secondArg: String
  ): String => Boolean =
    (entityName: String) =>
      val pattern = List(
        Some(RelationValue.Const(entityName)),
        Some(RelationValue.Const(secondArg))
      )
      source.query(relationName, pattern).nonEmpty
