package parser

import logic.{FOL, Formula}
import lexer.{Lexer, Token}
import lexer.Lexer.lex
import parser.Combinators.tokensLabel
import util.StringUtil.explode

/** Complete FOL parser API
  *
  * Public entry point that wires together the lexer, term parser, formula
  * parser, and FOL atom parser.
  *
  * OCaml equivalent:
  *   let parse = make_parser (parse_formula (parse_infix_atom,parse_atom) [])
  *   let default_parser = parse
  */
object FOLParser:

  /** Parse FOL formula from string. */
  def parse(s: String): Formula[FOL] =
    FOLAtomParser.parseFromString(s)

  /** Alternative name for parse (for familiarity with OCaml code) */
  def defaultParser(s: String): Formula[FOL] = parse(s)

  /** Parse from token list (useful for testing or custom lexing) */
  def parseTokens(tokens: List[Token]): (Formula[FOL], List[Token]) =
    FOLAtomParser.parse(tokens)

  /** Parse with custom lexer (for advanced use cases) */
  def parseWithLexer(s: String, lexer: List[Char] => List[Token]): Formula[FOL] =
    val tokens = lexer(explode(s))
    val (result, rest) = parseTokens(tokens)
    if rest.isEmpty then
      result
    else
      throw new Exception(s"Unparsed input: ${tokensLabel(rest)}")
