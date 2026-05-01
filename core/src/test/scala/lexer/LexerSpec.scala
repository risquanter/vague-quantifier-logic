package lexer

import munit.FunSuite
import util.StringUtil.*
import Lexer.*
import Token.*

class LexerSpec extends FunSuite:

  // ── lexwhile (unchanged primitive) ──────────────────────────────────

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

  // ── lex: existing behaviour, expressed in Token ADT ─────────────────

  test("lex: simple arithmetic expression") {
    assertEquals(lex(explode("x + 1")), List(Word("x"), OpSym("+"), Word("1")))
  }

  test("lex: complex expression from OCaml example") {
    assertEquals(
      lex(explode("2*((var_1 + x') + 11)")),
      List(Word("2"), OpSym("*"), LParen, LParen, Word("var_1"), OpSym("+"),
           Word("x'"), RParen, OpSym("+"), Word("11"), RParen)
    )
  }

  test("lex: skips leading whitespace") {
    assertEquals(lex(explode("  x + 1")), List(Word("x"), OpSym("+"), Word("1")))
  }

  test("lex: handles multiple spaces") {
    assertEquals(lex(explode("x   +    1")), List(Word("x"), OpSym("+"), Word("1")))
  }

  test("lex: handles tabs and newlines") {
    assertEquals(lex(explode("x\t+\n1")), List(Word("x"), OpSym("+"), Word("1")))
  }

  test("lex: empty string") {
    assertEquals(lex(explode("")), Nil)
  }

  test("lex: only whitespace") {
    assertEquals(lex(explode("   \t\n  ")), Nil)
  }

  test("lex: single character") {
    assertEquals(lex(explode("x")), List(Word("x")))
  }

  test("lex: symbolic operators") {
    assertEquals(
      lex(explode("+ - * / < > = <==>")),
      List(OpSym("+"), OpSym("-"), OpSym("*"), OpSym("/"),
           OpSym("<"), OpSym(">"), OpSym("="), OpSym("<==>"))
    )
  }

  test("lex: mixed alphanumeric and symbolic") {
    assertEquals(
      lex(explode("foo + bar * baz")),
      List(Word("foo"), OpSym("+"), Word("bar"), OpSym("*"), Word("baz"))
    )
  }

  test("lex: parentheses are dedicated punctuation tokens") {
    assertEquals(
      lex(explode("(x + y)")),
      List(LParen, Word("x"), OpSym("+"), Word("y"), RParen)
    )
  }

  test("lex: brackets and punctuation") {
    assertEquals(
      lex(explode("f(x, y)")),
      List(Word("f"), LParen, Word("x"), Comma, Word("y"), RParen)
    )
  }

  test("lex: FOL formula from OCaml example") {
    assertEquals(
      lex(explode("forall x. P(x)")),
      List(Word("forall"), Word("x"), Dot, Word("P"), LParen, Word("x"), RParen)
    )
  }

  test("lex: complex FOL formula") {
    assertEquals(
      lex(explode("forall x y. exists z. x < z /\\ y < z")),
      List(Word("forall"), Word("x"), Word("y"), Dot,
           Word("exists"), Word("z"), Dot,
           Word("x"), OpSym("<"), Word("z"), OpSym("/\\"),
           Word("y"), OpSym("<"), Word("z"))
    )
  }

  test("lex: logical operators") {
    assertEquals(
      lex(explode("p /\\ q \\/ ~r ==> s <=> t")),
      List(Word("p"), OpSym("/\\"), Word("q"), OpSym("\\/"), OpSym("~"),
           Word("r"), OpSym("==>"), Word("s"), OpSym("<=>"), Word("t"))
    )
  }

  test("lex: identifiers with apostrophes") {
    assertEquals(lex(explode("x' y'' z'''")), List(Word("x'"), Word("y''"), Word("z'''")))
  }

  test("lex: identifiers with underscores") {
    assertEquals(lex(explode("var_1 foo_bar_baz")), List(Word("var_1"), Word("foo_bar_baz")))
  }

  test("lexString convenience function") {
    assertEquals(lexString("x + 1"), List(Word("x"), OpSym("+"), Word("1")))
  }

  test("lex: OCaml conditional example") {
    assertEquals(
      lex(explode("if (*p1-- == *p2++) then f() else g()")),
      List(Word("if"), LParen, OpSym("*"), Word("p1"), OpSym("--"),
           OpSym("=="), OpSym("*"), Word("p2"), OpSym("++"), RParen,
           Word("then"), Word("f"), LParen, RParen,
           Word("else"), Word("g"), LParen, RParen)
    )
  }

  test("lex: braces are dedicated punctuation tokens") {
    assertEquals(
      lex(explode("Q[op]^{1/2}")),
      List(Word("Q"), LBracket, Word("op"), RBracket, OpSym("^"),
           LBrace, Word("1"), OpSym("/"), Word("2"), RBrace)
    )
  }

  // ── L1–L6: new behaviour for D1 fix (Plan §5.1) ─────────────────────

  test("L1 — quoted string literal yields a single StringLit with quotes stripped") {
    assertEquals(lex(explode("\"IT Risk\"")), List(StringLit("IT Risk")))
  }

  test("L2 — empty quoted literal yields StringLit(\"\")") {
    assertEquals(lex(explode("\"\"")), List(StringLit("")))
  }

  test("L3 — unterminated string literal raises LexerError") {
    intercept[LexerError] { lex(explode("\"abc")) }
  }

  test("L4 — newline inside string literal raises LexerError") {
    intercept[LexerError] { lex(explode("\"a\nb\"")) }
  }

  test("L5 — each punctuation char emits its dedicated Token case") {
    assertEquals(
      lex(explode("()[]{},.")),
      List(LParen, RParen, LBracket, RBracket, LBrace, RBrace, Comma, Dot)
    )
  }

  test("L6 — quoted literal embedded in a larger query lexes as one token") {
    assertEquals(
      lex(explode("leaf_descendant_of(x, \"IT Risk\")")),
      List(Word("leaf_descendant_of"), LParen, Word("x"), Comma,
           StringLit("IT Risk"), RParen)
    )
  }

  test("L6b — quoted literal preserves all internal characters verbatim") {
    assertEquals(
      lex(explode("\"a, b. (c) [d] {e} ==> f\"")),
      List(StringLit("a, b. (c) [d] {e} ==> f"))
    )
  }
