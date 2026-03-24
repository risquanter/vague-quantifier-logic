package fol.datastore

/** RelationValue-specific validation for relation schemas.
  *
  * Contains `PositionType` and the `validates` check that was
  * originally on `Relation`.  These are specific to
  * `RelationValue` (Const/Num) and do not apply to generic
  * `KnowledgeBase[D]` schemas, which use arity-only validation.
  *
  * Extracted in Phase 2 of the domain-type-safety plan (ADR-006).
  */

/** Type of a relation position (RelationValue-specific) */
enum PositionType:
  case Constant  // String constants (e.g., component IDs, risk IDs)
  case Numeric   // Integer values (e.g., severity scores, probabilities)

object PositionType:
  /** All positions are constants (most common case) */
  def allConstants(arity: Int): List[PositionType] =
    List.fill(arity)(PositionType.Constant)

  /** All positions are numeric */
  def allNumeric(arity: Int): List[PositionType] =
    List.fill(arity)(PositionType.Numeric)

/** Validation extensions for `Relation` when working with `RelationValue` tuples. */
object RelationValueValidation:

  /** Check if a tuple conforms to a relation's expected position types.
    *
    * @param relation  the relation schema (only `arity` is used for the check)
    * @param tuple     the tuple to validate
    * @param positionTypes per-position type constraints
    * @return true if each position's value matches its expected type
    */
  def validates(
    relation: Relation,
    tuple: RelationTuple[RelationValue],
    positionTypes: List[PositionType]
  ): Boolean =
    require(
      positionTypes.length == relation.arity,
      "Position types must match relation arity"
    )
    tuple.arity == relation.arity && tuple.values.zip(positionTypes).forall {
      case (RelationValue.Const(_), PositionType.Constant) => true
      case (RelationValue.Num(_), PositionType.Numeric)    => true
      case _                                                => false
    }

  /** Convert a string to a RelationValue based on the expected position type. */
  def fromString(s: String, expected: PositionType): RelationValue =
    expected match
      case PositionType.Constant => RelationValue.Const(s)
      case PositionType.Numeric  => RelationValue.Num(s.toInt)

  /** Create a binary mixed relation (first constant, second numeric).
    *
    * Convenience kept for backward compatibility; not needed on the
    * generic `Relation` path since `Relation` is now arity-only.
    */
  def binaryMixed(name: String): (Relation, List[PositionType]) =
    (Relation(name, 2), List(PositionType.Constant, PositionType.Numeric))
