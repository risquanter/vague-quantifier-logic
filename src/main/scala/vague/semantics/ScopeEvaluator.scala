package vague.semantics

import logic.{FOL, Formula}
import semantics.{Model, Valuation, FOLSemantics, EvaluationContext}
import semantics.holdsWithRelationValue  // Extension method
import vague.datastore.{RelationValue, RelationValueUtil}
import RelationValueUtil.*

/** Evaluate scope formula using FOL semantics (paper Definition 2).
  * 
  * Prop_D(S, φ(x,c)) = |{x ∈ S | D ⊨ φ(x,c)}| / |S|
  * 
  * OCaml-style: module with pure functions (object with methods)
  * OCaml reference: fol.ml has "let rec holds (domain,func,pred) v fm = ..."
  * 
  * Paper reference: Definition 2 (Section 5.2)
  * "For a sample S ⊆ D_R, Prop_D(S, φ(x,c)) is the proportion of 
  *  elements in S that satisfy φ under valuation σ{x ↦ element}"
  * 
  * This is THE KEY INTEGRATION POINT between FOL infrastructure and
  * vague quantifiers. Scope formulas φ(x,y) are FOL formulas evaluated
  * using FOLSemantics.holds(), not Scala functions.
  */
object ScopeEvaluator:
  
  /** Evaluate scope formula for single element
    * 
    * Check: D ⊨_σ φ where σ maps x ↦ element
    * 
    * OCaml pattern: recursive function with pattern matching
    * OCaml reference: fol.ml
    *   let rec holds (domain,func,pred as m) v fm =
    *     match fm with
    *       False -> false
    *     | True -> true
    *     | Atom(R(r,args)) -> pred r (map (termval m v) args)
    *     (* ... *)
    * 
    * Scala translation:
    *   FOLSemantics.holds(formula, model, valuation)
    * 
    * @param formula Scope formula φ(x,y)
    * @param element Value for x from range D_R
    * @param variable Name of quantified variable x
    * @param model FOL model (from KB via toModel)
    * @param substitution Values for answer variables y
    * @return true if D ⊨_σ φ (uses FOLSemantics.holds!)
    */
  def evaluateForElement(
    formula: Formula[FOL],
    element: RelationValue,
    variable: String,
    model: Model[Any],
    substitution: Map[String, Any] = Map.empty
  ): Boolean =
    // Create evaluation context with substitution (answer variables)
    val ctx = EvaluationContext(model, substitution)
    
    // THE KEY INTEGRATION: Use EvaluationContext.holdsWithRelationValue()
    // This combines:
    // 1. RelationValue → domain value conversion
    // 2. Variable binding (σ{x ↦ element})
    // 3. Formula evaluation via FOLSemantics
    ctx.holdsWithRelationValue(formula, variable, element)
  
  /** Calculate proportion (paper's Prop_D)
    * 
    * Prop_D(S, φ(x,c)) = |{x ∈ S | D ⊨ φ(x,c)}| / |S|
    * 
    * Where:
    * - S is a sample from D_R (range domain)
    * - φ(x,c) is the scope formula with x free, c substituted
    * - D ⊨ φ(x,c) means "φ holds in database D"
    * 
    * OCaml pattern: higher-order function (filter + count)
    * OCaml reference: lib.ml has filter, length
    *   let satisfying = filter (fun x -> holds model (x::valuation) formula) sample
    *   length satisfying / length sample
    * 
    * @param sample Sample S ⊆ D_R
    * @param formula Scope formula φ(x,y)
    * @param variable Quantified variable x
    * @param model FOL model (from KB)
    * @param substitution Values for answer variables y
    * @return Proportion in [0, 1]
    */
  def calculateProportion(
    sample: Set[RelationValue],
    formula: Formula[FOL],
    variable: String,
    model: Model[Any],
    substitution: Map[String, Any] = Map.empty
  ): Double =
    if sample.isEmpty then 0.0
    else
      val satisfying = sample.count(elem =>
        evaluateForElement(formula, elem, variable, model, substitution)
      )
      satisfying.toDouble / sample.size.toDouble
  
  /** Batch evaluation (optimization for large samples)
    * 
    * Partition sample into elements satisfying and not satisfying φ.
    * Useful for debugging and analysis.
    * 
    * OCaml pattern: partition via filter
    * OCaml: let (sat, nonsat) = partition predicate list
    * 
    * @param sample Sample S ⊆ D_R
    * @param formula Scope formula φ(x,y)
    * @param variable Quantified variable x
    * @param model FOL model
    * @param substitution Values for y
    * @return (satisfying_set, non_satisfying_set)
    */
  def evaluateSample(
    sample: Set[RelationValue],
    formula: Formula[FOL],
    variable: String,
    model: Model[Any],
    substitution: Map[String, Any] = Map.empty
  ): (Set[RelationValue], Set[RelationValue]) =
    // Partition into satisfying and non-satisfying
    // OCaml: partition (fun elem -> holds ...) sample
    val (satisfying, nonSatisfying) = sample.partition(elem =>
      evaluateForElement(formula, elem, variable, model, substitution)
    )
    (satisfying, nonSatisfying)
  
  /** Evaluate formula for multiple elements (bulk operation)
    * 
    * Returns map from elements to their satisfaction results.
    * Useful for caching and analysis.
    * 
    * OCaml pattern: map then filter
    * OCaml: map (fun x -> (x, holds model valuation formula)) list
    * 
    * @param elements Set of elements to evaluate
    * @param formula Scope formula φ(x,y)
    * @param variable Quantified variable x
    * @param model FOL model
    * @param substitution Values for y
    * @return Map from elements to satisfaction (true/false)
    */
  def evaluateElements(
    elements: Set[RelationValue],
    formula: Formula[FOL],
    variable: String,
    model: Model[Any],
    substitution: Map[String, Any] = Map.empty
  ): Map[RelationValue, Boolean] =
    elements.map { elem =>
      elem -> evaluateForElement(formula, elem, variable, model, substitution)
    }.toMap
  
  /** Count satisfying elements (optimization when only count is needed)
    * 
    * More efficient than calculateProportion when we don't need the ratio.
    * 
    * @param sample Sample S ⊆ D_R
    * @param formula Scope formula φ(x,y)
    * @param variable Quantified variable x
    * @param model FOL model
    * @param substitution Values for y
    * @return Number of satisfying elements
    */
  def countSatisfying(
    sample: Set[RelationValue],
    formula: Formula[FOL],
    variable: String,
    model: Model[Any],
    substitution: Map[String, Any] = Map.empty
  ): Int =
    sample.count(elem =>
      evaluateForElement(formula, elem, variable, model, substitution)
    )
