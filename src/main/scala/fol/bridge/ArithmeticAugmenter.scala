package fol.bridge

import semantics.{Model, ModelAugmenter}

/** Generic arithmetic-function augmenter.
  *
  * Adds the arithmetic functions `+`, `-`, `*`, `/` to any model
  * whose domain type has a `Fractional` instance.
  *
  * The `-` function supports both unary negation (`-(x)`) and
  * binary subtraction (`-(x, y)`).
  *
  * Division-by-zero is detected and raises an exception.
  *
  * @see [[ComparisonAugmenter]] for comparison predicates (>, <, etc.)
  * @see [[LiteralResolver]]     for numeric literal fallback
  * @see [[NumericAugmenter]]    for backward-compatible composition
  */
object ArithmeticAugmenter:

  /** Create a ModelAugmenter that adds arithmetic functions.
    *
    * @tparam D Domain type with a `Fractional` instance
    * @return ModelAugmenter adding +, -, *, / functions
    */
  def augmenter[D: Fractional]: ModelAugmenter[D] = ModelAugmenter { model =>
    val frac = summon[Fractional[D]]
    val fns = Map[String, List[D] => D](
      "+" -> { case List(a, b) => frac.plus(a, b)
               case args => throw Exception(s"+ expects 2 arguments, got ${args.length}") },
      "-" -> {
        case List(a)    => frac.negate(a)
        case List(a, b) => frac.minus(a, b)
        case args => throw Exception(s"- expects 1 or 2 arguments, got ${args.length}")
      },
      "*" -> { case List(a, b) => frac.times(a, b)
               case args => throw Exception(s"* expects 2 arguments, got ${args.length}") },
      "/" -> { case List(a, b) =>
        if frac.equiv(b, frac.zero) then throw Exception("Division by zero")
        else frac.div(a, b)
               case args => throw Exception(s"/ expects 2 arguments, got ${args.length}") },
    )
    Model(model.interpretation.withFunctions(fns))
  }
