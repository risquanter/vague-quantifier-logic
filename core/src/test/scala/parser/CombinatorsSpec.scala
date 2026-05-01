package parser

import munit.FunSuite
import lexer.Token
import lexer.Token.*
import Combinators.*

class CombinatorsSpec extends FunSuite:

  // Token literal helpers — keep tests readable.
  private def w(s: String): Token = Word(s)
  private def op(s: String): Token = OpSym(s)

  // Simple test parser: parses a single Word token as an Int
  def parseNum(inp: List[Token]): ParseResult[Int] =
    inp match
      case Word(n) :: rest => (n.toInt, rest)
      case Nil             => throw new Exception("Expected number")
      case other :: _      => throw new Exception(s"Expected number, got: $other")

  test("papply transforms parse result") {
    val result = parseNum(List(w("42"), w("x")))
    val doubled = papply((n: Int) => n * 2)(result)
    assertEquals(doubled, (84, List(w("x"))))
  }

  test("nextin checks next token") {
    assert(nextin(List(op("+"), w("1")), op("+")))
    assert(!nextin(List(op("-"), w("1")), op("+")))
    assert(!nextin(List(), op("+")))
  }

  test("parseBracketed success") {
    val result = parseBracketed(parseNum, RParen)(List(w("42"), RParen, w("x")))
    assertEquals(result, (42, List(w("x"))))
  }

  test("parseBracketed fails without closing bracket") {
    intercept[Exception] {
      parseBracketed(parseNum, RParen)(List(w("42"), w("x")))
    }
  }

  test("parseLeftInfix: simple addition") {
    val result = parseLeftInfix(op("+"), (a: Int, b: Int) => a + b)(parseNum)(List(w("1"), op("+"), w("2")))
    assertEquals(result._1, 3)
  }

  test("parseLeftInfix: left associativity (10 - 3 - 2)") {
    val tokens = List(w("10"), op("-"), w("3"), op("-"), w("2"))
    val result = parseLeftInfix(op("-"), (a: Int, b: Int) => a - b)(parseNum)(tokens)
    assertEquals(result._1, 5) // (10 - 3) - 2 = 5
  }

  test("parseRightInfix: right associativity (2 ^ 3 ^ 2)") {
    def pow(a: Int, b: Int): Int = Math.pow(a.toDouble, b.toDouble).toInt
    val tokens = List(w("2"), op("^"), w("3"), op("^"), w("2"))
    val result = parseRightInfix(op("^"), pow)(parseNum)(tokens)
    assertEquals(result._1, 512)
  }

  test("parseRightInfix: simple case") {
    val tokens = List(w("1"), op("+"), w("2"))
    val result = parseRightInfix(op("+"), (a: Int, b: Int) => a + b)(parseNum)(tokens)
    assertEquals(result._1, 3)
  }

  test("parseList: comma-separated numbers") {
    val tokens = List(w("1"), Comma, w("2"), Comma, w("3"), w("x"))
    val result = parseList(Comma)(parseNum)(tokens)
    assertEquals(result._1, List(1, 2, 3))
    assertEquals(result._2, List(w("x")))
  }

  test("parseList: single element") {
    val tokens = List(w("42"), w("x"))
    val result = parseList(Comma)(parseNum)(tokens)
    assertEquals(result._1, List(42))
    assertEquals(result._2, List(w("x")))
  }

  test("parseList: empty list fails") {
    intercept[Exception] {
      parseList(Comma)(parseNum)(List())
    }
  }

  test("parseLeftInfix: no operator (single element)") {
    val tokens = List(w("42"), w("x"))
    val result = parseLeftInfix(op("+"), (a: Int, b: Int) => a + b)(parseNum)(tokens)
    assertEquals(result._1, 42)
    assertEquals(result._2, List(w("x")))
  }

  test("parseRightInfix: no operator (single element)") {
    val tokens = List(w("42"), w("x"))
    val result = parseRightInfix(op("+"), (a: Int, b: Int) => a + b)(parseNum)(tokens)
    assertEquals(result._1, 42)
    assertEquals(result._2, List(w("x")))
  }

  test("chaining parsers: parse then transform") {
    val tokens = List(w("5"), op("+"), w("3"), op("*"))
    val (sum, rest) = parseLeftInfix(op("+"), (a: Int, b: Int) => a + b)(parseNum)(tokens)
    assertEquals(sum, 8)
    assertEquals(rest, List(op("*")))
  }

  test("complex: bracket parsing works") {
    val simple = parseBracketed(parseNum, RParen)(List(w("5"), RParen, w("x")))
    assertEquals(simple, (5, List(w("x"))))
  }

  test("isEmpty helper") {
    assert(isEmpty(List()))
    assert(!isEmpty(List(w("x"))))
  }

  test("headOption helper") {
    assertEquals(headOption(List(w("x"), w("y"))), Some(w("x")))
    assertEquals(headOption(List()), None)
  }
