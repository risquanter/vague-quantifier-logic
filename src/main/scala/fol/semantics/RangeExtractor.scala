package fol.semantics

import logic.{FOL, Term}
import fol.datastore.{KnowledgeBase, KnowledgeSource, RelationValue, RelationTuple}
import fol.logic.ParsedQuery
import fol.error.{QueryError, QueryException}
import logic.Formula

/** Range Extraction (D_R)
  *
  * Extracts the domain of quantification from a knowledge source based on the
  * range predicate R(x,y') in a vague query Q x (R(x,y'), φ(x,y))(y).
  *
  * From paper (Definition 2):
  *   D_R = {c ∈ ADom(D) | R(c,σ(y')) ∈ D}
  *
  * Where:
  * - ADom(D) is the active domain (all constants in the source)
  * - R is the range relation
  * - c is a candidate for the quantified variable x
  * - σ(y') is the substitution for free variables y' in the range
  * - D is the database (knowledge source)
  *
  * OCaml-style: internal functions throw on error (like FOLAtomParser).
  * The single Either boundary is at `extractRange` (public API).
  *
  * Example:
  *   Query: Q[≥]^{3/4} x (country(x), ...)
  *   Range: country(x)
  *   D_R: all elements c where country(c) holds in source
  *
  * Example with substitution:
  *   Query: Q[~#]^{1/2} x (capital(x), ...)(y)  where x is capital of y
  *   Range: capital(x)
  *   Substitution: {y → "France"}
  *   D_R: all elements c where capital(c, "France") holds in source
  */
