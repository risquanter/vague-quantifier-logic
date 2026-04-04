package fol.typed

/** Helpers for constructing type-safe [[RuntimeDispatcher]] function lambdas.
  *
  * == Motivation ==
  *
  * After [[RuntimeDispatcher.evalFunction]] was changed to return
  * `Either[String, LiteralValue]`, every function lambda must produce a
  * `LiteralValue` rather than a raw `Value`.  Without a helper the consumer
  * writes the wrap call explicitly at every lambda site, which is both verbose
  * and easy to get wrong.
  *
  * `TypedFunctionImpl.of[A]` separates the concern:
  *
  *  - `impl` — the consumer's native computation, returning `A`
  *  - `wrap` — the single declaration of how `A` maps to a `LiteralValue`
  *
  * The framework calls `impl`, then wraps the result with `wrap`.  The
  * consumer never constructs `LiteralValue` inline at every use site.
  *
  * == Usage ==
  *
  * {{{
  * // [ILLUSTRATIVE — sorts, symbol names, and types are consumer-chosen]
  * import TypedFunctionImpl.of
  *
  * val dispatcher = MapDispatcher(
  *   functions = Map(
  *     SymbolName("lec") -> of[Double](
  *       impl = args => Right(computeLec(args(0).raw.asInstanceOf[AssetId],
  *                                       args(1).raw match { case IntLiteral(n) => n })),
  *       wrap = FloatLiteral(_)
  *     ),
  *     SymbolName("p95") -> of[Long](
  *       impl = args => Right(computeP95(args(0).raw.asInstanceOf[AssetId])),
  *       wrap = IntLiteral(_)
  *     )
  *   ),
  *   predicates = Map(...)
  * )
  * }}}
  *
  * See ADR-015 §1 and the function return normalisation plan for design rationale.
  */
object TypedFunctionImpl:

  /** Build a `MapDispatcher` function lambda from a native-typed computation.
    *
    * @param impl  The computation: receives dispatcher `args`, returns either
    *              an error message or the native result of type `A`.
    * @param wrap  Converts the native result to a [[LiteralValue]].
    *              Declared once per function — not at every call site.
    * @tparam A    The native JVM type of the function's return value, e.g.
    *              `Double` for probability, `Long` for integer loss values.
    */
  def of[A](
    impl: List[Value] => Either[String, A],
    wrap: A => LiteralValue
  ): List[Value] => Either[String, LiteralValue] =
    args => impl(args).map(wrap)
