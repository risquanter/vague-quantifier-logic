package parser

import util.StringUtil.*
import lexer.{Lexer, Token}
import parser.Combinators.*

/** Simple algebraic expression example from intro.ml
  *
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
    * Grammar (in BNF-like notation):
    * expression ::= product ('+' product)*
    * product    ::= atom ('*' atom)*
    * atom       ::= variable | constant | '(' expression ')'
    */

object SimpleExprParser:
  import Expr.*

  /** Parse arithmetic expression with precedence: * binds tighter than + */
  def parseExpression(inp: List[Token]): ParseResult[Expr] =
    parseRightInfix(Token.OpSym("+"), (e1: Expr, e2: Expr) => Add(e1, e2))(parseProduct)(inp)

  /** Parse multiplication (higher precedence than addition) */
  def parseProduct(inp: List[Token]): ParseResult[Expr] =
    parseRightInfix(Token.OpSym("*"), (e1: Expr, e2: Expr) => Mul(e1, e2))(parseAtom)(inp)

  /** Parse atomic expression: variable, constant, or parenthesized expression */
  def parseAtom(inp: List[Token]): ParseResult[Expr] =
    inp match
      case Nil =>
        throw new Exception("Expected an expression at end of input")

      case Token.LParen :: rest =>
        // Parse parenthesized expression
        parseBracketed(parseExpression, Token.RParen)(rest)

      case Token.Word(tok) :: rest =>
        // All-numeric → Const, otherwise Var.
        if tok.forall(numeric) then
          (Const(tok.toInt), rest)
        else
          (Var(tok), rest)

      case other :: _ =>
        throw new Exception(s"Expected variable, constant, or '(', got: ${tokenLabel(other)}")

  /** Generic function to impose lexing and exhaustion checking on a parser */
  def makeParser(pfn: List[Token] => ParseResult[Expr])(s: String): Expr =
    val tokens = Lexer.lex(explode(s))
    val (expr, rest) = pfn(tokens)
    if rest.isEmpty then
      expr
    else
      throw new Exception(s"Unparsed input: ${tokensLabel(rest)}")

  /** Default parser for expressions (convenience method) */
  def parse(s: String): Expr =
    makeParser(parseExpression)(s)
