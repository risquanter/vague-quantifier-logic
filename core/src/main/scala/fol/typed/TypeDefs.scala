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

/** A parsed inline literal value produced by a `literalValidators` entry in
  * [[TypeCatalog]].
  *
  * Enum so the library controls the finite set of possible raw forms.
  * Dispatcher lambdas receive `LiteralValue` inside `Value.raw` for any
  * argument that came from an inline query literal (as opposed to a domain
  * element or a function return value). Pattern-match exhaustively — no
  * `asInstanceOf` required. See ADR-006 (enum encoding) and ADR-015 (injection boundary).
  */
enum LiteralValue:
  /** An integer literal (source text was a decimal integer, parsed as `Long`). */
  case IntLiteral(value: Long)

  /** A floating-point literal (source text was a decimal fraction, parsed as `Double`). */
  case FloatLiteral(value: Double)

  /** A text/opaque literal. Currently used as a stopgap for named constants
    * (see TODOS.md T-002) — the `value` is the raw source token, not a
    * consumer-typed value. Do not use for new literal types; prefer `IntLiteral`
    * or `FloatLiteral` where the type is known.
    */
  case TextLiteral(value: String)
