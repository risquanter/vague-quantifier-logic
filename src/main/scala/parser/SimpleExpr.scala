package parser

import util.StringUtil.*
import lexer.Lexer.*
import parser.Combinators.*

/** Simple algebraic expression example from intro.ml
  * 
  * This is a learning exercise before tackling FOL parsing.
  * Demonstrates precedence handling with right-associative operators.
  * 
  * OCaml type definition:
  *   type expression =
  *      Var of string
  *    | Const of int
  *    | Add of expression * expression
  *    | Mul of expression * expression
  */
enum Expr:
  case Var(name: String)
  case Const(value: Int)
  case Add(left: Expr, right: Expr)
  case Mul(left: Expr, right: Expr)


  /** 
    * 
    * Grammar (in BNF-like notation):
    * expression ::= product ('+' product)*
    * product    ::= atom ('*' atom)*
    * atom       ::= variable | constant | '(' expression ')'
    */
    
object SimpleExprParser:
  import Expr.*
  
  /** Parse arithmetic expression with precedence: * binds tighter than +
    * 
    * Uses RIGHT ASSOCIATIVITY for +
    * Example: 1 + 2 + 3 = 1 + (2 + 3) = Add(1, Add(2, 3))
    * 
    * Note: We call parseProduct as the subparser (higher precedence)
    */
  def parseExpression(inp: List[String]): ParseResult[Expr] =
    parseRightInfix("+", (e1: Expr, e2: Expr) => Add(e1, e2))(parseProduct)(inp)
  
  /** Parse multiplication (higher precedence than addition)
    * 
    * Uses RIGHT ASSOCIATIVITY for *
    * Example: 2 * 3 * 4 = 2 * (3 * 4) = Mul(2, Mul(3, 4))
    * 
    * Note: We call parseAtom as the subparser (base case)
    */
  def parseProduct(inp: List[String]): ParseResult[Expr] =
    parseRightInfix("*", (e1: Expr, e2: Expr) => Mul(e1, e2))(parseAtom)(inp)
  
  /** Parse atomic expression: variable, constant, or parenthesized expression
    * 
    * Three cases:
    * 1. Empty input -> error
    * 2. Opening paren -> parse expression, expect closing paren
    * 3. Token -> if all digits, parse as Const, otherwise Var
    */
  def parseAtom(inp: List[String]): ParseResult[Expr] =
    inp match
      case Nil =>
        throw new Exception("Expected an expression at end of input")
      
      case "(" :: rest =>
        // Parse parenthesized expression
        parseBracketed(parseExpression, ")")(rest)
      
      case tok :: rest =>
        // Check if token is all numeric
        if tok.forall(numeric) then
          (Const(tok.toInt), rest)
        else
          (Var(tok), rest)
  
  /** Generic function to impose lexing and exhaustion checking on a parser
    * 
    * This is a wrapper that:
    * 1. Converts string to character list (explode)
    * 2. Tokenizes (lex)
    * 3. Parses with given parser function
    * 4. Checks that all input was consumed
    */
  def makeParser(pfn: List[String] => ParseResult[Expr])(s: String): Expr =
    val tokens = lex(explode(s)) // TODO: refactor to use Scala collections
    val (expr, rest) = pfn(tokens)
    if rest.isEmpty then
      expr
    else
      throw new Exception(s"Unparsed input: ${rest.mkString(" ")}")
  
  /** Default parser for expressions (convenience method)*/
  def parse(s: String): Expr =
    makeParser(parseExpression)(s)
