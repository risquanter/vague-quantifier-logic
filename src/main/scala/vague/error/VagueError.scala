package vague.error

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
  * def parse(input: String): Either[VagueError, VagueQuery] = ???
  * }}}
  * 
  * Usage with ZIO (user's choice):
  * {{{
  * import zio.*
  * def parse(input: String): IO[VagueError, VagueQuery] = ???
  * }}}
  * 
  * Usage with cats-effect (user's choice):
  * {{{
  * import cats.effect.IO
  * def parse(input: String): IO[VagueQuery] = ???  // Can handle VagueError
  * }}}
  */
sealed trait VagueError:
  /** Human-readable error message */
  def message: String
  
  /** Optional context information */
  def context: Map[String, String] = Map.empty
  
  /** Convert to exception for backward compatibility */
  def toThrowable: Throwable = VagueException(this)
  
  /** Pretty-print error with context */
  def formatted: String =
    val ctx = if context.isEmpty then ""
              else context.map { case (k, v) => s"  $k: $v" }.mkString("\n", "\n", "")
    s"$message$ctx"

object VagueError:
  
  // ==================== Parsing Errors ====================
  
  /** Error during query parsing */
  case class ParseError(
    message: String,
    input: String,
    position: Option[Int] = None,
    override val context: Map[String, String] = Map.empty
  ) extends VagueError:
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
  ) extends VagueError:
    override val context = Map("character" -> char.toString, "position" -> position.toString)
  
  // ==================== Validation Errors ====================
  
  /** Validation error (well-formed but invalid query) */
  case class ValidationError(
    message: String,
    field: String,
    override val context: Map[String, String] = Map.empty
  ) extends VagueError:
    override def formatted: String =
      s"Validation error in '$field': $message" +
        (if context.isEmpty then "" else "\n" + context.map { case (k, v) => s"  $k: $v" }.mkString("\n"))
  
  /** Query structure validation errors */
  case class QueryStructureError(
    message: String,
    queryPart: String,
    suggestion: Option[String] = None
  ) extends VagueError:
    override val context = Map("query_part" -> queryPart) ++ suggestion.map("suggestion" -> _)
  
  /** Quantifier constraint violation */
  case class QuantifierError(
    message: String,
    k: Int,
    n: Int,
    tolerance: Double
  ) extends VagueError:
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
  ) extends VagueError:
    override def formatted: String =
      val causeMsg = cause.map(c => s"\nCause: ${c.getMessage}").getOrElse("")
      s"Evaluation error in $phase: $message$causeMsg" +
        (if context.isEmpty then "" else "\n" + context.map { case (k, v) => s"  $k: $v" }.mkString("\n"))
  
  /** Empty range extracted (D_R = ∅) */
  case class EmptyRangeError(
    relationName: String,
    pattern: String,
    suggestion: Option[String] = None
  ) extends VagueError:
    def message = s"Empty range: no tuples match pattern '$pattern' in relation '$relationName'"
    override val context = Map("relation" -> relationName, "pattern" -> pattern) ++
      suggestion.map("suggestion" -> _)
  
  /** Scope evaluation failed */
  case class ScopeEvaluationError(
    message: String,
    formula: String,
    element: String
  ) extends VagueError:
    override val context = Map("formula" -> formula, "element" -> element)
  
  // ==================== Data Store Errors ====================
  
  /** Knowledge source/database errors */
  case class DataStoreError(
    message: String,
    operation: String,
    relation: Option[String] = None,
    cause: Option[Throwable] = None
  ) extends VagueError:
    override val context = Map("operation" -> operation) ++ relation.map("relation" -> _)
    override def formatted: String =
      val rel = relation.map(r => s" on relation '$r'").getOrElse("")
      val causeMsg = cause.map(c => s"\nCause: ${c.getMessage}").getOrElse("")
      s"Data store error during $operation$rel: $message$causeMsg"
  
  /** Relation not found in schema */
  case class RelationNotFoundError(
    relationName: String,
    availableRelations: Set[String]
  ) extends VagueError:
    def message = s"Relation '$relationName' not found"
    override val context = Map(
      "relation" -> relationName,
      "available" -> availableRelations.mkString(", ")
    )
    override def formatted: String =
      s"Relation '$relationName' not found. Available relations: ${availableRelations.mkString(", ")}"
  
  /** Schema validation error */
  case class SchemaError(
    message: String,
    relationName: String,
    expectedArity: Int,
    actualArity: Int
  ) extends VagueError:
    override val context = Map(
      "relation" -> relationName,
      "expected_arity" -> expectedArity.toString,
      "actual_arity" -> actualArity.toString
    )
  
  // ==================== FOL Semantics Errors ====================
  
  /** Uninterpreted symbol in FOL evaluation */
  case class UninterpretedSymbolError(
    symbolType: String,  // "function" or "predicate"
    symbolName: String,
    availableSymbols: Set[String] = Set.empty
  ) extends VagueError:
    def message = s"Uninterpreted $symbolType: '$symbolName'"
    override val context = Map(
      "symbol_type" -> symbolType,
      "symbol_name" -> symbolName
    ) ++ (if availableSymbols.nonEmpty then Map("available" -> availableSymbols.mkString(", ")) else Map.empty)
  
  /** Unbound variable in valuation */
  case class UnboundVariableError(
    variableName: String,
    boundVariables: Set[String]
  ) extends VagueError:
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
  ) extends VagueError:
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
  ) extends VagueError:
    override val context = Map("resource_type" -> resourceType)
  
  /** Connection/network error */
  case class ConnectionError(
    message: String,
    endpoint: String,
    cause: Option[Throwable] = None
  ) extends VagueError:
    override val context = Map("endpoint" -> endpoint)
  
  // ==================== Timeout Errors ====================
  
  /** Operation timeout */
  case class TimeoutError(
    operation: String,
    timeoutMs: Long,
    message: String = "Operation timed out"
  ) extends VagueError:
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
  ) extends VagueError:
    override val context = Map("config_key" -> key)

/** Exception wrapper for backward compatibility
  * 
  * Allows throwing VagueError as exception when needed,
  * but encourages use of Either/IO instead.
  */
case class VagueException(error: VagueError) extends Exception(error.formatted):
  def getError: VagueError = error

/** Helper functions for error handling */
object ErrorOps:
  
  /** Lift exception to VagueError */
  def fromThrowable(t: Throwable, phase: String = "unknown"): VagueError =
    VagueError.EvaluationError(
      message = t.getMessage,
      phase = phase,
      cause = Some(t)
    )
  
  /** Try block that returns Either */
  def attempt[A](phase: String)(f: => A): Either[VagueError, A] =
    try Right(f)
    catch
      case e: VagueException => Left(e.error)
      case e: IllegalArgumentException => Left(VagueError.ValidationError(e.getMessage, "input"))
      case e: Throwable => Left(fromThrowable(e, phase))
  
  /** Validate a condition */
  def validate(condition: Boolean, error: => VagueError): Either[VagueError, Unit] =
    if condition then Right(()) else Left(error)
  
  /** Require a condition (returns Unit on success, error on failure) */
  def require(condition: Boolean, field: String, message: String): Either[VagueError, Unit] =
    if condition then Right(())
    else Left(VagueError.ValidationError(message, field))
  
  /** Convert Option to Either with error */
  def fromOption[A](opt: Option[A], error: => VagueError): Either[VagueError, A] =
    opt.toRight(error)
