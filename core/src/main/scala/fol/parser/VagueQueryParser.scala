package fol.parser

import fol.logic.ParsedQuery
import fol.quantifier.Quantifier
import fol.error.{QueryError, QueryException}
import logic.{FOL, Formula, Term}
import parser.{FOLAtomParser, FormulaParser}
import parser.Combinators.{tokenLabel, tokensLabel}
import lexer.{Lexer, Token, LexerError}
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
  * OCaml-style: parsers are `List[Token] => (A, List[Token])` (ADR-007 C13).
  * Exceptions signal parse failure — mirrors OCaml's
  * `try … with Failure _ ->` backtracking pattern.
  * The single Either boundary lives at the public `parse` entry point.
  *
  * OCaml reference: parsing pattern from fol.ml / formulas.ml
  * Paper reference: Definition 1 (Section 5.2)
  */
object VagueQueryParser:

  // ── Public API ──────────────────────────────────────────────────────

  /** Parse a vague query string. */
  def parse(s: String): Either[QueryError, ParsedQuery] =
    try
      val tokens = mergeDecimalTokens(Lexer.lex(explode(s)))
      val (query, remaining) = parseTokens(tokens)
      if remaining.nonEmpty then
        Left(QueryError.ParseError(
          s"Unexpected tokens after query: ${tokensLabel(remaining)}",
          s,
          Some(s.length - tokensLabel(remaining).length),
          Map("remaining_tokens" -> tokensLabel(remaining))
        ))
      else
        Right(query)
    catch
      case e: QueryException => Left(e.error)
      case e: LexerError =>
        Left(QueryError.ParseError(
          s"Lexer error: ${e.getMessage}",
          s,
          None,
          Map("exception" -> e.getClass.getSimpleName)
        ))
      case e: Exception =>
        Left(QueryError.ParseError(
          s"Unexpected error during parsing: ${e.getMessage}",
          s,
          None,
          Map("exception" -> e.getClass.getSimpleName)
        ))

  // ── Combinators ─────────────────────────────────────────────────────
  // Each is List[Token] => (A, List[Token]) — the ParseResult pattern.
  // Throws QueryException on error, like FOLAtomParser.

  /** Parse vague query from tokens — combinator style. */
  def parseTokens(tokens: List[Token]): (ParsedQuery, List[Token]) =
    // 1. Q[op]^{k/n}
    val (quantifier, t1) = parseQuantifier(tokens)
    // 2. x
    val (variable, t2) = parseVariable(t1)
    // 3. (
    val t3 = expect(Token.LParen, t2, "range predicate")
    // 4. R(x,y') — delegate to FOL atom parser (OCaml style)
    val (range, t4) = FOLAtomParser.parseAtom(List(), t3)
    // 5. ,
    val t5 = expect(Token.Comma, t4, "scope formula")
    // 6. φ(x,y) — delegate to formula parser (OCaml style)
    val (scope, t6) = FormulaParser.parse(
      FOLAtomParser.parseInfixAtom,
      FOLAtomParser.parseAtom
    )(t5)
    // 7. )
    val t7 = expect(Token.RParen, t6, "answer variables")
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
    *
    * The bracketed operator may arrive as a single [[Token.OpSym]] (e.g. `>=`,
    * `<=`, `~`) or as a [[Token.Word]] when an operator happens to be
    * alphanumeric. Both shapes are accepted via [[opLabel]].
    */
  private def parseQuantifier(tokens: List[Token]): (Quantifier, List[Token]) =
    tokens match
      case Token.Word("Q") :: Token.LBracket :: opTok :: Token.RBracket ::
           Token.OpSym("^") :: Token.LBrace :: rest =>
        val op = opLabel(opTok)
        val (k, afterK) = parseInteger(rest, "numerator k")
        val afterSlash = expect(Token.OpSym("/"), afterK, "denominator")
        val (n, afterN) = parseInteger(afterSlash, "denominator n")
        val afterBrace = expect(Token.RBrace, afterN, "tolerance or end")

        // Optional tolerance [ε]
        val (tolerance, afterTol) = afterBrace match
          case Token.LBracket :: Token.Word(tolStr) :: Token.RBracket :: rest2
            if isNumeric(tolStr) =>
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
              tokensLabel(tokens),
              None,
              Map("operator" -> op, "valid_operators" -> "~, >=, <=")
            ))

        (quantifier, afterTol)

      case _ =>
        throw QueryException(QueryError.ParseError(
          s"Expected quantifier Q[op]^{k/n}, got: ${tokensLabel(tokens.take(7))}",
          tokensLabel(tokens),
          None,
          Map("expected" -> "Q[op]^{k/n}", "got" -> tokensLabel(tokens.take(7)))
        ))

  /** Parse variable name: single identifier token. */
  private def parseVariable(tokens: List[Token]): (String, List[Token]) =
    tokens match
      case Token.Word(v) :: rest if isIdentifier(v) => (v, rest)
      case head :: _ =>
        throw QueryException(QueryError.ParseError(
          s"Expected variable identifier, got: ${tokenLabel(head)}",
          tokensLabel(tokens),
          None,
          Map("got" -> tokenLabel(head), "expected" -> "variable identifier")
        ))
      case Nil =>
        throw QueryException(QueryError.ParseError(
          "Expected variable identifier, got end of input",
          "",
          None,
          Map("expected" -> "variable identifier")
        ))

  /** Parse optional answer variables: (y₁, …, yₘ) */
  private def parseAnswerVars(tokens: List[Token]): (List[String], List[Token]) =
    tokens match
      case Token.LParen :: rest =>
        val (vars, afterVars) = parseVariableList(rest)
        val afterClose = expect(Token.RParen, afterVars, "end of answer variables")
        (vars, afterClose)
      case _ =>
        (Nil, tokens) // No answer variables (Boolean query)

  /** Parse comma-separated list of variables. */
  private def parseVariableList(tokens: List[Token]): (List[String], List[Token]) =
    def loop(tokens: List[Token], acc: List[String]): (List[String], List[Token]) =
      tokens match
        case Token.Word(v) :: Token.Comma :: rest if isIdentifier(v) =>
          loop(rest, acc :+ v)
        case Token.Word(v) :: rest if isIdentifier(v) =>
          (acc :+ v, rest)
        case Token.RParen :: _ =>
          (acc, tokens) // Empty list or end of list
        case head :: _ =>
          throw QueryException(QueryError.ParseError(
            s"Expected variable in list, got: ${tokenLabel(head)}",
            tokensLabel(tokens),
            None,
            Map("got" -> tokenLabel(head), "expected" -> "variable identifier")
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

  /** Expect a specific token — OCaml pattern from fol.ml. */
  private def expect(expected: Token, tokens: List[Token], context: String): List[Token] =
    tokens match
      case `expected` :: rest => rest
      case actual :: _ =>
        throw QueryException(QueryError.ParseError(
          s"Expected '${tokenLabel(expected)}' before $context, got '${tokenLabel(actual)}'",
          tokensLabel(tokens),
          None,
          Map("expected" -> tokenLabel(expected),
              "got"      -> tokenLabel(actual),
              "context"  -> context)
        ))
      case Nil =>
        throw QueryException(QueryError.ParseError(
          s"Expected '${tokenLabel(expected)}' before $context, got end of input",
          "",
          None,
          Map("expected" -> tokenLabel(expected), "context" -> context)
        ))

  /** Parse integer token (a `Word` whose payload is all digits). */
  private def parseInteger(tokens: List[Token], context: String): (Int, List[Token]) =
    tokens match
      case Token.Word(num) :: rest if num.nonEmpty && num.forall(_.isDigit) =>
        try
          (num.toInt, rest)
        catch
          case _: NumberFormatException =>
            throw QueryException(QueryError.ParseError(
              s"Integer out of range for $context: $num",
              tokensLabel(tokens),
              None,
              Map("value" -> num, "context" -> context)
            ))
      case head :: _ =>
        throw QueryException(QueryError.ParseError(
          s"Expected integer for $context, got: ${tokenLabel(head)}",
          tokensLabel(tokens),
          None,
          Map("got" -> tokenLabel(head), "expected" -> "integer", "context" -> context)
        ))
      case Nil =>
        throw QueryException(QueryError.ParseError(
          s"Expected integer for $context, got end of input",
          "",
          None,
          Map("expected" -> "integer", "context" -> context)
        ))

  /** Render the operator slot inside `Q[…]` as a string for downstream matching.
    *
    * The bracketed operator may lex as a [[Token.Word]] (`~`, but `~` is in
    * `symbolic`, so this is rare — kept defensively), a [[Token.OpSym]]
    * (`>=`, `<=`, `~`, `~#`), or a single non-Western glyph wrapped as
    * [[Token.OpSym]] (e.g. `≥`, `≤`).
    */
  private def opLabel(t: Token): String = t match
    case Token.Word(s)  => s
    case Token.OpSym(s) => s
    case other          => tokenLabel(other)

  /** Check if string is a valid identifier (alphanumeric, starts with letter). */
  private def isIdentifier(s: String): Boolean =
    s.nonEmpty && s.head.isLetter && s.forall(c => c.isLetterOrDigit || c == '_')

  /** Check if string is numeric (integer or decimal). */
  private def isNumeric(s: String): Boolean =
    util.StringUtil.isNumeric(s) || util.StringUtil.isDecimalLiteral(s)

  /** Merge `Word(digits)` `Dot` `Word(digits)` triples into a single
    * `Word("d1.d2")` decimal token.
    *
    * The OCaml-ported lexer would historically classify `.` as a symbolic
    * character, splitting `0.05` into three tokens. Post-D1 the lexer emits
    * `[Token.Word("0"), Token.Dot, Token.Word("05")]`; this post-processor
    * collapses such triples to `[Token.Word("0.05")]` so [[TermParser]]'s
    * `isConstName` recognises the merged form.
    *
    * Only digit-dot-digit triples are merged; other token sequences are
    * unchanged.
    */
  private def mergeDecimalTokens(tokens: List[Token]): List[Token] =
    tokens match
      case Token.Word(a) :: Token.Dot :: Token.Word(b) :: rest
        if a.nonEmpty && a.forall(_.isDigit) && b.nonEmpty && b.forall(_.isDigit) =>
        Token.Word(s"$a.$b") :: mergeDecimalTokens(rest)
      case t :: rest => t :: mergeDecimalTokens(rest)
      case Nil       => Nil
