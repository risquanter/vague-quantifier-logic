package fol.bridge

import fol.datastore.DomainCodec
import semantics.{Model, ModelAugmenter}

/** Generic numeric-literal resolver augmenter.
  *
  * Installs a function fallback that turns digit-strings (e.g. `"5000000"`,
  * `"-42"`) into domain values via `DomainCodec[D].fromNumericLiteral`.
  *
  * This is necessary because the FOL parser represents numeric literals
  * as `Term.Const("5000000")`, which the semantics layer looks up as a
  * 0-ary function.  If the literal is not in the KB's active domain
  * (and therefore not installed as a constant function), the fallback
  * catches it and converts it.
  *
  * @see [[ComparisonAugmenter]]  for comparison predicates
  * @see [[ArithmeticAugmenter]]  for arithmetic functions
  * @see [[NumericAugmenter]]     for backward-compatible composition
  */
object LiteralResolver:

  /** Create a ModelAugmenter that resolves numeric literal strings.
    *
    * @tparam D Domain type with a `DomainCodec` instance
    * @return ModelAugmenter installing a numeric-literal function fallback
    */
  def augmenter[D: DomainCodec]: ModelAugmenter[D] = ModelAugmenter { model =>
    val codec = summon[DomainCodec[D]]
    val fallback: String => Option[List[D] => D] = name =>
      codec.fromNumericLiteral(name).map(d => (_: List[D]) => d)
    Model(model.interpretation.withFunctionFallback(fallback))
  }
