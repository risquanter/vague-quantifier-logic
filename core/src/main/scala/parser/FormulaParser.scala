package parser

import logic.Formula
import logic.Formula.*
import lexer.Token
import parser.Combinators.*

/** Generic formula parser from formulas.ml
  *
  * Parametrized by atom parsers:
  * - ifn: infix atom parser (for things like "x < y")
  * - afn: general atom parser (for things like "P(x)")
  *
  * Element-type refinement: token-list element type is now [[lexer.Token]]
  * (ADR-007 C13). Variable scope `vs: List[String]` remains a list of names.
  */
object FormulaParser:

  /** Type alias for atom parsers
    *
    * - vs: List of bound variable names
    * - inp: Token stream
    * Returns: (parsed atom, remaining tokens)
    */
  type AtomParser[A] = (List[String], List[Token]) => ParseResult[A]

  /** Parse atomic formula (base case for formula parsing)
    *
    * OCaml implementation:
    *   let rec parse_atomic_formula (ifn,afn) vs inp =
    *     match inp with
    *       [] -> failwith "formula expected"
    *     | "false"::rest -> False,rest
    *     | "true"::rest -> True,rest
    *     | "("::rest -> (try ifn vs inp with Failure _ ->
    *                     parse_bracketed (parse_formula (ifn,afn) vs) ")" rest)
    *     | "~"::rest -> papply (fun p -> Not p)
    *                           (parse_atomic_formula (ifn,afn) vs rest)
    *     | "forall"::x::rest ->
    *           parse_quant (ifn,afn) (x::vs) (fun (x,p) -> Forall(x,p)) x rest
    *     | "exists"::x::rest ->
    *           parse_quant (ifn,afn) (x::vs) (fun (x,p) -> Exists(x,p)) x rest
    *     | _ -> afn vs inp
    */
  def parseAtomicFormula[A](
    ifn: AtomParser[A],
    afn: AtomParser[A]
  )(vs: List[String])(inp: List[Token]): ParseResult[Formula[A]] =
    inp match
      case Nil =>
        throw new Exception("formula expected")

      case Token.Word("false") :: rest =>
        (False, rest)

      case Token.Word("true") :: rest =>
        (True, rest)

      case Token.LParen :: rest =>
        // Try infix atom parser first; on backtracking, parse bracketed formula.
        try
          papply((a: A) => Atom(a))(ifn(vs, inp))
        catch
          case _: ParseFailure =>
            parseBracketed(parseFormula(ifn, afn)(vs), Token.RParen)(rest)

      case Token.OpSym("~") :: rest =>
        // Negation
        papply((p: Formula[A]) => Not(p))(parseAtomicFormula(ifn, afn)(vs)(rest))

      case Token.Word("forall") :: Token.Word(x) :: rest =>
        parseQuant(ifn, afn)(x :: vs)((x: String, p: Formula[A]) => Forall(x, p))(x)(rest)

      case Token.Word("exists") :: Token.Word(x) :: rest =>
        parseQuant(ifn, afn)(x :: vs)((x: String, p: Formula[A]) => Exists(x, p))(x)(rest)

      case _ =>
        // Default: parse as atom
        papply((a: A) => Atom(a))(afn(vs, inp))

  /** Parse quantified formula body
    *
    * OCaml implementation:
    *   and parse_quant (ifn,afn) vs qcon x inp =
    *      match inp with
    *        [] -> failwith "Body of quantified term expected"
    *      | y::rest ->
    *           papply (fun fm -> qcon(x,fm))
    *                  (if y = "." then parse_formula (ifn,afn) vs rest
    *                   else parse_quant (ifn,afn) (y::vs) qcon y rest)
    *
    * Token-shape note: the OCaml dot literal `"."` is now [[lexer.Token.Dot]];
    * additional bound variables arrive as [[lexer.Token.Word]].
    */
  def parseQuant[A](
    ifn: AtomParser[A],
    afn: AtomParser[A]
  )(vs: List[String])(
    qcon: (String, Formula[A]) => Formula[A]
  )(x: String)(inp: List[Token]): ParseResult[Formula[A]] =
    inp match
      case Nil =>
        throw new Exception("Body of quantified term expected")

      case Token.Dot :: rest =>
        // Dot marks end of variables, parse body
        papply((fm: Formula[A]) => qcon(x, fm))(parseFormula(ifn, afn)(vs)(rest))

      case Token.Word(y) :: rest =>
        // Another bound variable, recurse
        papply((fm: Formula[A]) => qcon(x, fm))(
          parseQuant(ifn, afn)(y :: vs)(qcon)(y)(rest)
        )

      case other :: _ =>
        throw new Exception(
          s"Body of quantified term expected, got: ${tokenLabel(other)}"
        )

  /** Parse complete formula with all logical operators.
    *
    * Operator chain (RIGHT ASSOCIATIVITY) by precedence:
    * 1. /\  (and)     - highest precedence
    * 2. \/  (or)
    * 3. ==> (implies)
    * 4. <=> (iff)     - lowest precedence
    */
  def parseFormula[A](
    ifn: AtomParser[A],
    afn: AtomParser[A]
  )(vs: List[String])(inp: List[Token]): ParseResult[Formula[A]] =
    parseRightInfix(Token.OpSym("<=>"), (p: Formula[A], q: Formula[A]) => Iff(p, q))(
      parseRightInfix(Token.OpSym("==>"), (p: Formula[A], q: Formula[A]) => Imp(p, q))(
        parseRightInfix(Token.OpSym("\\/"), (p: Formula[A], q: Formula[A]) => Or(p, q))(
          parseRightInfix(Token.OpSym("/\\"), (p: Formula[A], q: Formula[A]) => And(p, q))(
            parseAtomicFormula(ifn, afn)(vs)
          )
        )
      )
    )(inp)

  /** Convenience wrapper: parse formula from token list with empty variable context. */
  def parse[A](
    ifn: AtomParser[A],
    afn: AtomParser[A]
  )(tokens: List[Token]): ParseResult[Formula[A]] =
    parseFormula(ifn, afn)(List())(tokens)
