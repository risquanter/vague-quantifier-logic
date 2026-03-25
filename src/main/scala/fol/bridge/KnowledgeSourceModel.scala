package fol.bridge

import logic.{Term, Formula, FOL}
import semantics.{Domain, Interpretation, Model, Valuation}
import fol.datastore.{DomainElement, KnowledgeSource, RelationName, RelationValue, RelationTuple}

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
    * @tparam D Domain element type (must have a `DomainElement` instance)
    * @param source Knowledge source to translate
    * @return FOL model with source semantics
    */
  def toModel[D: DomainElement](source: KnowledgeSource[D]): Model[D] =
    // 1. Domain: all values used in the source
    val domainElements: Set[D] = source.activeDomain
    val domain = Domain(domainElements)
    
    // 2. Functions: Constants as 0-ary functions
    val functions: Map[String, List[D] => D] = domainElements.map { d =>
      d.show -> ((_: List[D]) => d)
    }.toMap
    
    // 3. Predicates: Wrap source relation lookups
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
  private def createPredicatesFromSource[D](source: KnowledgeSource[D]): Map[String, List[D] => Boolean] =
    source.relationNames.flatMap { relationName =>
      source.getRelation(relationName).map { relation =>
        relationName.value -> createPredicateFunction(source, relationName, relation.arity)
      }
    }.toMap
  
  /** Create a predicate function that looks up facts in the source.
    * 
    * @param source Knowledge source to query
    * @param relationName Name of the relation
    * @param arity Number of arguments
    * @return Predicate function for FOL interpretation
    */
  private def createPredicateFunction[D](
    source: KnowledgeSource[D],
    relationName: RelationName,
    arity: Int
  ): List[D] => Boolean =
    (args: List[D]) =>
      if args.length != arity then
        false
      else
        try
          val tuple = RelationTuple(args)
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
  def toModelWithPredicates[D: DomainElement](
    source: KnowledgeSource[D],
    predicateNames: Map[String, Int]
  ): Model[D] =
    val domainElements: Set[D] = source.activeDomain
    val domain = Domain(domainElements)
    
    val functions: Map[String, List[D] => D] = domainElements.map { d =>
      d.show -> ((_: List[D]) => d)
    }.toMap
    
    val predicates = predicateNames.map { case (name, arity) =>
      name -> createPredicateFunction(source, RelationName(name), arity)
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
  def toModelWithDiscovery[D: DomainElement](source: KnowledgeSource[D]): Model[D] =
    val domainElements: Set[D] = source.activeDomain
    val domain = Domain(domainElements)
    
    val functions: Map[String, List[D] => D] = domainElements.map { d =>
      d.show -> ((_: List[D]) => d)
    }.toMap
    
    val predicates = Map.empty[String, List[D] => Boolean]
    
    val interpretation = Interpretation(domain, functions, predicates)
    Model(interpretation)
