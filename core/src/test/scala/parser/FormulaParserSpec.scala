package parser

import munit.FunSuite
import logic.Formula
import logic.Formula.*
import parser.FormulaParser.*
import parser.Combinators.*
import util.StringUtil.*
import lexer.Lexer.*

class FormulaParserSpec extends FunSuite:
  
  /** Simple propositional atom parser for testing
    * 
    * An atom is just an identifier (alphanumeric token).
    * This is a minimal parser to test the formula infrastructure.
    * 
    * Note: vs (bound variables) is ignored for propositional logic
    */
  def parsePropAtom(vs: List[String], inp: List[String]): ParseResult[String] =
    inp match
      case Nil =>
        throw new Exception("Expected atom")
      case tok :: rest if tok.forall(c => alphanumeric(c) || numeric(c)) =>
        (tok, rest)
      case tok :: _ =>
        throw new Exception(s"Expected atom, got: $tok")
  
  /** No infix atoms for simple propositional logic */
  def parseNoInfix(vs: List[String], inp: List[String]): ParseResult[String] =
    throw new Exception("No infix atoms in propositional logic")
  
  /** Helper to parse from string */
  def parseFromString(s: String): Formula[String] =
    val tokens = lex(explode(s))
    val (result, rest) = parse(parseNoInfix, parsePropAtom)(tokens)
    if rest.isEmpty then
      result
    else
      throw new Exception(s"Unparsed input: ${rest.mkString(" ")}")
  
  test("parse true constant") {
    val result = parseFromString("true")
    assertEquals(result, True)
  }
  
  test("parse false constant") {
    val result = parseFromString("false")
    assertEquals(result, False)
  }
  
  test("parse atom") {
    val result = parseFromString("p")
    assertEquals(result, Atom("p"))
  }
  
  test("parse negation") {
    val result = parseFromString("~ p")
    assertEquals(result, Not(Atom("p")))
  }
  
  test("parse conjunction") {
    val result = parseFromString("p /\\ q")
    assertEquals(result, And(Atom("p"), Atom("q")))
  }
  
  test("parse disjunction") {
    val result = parseFromString("p \\/ q")
    assertEquals(result, Or(Atom("p"), Atom("q")))
  }
  
  test("parse implication") {
    val result = parseFromString("p ==> q")
    assertEquals(result, Imp(Atom("p"), Atom("q")))
  }
  
  test("parse bi-implication") {
    val result = parseFromString("p <=> q")
    assertEquals(result, Iff(Atom("p"), Atom("q")))
  }
  
  test("parse universal quantifier") {
    val result = parseFromString("forall x . p")
    assertEquals(result, Forall("x", Atom("p")))
  }
  
  test("parse existential quantifier") {
    val result = parseFromString("exists x . p")
    assertEquals(result, Exists("x", Atom("p")))
  }
  
  test("parse multiple quantified variables (forall x y)") {
    val result = parseFromString("forall x y . p")
    // Should parse as: forall x. forall y. p
    assertEquals(result, Forall("x", Forall("y", Atom("p"))))
  }
  
  test("parse multiple quantified variables (exists x y z)") {
    val result = parseFromString("exists x y z . p")
    // Should parse as: exists x. exists y. exists z. p
    assertEquals(result, Exists("x", Exists("y", Exists("z", Atom("p")))))
  }
  
  test("parse parenthesized formula") {
    val result = parseFromString("( p /\\ q )")
    assertEquals(result, And(Atom("p"), Atom("q")))
  }
  
  test("parse precedence: /\\ binds tighter than \\/") {
    // p /\ q \/ r should parse as (p /\ q) \/ r
    val result = parseFromString("p /\\ q \\/ r")
    assertEquals(result, Or(And(Atom("p"), Atom("q")), Atom("r")))
  }
  
  test("parse precedence: \\/ binds tighter than ==>") {
    // p \/ q ==> r should parse as (p \/ q) ==> r
    val result = parseFromString("p \\/ q ==> r")
    assertEquals(result, Imp(Or(Atom("p"), Atom("q")), Atom("r")))
  }
  
  test("parse precedence: ==> binds tighter than <=>") {
    // p ==> q <=> r should parse as (p ==> q) <=> r
    val result = parseFromString("p ==> q <=> r")
    assertEquals(result, Iff(Imp(Atom("p"), Atom("q")), Atom("r")))
  }
  
  test("parse right associativity of /\\") {
    // p /\ q /\ r should parse as p /\ (q /\ r)
    val result = parseFromString("p /\\ q /\\ r")
    assertEquals(result, And(Atom("p"), And(Atom("q"), Atom("r"))))
  }
  
  test("parse right associativity of ==>") {
    // p ==> q ==> r should parse as p ==> (q ==> r)
    val result = parseFromString("p ==> q ==> r")
    assertEquals(result, Imp(Atom("p"), Imp(Atom("q"), Atom("r"))))
  }
  
  test("parse complex formula with all operators") {
    // (p /\ q) \/ r ==> s <=> t
    val result = parseFromString("p /\\ q \\/ r ==> s <=> t")
    
    // Expected: Iff(Imp(Or(And(p, q), r), s), t)
    val expected = Iff(
      Imp(
        Or(And(Atom("p"), Atom("q")), Atom("r")),
        Atom("s")
      ),
      Atom("t")
    )
    assertEquals(result, expected)
  }
  
  test("parse negation with conjunction") {
    val result = parseFromString("~ p /\\ q")
    assertEquals(result, And(Not(Atom("p")), Atom("q")))
  }
  
  test("parse nested negation") {
    val result = parseFromString("~ ~ p")
    assertEquals(result, Not(Not(Atom("p"))))
  }
  
  test("parse quantifier with conjunction") {
    val result = parseFromString("forall x . p /\\ q")
    assertEquals(result, Forall("x", And(Atom("p"), Atom("q"))))
  }
  
  test("parse nested quantifiers") {
    val result = parseFromString("forall x . exists y . p")
    assertEquals(result, Forall("x", Exists("y", Atom("p"))))
  }
  
  test("parse parentheses override precedence") {
    // p /\ (q \/ r) - parentheses force \/ to be evaluated first
    val result = parseFromString("p /\\ ( q \\/ r )")
    assertEquals(result, And(Atom("p"), Or(Atom("q"), Atom("r"))))
  }
  
  test("parse complex nested formula") {
    // forall x . (p ==> q) /\ (r \/ s)
    val result = parseFromString("forall x . ( p ==> q ) /\\ ( r \\/ s )")
    val expected = Forall(
      "x",
      And(
        Imp(Atom("p"), Atom("q")),
        Or(Atom("r"), Atom("s"))
      )
    )
    assertEquals(result, expected)
  }
  
  test("fail on empty input") {
    intercept[Exception] {
      parseFromString("")
    }
  }
  
  test("fail on missing quantifier body") {
    intercept[Exception] {
      parseFromString("forall x")
    }
  }
  
  test("fail on missing dot after quantifier variable") {
    intercept[Exception] {
      parseFromString("forall x p")
    }
  }
  
  test("parse atom with numbers") {
    val result = parseFromString("p1")
    assertEquals(result, Atom("p1"))
  }
  
  test("parse multiple atoms in formula") {
    val result = parseFromString("p1 /\\ p2 \\/ p3")
    assertEquals(result, Or(And(Atom("p1"), Atom("p2")), Atom("p3")))
  }
