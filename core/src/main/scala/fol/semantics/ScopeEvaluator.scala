package fol.semantics

import logic.{FOL, Formula}
import semantics.{Model, Valuation, FOLSemantics}
// EvaluationContext and holdsWithBinding are in the same package (fol.semantics)

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
    * @tparam D Domain element type
    * @param formula Scope formula φ(x,y)
    * @param element Value for x from range D_R
    * @param variable Name of quantified variable x
    * @param model FOL model (from KB via toModel)
    * @param substitution Values for answer variables y
    * @return true if D ⊨_σ φ (uses FOLSemantics.holds!)
    */
  def evaluateForElement[D](
    formula: Formula[FOL],
    element: D,
    variable: String,
    model: Model[D],
    substitution: Map[String, D] = Map.empty
  ): Boolean =
    val ctx = EvaluationContext(model, substitution)
    ctx.holdsWithBinding(formula, variable, element)
  
  /** Calculate proportion (paper's Prop_D)
    * 
    * Prop_D(S, φ(x,c)) = |{x ∈ S | D ⊨ φ(x,c)}| / |S|
    * 
    * @tparam D Domain element type
    * @param sample Sample S ⊆ D_R
    * @param formula Scope formula φ(x,y)
    * @param variable Quantified variable x
    * @param model FOL model (from KB)
    * @param substitution Values for answer variables y
    * @return Proportion in [0, 1]
    */
  def calculateProportion[D](
    sample: Set[D],
    formula: Formula[FOL],
    variable: String,
    model: Model[D],
    substitution: Map[String, D] = Map.empty
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
    * @tparam D Domain element type
    * @param sample Sample S ⊆ D_R
    * @param formula Scope formula φ(x,y)
    * @param variable Quantified variable x
    * @param model FOL model
    * @param substitution Values for y
    * @return (satisfying_set, non_satisfying_set)
    */
  def evaluateSample[D](
    sample: Set[D],
    formula: Formula[FOL],
    variable: String,
    model: Model[D],
    substitution: Map[String, D] = Map.empty
  ): (Set[D], Set[D]) =
    val (satisfying, nonSatisfying) = sample.partition(elem =>
      evaluateForElement(formula, elem, variable, model, substitution)
    )
    (satisfying, nonSatisfying)
  
  /** Evaluate formula for multiple elements (bulk operation)
    * 
    * Returns map from elements to their satisfaction results.
    * Useful for caching and analysis.
    * 
    * @tparam D Domain element type
    * @param elements Set of elements to evaluate
    * @param formula Scope formula φ(x,y)
    * @param variable Quantified variable x
    * @param model FOL model
    * @param substitution Values for y
    * @return Map from elements to satisfaction (true/false)
    */
  def evaluateElements[D](
    elements: Set[D],
    formula: Formula[FOL],
    variable: String,
    model: Model[D],
    substitution: Map[String, D] = Map.empty
  ): Map[D, Boolean] =
    elements.map { elem =>
      elem -> evaluateForElement(formula, elem, variable, model, substitution)
    }.toMap
  
  /** Count satisfying elements (optimization when only count is needed)
    * 
    * More efficient than calculateProportion when we don't need the ratio.
    * 
    * @tparam D Domain element type
    * @param sample Sample S ⊆ D_R
    * @param formula Scope formula φ(x,y)
    * @param variable Quantified variable x
    * @param model FOL model
    * @param substitution Values for y
    * @return Number of satisfying elements
    */
  def countSatisfying[D](
    sample: Set[D],
    formula: Formula[FOL],
    variable: String,
    model: Model[D],
    substitution: Map[String, D] = Map.empty
  ): Int =
    sample.count(elem =>
      evaluateForElement(formula, elem, variable, model, substitution)
    )
