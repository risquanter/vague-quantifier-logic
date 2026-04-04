package fol.typed

/** A sort-tagged runtime value produced by `TypedSemantics.evaluate`.
  *
  * `raw` holds the actual JVM value. Its type is erased to `Any` because the
  * library evaluates over heterogeneous sorts without knowledge of consumer JVM
  * types. The sort correctness guarantee from the bind phase (ADR-013) ensures
  * that `raw` is always the JVM type the consumer associated with `sort` via
  * `TypeRepr[A]`.
  *
  * Do not cast `raw` directly. Use `Value.as[A]` with a `TypeRepr[A]` in scope
  * (see ADR-013) — this makes the sort→type mapping explicit and compiler-checked.
  */
case class Value(sort: TypeId, raw: Any)

object Value:
  extension (v: Value)
    /** Extract the value as consumer type `A`, returning `None` if `v.sort`
      * does not match `TypeRepr[A].typeId`.
      *
      * For a well-formed query the sort check always succeeds — `None` indicates
      * a consumer mapping error or a library bug, not a user query error.
      *
      * Requires a `TypeRepr[A]` given instance in scope, typically declared
      * in the consuming project (e.g. register). See ADR-013.
      */
    def as[A](using repr: TypeRepr[A]): Option[A] =
      if v.sort == repr.typeId
      then Some(v.raw.asInstanceOf[A])
      else None

trait RuntimeDispatcher:
  def evalFunction(name: SymbolName, args: List[Value]): Either[String, LiteralValue]
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
