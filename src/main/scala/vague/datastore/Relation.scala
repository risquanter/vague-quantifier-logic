package vague.datastore

/** Relation schema definition
  * 
  * Defines the schema for relations in a knowledge base.
  * Each relation has a name, arity, and position types.
  * 
  * This is inspired by the relational schema definition from
  * Section 5.1 of the paper (Fermüller et al.).
  */

/** Type of a relation position */
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

/** Value that can appear in a relation */
enum RelationValue:
  case Const(name: String)
  case Num(value: Int)
  
  override def toString: String = this match
    case Const(name) => name
    case Num(value) => value.toString

object RelationValue:
  /** Convert from string (used for parsing/input) */
  def fromString(s: String, expected: PositionType): RelationValue =
    expected match
      case PositionType.Constant => Const(s)
      case PositionType.Numeric => Num(s.toInt)

/** A tuple of values forming a fact */
case class RelationTuple(values: List[RelationValue]):
  require(values.nonEmpty, "Relation tuple must have at least one value")
  
  /** Get value at position (0-indexed) */
  def apply(pos: Int): RelationValue = values(pos)
  
  /** Arity of this tuple */
  def arity: Int = values.length
  
  /** Check if this tuple matches a pattern (Some = specific value, None = wildcard) */
  def matches(pattern: List[Option[RelationValue]]): Boolean =
    require(pattern.length == values.length, "Pattern length must match tuple arity")
    values.zip(pattern).forall {
      case (v, Some(p)) => v == p
      case (_, None) => true
    }
  
  override def toString: String = s"(${values.mkString(", ")})"

object RelationTuple:
  /** Create tuple from constant names only */
  def fromConstants(names: String*): RelationTuple =
    RelationTuple(names.map(RelationValue.Const.apply).toList)
  
  /** Create tuple from numeric values only */
  def fromNums(values: Int*): RelationTuple =
    RelationTuple(values.map(RelationValue.Num.apply).toList)
  
  /** Create tuple with mixed values */
  def of(values: RelationValue*): RelationTuple =
    RelationTuple(values.toList)

/** Relation schema
  * 
  * @param name Relation name (e.g., "component", "has_risk")
  * @param arity Number of arguments
  * @param positionTypes Type of each position (Constant or Numeric)
  */
case class Relation(
  name: String,
  arity: Int,
  positionTypes: List[PositionType]
):
  require(arity >= 1, "Relation must have at least arity 1")
  require(positionTypes.length == arity, "Position types must match arity")
  
  /** Check if this is a unary relation */
  def isUnary: Boolean = arity == 1
  
  /** Check if this is a binary relation */
  def isBinary: Boolean = arity == 2
  
  /** Check if a tuple conforms to this relation's schema */
  def validates(tuple: RelationTuple): Boolean =
    tuple.arity == arity && tuple.values.zip(positionTypes).forall {
      case (RelationValue.Const(_), PositionType.Constant) => true
      case (RelationValue.Num(_), PositionType.Numeric) => true
      case _ => false
    }
  
  override def toString: String = s"$name/${arity}"

object Relation:
  /** Create a unary relation over constants */
  def unary(name: String): Relation =
    Relation(name, 1, List(PositionType.Constant))
  
  /** Create a binary relation over constants */
  def binary(name: String): Relation =
    Relation(name, 2, PositionType.allConstants(2))
  
  /** Create a binary relation with first position constant, second numeric */
  def binaryMixed(name: String): Relation =
    Relation(name, 2, List(PositionType.Constant, PositionType.Numeric))
