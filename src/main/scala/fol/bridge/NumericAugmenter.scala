package fol.bridge

import fol.datastore.RelationValue
import fol.datastore.RelationValue.{Num, Const}
import semantics.{Model, ModelAugmenter}

/** Built-in augmenter providing comparison predicates, arithmetic
  * functions, and numeric literal resolution for `Model[RelationValue]`.
  *
  * This is the backward-compatible composition of the three generic
  * augmenters:
  *   - [[ComparisonAugmenter]]  — `>`, `<`, `>=`, `<=`, `=`
  *   - [[ArithmeticAugmenter]]  — `+`, `-`, `*`, `/`
  *   - [[LiteralResolver]]      — numeric literal fallback
  *
  * KB-backed models contain only relation-membership predicates and
  * identity constants.  This augmenter fills the gap so that formulas
  * like `>(p95(x), 5000000)` can evaluate once a consumer also supplies
  * domain-specific function augmenters (e.g. `p95`).
  *
  * Composition order:
  * {{{
  *   val augmenter = NumericAugmenter.augmenter andThen domainAugmenter
  *   VagueSemantics.evaluate(query, source, modelAugmenter = augmenter)
  * }}}
  *
  * '''Note:''' Arithmetic is integer-based (`RelationValue.Num(Int)`).
  * Division truncates toward zero.  Consumers needing precise decimal
  * arithmetic should use `ArithmeticAugmenter[Double]` with a richer
  * domain type.
  *
  * @see [[docs/ADR-005.md]] §Decision 4
  */
object NumericAugmenter:

  /** Domain-specific arithmetic for `RelationValue.Num(Int)`.
    *
    * Adds `+`, `-`, `*`, `/` as model functions by direct pattern
    * matching on `Num`.  No `Fractional` type class instance is
    * created — `RelationValue` is a sum type where arithmetic is
    * only meaningful on the `Num` variant, which does not satisfy
    * the totality requirement of `Fractional`.
    *
    * Consumers with a lawful `Fractional` domain (e.g. `Double`)
    * should use [[ArithmeticAugmenter]] directly instead.
    */
  private val relValueArithmetic: ModelAugmenter[RelationValue] = ModelAugmenter { model =>
    val fns = Map[String, List[RelationValue] => RelationValue](
      "+" -> { case List(Num(a), Num(b)) => Num(a + b)
               case args => throw Exception(s"+ expects 2 Num arguments, got $args") },
      "-" -> {
        case List(Num(a))         => Num(-a)
        case List(Num(a), Num(b)) => Num(a - b)
        case args => throw Exception(s"- expects 1 or 2 Num arguments, got $args")
      },
      "*" -> { case List(Num(a), Num(b)) => Num(a * b)
               case args => throw Exception(s"* expects 2 Num arguments, got $args") },
      "/" -> { case List(Num(a), Num(b)) =>
        if b == 0 then throw ArithmeticException("Division by zero")
        else Num(a / b)
               case args => throw Exception(s"/ expects 2 Num arguments, got $args") },
    )
    Model(model.interpretation.withFunctions(fns))
  }

  /** ModelAugmenter that adds comparisons, arithmetic, and literal resolution
    * for `RelationValue`-based models.
    *
    * Composed from:
    *   - `ComparisonAugmenter` — uses the lawful global `Ordering[RelationValue]`
    *   - `relValueArithmetic`  — domain-specific `Num` pattern matching (no type class)
    *   - `LiteralResolver`     — uses the lawful `DomainCodec[RelationValue]`
    */
  val augmenter: ModelAugmenter[RelationValue] =
    ComparisonAugmenter.augmenter[RelationValue]
      .andThen(relValueArithmetic)
      .andThen(LiteralResolver.augmenter[RelationValue])
