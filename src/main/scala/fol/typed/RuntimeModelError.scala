package fol.typed

/** Error produced by [[RuntimeModel.validateAgainst]].
  *
  * Structured counterpart to [[TypeCatalogError]] and [[TypeCheckError]].
  * Returned as `List[RuntimeModelError]` so all coverage gaps are reported
  * together rather than failing on the first missing symbol.
  *
  * See ADR-001 §4 — dispatcher coverage is validated at startup/model
  * construction, not per-query at evaluation time.
  */
enum RuntimeModelError:
  /** A function declared in the `TypeCatalog` has no implementation in the
    * `RuntimeDispatcher`.
    */
  case MissingFunctionImplementation(name: SymbolName)

  /** A predicate declared in the `TypeCatalog` has no implementation in the
    * `RuntimeDispatcher`.
    */
  case MissingPredicateImplementation(name: SymbolName)

  /** An enumerable type declared in [[TypeCatalog.enumerableTypes]] has no
    * registered domain (element set) in the `RuntimeModel`.
    * See ADR-001 §4.
    */
  case MissingDomainForEnumerableType(typeName: TypeId)
