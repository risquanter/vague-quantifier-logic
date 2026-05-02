package fol.typed

/** Helpers for constructing type-safe [[RuntimeDispatcher]] function lambdas.
  *
  * == Motivation ==
  *
  * `RuntimeDispatcher.evalFunction` returns `Either[String, Any]` after
  * ADR-015 §1 / PLAN-symmetric-value-boundaries Phase 4. The consumer's
  * native return type `A` is widened to `Any` at the dispatcher boundary;
  * downstream lambdas recover a typed view via `value.extract[A]`
  * (`Extract[A]` instance, ADR-015 §2).
  *
  * `TypedFunctionImpl.of[A]` documents the consumer's native return type at
  * the registration site without forcing the consumer to write the `: Any`
  * widening manually. The combinator is currently identity-shaped; a
  * follow-up plan (T-003) may introduce a normalizer to give inline literals
  * and function returns the same shape.
  *
  * == Usage ==
  *
  * {{{
  * // [ILLUSTRATIVE]
  * import TypedFunctionImpl.of
  *
  * val dispatcher = MapDispatcher(
  *   functions = Map(
  *     SymbolName("lec") -> of[Double](
  *       impl = args =>
  *         for
  *           assetId <- args(0).extract[String]   // domain element carrier
  *           loss    <- args(1).extract[Long]     // inline literal carrier
  *         yield computeLec(assetId, loss)
  *     ),
  *     SymbolName("p95") -> of[Long](
  *       impl = args => args(0).extract[String].map(computeP95)
  *     )
  *   ),
  *   predicates = Map(...)
  * )
  * }}}
  *
  * See ADR-015 §1–§2 and PLAN-symmetric-value-boundaries.md for design rationale.
  */
object TypedFunctionImpl:

  /** Build a function lambda whose native return type is `A`. The result is
    * widened to `Any` at the dispatcher boundary; the consumer's native
    * type is documented at the registration site by the `[A]` parameter.
    *
    * @param impl  The computation: receives dispatcher `args`, returns either
    *              an error message or the native result of type `A`.
    * @tparam A    The native JVM type of the function's return value, e.g.
    *              `Double` for probability, `Long` for integer loss values.
    */
  def of[A](
    impl: List[Value] => Either[String, A]
  ): List[Value] => Either[String, Any] =
    args => impl(args)
