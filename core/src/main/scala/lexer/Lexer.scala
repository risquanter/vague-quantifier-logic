package lexer

import util.StringUtil.*

/** Signals an unrecoverable lexer error.
  *
  * Used for malformed `"…"`-delimited string literals (unterminated; embedded
  * newline). Mirrors the exception-based error idiom established by the
  * OCaml-ported parser combinators (ADR-002, ADR-007 C2, ADR-012 §"Relationship
  * to ADR-007"). Distinct from `Combinators.ParseFailure` so the public
  * `parse` boundary in `VagueQueryParser` can map it to a structured
  * `QueryError.ParseError` without conflating with parser-level backtracking.
  */
class LexerError(msg: String) extends Exception(msg)

/** Lexical analysis following OCaml style from intro.ml
  *
  * Converts character streams into [[Token]] streams.
  * Key OCaml technique: accumulator-based recursion with token threading.
  *
  * Element-type refinement `string` ⇒ [[Token]] is permitted under fol-engine
  * ADR-007 C13: combinator shape preserved (signature arity `List[Char] =>
  * List[Token]`, exception backtracking, mutual recursion); each `Token` case
  * documents the OCaml `string` it replaces (see [[Token]]); downstream parser
  * pattern matches expand 1:1 from string-match arms to ADT-match arms.
  */
object Lexer:

  /** Consume characters while predicate holds (OCaml: lexwhile)
    *
    * OCaml implementation:
    *   let rec lexwhile prop inp =
    *     match inp with
    *       c::cs when prop c -> let tok,rest = lexwhile prop cs in c^tok,rest
    *     | _ -> "",inp
    *
    * Unchanged primitive — operates on `(String, List[Char])` regardless of
    * the outer `lex` return type (ADR-007 C10 preserved at the character level).
    */
  def lexwhile(prop: Char => Boolean, inp: List[Char]): (String, List[Char]) =
    inp match
      case c :: cs if prop(c) =>
        val (tok, rest) = lexwhile(prop, cs)
        (c.toString + tok, rest)
      case _ =>
        ("", inp)

  /** Single-character punctuation alphabet (closed, finite).
    *
    * Each char in this set maps to a dedicated [[Token]] case via
    * [[punctuationToken]]. Resolves the longstanding overlap noted in
    * `StringUtil`: `.` and `,` would otherwise be classified as symbolic /
    * fall-through and merged into multi-char symbolic runs.
    *
    * `;` deliberately stays out — it is not part of any current grammar arm
    * and remains a symbolic-run token (`Token.OpSym(";")`) for backwards
    * compatibility.
    */
  private val punctChars: Set[Char] = Set('(', ')', '[', ']', '{', '}', ',', '.')

  private def punctuationToken(c: Char): Token = c match
    case '(' => Token.LParen
    case ')' => Token.RParen
    case '[' => Token.LBracket
    case ']' => Token.RBracket
    case '{' => Token.LBrace
    case '}' => Token.RBrace
    case ',' => Token.Comma
    case '.' => Token.Dot

  /** Consume a `"…"`-delimited string literal up to (but not including) the
    * closing quote. No backslash escaping (per plan D-4); embedded newlines
    * raise [[LexerError]]; missing closing quote raises [[LexerError]].
    *
    * Caller has already consumed the opening `"`. Returns `(content, rest)`
    * where `rest` begins immediately after the closing `"`.
    */
  private def lexStringLit(inp: List[Char]): (String, List[Char]) =
    inp match
      case Nil =>
        throw new LexerError("Unterminated string literal: missing closing '\"'")
      case '\n' :: _ =>
        throw new LexerError("Unterminated string literal: newline before closing '\"'")
      case '"' :: rest =>
        ("", rest)
      case c :: cs =>
        val (tail, rest) = lexStringLit(cs)
        (c.toString + tail, rest)

  /** Main lexer function (OCaml: lex)
    *
    * OCaml implementation:
    *   let rec lex inp =
    *     match snd(lexwhile space inp) with
    *       [] -> []
    *     | c::cs -> let prop = if alphanumeric(c) then alphanumeric
    *                           else if symbolic(c) then symbolic
    *                           else fun c -> false in
    *                let toktl,rest = lexwhile prop cs in
    *                (c^toktl)::lex rest
    *
    * Scala extensions over the OCaml original (admissible under ADR-007 C13):
    *  - punctuation chars in [[punctChars]] short-circuit to dedicated [[Token]]
    *    cases before the symbolic check (resolves the `.`/`,` overlap noted in
    *    `StringUtil.symbolic`);
    *  - `"` opens a [[Token.StringLit]] branch consuming verbatim until the
    *    matching `"` (no escapes; newline ⇒ [[LexerError]]).
    */
  def lex(inp: List[Char]): List[Token] =
    // Skip whitespace: snd(lexwhile space inp)
    val (_, afterSpace) = lexwhile(space, inp)

    afterSpace match
      case Nil => Nil

      case '"' :: cs =>
        // String-literal branch (NEW per D1 fix; no OCaml counterpart).
        val (content, rest) = lexStringLit(cs)
        Token.StringLit(content) :: lex(rest)

      case c :: cs if punctChars.contains(c) =>
        // Single-character punctuation — emit dedicated Token case.
        punctuationToken(c) :: lex(cs)

      case c :: cs =>
        // OCaml original: alphanumeric run, symbolic run, or single-char fallthrough.
        if alphanumeric(c) then
          val (toktl, rest) = lexwhile(alphanumeric, cs)
          Token.Word(c.toString + toktl) :: lex(rest)
        else if symbolic(c) then
          val (toktl, rest) = lexwhile(symbolic, cs)
          Token.OpSym(c.toString + toktl) :: lex(rest)
        else
          // Any other character — emit as a single-char OpSym to preserve the
          // OCaml "permissive" fallthrough for unexpected input. Reachable only
          // for chars outside alphanumeric ∪ symbolic ∪ punctuation ∪ space ∪ {'"'}.
          Token.OpSym(c.toString) :: lex(cs)

  /** Convenience function: lex from string directly */
  def lexString(s: String): List[Token] =
    lex(explode(s))
