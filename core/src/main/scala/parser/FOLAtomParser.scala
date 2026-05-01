package parser

import logic.{FOL, Term, Formula}
import logic.Formula.*
import lexer.Token
import parser.Combinators.*
import parser.Combinators.ParseFailure
import parser.TermParser.*
import parser.FormulaParser.AtomParser
import util.StringUtil.*

/** FOL atom parser from fol.ml
  *
  * Parses FOL atoms (predicates and relations):
  * - Infix relations: x < y, x = y, x <= y
  * - Predicates: P(x), Q(x, y, z)
  * - Nullary predicates: P
  *
  * Element-type refinement note (ADR-007 C13): infix relational operators
  * arrive as [[lexer.Token.OpSym]]; predicate names arrive as [[lexer.Token.Word]].
  */
object FOLAtomParser:

  /** The set of relational-operator surface forms recognised by `parseInfixAtom`. */
  private val relOps: Set[String] = Set("=", "<", "<=", ">", ">=")

  /** Parse infix relational atom
    *
    * OCaml implementation:
    *   let parse_infix_atom vs inp =
    *     let tm,rest = parse_term vs inp in
    *     if exists (nextin rest) ["="; "<"; "<="; ">"; ">="] then
    *           papply (fun tm' -> Atom(R(hd rest,[tm;tm'])))
    *                  (parse_term vs (tl rest))
    *     else failwith ""
    */
  def parseInfixAtom(vs: List[String], inp: List[Token]): ParseResult[FOL] =
    val (tm, rest) = parseTerm(vs)(inp)

    rest match
      case Token.OpSym(op) :: tl if relOps.contains(op) =>
        papply((tm2: Term) => FOL(op, List(tm, tm2)))(parseTerm(vs)(tl))
      case _ =>
        throw ParseFailure("Not an infix atom")

  /** Parse general atom (predicate)
    *
    * OCaml implementation:
    *   let parse_atom vs inp =
    *     try parse_infix_atom vs inp with Failure _ ->
    *     match inp with
    *     | p::"("::")"::rest -> Atom(R(p,[])),rest
    *     | p::"("::rest ->
    *         papply (fun args -> Atom(R(p,args)))
    *                (parse_bracketed (parse_list "," (parse_term vs)) ")" rest)
    *     | p::rest when p <> "(" -> Atom(R(p,[])),rest
    *     | _ -> failwith "parse_atom"
    */
  def parseAtom(vs: List[String], inp: List[Token]): ParseResult[FOL] =
    try
      parseInfixAtom(vs, inp)
    catch
      case _: ParseFailure =>
        inp match
          case Nil =>
            throw new Exception("parse_atom: expected atom")

          case Token.Word(p) :: Token.LParen :: Token.RParen :: rest =>
            // Predicate with no arguments: P()
            (FOL(p, List()), rest)

          case Token.Word(p) :: Token.LParen :: rest =>
            // Predicate with arguments: P(x, y, z)
            papply((args: List[Term]) => FOL(p, args))(
              parseBracketed(
                parseList(Token.Comma)(parseTerm(vs)),
                Token.RParen
              )(rest)
            )

          case Token.Word(p) :: rest =>
            // Nullary predicate: P (no parentheses, no LParen check needed —
            // LParen pattern would have matched the previous arms first).
            (FOL(p, List()), rest)

          case _ =>
            throw new Exception("parse_atom")

  /** Complete FOL formula parser
    *
    * OCaml implementation:
    *   let parse = make_parser
    *     (parse_formula (parse_infix_atom,parse_atom) [])
    */
  def parse(tokens: List[Token]): ParseResult[Formula[FOL]] =
    FormulaParser.parse(parseInfixAtom, parseAtom)(tokens)

  /** Parse FOL formula from string (with lexing and exhaustion checking) */
  def parseFromString(s: String): Formula[FOL] =
    import lexer.Lexer.lex
    val tokens = lex(explode(s))
    val (result, rest) = parse(tokens)
    if rest.isEmpty then
      result
    else
      throw new Exception(s"Unparsed input: ${tokensLabel(rest)}")
