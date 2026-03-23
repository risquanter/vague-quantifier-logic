package fol.bridge

import logic.{Term, Formula, FOL}
import semantics.{Domain, Interpretation, Model, Valuation}
import fol.datastore.{KnowledgeBase, RelationValue, RelationTuple, RelationValueUtil}
import RelationValueUtil.*

/** Bridge between KnowledgeBase and FOL Model Theory.
  * 
  * This module translates between two representations:
  * 
  * **KnowledgeBase** (relational/symbolic):
  * - Relations: "person", "knows", "has_risk"
  * - Facts: knows(alice, bob), has_risk(C1, R1)
  * - Good for: explicit storage, querying, updates
  * 
  * **Model** (semantic/mathematical):
  * - Domain: Set of individuals
  * - Interpretation: Assigns meaning to functions and predicates
  * - Good for: FOL evaluation, logical reasoning, formal semantics
  * 
  * The translation enables:
  * - Evaluating FOL formulas against KB data
  * - Querying KB using FOL syntax
  * - Combining vague quantifiers with relational data
  * 
  * Translation strategy:
  * 1. Domain = Active domain of KB (all constants/numbers used)
  * 2. Functions = Constants as 0-ary functions (standard FOL semantics)
  * 3. Predicates = KB relation lookups wrapped as predicate functions
  */
object KnowledgeBaseModel:
  
  /** Translate KnowledgeBase to FOL Model.
    * 
    * The resulting model interprets:
    * - Constants (e.g., "alice", "C1") as 0-ary functions returning themselves
    * - Predicates (e.g., "person", "knows") as lookups in KB relations
    * 
    * Example usage:
    * ```scala
    * val kb = KnowledgeBase.builder
    *   .withUnaryRelation("person")
    *   .withBinaryRelation("knows")
    *   .withFact("person", "alice")
    *   .withFact("person", "bob")
    *   .withFact("knows", "alice", "bob")
    *   .build()
    * 
    * val model = KnowledgeBaseModel.toModel(kb)
    * 
    * // Now evaluate FOL formulas:
    * val formula = FOLParser.parse("person(alice) /\\ knows(alice, bob)")
    * FOLSemantics.holds(formula, model, Valuation(Map.empty))  // true
    * ```
    * 
    * @param kb Knowledge base to translate
    * @return FOL model with KB semantics
    */
  def toModel(kb: KnowledgeBase): Model[Any] =
    // 1. Domain: all values used in the KB
    val activeDomain = kb.activeDomain
    
    // Convert RelationValues to their underlying values (String or Int)
    val domainElements: Set[Any] = toDomainSet(activeDomain)
    
    val domain = Domain(domainElements)
    
    // 2. Functions: Constants as 0-ary functions
    // Each constant c is interpreted as a 0-ary function: () => c
    val functions = domainElements.map { value =>
      val name = value.toString
      name -> ((_: List[Any]) => value)
    }.toMap
    
    // 3. Predicates: Wrap KB relation lookups
    val predicates = kb.schema.map { case (relationName, relation) =>
      relationName -> createPredicateFunction(kb, relationName, relation.arity)
    }.toMap
    
    val interpretation = Interpretation(domain, functions, predicates)
    Model(interpretation)
  
  /** Create a predicate function that looks up facts in the KB.
    * 
    * @param kb Knowledge base to query
    * @param relationName Name of the relation
    * @param arity Number of arguments
    * @return Predicate function for FOL interpretation
    */
  private def createPredicateFunction(
    kb: KnowledgeBase,
    relationName: String,
    arity: Int
  ): List[Any] => Boolean =
    (args: List[Any]) =>
      // Check arity
      if args.length != arity then
        false
      else
        try {
          // Convert Scala values back to RelationValues
          val values = args.map(fromDomainValue)
        
          // Look up in KB
          val tuple = RelationTuple(values)
          kb.contains(relationName, tuple)
        } catch {
          case _: IllegalArgumentException => false
        }

/** Extension methods for KnowledgeBase. */
extension (kb: KnowledgeBase)
  
  /** Convert this knowledge base to an FOL model.
    * 
    * Delegates to KnowledgeBaseModel.toModel.
    * 
    * @return FOL model with KB semantics
    */
  def toModel: Model[Any] =
    KnowledgeBaseModel.toModel(kb)
  
  /** Evaluate an FOL formula against this knowledge base.
    * 
    * This is a convenience method that:
    * 1. Translates KB to Model
    * 2. Evaluates the formula in the model
    * 
    * @param formula FOL formula to evaluate
    * @param valuation Variable assignments (default: empty)
    * @return true if formula holds in the KB
    */
  def holds(formula: Formula[logic.FOL], valuation: Valuation[Any] = Valuation(Map.empty)): Boolean =
    import semantics.FOLSemantics
    val model = toModel
    FOLSemantics.holds(formula, model, valuation)
  
  /** Check if KB entails a formula.
    * 
    * KB ⊨ φ iff φ holds for all possible valuations.
    * For ground formulas (no free variables), this reduces to holds(φ).
    * 
    * @param formula FOL formula
    * @return true if KB entails the formula
    */
  def entails(formula: Formula[logic.FOL]): Boolean =
    // For simplicity, only handle ground formulas
    // Full implementation would need to quantify over all valuations
    holds(formula, Valuation(Map.empty))
