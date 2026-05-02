package fol.typed

/** A [[RuntimeDispatcher]] built from plain per-symbol function maps.
  *
  * == Motivation ==
  *
  * The base [[RuntimeDispatcher]] trait requires the implementor to declare
  * `functionSymbols` and `predicateSymbols` as separate `Set[SymbolName]`
  * values alongside the `eval*` match arms.  These two artefacts describe the
  * same information from different angles and must be kept in sync by
  * convention; `validateAgainst` exists in part to catch divergences.
  *
  * `MapDispatcher` eliminates the intra-dispatcher gap: the symbol sets are
  * derived automatically from the Map keys, so adding or removing a symbol
  * always modifies exactly one place.
  *
  * `validateAgainst` retains full value: it still enforces the
  * catalog-vs-dispatcher cross-boundary agreement (every symbol declared in
  * the [[TypeCatalog]] has an implementation here).  What changes is that
  * `validateAgainst` can no longer catch a mismatch *between* a dispatcher's
  * own symbol-set declaration and its own match arms — because that mismatch
  * can no longer exist.
  *
  * == When to use ==
  *
  * Use `MapDispatcher` when all dispatcher logic can be expressed as
  * independent per-symbol lambdas.  For dispatchers that require shared
  * mutable state or complex cross-symbol coordination, implement
  * [[RuntimeDispatcher]] directly.
  *
  * == Usage ==
  *
  * {{{
  * // [ILLUSTRATIVE — implementation bodies are stubs marked ???]
  * // Symbol names, sorts, and computation are determined by the consumer.
  *
  * val dispatcher = MapDispatcher(
  *   functions = Map(
  *     SymbolName("lec") -> { args =>
  *       // args(0) :: Asset, args(1) :: Loss (guaranteed by bind phase)
  *       // [CONSUMER IMPLEMENTS]: call domain computation, return Probability value
  *       ???
  *     },
  *     SymbolName("p95") -> { args =>
  *       // args(0) :: Asset
  *       // [CONSUMER IMPLEMENTS]: call domain computation, return Loss value
  *       ???
  *     }
  *   ),
  *   predicates = Map(
  *     SymbolName("leaf")    -> { args => /* [CONSUMER IMPLEMENTS] */ ??? },
  *     SymbolName("gt_prob") -> { args => /* [CONSUMER IMPLEMENTS] */ ??? },
  *     SymbolName("gt_loss") -> { args => /* [CONSUMER IMPLEMENTS] */ ??? }
  *   )
  * )
  * // dispatcher.functionSymbols  == Set(SymbolName("lec"), SymbolName("p95"))
  * // dispatcher.predicateSymbols == Set(SymbolName("leaf"), SymbolName("gt_prob"), SymbolName("gt_loss"))
  * // — derived from map keys, no separate declaration needed
  * }}}
  *
  * See also: [[RuntimeDispatcher]], [[RuntimeModel]], [[fol.typed.FolModel]] (planned)
  */
case class MapDispatcher(
  predicates: Map[SymbolName, List[Value] => Either[String, Boolean]],
  functions:  Map[SymbolName, List[Value] => Either[String, Any]] = Map.empty
) extends RuntimeDispatcher:

  /** Derived from `functions.keySet` — cannot diverge from the implementation map. */
  override val functionSymbols: Set[SymbolName] = functions.keySet

  /** Derived from `predicates.keySet` — cannot diverge from the implementation map. */
  override val predicateSymbols: Set[SymbolName] = predicates.keySet

  override def evalFunction(name: SymbolName, args: List[Value]): Either[String, Any] =
    functions.get(name)
      .map(_(args))
      .getOrElse(Left(s"MapDispatcher: no function implementation for '${name.value}'"))

  override def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean] =
    predicates.get(name)
      .map(_(args))
      .getOrElse(Left(s"MapDispatcher: no predicate implementation for '${name.value}'"))
