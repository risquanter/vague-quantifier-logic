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

  /** A domain type declared in [[TypeCatalog.domainTypes]] has no
    * registered element set in the `RuntimeModel`.
    * See ADR-001 §4 — startup coverage validation covers both dispatcher symbols
    * and domain-type registration.
    */
  case MissingDomainForType(typeName: TypeId)

  /** Human-readable summary of this error, used in [[fol.error.QueryError.ModelValidationError]]. */
  def message: String = this match
    case MissingFunctionImplementation(n) => s"missing function: ${n.value}"
    case MissingPredicateImplementation(n) => s"missing predicate: ${n.value}"
    case MissingDomainForType(t)           => s"missing domain for type: ${t.value}"
