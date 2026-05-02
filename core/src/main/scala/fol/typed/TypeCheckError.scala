package fol.typed

enum TypeCheckError:
  case UnknownPredicate(name: String)
  case UnknownFunction(name: String)
  case ArityMismatch(symbol: String, expected: Int, actual: Int)
  case UnknownConstantOrLiteral(name: String)
  case TypeMismatch(expected: TypeId, actual: TypeId, context: String)
  case UnboundAnswerVar(name: String)
  case UnconstrainedVar(name: String)
  case ConflictingTypes(name: String, left: TypeId, right: TypeId)
  /** A named constant token had a registered literal validator for its
    * expected sort, but the validator returned `None` for the source
    * text. Distinct from [[UnknownConstantOrLiteral]], which signals
    * the absence of *any* mapping (no constant, no validator).
    *
    * Introduced by ADR-015 §4 / PLAN-symmetric-value-boundaries Phase 3
    * (closes T-002).
    */
  case UnparseableConstant(name: String, sort: TypeId, sourceText: String)
  /** The quantified variable resolves to a type that is not in
    * [[TypeCatalog.domainTypes]]. The type is structurally valid; it is
    * a value type (scalar attribute) that cannot be iterated over.
    * See ADR-014 §2.
    */
  case TypeNotQuantifiable(name: String)
