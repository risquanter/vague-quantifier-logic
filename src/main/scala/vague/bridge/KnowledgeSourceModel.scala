package vague.bridge

import logic.{Term, Formula, FOL}
import semantics.{Domain, Interpretation, Model, Valuation}
import vague.datastore.{KnowledgeSource, RelationValue, RelationTuple, RelationValueUtil}
import RelationValueUtil.*

/** Bridge between KnowledgeSource and FOL Model Theory.
  * 
  * This is the generalized version of KnowledgeBaseModel that works
  * with any KnowledgeSource implementation (in-memory, SQL, RDF, etc.).
  * 
  * This module translates between two representations:
  * 
  * **KnowledgeSource** (abstract relational interface):
  * - Relations: "person", "knows", "has_risk"
  * - Facts: knows(alice, bob), has_risk(C1, R1)
  * - Implementations: InMemory, SQL, RDF, Graph DB
  * - Good for: explicit storage, querying, updates, scalability
  * 
  * **Model** (semantic/mathematical):
  * - Domain: Set of individuals
  * - Interpretation: Assigns meaning to functions and predicates
  * - Good for: FOL evaluation, logical reasoning, formal semantics
  * 
  * The translation enables:
  * - Evaluating FOL formulas against any data source
  * - Querying diverse backends using FOL syntax
  * - Combining vague quantifiers with relational data
  * 
  * Translation strategy:
  * 1. Domain = Active domain of source (all constants/numbers used)
  * 2. Functions = Constants as 0-ary functions (standard FOL semantics)
  * 3. Predicates = Source relation lookups wrapped as predicate functions
  * 
  * Example usage:
  * ```scala
  * // In-memory backend
  * val kb = KnowledgeBase.builder...build()
  * val source = KnowledgeSource.fromKnowledgeBase(kb)
  * 
  * // SQL backend (future)
  * val conn = DriverManager.getConnection("jdbc:postgresql://...")
  * val source = KnowledgeSource.fromSQLConnection(conn)
  * 
  * // Same API for both:
  * val model = KnowledgeSourceModel.toModel(source)
  * FOLSemantics.holds(formula, model, Valuation(Map.empty))
  * ```
  */
object KnowledgeSourceModel:
  
  /** Translate KnowledgeSource to FOL Model.
    * 
    * The resulting model interprets:
    * - Constants (e.g., "alice", "C1") as 0-ary functions returning themselves
    * - Predicates (e.g., "person", "knows") as lookups in source relations
    * 
    * @param source Knowledge source to translate
    * @return FOL model with source semantics
    */
  def toModel(source: KnowledgeSource): Model[Any] =
    // 1. Domain: all values used in the source
    val activeDomain = source.activeDomain
    
    // Convert RelationValues to their underlying values (String or Int)
    val domainElements: Set[Any] = toDomainSet(activeDomain)
    
    val domain = Domain(domainElements)
    
    // 2. Functions: Constants as 0-ary functions
    // Each constant c is interpreted as a 0-ary function: () => c
    val functions = domainElements.map { value =>
      val name = value.toString
      name -> ((_: List[Any]) => value)
    }.toMap
    
    // 3. Predicates: Wrap source relation lookups
    // Note: We need to get schema from source to know arities
    // For now, we create predicates lazily based on actual queries
    // A better approach would be to have source.getAllRelations()
    val predicates = createPredicatesFromSource(source)
    
    val interpretation = Interpretation(domain, functions, predicates)
    Model(interpretation)
  
  /** Create all predicate functions from a knowledge source.
    * 
    * This iterates through all relations in the source and creates
    * a predicate function for each one.
    * 
    * @param source Knowledge source to extract predicates from
    * @return Map of predicate names to predicate functions
    */
  private def createPredicatesFromSource(source: KnowledgeSource): Map[String, List[Any] => Boolean] =
    source.relationNames.flatMap { relationName =>
      source.getRelation(relationName).map { relation =>
        relationName -> createPredicateFunction(source, relationName, relation.arity)
      }
    }.toMap
  
  /** Create a predicate function that looks up facts in the source.
    * 
    * @param source Knowledge source to query
    * @param relationName Name of the relation
    * @param arity Number of arguments
    * @return Predicate function for FOL interpretation
    */
  private def createPredicateFunction(
    source: KnowledgeSource,
    relationName: String,
    arity: Int
  ): List[Any] => Boolean =
    (args: List[Any]) =>
      // Check arity
      if args.length != arity then
        false
      else
        try
          // Convert FOL values back to RelationValues
          val relationValues = args.map(fromDomainValue)
          val tuple = RelationTuple(relationValues)
          
          // Check if tuple exists in source
          source.contains(relationName, tuple)
        catch
          case _: Exception => false

  /** Extended version of toModel that includes known predicate names.
    * 
    * This version allows you to explicitly specify which predicates
    * should be available in the model. Useful when the source doesn't
    * provide a way to list all relations.
    * 
    * @param source Knowledge source to translate
    * @param predicateNames Map of predicate names to their arities
    * @return FOL model with source semantics
    */
  def toModelWithPredicates(
    source: KnowledgeSource,
    predicateNames: Map[String, Int]
  ): Model[Any] =
    val activeDomain = source.activeDomain
    val domainElements: Set[Any] = toDomainSet(activeDomain)
    val domain = Domain(domainElements)
    
    val functions = domainElements.map { value =>
      val name = value.toString
      name -> ((_: List[Any]) => value)
    }.toMap
    
    val predicates = predicateNames.map { case (name, arity) =>
      name -> createPredicateFunction(source, name, arity)
    }
    
    val interpretation = Interpretation(domain, functions, predicates)
    Model(interpretation)

  /** Helper method to create a model from a source with automatic predicate discovery.
    * 
    * This attempts to discover predicates by trying to get relations from the source.
    * Falls back to empty predicate set if discovery fails.
    * 
    * @param source Knowledge source to translate
    * @return FOL model with discovered predicates
    */
  def toModelWithDiscovery(source: KnowledgeSource): Model[Any] =
    // Try to discover predicates from source
    // This is a best-effort approach for sources that don't expose schema
    val activeDomain = source.activeDomain
    val domainElements: Set[Any] = toDomainSet(activeDomain)
    val domain = Domain(domainElements)
    
    val functions = domainElements.map { value =>
      val name = value.toString
      name -> ((_: List[Any]) => value)
    }.toMap
    
    // For now, return empty predicates
    // In practice, users should use toModelWithPredicates or ensure
    // their source provides schema information
    val predicates = Map.empty[String, List[Any] => Boolean]
    
    val interpretation = Interpretation(domain, functions, predicates)
    Model(interpretation)
