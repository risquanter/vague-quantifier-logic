package fol.typed

import TypeDecl.*
import LiteralValue.*

case class FunctionSig(params: List[TypeId], returns: TypeId)

case class PredicateSig(params: List[TypeId])

enum TypeCatalogError:
  /** A type referenced in a signature or validator is not declared in [[TypeCatalog.types]].
    * `location` names the signature site, e.g. `"function 'p95' return type"` or
    * `"predicate 'hasloss' parameter 0"`, following compiler-engineering convention
    * that every diagnostic names its source site.
    */
  case UnknownType(name: String, location: String)
  case NameCollision(name: String)
  /** A function declares a [[DomainType]] sort as its return type, which is
    * unsupported. Functions must return [[ValueType]] sorts — the framework
    * wraps the return in `Value(resultSort, literalResult: LiteralValue)` and
    * `LiteralValue` cannot represent domain entities. See ADR-015 §1 and T-004.
    */
  case FunctionReturnIsDomainType(functionName: String, returnType: String)

/** A validated type catalog.
  *
  * Construction always runs validation. Use [[TypeCatalog.apply]] for
  * production code (returns Either). Use [[TypeCatalog.unsafe]] in tests
  * where the catalog is known-correct.
  *
  * A TypeCatalog value is guaranteed internally consistent: all type
  * references in signatures resolve to declared types, and no symbol name
  * appears as both a function and a predicate.
  */
case class TypeCatalog private (
  types: Set[TypeDecl],
  constants: Map[String, TypeId],
  functions: Map[SymbolName, FunctionSig],
  predicates: Map[SymbolName, PredicateSig],
  literalValidators: Map[TypeId, String => Option[LiteralValue]]
):
  /** All declared type IDs, regardless of role. */
  def typeIds: Set[TypeId] = types.map(_.typeId)

  /** Types that are first-class entities requiring a registered domain in
    * [[RuntimeModel]]. Derived from the [[DomainType]] tags in [[types]].
    * Validated by [[RuntimeModel.validateAgainst]] to enforce domain coverage.
    * See ADR-014.
    */
  def domainTypes: Set[TypeId] = types.collect { case DomainType(typeId) => typeId }

object TypeCatalog:

  /** Validate and construct. Returns Left if any type reference is undeclared
    * or any symbol name collides between functions and predicates.
    *
    * @param types The type declarations. Use [[DomainType]] for types that can
    *   be quantified over and [[ValueType]] for scalar / computed types. See
    *   ADR-014.
    */
  def apply(
    types: Set[TypeDecl],
    constants: Map[String, TypeId] = Map.empty,
    functions: Map[SymbolName, FunctionSig] = Map.empty,
    predicates: Map[SymbolName, PredicateSig] = Map.empty,
    literalValidators: Map[TypeId, String => Option[LiteralValue]] = Map.empty
  ): Either[List[TypeCatalogError], TypeCatalog] =
    val errors = collectErrors(types, constants, functions, predicates, literalValidators)
    if errors.isEmpty then Right(new TypeCatalog(types, constants, functions, predicates, literalValidators))
    else Left(errors)

  /** Construct without returning Either. Throws [[IllegalArgumentException]] if
    * validation fails. Use only in tests or application startup where a
    * catalog inconsistency is a programming error, not a user error.
    *
    * @param types See [[apply]] for semantics.
    */
  def unsafe(
    types: Set[TypeDecl],
    constants: Map[String, TypeId] = Map.empty,
    functions: Map[SymbolName, FunctionSig] = Map.empty,
    predicates: Map[SymbolName, PredicateSig] = Map.empty,
    literalValidators: Map[TypeId, String => Option[LiteralValue]] = Map.empty
  ): TypeCatalog =
    apply(types, constants, functions, predicates, literalValidators)
      .fold(
        errors => throw IllegalArgumentException(s"Invalid TypeCatalog: ${errors.map(_.toString).mkString(", ")}"),
        identity
      )

  private def collectErrors(
    types: Set[TypeDecl],
    constants: Map[String, TypeId],
    functions: Map[SymbolName, FunctionSig],
    predicates: Map[SymbolName, PredicateSig],
    literalValidators: Map[TypeId, String => Option[LiteralValue]]
  ): List[TypeCatalogError] =
    val typeIds = types.map(_.typeId)
    val unknownTypes = List.newBuilder[TypeCatalogError]

    constants.foreach { (key, s) =>
      if !typeIds.contains(s) then
        unknownTypes += TypeCatalogError.UnknownType(s.value, s"constant '$key'")
    }

    val domainTypeIds = types.collect { case DomainType(typeId) => typeId }

    functions.foreach { (sym, sig) =>
      sig.params.zipWithIndex.foreach { (s, i) =>
        if !typeIds.contains(s) then
          unknownTypes += TypeCatalogError.UnknownType(s.value, s"function '${sym.value}' parameter $i")
      }
      if !typeIds.contains(sig.returns) then
        unknownTypes += TypeCatalogError.UnknownType(sig.returns.value, s"function '${sym.value}' return type")
      else if domainTypeIds.contains(sig.returns) then
        unknownTypes += TypeCatalogError.FunctionReturnIsDomainType(sym.value, sig.returns.value)
    }

    predicates.foreach { (sym, sig) =>
      sig.params.zipWithIndex.foreach { (s, i) =>
        if !typeIds.contains(s) then
          unknownTypes += TypeCatalogError.UnknownType(s.value, s"predicate '${sym.value}' parameter $i")
      }
    }

    literalValidators.keys.foreach { s =>
      if !typeIds.contains(s) then
        unknownTypes += TypeCatalogError.UnknownType(s.value, s"literal validator for '${s.value}'")
    }

    val collisions =
      functions.keySet.intersect(predicates.keySet).toList.map(n => TypeCatalogError.NameCollision(n.value))

    unknownTypes.result() ++ collisions
