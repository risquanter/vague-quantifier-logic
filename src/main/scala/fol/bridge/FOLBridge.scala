package fol.bridge

import logic.{FOL, Formula}
import semantics.EvaluationContext
import semantics.holdsWithBinding
import fol.datastore.{KnowledgeSource, RelationValue}
import semantics.ModelAugmenter

/** FOL → typed predicate bridge.
  * 
  * Converts string-parsed FOL scope formulas into `RelationValue => Boolean`
  * predicates suitable for the typed-DSL evaluation pipeline.
  * 
  * This is the key integration point that enables the unified evaluation
  * architecture described in ADR-001.md §Decision:
  * 
  *   String query → Parser → ParsedQuery(FOL) → FOLBridge.scopeToPredicate → ResolvedQuery
  * 
  * The bridge captures the `KnowledgeSource` and answer substitution in a
  * closure, constructing the FOL Model once and reusing it for all elements.
  * 
  * Paper reference: Definition 2 (Section 5.2)
  * "For each element x ∈ S, check D ⊨_σ φ where σ maps x ↦ element"
  */
object FOLBridge:
  
  /** Convert a FOL scope formula into a typed predicate.
    * 
    * The returned predicate evaluates `formula` for each element by:
    * 1. Binding the quantified variable to the element value
    * 2. Applying answer substitutions for free variables
    * 3. Evaluating via FOLSemantics.holds against the model
    * 
    * The Model is constructed once from the KnowledgeSource and
    * reused across all predicate invocations (amortized cost).
    * 
    * @param formula     Scope formula φ(x, y) where x is quantified
    * @param variable    Name of the quantified variable (bound per-element)
    * @param source      Knowledge source providing data and model
    * @param answerTuple Substitution for answer variables y (e.g., y ↦ "R1")
    * @return Predicate that checks D ⊨_σ{x↦element} φ
    */
  def scopeToPredicate(
    formula: Formula[FOL],
    variable: String,
    source: KnowledgeSource[RelationValue],
    answerTuple: Map[String, RelationValue] = Map.empty,
    modelAugmenter: ModelAugmenter[RelationValue] = ModelAugmenter.identity
  ): RelationValue => Boolean =
    // Construct model once — this is the expensive part
    val model = modelAugmenter(KnowledgeSourceModel.toModel(source))
    
    // Return closure that evaluates formula for each element
    (element: RelationValue) =>
      val ctx = EvaluationContext(model, answerTuple)
      ctx.holdsWithBinding(formula, variable, element)
  
  /** Convert a FOL scope formula into a typed predicate over Strings.
    * 
    * Convenience overload for the common case where domain elements
    * are string constants. Wraps each String in RelationValue.Const.
    * 
    * @param formula     Scope formula φ(x, y)
    * @param variable    Name of the quantified variable
    * @param source      Knowledge source
    * @param answerTuple Answer variable substitutions
    * @return Predicate over String domain elements
    */
  def scopeToStringPredicate(
    formula: Formula[FOL],
    variable: String,
    source: KnowledgeSource[RelationValue],
    answerTuple: Map[String, RelationValue] = Map.empty,
    modelAugmenter: ModelAugmenter[RelationValue] = ModelAugmenter.identity
  ): String => Boolean =
    val rvPredicate = scopeToPredicate(formula, variable, source, answerTuple, modelAugmenter)
    (s: String) => rvPredicate(RelationValue.Const(s))
