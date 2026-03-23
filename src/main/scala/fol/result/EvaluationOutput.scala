package fol.result

import fol.datastore.RelationValue

/** Element-aware evaluation result.
  *
  * Extends [[VagueQueryResult]] (statistics only) with the concrete
  * element sets needed for tree highlighting in register.
  *
  * Concrete type — no type parameter. Elements are always `RelationValue`.
  *
  * - `rangeElements`: the full candidate pool (D_R from the paper)
  * - `satisfyingElements`: the subset that passed the scope predicate
  * - In sampled mode, `satisfyingElements` contains only elements from
  *   the sample (Decision D2B).
  *
  * See docs/EVALUATION-PATH-UNIFICATION.md §EvaluationOutput.
  *
  * @param result             Statistics: satisfied, proportion, CI, counts
  * @param rangeElements      Full candidate pool
  * @param satisfyingElements Subset that satisfied the predicate
  */
case class EvaluationOutput(
  result: VagueQueryResult,
  rangeElements: Set[RelationValue],
  satisfyingElements: Set[RelationValue]
):
  /** Convenience: whether the quantifier was satisfied. */
  def satisfied: Boolean = result.satisfied

  /** Convenience: observed or estimated proportion. */
  def proportion: Double = result.proportion
