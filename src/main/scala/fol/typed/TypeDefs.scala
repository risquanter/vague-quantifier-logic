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