object RangeExtractor:

  // ── Public API ──────────────────────────────────────────────────────

  /** Extract range domain D_R from knowledge source.
    *
    * Single public entry point.  Internal helpers throw; this method
    * catches everything and returns structured `Either`.
    *
    * @param source       The knowledge source to query
    * @param query        The vague query containing the range predicate
    * @param substitution Values for free variables (answer variables from query)
    * @return Either[QueryError, Set[RelationValue]]
    */
  def extractRange(
    source: KnowledgeSource,
    query: ParsedQuery,
    substitution: Map[String, RelationValue] = Map.empty
  ): Either[QueryError, Set[RelationValue]] =
    try
      Right(extractRangeUnsafe(source, query, substitution))
    catch
      case e: QueryException => Left(e.error)
      case e: Exception =>
        Left(QueryError.EvaluationError(
          s"Error during range extraction: ${e.getMessage}",
          "range_extraction",
          Some(e),
          Map("predicate" -> query.range.predicate)
        ))

  /** Extract range for Boolean queries (no free variables).
    *
    * Convenience wrapper for Boolean queries.
    */
  def extractRangeBoolean(source: KnowledgeSource, query: ParsedQuery): Either[QueryError, Set[RelationValue]] =
    if !query.isBoolean then
      Left(QueryError.ValidationError(
        "Query must be Boolean (no answer variables)",
        "query_type",
        Map("answer_vars" -> query.answerVars.mkString(", "))
      ))
    else
      extractRange(source, query, Map.empty)

  /** Extract range with single answer variable.
    *
    * Convenience wrapper for unary queries.
    */
  def extractRangeUnary(
    source: KnowledgeSource,
    query: ParsedQuery,
    answerValue: RelationValue
  ): Either[QueryError, Set[RelationValue]] =
    if !query.isUnary then
      Left(QueryError.ValidationError(
        "Query must be unary (single answer variable)",
        "query_type",
        Map("answer_vars" -> query.answerVars.mkString(", "))
      ))
    else
      val answerVar = query.answerVars.head
      extractRange(source, query, Map(answerVar -> answerValue))

  /** Get all possible ranges for a query with answer variables.
    *
    * For queries with answer variables y, evaluates the range
    * for each possible substitution σ(y).
    *
    * Example:
    *   Query: Q[~#]^{1/2} x (capital(x, y), ...)(y)
    *   Returns: Map(
    *     {y → "France"} → Set("Paris"),
    *     {y → "Germany"} → Set("Berlin"),
    *     ...
    *   )
    */
  def extractAllRanges(
    source: KnowledgeSource,
    query: ParsedQuery
  ): Either[QueryError, Map[Map[String, RelationValue], Set[RelationValue]]] =
    try
      val result = if query.isBoolean then
        Map(Map.empty[String, RelationValue] -> extractRangeUnsafe(source, query, Map.empty))
      else
        val substitutions = generateSubstitutions(source, query)
        substitutions.map { subst =>
          subst -> extractRangeUnsafe(source, query, subst)
        }.toMap
      Right(result)
    catch
      case e: QueryException => Left(e.error)
      case e: Exception =>
        Left(QueryError.EvaluationError(
          s"Error extracting all ranges: ${e.getMessage}",
          "range_extraction",
          Some(e),
          Map("predicate" -> query.range.predicate)
        ))

  // ── Internal (throwing — OCaml style) ───────────────────────────────

  /** Core extraction — throws on error, like FOLAtomParser. */
  private def extractRangeUnsafe(
    source: KnowledgeSource,
    query: ParsedQuery,
    substitution: Map[String, RelationValue]
  ): Set[RelationValue] =
    val range = query.range
    val quantifiedVar = query.variable
    val pattern = buildPattern(range, quantifiedVar, substitution)
    val quantVarPosition = findVariablePosition(range, quantifiedVar)
    DomainExtraction.extractFromPatternAtPosition(
      source, range.predicate, pattern, quantVarPosition
    )

  /** Build query pattern for source lookup.
    *
    * NOT FOL formula substitution (that's FOLUtil.subst).
    * This converts FOL range terms to KB query patterns for database lookup.
    *
    * Converts FOL range predicate to KB query pattern:
    * - Quantified variable x → None (wildcard — we're searching for these!)
    * - Free variables with substitution → Some(value)
    * - Free variables without substitution → None (wildcard)
    * - Constants → Some(value)
    *
    * Example:
    *   Range: capital(x, y), Quantified: x, Substitution: {y → "France"}
    *   Pattern: [None, Some(Const("France"))]
    *   Meaning: "Find all x where capital(x, France) holds in KB"
    */
  private def buildPattern(
    range: FOL,
    quantifiedVar: String,
    substitution: Map[String, RelationValue]
  ): List[Option[RelationValue]] =
    // Check for unsupported function terms
    range.terms.collectFirst {
      case Term.Fn(f, args) => (f, args)
    } match
      case Some((f, args)) =>
        throw QueryException(QueryError.EvaluationError(
          s"Function terms in range predicates are not supported: $f",
          "pattern_building",
          None,
          Map(
            "function" -> f,
            "args" -> args.toString,
            "suggestion" -> "Use only variables and constants in range predicates"
          )
        ))
      case None =>
        range.terms.map {
          case Term.Var(v) if v == quantifiedVar =>
            None // Wildcard for quantified variable

          case Term.Var(v) =>
            substitution.get(v) // Substitute if available, else wildcard

          case Term.Const(c) =>
            Some(c.toIntOption match {
              case Some(n) => RelationValue.Num(n)
              case None    => RelationValue.Const(c)
            })

          case Term.Fn(_, _) =>
            None // Should never reach here due to check above
        }

  /** Find position of quantified variable in range predicate.
    *
    * Example:
    *   capital(x, y) with quantified var x → position 0
    *   capital(y, x) with quantified var x → position 1
    */
  private def findVariablePosition(range: FOL, quantifiedVar: String): Int =
    range.terms.indexWhere {
      case Term.Var(v) => v == quantifiedVar
      case _ => false
    } match
      case -1 =>
        throw QueryException(QueryError.ValidationError(
          s"Quantified variable $quantifiedVar not found in range ${range.predicate}",
          "quantified_variable",
          Map(
            "variable" -> quantifiedVar,
            "range_predicate" -> range.predicate,
            "range_terms" -> range.terms.toString
          )
        ))
      case pos => pos

  /** Generate all possible substitutions for answer variables. */
  private def generateSubstitutions(
    source: KnowledgeSource,
    query: ParsedQuery
  ): Set[Map[String, RelationValue]] =
    val domain = source.activeDomain.toList

    def generateForVars(vars: List[String]): Set[Map[String, RelationValue]] =
      vars match
        case Nil => Set(Map.empty)
        case v :: rest =>
          val restSubsts = generateForVars(rest)
          for
            value <- domain.toSet
            restSubst <- restSubsts
          yield restSubst + (v -> value)

    generateForVars(query.answerVars)
