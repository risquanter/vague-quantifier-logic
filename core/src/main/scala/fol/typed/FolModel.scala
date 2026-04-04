package fol.typed

import fol.error.QueryError

/** A validated pairing of [[TypeCatalog]] and [[RuntimeModel]].
  *
  * Construction validates that:
  * - every function and predicate declared in the catalog has an implementation
  *   in the dispatcher ([[RuntimeModel.validateAgainst]]);
  * - every [[TypeDecl.DomainType]] in the catalog has a registered element set
  *   in [[RuntimeModel.domains]].
  *
  * Once constructed, evaluation calls on this model skip per-query re-validation.
  * Use [[FolModel.apply]] (returns `Either`) to construct — handle the `Left` at
  * the application boundary rather than bypassing it.
  */
case class FolModel private (catalog: TypeCatalog, model: RuntimeModel)

object FolModel:

  /** Construct and validate a [[FolModel]].
    *
    * Returns `Left(QueryError.ModelValidationError)` if any catalog symbol
    * lacks a dispatcher implementation, or any declared domain type has no
    * element set in [[RuntimeModel.domains]].
    */
  def apply(catalog: TypeCatalog, model: RuntimeModel): Either[QueryError, FolModel] =
    model.validateAgainst(catalog) match
      case Right(_)     => Right(new FolModel(catalog, model))
      case Left(errors) => Left(QueryError.ModelValidationError(errors = errors.map(_.message)))
