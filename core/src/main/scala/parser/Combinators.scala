package parser

import lexer.Token

/** Parser combinator infrastructure following OCaml style from formulas.ml
  *
  * Key concept: Parsers consume token lists and return (result, remaining_tokens).
  * This is the foundation of the entire parsing approach.
  *
  * Element-type refinement note (ADR-007 C13): the OCaml original uses
  * `string list`; this Scala port uses `List[Token]` (see [[lexer.Token]]).
  * The combinator *shape* — tuple threading, exception backtracking, mutual
  * recursion — is preserved unchanged.
  */
object Combinators:

  /** Signals a parser backtracking failure — the direct analogue of OCaml's `Failure _`.
    *
    * Thrown internally by `parseInfixAtom` when the next token is not a relational
    * operator, and caught by `parseAtom` / `parseAtomicFormula` to try the next
    * branch. Must NEVER be caught in a broad `case _: Exception` clause — always
    * name this type explicitly so real programming errors (NPE, StackOverflow, etc.)
    * are not silently swallowed as parse failures. See ADR-007 C2.
    */
  class ParseFailure(msg: String) extends Exception(msg)

  /** Parse result type: (parsed_value, remaining_tokens)
    *
    * OCaml uses tuples directly: `'a * string list`.
    * Scala (post-ADR-007 C13): `(A, List[Token])`.
    */
  type ParseResult[A] = (A, List[Token])

  /** Generic iterated infix parser (OCaml: parse_ginfix)
    *
    * OCaml implementation:
    *   let rec parse_ginfix opsym opupdate sof subparser inp =
    *     let e1,inp1 = subparser inp in
    *     if inp1 <> [] & hd inp1 = opsym then
    *        parse_ginfix opsym opupdate (opupdate sof e1) subparser (tl inp1)
    *     else sof e1,inp1
    *
    * @param opsym Token signalling the operator (e.g. `Token.OpSym("+")`)
    */
  def parseGinfix[A](
    opsym: Token,
    opupdate: (A => A, A) => (A => A),
    sof: A => A,
    subparser: List[Token] => ParseResult[A]
  )(inp: List[Token]): ParseResult[A] =
    val (e1, inp1) = subparser(inp)
    if inp1.nonEmpty && inp1.head == opsym then
      parseGinfix(opsym, opupdate, opupdate(sof, e1), subparser)(inp1.tail)
    else
      (sof(e1), inp1)

  /** Parse left-associative infix operator (OCaml: parse_left_infix) */
  def parseLeftInfix[A](
    opsym: Token,
    opcon: (A, A) => A
  )(subparser: List[Token] => ParseResult[A])(inp: List[Token]): ParseResult[A] =
    parseGinfix(
      opsym,
      (f: A => A, e1: A) => ((x: A) => opcon(f(e1), x)),
      identity[A],
      subparser
    )(inp)

  /** Parse right-associative infix operator (OCaml: parse_right_infix) */
  def parseRightInfix[A](
    opsym: Token,
    opcon: (A, A) => A
  )(subparser: List[Token] => ParseResult[A])(inp: List[Token]): ParseResult[A] =
    parseGinfix(
      opsym,
      (f: A => A, e1: A) => ((x: A) => f(opcon(e1, x))),
      identity[A],
      subparser
    )(inp)

  /** Parse comma-separated list (OCaml: parse_list) */
  def parseList[A](
    opsym: Token
  )(subparser: List[Token] => ParseResult[A])(inp: List[Token]): ParseResult[List[A]] =
    parseGinfix[List[A]](
      opsym,
      (f: List[A] => List[A], e1: List[A]) => ((x: List[A]) => f(e1) :+ x.head),
      (x: List[A]) => List(x.head),
      (inp: List[Token]) => {
        val (result, rest) = subparser(inp)
        (List(result), rest)
      }
    )(inp)

  /** Apply function to parse result (OCaml: papply) */
  def papply[A, B](f: A => B)(result: ParseResult[A]): ParseResult[B] =
    val (ast, rest) = result
    (f(ast), rest)

  /** Check if next token matches (OCaml: nextin) */
  def nextin(inp: List[Token], tok: Token): Boolean =
    inp.nonEmpty && inp.head == tok

  /** Parse bracketed expression (OCaml: parse_bracketed) */
  def parseBracketed[A](
    subparser: List[Token] => ParseResult[A],
    cbra: Token
  )(inp: List[Token]): ParseResult[A] =
    val (ast, rest) = subparser(inp)
    if nextin(rest, cbra) then
      (ast, rest.tail)
    else
      throw new ParseFailure(
        s"Closing bracket '${tokenLabel(cbra)}' expected, but got: ${rest.headOption.map(tokenLabel).getOrElse("end of input")}"
      )

  /** Helper to check if token list is empty */
  def isEmpty(inp: List[Token]): Boolean = inp.isEmpty

  /** Helper to get head of token list safely */
  def headOption(inp: List[Token]): Option[Token] = inp.headOption

  /** Render a single [[Token]] back to its surface-syntax string for error
    * messages — preserves the look of OCaml-original `failwith` strings while
    * stripping ADT noise.
    */
  def tokenLabel(t: Token): String = t match
    case Token.Word(s)      => s
    case Token.OpSym(s)     => s
    case Token.StringLit(s) => "\"" + s + "\""
    case Token.LParen       => "("
    case Token.RParen       => ")"
    case Token.LBracket     => "["
    case Token.RBracket     => "]"
    case Token.LBrace       => "{"
    case Token.RBrace       => "}"
    case Token.Comma        => ","
    case Token.Dot          => "."

  /** Render a token stream back to a space-separated string for error messages. */
  def tokensLabel(ts: List[Token]): String =
    ts.map(tokenLabel).mkString(" ")
