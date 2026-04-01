package fol.bridge

import semantics.{Model, ModelAugmenter}

/** Generic comparison-predicate augmenter.
  *
  * Adds the binary comparison predicates `>`, `<`, `>=`, `<=`, `=`
  * to any model whose domain type has an `Ordering` instance.
  *
  * These predicates are required for scope formulas that contain
  * numeric comparisons, e.g. `>(score(x), 50)`.
  *
  * @see [[ArithmeticAugmenter]] for arithmetic functions (+, -, *, /)
  * @see [[LiteralResolver]]     for numeric literal fallback
  * @see [[NumericAugmenter]]    for backward-compatible composition
  */
object ComparisonAugmenter:

  /** Create a ModelAugmenter that adds comparison predicates.
    *
    * @tparam D Domain type with an `Ordering` instance
    * @return ModelAugmenter adding >, <, >=, <=, = predicates
    */
  def augmenter[D: Ordering]: ModelAugmenter[D] = ModelAugmenter { model =>
    val ord = summon[Ordering[D]]
    val preds = Map[String, List[D] => Boolean](
      ">"  -> { case List(a, b) => ord.gt(a, b)
                case args => throw Exception(s"> expects 2 arguments, got ${args.length}") },
      "<"  -> { case List(a, b) => ord.lt(a, b)
                case args => throw Exception(s"< expects 2 arguments, got ${args.length}") },
      ">=" -> { case List(a, b) => ord.gteq(a, b)
                case args => throw Exception(s">= expects 2 arguments, got ${args.length}") },
      "<=" -> { case List(a, b) => ord.lteq(a, b)
                case args => throw Exception(s"<= expects 2 arguments, got ${args.length}") },
      "="  -> { case List(a, b) => ord.equiv(a, b)
                case args => throw Exception(s"= expects 2 arguments, got ${args.length}") },
    )
    Model(model.interpretation.withPredicates(preds))
  }
