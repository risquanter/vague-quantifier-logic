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
  * - [[DomainType]] — first-class entities that can be quantified over; require
  *   a registered domain in [[RuntimeModel]].
  * - [[ValueType]] — scalar / computed values; cannot be quantified over.
  *
  * Both sub-types carry their [[TypeId]] so the declaration is self-contained:
  * there is no need to list a type separately in a `types` set and then again
  * in a `domainTypes` set. See ADR-014.
  */
sealed trait TypeDecl:
  def typeId: TypeId

/** A first-class entity type that can be quantified over. */
case class DomainType(typeId: TypeId) extends TypeDecl

/** A scalar value type that cannot be quantified over. */
case class ValueType(typeId: TypeId) extends TypeDecl

/** Declares that consumer type `A` is the JVM carrier for a specific `TypeId`.
  *
  * Provided by the consumer (e.g. register), not the library. One `given`
  * instance per sort→type mapping. Multiple sorts may share the same JVM carrier
  * (e.g. both `LeafId` and `RiskId` backed by `String`) — each gets its own
  * `TypeRepr` instance with a distinct `typeId`.
  *
  * Used with `Value.as[A]` to project `EvaluationOutput[Value]` results back into
  * consumer domain types. See ADR-013.
  */
trait TypeRepr[A]:
  def typeId: TypeId
