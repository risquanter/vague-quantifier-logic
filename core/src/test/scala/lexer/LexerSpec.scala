package lexer

import munit.FunSuite
import util.StringUtil.*
import Lexer.*

class LexerSpec extends FunSuite:
  
  test("lexwhile consumes characters while predicate holds") {
    val (tok, rest) = lexwhile(numeric, explode("123abc"))
    assertEquals(tok, "123")
    assertEquals(rest, explode("abc"))
  }
  
  test("lexwhile returns empty string when predicate fails immediately") {
    val (tok, rest) = lexwhile(numeric, explode("abc"))
    assertEquals(tok, "")
    assertEquals(rest, explode("abc"))
  }
  
  test("lexwhile consumes all characters when all match") {
    val (tok, rest) = lexwhile(numeric, explode("123"))
    assertEquals(tok, "123")
    assertEquals(rest, Nil)
  }
  
  test("lexwhile with alphanumeric predicate") {
    val (tok, rest) = lexwhile(alphanumeric, explode("foo_bar'123 + x"))
    assertEquals(tok, "foo_bar'123")
    assertEquals(rest, explode(" + x"))
  }
  
  test("lexwhile with symbolic predicate") {
    val (tok, rest) = lexwhile(symbolic, explode("<==> x"))
    assertEquals(tok, "<==>")
    assertEquals(rest, explode(" x"))
  }
  
  test("lex: simple arithmetic expression") {
    val tokens = lex(explode("x + 1"))
    assertEquals(tokens, List("x", "+", "1"))
  }
  
  test("lex: complex expression from OCaml example") {
    val tokens = lex(explode("2*((var_1 + x') + 11)"))
    assertEquals(tokens, List("2", "*", "(", "(", "var_1", "+", "x'",")", "+", "11",  ")"))
  }
  
  test("lex: skips leading whitespace") {
    val tokens = lex(explode("  x + 1"))
    assertEquals(tokens, List("x", "+", "1"))
  }
  
  test("lex: handles multiple spaces") {
    val tokens = lex(explode("x   +    1"))
    assertEquals(tokens, List("x", "+", "1"))
  }
  
  test("lex: handles tabs and newlines") {
    val tokens = lex(explode("x\t+\n1"))
    assertEquals(tokens, List("x", "+", "1"))
  }
  
  test("lex: empty string") {
    val tokens = lex(explode(""))
    assertEquals(tokens, Nil)
  }
  
  test("lex: only whitespace") {
    val tokens = lex(explode("   \t\n  "))
    assertEquals(tokens, Nil)
  }
  
  test("lex: single character") {
    val tokens = lex(explode("x"))
    assertEquals(tokens, List("x"))
  }
  
  test("lex: symbolic operators") {
    val tokens = lex(explode("+ - * / < > = <==>"))
    assertEquals(tokens, List("+", "-", "*", "/", "<", ">", "=", "<==>"))
  }
  
  test("lex: mixed alphanumeric and symbolic") {
    val tokens = lex(explode("foo + bar * baz"))
    assertEquals(tokens, List("foo", "+", "bar", "*", "baz"))
  }
  
  test("lex: parentheses are single char tokens") {
    val tokens = lex(explode("(x + y)"))
    assertEquals(tokens, List("(", "x", "+", "y", ")"))
  }
  
  test("lex: brackets and punctuation") {
    val tokens = lex(explode("f(x, y)"))
    assertEquals(tokens, List("f", "(", "x", ",", "y", ")"))
  }
  
  test("lex: FOL formula from OCaml example") {
    val tokens = lex(explode("forall x. P(x)"))
    assertEquals(tokens, List("forall", "x", ".", "P", "(", "x", ")"))
  }
  
  test("lex: complex FOL formula") {
    val tokens = lex(explode("forall x y. exists z. x < z /\\ y < z"))
    assertEquals(tokens, List("forall", "x", "y", ".", "exists", "z", ".", "x", "<", "z", "/\\", "y", "<", "z"))
  }
  
  test("lex: logical operators") {
    val tokens = lex(explode("p /\\ q \\/ ~r ==> s <=> t"))
    assertEquals(tokens, List("p", "/\\", "q", "\\/", "~", "r", "==>", "s", "<=>", "t"))
  }
  
  test("lex: identifiers with apostrophes") {
    val tokens = lex(explode("x' y'' z'''"))
    assertEquals(tokens, List("x'", "y''", "z'''"))
  }
  
  test("lex: identifiers with underscores") {
    val tokens = lex(explode("var_1 foo_bar_baz"))
    assertEquals(tokens, List("var_1", "foo_bar_baz"))
  }
  
  test("lexString convenience function") {
    val tokens = lexString("x + 1")
    assertEquals(tokens, List("x", "+", "1"))
  }
  
  test("lex: OCaml conditional example") {
    val tokens = lex(explode("if (*p1-- == *p2++) then f() else g()"))
    assertEquals(tokens, List("if", "(", "*", "p1", "--", "==", "*", "p2", "++", ")", "then", "f", "(", ")", "else", "g", "(", ")"))
  }
