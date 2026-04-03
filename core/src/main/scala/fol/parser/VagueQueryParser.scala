package fol.parser

import fol.logic.{ParsedQuery, Quantifier}
import fol.error.{QueryError, QueryException}
import logic.{FOL, Formula, Term}
import parser.{FOLAtomParser, FormulaParser}
import lexer.Lexer
import util.StringUtil.explode

/** Parser for vague queries (paper Section 5.2)
  *
  * Syntax: Q[op]^{k/n} x (R(x,y'), φ(x,y))(y₁, ..., yₘ)
  *
  * Examples:
  *   Q[>=]^{3/4} x (country(x), exists y (hasGDP_agr(x,y) /\ y<=20))
  *   Q[~]^{1/2} x (capital(x, y), large(x))(y)
  *   Q[<=]^{1/3} x (city(x), populous(x))
  *
  * OCaml-style: parsers are `List[String] => (A, List[String])`.
  * Exceptions signal parse failure — mirrors OCaml's
  * `try … with Failure _ ->` backtracking pattern.
  * The single Either boundary lives at the public `parse` entry point.
  *
  * OCaml reference: parsing pattern from fol.ml / formulas.ml
  * Paper reference: Definition 1 (Section 5.2)
  */
object VagueQueryParser:

  // ── Public API ──────────────────────────────────────────────────────

  /** Parse a vague query string.
    *
    * Single public entry point.  Internal combinators throw on error;
    * this method catches everything and returns structured `Either`.
    *
    * @param s Query string in paper syntax
    * @return Either[QueryError, ParsedQuery]
    *
    * Example:
    * {{{
    * VagueQueryParser.parse("Q[~]^{1/2} x (city(x), large(x))") match
    *   case Right(query) => // use query
    *   case Left(error)  => // handle error
    * }}}
    */
  def parse(s: String): Either[QueryError, ParsedQuery] =
    try
      val tokens = mergeDecimalTokens(Lexer.lex(explode(s)))
      val (query, remaining) = parseTokens(tokens)
      if remaining.nonEmpty then
        Left(QueryError.ParseError(
          s"Unexpected tokens after query: ${remaining.mkString(" ")}",
          s,
          Some(s.length - remaining.mkString(" ").length),
          Map("remaining_tokens" -> remaining.mkString(" "))
        ))
      else
        Right(query)
    catch
      case e: QueryException => Left(e.error)
      case e: Exception =>
        Left(QueryError.ParseError(
          s"Unexpected error during parsing: ${e.getMessage}",
          s,
          None,
          Map("exception" -> e.getClass.getSimpleName)
        ))

  // ── Combinators ─────────────────────────────────────────────────────
  // Each is List[String] => (A, List[String])  — the ParseResult pattern.
  // Throws QueryException on error, like FOLAtomParser.

  /** Parse vague query from tokens — combinator style.
    *
    * `List[String] => (ParsedQuery, List[String])`
    *
    * Throws QueryException on parse errors, like FOLAtomParser.
    */
  def parseTokens(tokens: List[String]): (ParsedQuery, List[String]) =
    // 1. Q[op]^{k/n}
    val (quantifier, t1) = parseQuantifier(tokens)
    // 2. x
    val (variable, t2) = parseVariable(t1)
    // 3. (
    val t3 = expect("(", t2, "range predicate")
    // 4. R(x,y') — delegate to FOL atom parser (OCaml style)
    val (range, t4) = FOLAtomParser.parseAtom(List(), t3)
    // 5. ,
    val t5 = expect(",", t4, "scope formula")
    // 6. φ(x,y) — delegate to formula parser (OCaml style)
    val (scope, t6) = FormulaParser.parse(
      FOLAtomParser.parseInfixAtom,
      FOLAtomParser.parseAtom
    )(t5)
    // 7. )
    val t7 = expect(")", t6, "answer variables")
    // 8. Optional answer variables (y₁, …, yₘ)
    val (answerVars, t8) = parseAnswerVars(t7)
    // 9. Construct & validate
    val query = ParsedQuery.mk(quantifier, variable, range, scope, answerVars)
    (query, t8)

  /** Parse quantifier: Q[op]^{k/n}
    *
    * Syntax:
    *   Q[~]^{k/n}    — About
    *   Q[>=]^{k/n}   — AtLeast
    *   Q[<=]^{k/n}   — AtMost
    *
    * Optional tolerance: Q[~]^{k/n}[ε]
    */
  private def parseQuantifier(tokens: List[String]): (Quantifier, List[String]) =
    tokens match
      case "Q" :: "[" :: op :: "]" :: "^" :: "{" :: rest =>
        val (k, afterK) = parseInteger(rest, "numerator k")
        val afterSlash = expect("/", afterK, "denominator")
        val (n, afterN) = parseInteger(afterSlash, "denominator n")
        val afterBrace = expect("}", afterN, "tolerance or end")

        // Optional tolerance [ε]
        // mergeDecimalTokens (applied in parse()) ensures that a decimal like
        // 0.05 arrives here as a single token "0.05", so only the first branch
        // is needed.  The former three-token branch "[" intPart "." fracPart "]"
        // was dead code after mergeDecimalTokens was introduced and is removed.
        val (tolerance, afterTol) = afterBrace match
          case "[" :: tolStr :: "]" :: rest2 if isNumeric(tolStr) =>
            (tolStr.toDouble, rest2)
          case _ =>
            (0.1, afterBrace) // Default tolerance

        val quantifier = op match
          case "~" | "~#" => Quantifier.About(k, n, tolerance)
          case ">=" | "≥"  => Quantifier.AtLeast(k, n, tolerance)
          case "<=" | "≤"  => Quantifier.AtMost(k, n, tolerance)
          case _ =>
            throw QueryException(QueryError.ParseError(
              s"Invalid quantifier operator: $op (expected ~, >=, or <=)",
              tokens.mkString(" "),
              None,
              Map("operator" -> op, "valid_operators" -> "~, >=, <=")
            ))

        (quantifier, afterTol)

      case _ =>
        throw QueryException(QueryError.ParseError(
          s"Expected quantifier Q[op]^{k/n}, got: ${tokens.take(7).mkString(" ")}",
          tokens.mkString(" "),
          None,
          Map("expected" -> "Q[op]^{k/n}", "got" -> tokens.take(7).mkString(" "))
        ))

  /** Parse variable name: single identifier token. */
  private def parseVariable(tokens: List[String]): (String, List[String]) =
    tokens match
      case v :: rest if isIdentifier(v) => (v, rest)
      case head :: _ =>
        throw QueryException(QueryError.ParseError(
          s"Expected variable identifier, got: $head",
          tokens.mkString(" "),
          None,
          Map("got" -> head, "expected" -> "variable identifier")
        ))
      case Nil =>
        throw QueryException(QueryError.ParseError(
          "Expected variable identifier, got end of input",
          "",
          None,
          Map("expected" -> "variable identifier")
        ))

  /** Parse optional answer variables: (y₁, …, yₘ)
    *
    * Returns empty list if no answer variables present.
    */
  private def parseAnswerVars(tokens: List[String]): (List[String], List[String]) =
    tokens match
      case "(" :: rest =>
        val (vars, afterVars) = parseVariableList(rest)
        val afterClose = expect(")", afterVars, "end of answer variables")
        (vars, afterClose)
      case _ =>
        (Nil, tokens) // No answer variables (Boolean query)

  /** Parse comma-separated list of variables. */
  private def parseVariableList(tokens: List[String]): (List[String], List[String]) =
    def loop(tokens: List[String], acc: List[String]): (List[String], List[String]) =
      tokens match
        case v :: "," :: rest if isIdentifier(v) =>
          loop(rest, acc :+ v)
        case v :: rest if isIdentifier(v) =>
          (acc :+ v, rest)
        case ")" :: _ =>
          (acc, tokens) // Empty list or end of list
        case head :: _ =>
          throw QueryException(QueryError.ParseError(
            s"Expected variable in list, got: $head",
            tokens.mkString(" "),
            None,
            Map("got" -> head, "expected" -> "variable identifier")
          ))
        case Nil =>
          throw QueryException(QueryError.ParseError(
            "Expected variable in list, got end of input",
            "",
            None,
            Map("expected" -> "variable identifier")
          ))
    loop(tokens, Nil)

  // ── Helpers (OCaml-style) ───────────────────────────────────────────

  /** Expect a specific token — OCaml pattern from fol.ml.
    *
    * @param expected Token to consume
    * @param tokens   Remaining tokens
    * @param context  What we're parsing (for error messages)
    * @return Remaining tokens after consuming `expected`
    * @throws QueryException if token doesn't match
    */
  private def expect(expected: String, tokens: List[String], context: String): List[String] =
    tokens match
      case `expected` :: rest => rest
      case actual :: _ =>
        throw QueryException(QueryError.ParseError(
          s"Expected '$expected' before $context, got '$actual'",
          tokens.mkString(" "),
          None,
          Map("expected" -> expected, "got" -> actual, "context" -> context)
        ))
      case Nil =>
        throw QueryException(QueryError.ParseError(
          s"Expected '$expected' before $context, got end of input",
          "",
          None,
          Map("expected" -> expected, "context" -> context)
        ))

  /** Parse integer token. */
  private def parseInteger(tokens: List[String], context: String): (Int, List[String]) =
    tokens match
      case num :: rest if num.forall(_.isDigit) =>
        try
          (num.toInt, rest)
        catch
          case _: NumberFormatException =>
            throw QueryException(QueryError.ParseError(
              s"Integer out of range for $context: $num",
              tokens.mkString(" "),
              None,
              Map("value" -> num, "context" -> context)
            ))
      case head :: _ =>
        throw QueryException(QueryError.ParseError(
          s"Expected integer for $context, got: $head",
          tokens.mkString(" "),
          None,
          Map("got" -> head, "expected" -> "integer", "context" -> context)
        ))
      case Nil =>
        throw QueryException(QueryError.ParseError(
          s"Expected integer for $context, got end of input",
          "",
          None,
          Map("expected" -> "integer", "context" -> context)
        ))

  /** Check if string is a valid identifier (alphanumeric, starts with letter). */
  private def isIdentifier(s: String): Boolean =
    s.nonEmpty && s.head.isLetter && s.forall(c => c.isLetterOrDigit || c == '_')

  /** Check if string is numeric (integer or decimal). */
  private def isNumeric(s: String): Boolean =
    util.StringUtil.isNumeric(s) || util.StringUtil.isDecimalLiteral(s)

  /** Merge consecutive digit "." digit token triples into a single decimal token.
    *
    * The OCaml-ported lexer classifies "." as a symbolic character, so the input
    * "0.05" tokenises to ["0", ".", "05"] — three tokens.  This post-processor
    * merges them back to ["0.05"] before parsing begins.  Applied to the full
    * token stream so decimal literals in predicate/function arguments (scope
    * formula) are handled as well as in the tolerance bracket.
    *
    * Only digit-dot-digit triples are merged; other token sequences are
    * unchanged.
    */
  private def mergeDecimalTokens(tokens: List[String]): List[String] =
    tokens match
      case a :: "." :: b :: rest
        if a.nonEmpty && a.forall(_.isDigit) && b.nonEmpty && b.forall(_.isDigit) =>
        s"$a.$b" :: mergeDecimalTokens(rest)
      case t :: rest => t :: mergeDecimalTokens(rest)
      case Nil       => Nil
