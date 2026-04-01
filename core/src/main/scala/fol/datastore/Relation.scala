package fol.datastore

/** Relation schema definition
  *
  * Defines the schema for relations in a knowledge base.
  * Each relation has a name and arity.  Per-position type constraints
  * (PositionType) are handled separately in `RelationValueValidation`
  * and are specific to `RelationValue`-typed knowledge bases.
  *
  * This is inspired by the relational schema definition from
  * Section 5.1 of the paper (Fermüller et al.).
  */

/** Value that can appear in a relation */
enum RelationValue:
  case Const(name: String)
  case Num(value: Int)

  override def toString: String = this match
    case Const(name)  => name
    case Num(value)   => value.toString

object RelationValue:

  /** Compare two RelationValues.
    *
    * - `Num` values compared by integer value
    * - `Const` values compared lexicographically
    * - Mixed `Num`/`Const` throws (matching the existing `toDouble` behaviour)
    */
  private def compareValues(x: RelationValue, y: RelationValue): Int = (x, y) match
    case (RelationValue.Num(a), RelationValue.Num(b))     => a compare b
    case (RelationValue.Const(a), RelationValue.Const(b)) => a compare b
    case _ =>
      throw IllegalArgumentException(
        s"Cannot compare different RelationValue variants: $x, $y"
      )

  /** Ordering instance for RelationValue.
    *
    * Total on all variants:
    *   - `Num` values compared by integer value
    *   - `Const` values compared lexicographically
    *   - Mixed `Num`/`Const` throws (heterogeneous comparison is undefined)
    */
  given Ordering[RelationValue] with
    def compare(x: RelationValue, y: RelationValue): Int = compareValues(x, y)

/** A tuple of values forming a fact.
  *
  * @tparam D the domain element type
  */
case class RelationTuple[D](values: List[D]):
  require(values.nonEmpty, "Relation tuple must have at least one value")

  /** Get value at position (0-indexed) */
  def apply(pos: Int): D = values(pos)

  /** Arity of this tuple */
  def arity: Int = values.length

  /** Check if this tuple matches a pattern (Some = specific value, None = wildcard) */
  def matches(pattern: List[Option[D]]): Boolean =
    require(pattern.length == values.length, "Pattern length must match tuple arity")
    values.zip(pattern).forall {
      case (v, Some(p)) => v == p
      case (_, None)    => true
    }

  override def toString: String = s"(${values.mkString(", ")})"

object RelationTuple:
  /** Create tuple from constant names only (RelationValue convenience) */
  def fromConstants(names: String*): RelationTuple[RelationValue] =
    RelationTuple(names.map(RelationValue.Const.apply).toList)

  /** Create tuple from numeric values only (RelationValue convenience) */
  def fromNums(values: Int*): RelationTuple[RelationValue] =
    RelationTuple(values.map(RelationValue.Num.apply).toList)

  /** Create tuple with mixed RelationValues */
  def of(values: RelationValue*): RelationTuple[RelationValue] =
    RelationTuple(values.toList)

/** Metadata properties for a relation.
  *
  * Properties express schema-level constraints that the datastore
  * enforces automatically.  Currently only `Symmetric` is supported;
  * see ADR-009 for the decision record and future exploration notes
  * (inverse relations, predicate combinators).
  *
  * @see [[Relation]]
  * @see docs/ADR-009.md
  */
enum RelationProperty:
  /** Symmetric relation: `R(a, b) ↔ R(b, a)`.
    *
    * Only meaningful for binary relations.  When set,
    * [[KnowledgeBase.addFact]] automatically materialises the
    * reverse tuple at insert time so that `contains`, `query`,
    * and all downstream layers see both directions.
    *
    * Mirrors `owl:SymmetricProperty` (OWL) and the Datalog rule
    * `R(Y,X) :- R(X,Y)` — eagerly evaluated.
    */
  case Symmetric

/** Relation schema (arity + properties).
  *
  * @param name       Relation name (typed via `RelationName` — see ADR-010)
  * @param arity      Number of arguments
  * @param properties Schema-level properties (e.g. `Symmetric`)
  *
  * @see [[RelationProperty]]
  * @see docs/ADR-009.md
  * @see docs/ADR-010.md
  */
case class Relation(
  name: RelationName,
  arity: Int,
  properties: Set[RelationProperty] = Set.empty
):
  require(arity >= 1, "Relation must have at least arity 1")
  require(
    !(properties.contains(RelationProperty.Symmetric) && !isBinary),
    s"Symmetric property requires binary relation, but $name has arity $arity"
  )

  /** Check if this is a unary relation */
  def isUnary: Boolean = arity == 1

  /** Check if this is a binary relation */
  def isBinary: Boolean = arity == 2

  /** Check if this relation has the Symmetric property. */
  def isSymmetric: Boolean = properties.contains(RelationProperty.Symmetric)

  override def toString: String =
    val props = if properties.isEmpty then "" else s" [${properties.mkString(", ")}]"
    s"${name.value}/$arity$props"

object Relation:
  /** Create a unary relation.
    *
    * Accepts raw `String` — wraps to `RelationName` internally (ADR-010 §3).
    */
  def unary(name: String): Relation =
    Relation(RelationName(name), 1)

  /** Create a binary relation.
    *
    * Accepts raw `String` — wraps to `RelationName` internally (ADR-010 §3).
    */
  def binary(name: String): Relation =
    Relation(RelationName(name), 2)

  /** Create a symmetric binary relation.
    *
    * Inserting `(A, B)` automatically materialises `(B, A)`.
    * Accepts raw `String` — wraps to `RelationName` internally (ADR-010 §3).
    *
    * @see [[RelationProperty.Symmetric]]
    * @see docs/ADR-009.md
    */
  def symmetricBinary(name: String): Relation =
    Relation(RelationName(name), 2, Set(RelationProperty.Symmetric))
