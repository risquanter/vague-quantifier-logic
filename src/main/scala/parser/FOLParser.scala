package parser

import logic.{FOL, Formula}
import parser.FOLAtomParser
import util.StringUtil.explode
import lexer.Lexer.lex

/** Complete FOL parser API - Phase 9
  * 
  * This is the public API that users will interact with.
  * It wires together all the parsing components:
  * - Lexer (tokenization)
  * - TermParser (term parsing)
  * - FormulaParser (generic formula parsing)
  * - FOLAtomParser (FOL-specific atom parsing)
  * 
  * OCaml equivalent:
  *   let parse = make_parser (parse_formula (parse_infix_atom,parse_atom) [])
  *   let default_parser = parse
  */
object FOLParser:
  
  /** Parse FOL formula from string
    * 
    * This is the main entry point for parsing FOL formulas.
    * 
    * Example usage:
    *   val formula = FOLParser.parse("forall x. exists y. x < y")
    *   val formula2 = FOLParser.parse("P(x) /\\ Q(y) ==> R(x, y)")
    * 
    * @param s Input string containing FOL formula
    * @return Parsed formula as Formula[FOL]
    * @throws Exception if parsing fails or input is not fully consumed
    */
  def parse(s: String): Formula[FOL] =
    FOLAtomParser.parseFromString(s)
  
  /** Alternative name for parse (for familiarity with OCaml code)
    * 
    * OCaml: let default_parser = parse
    */
  def defaultParser(s: String): Formula[FOL] = parse(s)
  
  /** Parse from token list (useful for testing or custom lexing)
    * 
    * @param tokens Pre-tokenized input
    * @return Tuple of (parsed formula, remaining tokens)
    */
  def parseTokens(tokens: List[String]): (Formula[FOL], List[String]) =
    FOLAtomParser.parse(tokens)
  
  /** Parse with custom lexer (for advanced use cases)
    * 
    * @param s Input string
    * @param lexer Custom lexer function
    * @return Parsed formula
    */
  def parseWithLexer(s: String, lexer: List[Char] => List[String]): Formula[FOL] =
    val tokens = lexer(explode(s))
    val (result, rest) = parseTokens(tokens)
    if rest.isEmpty then
      result
    else
      throw new Exception(s"Unparsed input: ${rest.mkString(" ")}")
