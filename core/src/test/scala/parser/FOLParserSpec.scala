package parser

import munit.FunSuite
import logic.{FOL, Term, Formula}
import logic.Term.*
import logic.Formula.*
import parser.FOLParser

class FOLParserSpec extends FunSuite:
  
  test("parse simple predicate") {
    val formula = FOLParser.parse("P(x)")
    assertEquals(formula, Atom(FOL("P", List(Var("x")))))
  }
  
  test("parse infix relation") {
    val formula = FOLParser.parse("x < y")
    assertEquals(formula, Atom(FOL("<", List(Var("x"), Var("y")))))
  }
  
  test("parse quantified formula") {
    val formula = FOLParser.parse("forall x . P(x)")
    assertEquals(formula, Forall("x", Atom(FOL("P", List(Var("x"))))))
  }
  
  test("parse complex formula from documentation") {
    val formula = FOLParser.parse("forall x. exists y. x < y")
    assertEquals(
      formula,
      Forall("x", Exists("y", Atom(FOL("<", List(Var("x"), Var("y"))))))
    )
  }
  
  test("parse formula with conjunction and implication") {
    val formula = FOLParser.parse("P(x) /\\ Q(y) ==> R(x, y)")
    assertEquals(
      formula,
      Imp(
        And(
          Atom(FOL("P", List(Var("x")))),
          Atom(FOL("Q", List(Var("y"))))
        ),
        Atom(FOL("R", List(Var("x"), Var("y"))))
      )
    )
  }
  
  test("parse arithmetic relation") {
    val formula = FOLParser.parse("2 * x + 3 = y")
    assertEquals(
      formula,
      Atom(FOL("=", List(
        Fn("+", List(
          Fn("*", List(Fn("2", List()), Var("x"))),
          Fn("3", List())
        )),
        Var("y")
      )))
    )
  }
  
  test("parse with true/false constants") {
    val formula = FOLParser.parse("P(x) \\/ false")
    assertEquals(formula, Or(Atom(FOL("P", List(Var("x")))), False))
  }
  
  test("parse nested quantifiers") {
    val formula = FOLParser.parse("forall x y. exists z. x < z /\\ y < z")
    assertEquals(
      formula,
      Forall("x", Forall("y", Exists("z",
        And(
          Atom(FOL("<", List(Var("x"), Var("z")))),
          Atom(FOL("<", List(Var("y"), Var("z"))))
        )
      )))
    )
  }
  
  test("parse negation") {
    val formula = FOLParser.parse("~ P(x)")
    assertEquals(formula, Not(Atom(FOL("P", List(Var("x"))))))
  }
  
  test("parse bi-implication") {
    val formula = FOLParser.parse("P(x) <=> Q(x)")
    assertEquals(
      formula,
      Iff(
        Atom(FOL("P", List(Var("x")))),
        Atom(FOL("Q", List(Var("x"))))
      )
    )
  }
  
  test("defaultParser works") {
    val formula = FOLParser.defaultParser("P(x)")
    assertEquals(formula, Atom(FOL("P", List(Var("x")))))
  }
  
  test("parseTokens returns remaining tokens") {
    val tokens = List("P", "(", "x", ")", "extra")
    val (formula, rest) = FOLParser.parseTokens(tokens)
    assertEquals(formula, Atom(FOL("P", List(Var("x")))))
    assertEquals(rest, List("extra"))
  }
  
  test("parseTokens with empty remaining") {
    val tokens = List("P", "(", "x", ")")
    val (formula, rest) = FOLParser.parseTokens(tokens)
    assertEquals(formula, Atom(FOL("P", List(Var("x")))))
    assertEquals(rest, List())
  }
  
  test("fail on unparsed input") {
    intercept[Exception] {
      FOLParser.parse("P(x) )")
    }
  }
  
  test("fail on empty input") {
    intercept[Exception] {
      FOLParser.parse("")
    }
  }
  
  test("fail on incomplete quantifier") {
    intercept[Exception] {
      FOLParser.parse("forall x")
    }
  }
  
  test("parse real-world example: transitivity of less-than") {
    // forall x y z. x < y /\ y < z ==> x < z
    val formula = FOLParser.parse("forall x y z . x < y /\\ y < z ==> x < z")
    assertEquals(
      formula,
      Forall("x", Forall("y", Forall("z",
        Imp(
          And(
            Atom(FOL("<", List(Var("x"), Var("y")))),
            Atom(FOL("<", List(Var("y"), Var("z"))))
          ),
          Atom(FOL("<", List(Var("x"), Var("z"))))
        )
      )))
    )
  }
  
  test("parse real-world example: existence of intermediate element") {
    // forall x y. x < y ==> exists z. x < z /\ z < y
    val formula = FOLParser.parse("forall x y . x < y ==> exists z . x < z /\\ z < y")
    assertEquals(
      formula,
      Forall("x", Forall("y",
        Imp(
          Atom(FOL("<", List(Var("x"), Var("y")))),
          Exists("z",
            And(
              Atom(FOL("<", List(Var("x"), Var("z")))),
              Atom(FOL("<", List(Var("z"), Var("y"))))
            )
          )
        )
      ))
    )
  }
  
  test("parse equality with function symbols") {
    val formula = FOLParser.parse("f(x) = g(y, z)")
    assertEquals(
      formula,
      Atom(FOL("=", List(
        Fn("f", List(Var("x"))),
        Fn("g", List(Var("y"), Var("z")))
      )))
    )
  }
  
  test("parse complex arithmetic: (x + y) * z = w") {
    val formula = FOLParser.parse("( x + y ) * z = w")
    assertEquals(
      formula,
      Atom(FOL("=", List(
        Fn("*", List(
          Fn("+", List(Var("x"), Var("y"))),
          Var("z")
        )),
        Var("w")
      )))
    )
  }
