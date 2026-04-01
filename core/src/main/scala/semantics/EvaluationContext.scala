package semantics

import logic.{FOL, Formula, Term}

/** Context for evaluating FOL formulas in a model.
  * 
  * Encapsulates the model and valuation together, providing a cleaner API
  * for formula evaluation. Reduces parameter passing and makes the evaluation
  * code more readable.
  * 
  * This is a convenience wrapper around FOLSemantics that maintains the
  * tagless initial architecture - it doesn't change the evaluation semantics,
  * just provides a more ergonomic interface.
  * 
  * Example usage:
  * {{{
  * val ctx = EvaluationContext(model, Valuation(Map.empty))
  * 
  * // Instead of: FOLSemantics.holds(formula, model, valuation)
  * val result = ctx.holds(formula)
  * 
  * // Easily extend valuation for quantifiers:
  * val extendedCtx = ctx.withBinding("x", value)
  * extendedCtx.holds(body)
  * }}}
  * 
  * @param model FOL model (domain + interpretation)
  * @param valuation Variable assignments
  * @tparam D Domain type
  */
case class EvaluationContext[D](
  model: Model[D],
  valuation: Valuation[D]
):
  
  /** Evaluate formula in this context.
    * 
    * Delegates to FOLSemantics.holds with the encapsulated model and valuation.
    * 
    * @param formula Formula to evaluate
    * @return true if formula holds in this context
    */
  def holds(formula: Formula[FOL]): Boolean =
    FOLSemantics.holds(formula, model, valuation)
  
  /** Create new context with additional variable binding.
    * 
    * Used for quantifiers: extend valuation with x ↦ value, then evaluate body.
    * 
    * Example:
    * {{{
    * // Evaluate ∀x. φ(x):
    * domain.elements.forall { d =>
    *   ctx.withBinding("x", d).holds(phi)
    * }
    * }}}
    * 
    * @param variable Variable name to bind
    * @param value Domain value to assign
    * @return New context with extended valuation
    */
  def withBinding(variable: String, value: D): EvaluationContext[D] =
    EvaluationContext(model, valuation.updated(variable, value))
  
  /** Evaluate term in this context.
    * 
    * Computes the domain value of a term under the current valuation.
    * 
    * @param term Term to evaluate
    * @return Domain value
    */
  def evalTerm(term: Term): D =
    FOLSemantics.evalTerm(term, model.interpretation, valuation)
  
  /** Access the underlying domain. */
  def domain: Domain[D] = model.domain
  
  /** Check if variable is bound in valuation. */
  def isBound(variable: String): Boolean =
    valuation.contains(variable)

/** Generic extension methods for any EvaluationContext[D]. */
extension [D](ctx: EvaluationContext[D])

  /** Evaluate formula with a single domain-element binding.
    *
    * Convenience method that combines `withBinding` and `holds` in one call.
    * Used by FOLBridge and ScopeEvaluator to bind the quantified variable
    * for each element.
    *
    * @param formula  Formula to evaluate
    * @param variable Variable to bind
    * @param value    Domain value to bind to variable
    * @return true if formula holds with this binding
    */
  def holdsWithBinding(
    formula: Formula[FOL],
    variable: String,
    value: D
  ): Boolean =
    ctx.withBinding(variable, value).holds(formula)

/** Companion object with factory methods. */
object EvaluationContext:
  
  /** Create evaluation context with empty valuation.
    * 
    * Useful for closed formulas (no free variables).
    * 
    * @param model FOL model
    * @return Context with empty valuation
    */
  def empty[D](model: Model[D]): EvaluationContext[D] =
    EvaluationContext(model, Valuation(Map.empty))
  
  /** Create evaluation context from model and variable bindings.
    * 
    * @param model FOL model
    * @param bindings Initial variable assignments
    * @return Context with given bindings
    */
  def apply[D](model: Model[D], bindings: Map[String, D]): EvaluationContext[D] =
    EvaluationContext(model, Valuation(bindings))
