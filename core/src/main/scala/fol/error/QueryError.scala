package fol.error

import fol.datastore.RelationName

/** Structured error types for vague quantifier evaluation.
  * 
  * This error hierarchy provides:
  * - Type-safe error handling without exceptions
  * - Composable error messages
  * - Effect-system agnostic (works with Either, Try, ZIO, cats-effect)
  * - No external dependencies (pure Scala 3)
  * 
  * Usage with Either:
  * {{{
  * def parse(input: String): Either[QueryError, ParsedQuery] = ???
  * }}}
  * 
  * Usage with ZIO (user's choice):
  * {{{
  * import zio.*
  * def parse(input: String): IO[QueryError, ParsedQuery] = ???
  * }}}
  * 
  * Usage with cats-effect (user's choice):
  * {{{
  * import cats.effect.IO
  * def parse(input: String): IO[ParsedQuery] = ???  // Can handle QueryError
  * }}}
  */
sealed trait QueryError:
  /** Human-readable error message */
  def message: String
  
  /** Optional context information */
  def context: Map[String, String] = Map.empty
  
  /** Convert to exception for backward compatibility */
  def toThrowable: Throwable = QueryException(this)
  
  /** Pretty-print error with context */
  def formatted: String =
    val ctx = if context.isEmpty then ""
              else context.map { case (k, v) => s"  $k: $v" }.mkString("\n", "\n", "")
    s"$message$ctx"

object QueryError:
  
  // ==================== Parsing Errors ====================
  
  /** Error during query parsing */
  case class ParseError(
    message: String,
    input: String,
    position: Option[Int] = None,
    override val context: Map[String, String] = Map.empty
  ) extends QueryError:
    override def formatted: String =
      val pos = position.map(p => s" at position $p").getOrElse("")
      val snippet = if input.length > 50 then input.take(50) + "..." else input
      s"Parse error$pos: $message\nInput: $snippet" + 
        (if context.isEmpty then "" else "\n" + context.map { case (k, v) => s"  $k: $v" }.mkString("\n"))
  
  /** Lexical error (invalid tokens) */
  case class LexicalError(
    message: String,
    char: Char,
    position: Int
  ) extends QueryError:
    override val context = Map("character" -> char.toString, "position" -> position.toString)
  
  // ==================== Validation Errors ====================
  
  /** Validation error (well-formed but invalid query) */
  case class ValidationError(
    message: String,
    field: String,
    override val context: Map[String, String] = Map.empty
  ) extends QueryError:
    override def formatted: String =
      s"Validation error in '$field': $message" +
        (if context.isEmpty then "" else "\n" + context.map { case (k, v) => s"  $k: $v" }.mkString("\n"))
  
  /** Query structure validation errors */
  case class QueryStructureError(
    message: String,
    queryPart: String,
    suggestion: Option[String] = None
  ) extends QueryError:
    override val context = Map("query_part" -> queryPart) ++ suggestion.map("suggestion" -> _)
  
  /** Quantifier constraint violation */
  case class QuantifierError(
    message: String,
    k: Int,
    n: Int,
    tolerance: Double
  ) extends QueryError:
    override val context = Map(
      "numerator" -> k.toString,
      "denominator" -> n.toString,
      "tolerance" -> tolerance.toString
    )
  
  // ==================== Evaluation Errors ====================
  
  /** Error during query evaluation */
  case class EvaluationError(
    message: String,
    phase: String,
    cause: Option[Throwable] = None,
    override val context: Map[String, String] = Map.empty
  ) extends QueryError:
    override def formatted: String =
      val causeMsg = cause.map(c => s"\nCause: ${c.getMessage}").getOrElse("")
      s"Evaluation error in $phase: $message$causeMsg" +
        (if context.isEmpty then "" else "\n" + context.map { case (k, v) => s"  $k: $v" }.mkString("\n"))
  
  /** Scope evaluation failed */
  case class ScopeEvaluationError(
    message: String,
    formula: String,
    element: String
  ) extends QueryError:
    override val context = Map("formula" -> formula, "element" -> element)
  
  // ==================== Data Store Errors ====================
  
  /** Knowledge source/database errors */
  case class DataStoreError(
    message: String,
    operation: String,
    relation: Option[String] = None,
    cause: Option[Throwable] = None
  ) extends QueryError:
    override val context = Map("operation" -> operation) ++ relation.map("relation" -> _)
    override def formatted: String =
      val rel = relation.map(r => s" on relation '$r'").getOrElse("")
      val causeMsg = cause.map(c => s"\nCause: ${c.getMessage}").getOrElse("")
      s"Data store error during $operation$rel: $message$causeMsg"
  
  /** Relation not found in schema */
  case class RelationNotFoundError(
    relationName: RelationName,
    availableRelations: Set[RelationName]
  ) extends QueryError:
    def message = s"Relation '${relationName.value}' not found"
    override val context = Map(
      "relation" -> relationName.value,
      "available" -> availableRelations.map(_.value).mkString(", ")
    )
    override def formatted: String =
      s"Relation '${relationName.value}' not found. Available relations: ${availableRelations.map(_.value).mkString(", ")}"
  
  /** Schema validation error */
  case class SchemaError(
    message: String,
    relationName: RelationName,
    expectedArity: Int,
    actualArity: Int
  ) extends QueryError:
    override val context = Map(
      "relation" -> relationName.value,
      "expected_arity" -> expectedArity.toString,
      "actual_arity" -> actualArity.toString
    )

  /** Position out of bounds for a relation */
  case class PositionOutOfBoundsError(
    message: String,
    relationName: RelationName,
    arity: Int,
    position: Int
  ) extends QueryError:
    override val context = Map(
      "relation" -> relationName.value,
      "arity" -> arity.toString,
      "position" -> position.toString
    )
  
  // ==================== FOL Semantics Errors ====================
  
  /** Uninterpreted symbol in FOL evaluation */
  case class UninterpretedSymbolError(
    symbolType: String,  // "function" or "predicate"
    symbolName: String,
    availableSymbols: Set[String] = Set.empty
  ) extends QueryError:
    def message = s"Uninterpreted $symbolType: '$symbolName'"
    override val context = Map(
      "symbol_type" -> symbolType,
      "symbol_name" -> symbolName
    ) ++ (if availableSymbols.nonEmpty then Map("available" -> availableSymbols.mkString(", ")) else Map.empty)
  
  /** Defensive fallback raised when evaluation reaches a type with no registered
    * domain, despite passing binding. In a correctly wired system this should
    * not be reachable: [[fol.typed.RuntimeModel.validateAgainst]] enforces domain
    * coverage before queries are served.
    * See also: [[fol.typed.TypedSemantics]]
    */
  case class DomainNotFoundError(
    typeName: String,
    availableTypes: Set[String]
  ) extends QueryError:
    def message = s"No domain found for type '$typeName'"
    override val context = Map(
      "type"      -> typeName,
      "available" -> availableTypes.mkString(", ")
    )
    override def formatted: String =
      s"No domain found for type '$typeName'. " +
      s"Available types with domains: ${availableTypes.mkString(", ")}"

  /** Raised when a query fails the typed bind phase (type-check errors).
    * Indicates a user query error — all instances map to HTTP 400.
    * Individual error messages are rendered strings; raw TypeCheckError
    * detail is not carried here due to fol.error → fol.typed package constraint.
    */
  case class BindError(
    errors: List[String]
  ) extends QueryError:
    def message = s"Query type-checking failed: ${errors.mkString("; ")}"
    override val context = Map("errors" -> errors.mkString("; "))

  /** Raised when RuntimeModel.validateAgainst fails (dispatcher or domain
    * coverage gaps). Indicates a wiring/infra error — all instances map to HTTP 500.
    * Individual error messages are rendered strings; raw RuntimeModelError
    * detail is not carried here due to fol.error → fol.typed package constraint.
    */
  case class ModelValidationError(
    errors: List[String]
  ) extends QueryError:
    def message = s"Runtime model validation failed: ${errors.mkString("; ")}"
    override val context = Map("errors" -> errors.mkString("; "))

  /** Unbound variable in valuation */
  case class UnboundVariableError(
    variableName: String,
    boundVariables: Set[String]
  ) extends QueryError:
    def message = s"Unbound variable: '$variableName'"
    override val context = Map(
      "variable" -> variableName,
      "bound_variables" -> boundVariables.mkString(", ")
    )
  
  /** Type mismatch in FOL evaluation */
  case class TypeMismatchError(
    message: String,
    expected: String,
    actual: String,
    location: String
  ) extends QueryError:
    override val context = Map(
      "expected" -> expected,
      "actual" -> actual,
      "location" -> location
    )
  
  // ==================== Resource Errors ====================
  
  /** Resource management error */
  case class ResourceError(
    message: String,
    resourceType: String,
    cause: Option[Throwable] = None
  ) extends QueryError:
    override val context = Map("resource_type" -> resourceType)
  
  /** Connection/network error */
  case class ConnectionError(
    message: String,
    endpoint: String,
    cause: Option[Throwable] = None
  ) extends QueryError:
    override val context = Map("endpoint" -> endpoint)
  
  // ==================== Timeout Errors ====================
  
  /** Operation timeout */
  case class TimeoutError(
    operation: String,
    timeoutMs: Long,
    message: String = "Operation timed out"
  ) extends QueryError:
    override val context = Map(
      "operation" -> operation,
      "timeout_ms" -> timeoutMs.toString
    )
  
  // ==================== Configuration Errors ====================
  
  /** Configuration error */
  case class ConfigError(
    message: String,
    key: String,
    cause: Option[Throwable] = None
  ) extends QueryError:
    override val context = Map("config_key" -> key)

