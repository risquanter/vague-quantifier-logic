package fol.typed

/** A sort-tagged runtime value produced by `TypedSemantics.evaluate`.
  *
  * `raw` holds the actual JVM value. Its type is erased to `Any` because the
  * library evaluates over heterogeneous sorts without knowledge of consumer JVM
  * types. The sort correctness guarantee from the bind phase (ADR-013) ensures
  * that `raw` is always the JVM type the consumer associated with `sort` at
  * model construction time. Recover the typed view via `value.extract[A]`
  * (ADR-015 §2, `Extract` typeclass).
  */
case class Value(sort: TypeId, raw: Any)

trait RuntimeDispatcher:
  def evalFunction(name: SymbolName, args: List[Value]): Either[String, Any]
  def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean]
  def functionSymbols: Set[SymbolName]
  def predicateSymbols: Set[SymbolName]

case class RuntimeModel(
  domains: Map[TypeId, Set[Value]],
  dispatcher: RuntimeDispatcher
):
  def validateAgainst(catalog: TypeCatalog): Either[List[RuntimeModelError], RuntimeModel] =
    val missingFns = catalog.functions.keySet.diff(dispatcher.functionSymbols).toList
      .map(RuntimeModelError.MissingFunctionImplementation(_))
    val missingPreds = catalog.predicates.keySet.diff(dispatcher.predicateSymbols).toList
      .map(RuntimeModelError.MissingPredicateImplementation(_))
    val missingDomains = catalog.domainTypes.diff(domains.keySet).toList
      .map(RuntimeModelError.MissingDomainForType(_))
    val errors = missingFns ++ missingPreds ++ missingDomains
    if errors.isEmpty then Right(this) else Left(errors)
