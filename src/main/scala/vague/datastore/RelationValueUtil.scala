package vague.datastore

/** Utilities for converting between RelationValue and domain values.
  * 
  * RelationValue is the KB representation (symbolic):
  * - RelationValue.Const(name) - string constants like "alice", "C1"
  * - RelationValue.Num(value) - numeric values like 42, 100
  * 
  * Domain values are used in FOL semantics (Any type):
  * - String for constants
  * - Int for numbers
  * 
  * These conversions appear throughout the codebase when integrating
  * KB data with FOL model theory. Centralizing them here provides:
  * - Single source of truth
  * - Easier extension (if new RelationValue types added)
  * - Clear documentation of conversion semantics
  * 
  * Used by: KnowledgeBaseModel, ScopeEvaluator, Query DSL
  */
object RelationValueUtil:
  
  /** Convert RelationValue to domain value (Any type).
    * 
    * Used when passing KB data to FOL semantics evaluation.
    * The result type is Any because FOL models are polymorphic (Model[D]).
    * 
    * Examples:
    * - RelationValue.Const("alice") => "alice" (String)
    * - RelationValue.Num(42) => 42 (Int)
    * 
    * @param rv RelationValue from knowledge base
    * @return Underlying value (String or Int)
    */
  def toDomainValue(rv: RelationValue): Any = rv match
    case RelationValue.Const(name) => name
    case RelationValue.Num(value) => value
  
  /** Convert domain value to RelationValue.
    * 
    * Used when translating from FOL model back to KB representation.
    * Supports String and Int types.
    * 
    * @param value Domain value (String or Int)
    * @return Corresponding RelationValue
    * @throws IllegalArgumentException if value type unsupported
    */
  def fromDomainValue(value: Any): RelationValue = value match
    case s: String => RelationValue.Const(s)
    case i: Int => RelationValue.Num(i)
    case _ => 
      throw new IllegalArgumentException(
        s"Unsupported domain value type: ${value.getClass.getName}"
      )
  
  /** Convert set of RelationValues to set of domain values.
    * 
    * Convenience method for bulk conversion (e.g., converting active domain
    * for use in FOL model construction).
    * 
    * @param rvs Set of RelationValues
    * @return Set of domain values (Set[Any])
    */
  def toDomainSet(rvs: Set[RelationValue]): Set[Any] =
    rvs.map(toDomainValue)
  
  /** Convert set of RelationValues to typed domain set.
    * 
    * Used by Query DSL where population type is known (typically String).
    * Performs unchecked cast to target type A.
    * 
    * Warning: This is type-unsafe! Caller must ensure all values can
    * be cast to type A. Used only in Query DSL where type is controlled.
    * 
    * @param rvs Set of RelationValues
    * @tparam A Target type (typically String)
    * @return Set of values cast to type A
    */
  def toDomainSetTyped[A](rvs: Set[RelationValue]): Set[A] =
    rvs.map(rv => toDomainValue(rv).asInstanceOf[A])
  
  /** Convert list of RelationValues to list of domain values.
    * 
    * List variant of toDomainSet for ordered collections.
    * 
    * @param rvs List of RelationValues
    * @return List of domain values
    */
  def toDomainList(rvs: List[RelationValue]): List[Any] =
    rvs.map(toDomainValue)