/** Exception wrapper for backward compatibility
  * 
  * Allows throwing QueryError as exception when needed,
  * but encourages use of Either/IO instead.
  */
case class QueryException(error: QueryError) extends Exception(error.formatted):
  def getError: QueryError = error

/** Helper functions for error handling */
object ErrorOps:
  
  /** Lift exception to QueryError */
  def fromThrowable(t: Throwable, phase: String = "unknown"): QueryError =
    QueryError.EvaluationError(
      message = t.getMessage,
      phase = phase,
      cause = Some(t)
    )
  
  /** Try block that returns Either */
  def attempt[A](phase: String)(f: => A): Either[QueryError, A] =
    try Right(f)
    catch
      case e: QueryException => Left(e.error)
      case e: IllegalArgumentException => Left(QueryError.ValidationError(e.getMessage, "input"))
      case e: Throwable => Left(fromThrowable(e, phase))
  
  /** Validate a condition */
  def validate(condition: Boolean, error: => QueryError): Either[QueryError, Unit] =
    if condition then Right(()) else Left(error)
  
  /** Require a condition (returns Unit on success, error on failure) */
  def require(condition: Boolean, field: String, message: String): Either[QueryError, Unit] =
    if condition then Right(())
    else Left(QueryError.ValidationError(message, field))
  
  /** Convert Option to Either with error */
  def fromOption[A](opt: Option[A], error: => QueryError): Either[QueryError, A] =
    opt.toRight(error)
