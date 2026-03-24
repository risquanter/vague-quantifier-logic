package fol.bridge

import semantics.{Model, ModelAugmenter}

/** Built-in augmenter providing comparison predicates, arithmetic
  * functions, and numeric literal resolution for `Model[Any]`.
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
  * @see [[docs/ADR-005.md]] §Decision 4
  */
object NumericAugmenter:

  /** ModelAugmenter that adds:
    *   - Comparison predicates: `>`, `<`, `>=`, `<=`, `=`
    *   - Arithmetic functions: `+`, `-`, `*`, `/`
    *   - Numeric literal resolution via fallback (any digit string → Double)
    */
  val augmenter: ModelAugmenter[Any] = ModelAugmenter { model =>
    val preds = Map[String, List[Any] => Boolean](
      ">"  -> { case List(a, b) => toDouble(a) > toDouble(b)
               case args => throw new Exception(s"> expects 2 arguments, got ${args.length}") },
      "<"  -> { case List(a, b) => toDouble(a) < toDouble(b)
               case args => throw new Exception(s"< expects 2 arguments, got ${args.length}") },
      ">=" -> { case List(a, b) => toDouble(a) >= toDouble(b)
               case args => throw new Exception(s">= expects 2 arguments, got ${args.length}") },
      "<=" -> { case List(a, b) => toDouble(a) <= toDouble(b)
               case args => throw new Exception(s"<= expects 2 arguments, got ${args.length}") },
      "="  -> { case List(a, b) => toDouble(a) == toDouble(b)
               case args => throw new Exception(s"= expects 2 arguments, got ${args.length}") },
    )

    val fns = Map[String, List[Any] => Any](
      "+" -> { case List(a, b) => toDouble(a) + toDouble(b)
               case args => throw new Exception(s"+ expects 2 arguments, got ${args.length}") },
      "-" -> {
        case List(a)    => -toDouble(a)
        case List(a, b) => toDouble(a) - toDouble(b)
        case args => throw new Exception(s"- expects 1 or 2 arguments, got ${args.length}")
      },
      "*" -> { case List(a, b) => toDouble(a) * toDouble(b)
               case args => throw new Exception(s"* expects 2 arguments, got ${args.length}") },
      "/" -> { case List(a, b) =>
        val denom = toDouble(b)
        if denom == 0.0 then throw new Exception("Division by zero")
        else toDouble(a) / denom
               case args => throw new Exception(s"/ expects 2 arguments, got ${args.length}") },
    )

    val withPreds = model.interpretation.withPredicates(preds)
    val withFns   = withPreds.withFunctions(fns)
    Model(withFns.withFunctionFallback(numericLiteral))
  }

  /** Try to parse a symbol name as a numeric literal.
    *
    * Returns `Some` for any string parseable as `Double`
    * (integers, decimals, negatives).  Returns `None` for
    * non-numeric strings — the fallback chain continues.
    */
  private[bridge] def numericLiteral(name: String): Option[List[Any] => Any] =
    name.toDoubleOption.map(d => (_: List[Any]) => d)

  /** Convert an `Any` value to `Double` for numeric operations.
    *
    * Handles the types that appear in KB-backed models and
    * numeric augmenter outputs: `Double`, `Int`, `Long`,
    * `BigDecimal`, and `String` (numeric).
    */
  private[bridge] def toDouble(v: Any): Double = v match
    case d: Double      => d
    case i: Int         => i.toDouble
    case l: Long        => l.toDouble
    case bd: BigDecimal => bd.toDouble
    case s: String      =>
      s.toDoubleOption.getOrElse(
        throw new Exception(s"Cannot convert to Double: '$s'")
      )
    case other =>
      throw new Exception(
        s"Cannot convert to Double: $other (${other.getClass.getName})"
      )
