package fol.typed

case class FunctionSig(params: List[TypeId], returns: TypeId)

case class PredicateSig(params: List[TypeId])

enum TypeCatalogError:
  case UnknownType(name: String)
  case NameCollision(name: String)

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
  types: Set[TypeId],
  /** Types that require a registered domain in [[RuntimeModel]].
    * Defaults to all declared types. Used by [[RuntimeModel.validateAgainst]]
    * to enforce domain coverage and by [[fol.typed.QueryBinder]] to reject
    * queries that quantify over non-domain types at bind time.
    */
  enumerableTypes: Set[TypeId],
  constants: Map[String, TypeId],
  functions: Map[SymbolName, FunctionSig],
  predicates: Map[SymbolName, PredicateSig],
  literalValidators: Map[TypeId, String => Boolean]
)

object TypeCatalog:

  /** Validate and construct. Returns Left if any type reference is undeclared
    * or any symbol name collides between functions and predicates.
    */
  def apply(
    types: Set[TypeId],
    constants: Map[String, TypeId] = Map.empty,
    functions: Map[SymbolName, FunctionSig] = Map.empty,
    predicates: Map[SymbolName, PredicateSig] = Map.empty,
    literalValidators: Map[TypeId, String => Boolean] = Map.empty,
    enumerableTypes: Set[TypeId] = Set.empty
  ): Either[List[TypeCatalogError], TypeCatalog] =
    val effective = if enumerableTypes.isEmpty then types else enumerableTypes
    val errors = collectErrors(types, effective, constants, functions, predicates, literalValidators)
    if errors.isEmpty then Right(new TypeCatalog(types, effective, constants, functions, predicates, literalValidators))
    else Left(errors)

  /** Construct without returning Either. Throws [[IllegalArgumentException]] if
    * validation fails. Use only in tests or application startup where a
    * catalog inconsistency is a programming error, not a user error.
    */
  def unsafe(
    types: Set[TypeId],
    constants: Map[String, TypeId] = Map.empty,
    functions: Map[SymbolName, FunctionSig] = Map.empty,
    predicates: Map[SymbolName, PredicateSig] = Map.empty,
    literalValidators: Map[TypeId, String => Boolean] = Map.empty,
    enumerableTypes: Set[TypeId] = Set.empty
  ): TypeCatalog =
    apply(types, constants, functions, predicates, literalValidators, enumerableTypes)
      .fold(
        errors => throw IllegalArgumentException(s"Invalid TypeCatalog: ${errors.map(_.toString).mkString(", ")}"),
        identity
      )

  private def collectErrors(
    types: Set[TypeId],
    enumerableTypes: Set[TypeId],
    constants: Map[String, TypeId],
    functions: Map[SymbolName, FunctionSig],
    predicates: Map[SymbolName, PredicateSig],
    literalValidators: Map[TypeId, String => Boolean]
  ): List[TypeCatalogError] =
    val unknownTypes = List.newBuilder[TypeCatalogError]

    enumerableTypes.foreach { t =>
      if !types.contains(t) then unknownTypes += TypeCatalogError.UnknownType(t.value)
    }

    constants.values.foreach { s =>
      if !types.contains(s) then unknownTypes += TypeCatalogError.UnknownType(s.value)
    }

    functions.values.foreach { sig =>
      sig.params.foreach { s =>
        if !types.contains(s) then unknownTypes += TypeCatalogError.UnknownType(s.value)
      }
      if !types.contains(sig.returns) then
        unknownTypes += TypeCatalogError.UnknownType(sig.returns.value)
    }

    predicates.values.foreach { sig =>
      sig.params.foreach { s =>
        if !types.contains(s) then unknownTypes += TypeCatalogError.UnknownType(s.value)
      }
    }

    literalValidators.keys.foreach { s =>
      if !types.contains(s) then unknownTypes += TypeCatalogError.UnknownType(s.value)
    }

    val collisions =
      functions.keySet.intersect(predicates.keySet).toList.map(n => TypeCatalogError.NameCollision(n.value))

    unknownTypes.result() ++ collisions
