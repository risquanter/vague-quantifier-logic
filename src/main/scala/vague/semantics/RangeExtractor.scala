package vague.semantics

import logic.{FOL, Term}
import vague.datastore.{KnowledgeBase, RelationValue, RelationTuple}
import vague.logic.VagueQuery

/** Range Extraction (D_R)
  * 
  * Extracts the domain of quantification from a knowledge base based on the
  * range predicate R(x,y') in a vague query Q x (R(x,y'), φ(x,y))(y).
  * 
  * From paper (Definition 2):
  *   D_R = {c ∈ ADom(D) | R(c,σ(y')) ∈ D}
  * 
  * Where:
  * - ADom(D) is the active domain (all constants in the KB)
  * - R is the range relation
  * - c is a candidate for the quantified variable x
  * - σ(y') is the substitution for free variables y' in the range
  * - D is the database (knowledge base)
  * 
  * OCaml reference: This component follows the relational query pattern
  * from fol.ml, where we extract tuples satisfying predicates.
  * 
  * Example:
  *   Query: Q[≥]^{3/4} x (country(x), ...)
  *   Range: country(x)
  *   D_R: all elements c where country(c) holds in KB
  * 
  * Example with substitution:
  *   Query: Q[~#]^{1/2} x (capital(x), ...)(y)  where x is capital of y
  *   Range: capital(x)
  *   Substitution: {y → "France"}
  *   D_R: all elements c where capital(c, "France") holds in KB
  */
object RangeExtractor:
  
  /** Extract range domain D_R from knowledge base
    * 
    * Given a vague query and a substitution for free variables,
    * extract all domain elements that satisfy the range predicate.
    * 
    * Algorithm:
    * 1. Identify the range relation R(x, y')
    * 2. Apply substitution σ to free variables y'
    * 3. Query KB for all tuples matching R(?, σ(y'))
    * 4. Extract values at the position of quantified variable x
    * 
    * @param kb The knowledge base to query
    * @param query The vague query containing the range predicate
    * @param substitution Values for free variables (answer variables from query)
    * @return Set of domain elements satisfying the range predicate
    */
  def extractRange(
    kb: KnowledgeBase,
    query: VagueQuery,
    substitution: Map[String, RelationValue] = Map.empty
  ): Set[RelationValue] =
    val range = query.range
    val quantifiedVar = query.variable
    
    // Build query pattern: replace variables with values or wildcards
    val pattern = buildPattern(range, quantifiedVar, substitution)
    
    // Query KB for matching tuples
    val matchingTuples = kb.query(range.predicate, pattern)
    
    // Extract values at quantified variable position
    val quantVarPosition = findVariablePosition(range, quantifiedVar)
    matchingTuples.map(_.values(quantVarPosition))
  
  /** Build query pattern for KB lookup
    * 
    * Converts FOL range predicate to KB query pattern:
    * - Quantified variable x → None (wildcard)
    * - Free variables with substitution → Some(value)
    * - Free variables without substitution → None (wildcard)
    * 
    * Example:
    *   Range: capital(x, y)
    *   Quantified: x
    *   Substitution: {y → "France"}
    *   Pattern: [None, Some(Const("France"))]
    */
  private def buildPattern(
    range: FOL,
    quantifiedVar: String,
    substitution: Map[String, RelationValue]
  ): List[Option[RelationValue]] =
    range.terms.map {
      case Term.Var(v) if v == quantifiedVar =>
        None  // Wildcard for quantified variable
      
      case Term.Var(v) =>
        substitution.get(v)  // Substitute if available, else wildcard
      
      case Term.Const(c) =>
        // Constants in range: try to parse as RelationValue
        // For now, treat as string constant (TODO: handle numeric constants)
        Some(RelationValue.Const(c))
      
      case Term.Fn(_, _) =>
        // Function terms not supported in KB queries
        throw new UnsupportedOperationException(
          "Function terms in range predicates not yet supported"
        )
    }
  
  /** Find position of quantified variable in range predicate
    * 
    * Scans the range relation terms to find which position
    * contains the quantified variable.
    * 
    * Example:
    *   capital(x, y) with quantified var x → position 0
    *   capital(y, x) with quantified var x → position 1
    */
  private def findVariablePosition(range: FOL, quantifiedVar: String): Int =
    range.terms.indexWhere {
      case Term.Var(v) => v == quantifiedVar
      case _ => false
    } match {
      case -1 =>
        throw new IllegalStateException(
          s"Quantified variable $quantifiedVar not found in range ${range.predicate}"
        )
      case pos => pos
    }
  
  /** Extract range for Boolean queries (no free variables)
    * 
    * Convenience method for Boolean queries where there are no
    * answer variables requiring substitution.
    */
  def extractRangeBoolean(kb: KnowledgeBase, query: VagueQuery): Set[RelationValue] =
    require(query.isBoolean, "Query must be Boolean (no answer variables)")
    extractRange(kb, query, Map.empty)
  
  /** Extract range with single answer variable
    * 
    * Convenience method for unary queries with one answer variable.
    */
  def extractRangeUnary(
    kb: KnowledgeBase,
    query: VagueQuery,
    answerValue: RelationValue
  ): Set[RelationValue] =
    require(query.isUnary, "Query must be unary (single answer variable)")
    val answerVar = query.answerVars.head
    extractRange(kb, query, Map(answerVar -> answerValue))
  
  /** Get all possible ranges for a query with answer variables
    * 
    * For queries with answer variables y, we need to evaluate the range
    * for each possible substitution σ(y). This returns a map from
    * substitutions to their corresponding ranges.
    * 
    * This is used for computing query results where we evaluate
    * the vague quantifier for each answer tuple.
    * 
    * Example:
    *   Query: Q[~#]^{1/2} x (capital(x, y), ...)(y)
    *   Returns: Map(
    *     {y → "France"} → Set("Paris"),
    *     {y → "Germany"} → Set("Berlin"),
    *     ...
    *   )
    */
  def extractAllRanges(
    kb: KnowledgeBase,
    query: VagueQuery
  ): Map[Map[String, RelationValue], Set[RelationValue]] =
    if query.isBoolean then
      // Boolean query: single range with empty substitution
      Map(Map.empty -> extractRangeBoolean(kb, query))
    else
      // Generate all possible substitutions for answer variables
      val substitutions = generateSubstitutions(kb, query)
      substitutions.map { subst =>
        subst -> extractRange(kb, query, subst)
      }.toMap
  
  /** Generate all possible substitutions for answer variables
    * 
    * For query with answer variables y, generate all possible
    * value assignments from the active domain.
    * 
    * This is used internally by extractAllRanges.
    */
  private def generateSubstitutions(
    kb: KnowledgeBase,
    query: VagueQuery
  ): Set[Map[String, RelationValue]] =
    // For now, use active domain for all answer variables
    // TODO: Could optimize by using getDomain for specific relations
    val domain = kb.activeDomain.toList
    
    def generateForVars(vars: List[String]): Set[Map[String, RelationValue]] =
      vars match
        case Nil => Set(Map.empty)
        case v :: rest =>
          val restSubsts = generateForVars(rest)
          for
            value <- domain.toSet
            restSubst <- restSubsts
          yield restSubst + (v -> value)
    
    generateForVars(query.answerVars)
