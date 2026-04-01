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
  /** The quantified variable resolves to a type that is not in
    * [[TypeCatalog.domainTypes]]. The type is structurally valid; it is
    * a value type (scalar attribute) that cannot be iterated over.
    * See ADR-014 §2.
    */
  case TypeNotQuantifiable(name: String)
