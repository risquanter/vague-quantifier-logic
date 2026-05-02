package fol.typed

opaque type TypeId = String

object TypeId:
  def apply(value: String): TypeId =
    require(value.nonEmpty, "TypeId must be non-empty")
    value

  extension (s: TypeId)
    def value: String = s

opaque type SymbolName = String

object SymbolName:
  def apply(value: String): SymbolName =
    require(value.nonEmpty, "SymbolName must be non-empty")
    value

  extension (s: SymbolName)
    def value: String = s

/** Declares a type's role in the [[TypeCatalog]].
  *
  * - [[TypeDecl.DomainType]] — first-class entities that can be quantified over;
  *   require a registered domain in [[RuntimeModel]].
  * - [[TypeDecl.ValueType]] — scalar / computed values; cannot be quantified over.
  *
  * Both variants carry their [[TypeId]] so the declaration is self-contained.
  * See ADR-006 (enum encoding) and ADR-014 (quantifiability semantics).
  */
enum TypeDecl:
  case DomainType(id: TypeId)
  case ValueType(id: TypeId)

  /** The type identifier carried by this declaration. */
  def typeId: TypeId = this match
    case DomainType(id) => id
    case ValueType(id)  => id


