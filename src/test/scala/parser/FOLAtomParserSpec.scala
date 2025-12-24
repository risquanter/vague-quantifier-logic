package parser

import munit.FunSuite
import logic.{FOL, Term, Formula}
import logic.Term.*
import logic.Formula.*
import parser.FOLAtomParser.*

class FOLAtomParserSpec extends FunSuite:
  
  // ========== Atom Parsing Tests ==========
  
  test("parse nullary predicate") {
    val result = parseFromString("P")
    assertEquals(result, Atom(FOL("P", List())))
  }
  
  test("parse predicate with no args (with parens)") {
    val result = parseFromString("P()")
    assertEquals(result, Atom(FOL("P", List())))
  }
  
  test("parse predicate with one arg") {
    val result = parseFromString("P(x)")
    assertEquals(result, Atom(FOL("P", List(Var("x")))))
  }
  
  test("parse predicate with multiple args") {
    val result = parseFromString("P(x, y, z)")
    assertEquals(result, Atom(FOL("P", List(Var("x"), Var("y"), Var("z")))))
  }
  
  test("parse equality (infix)") {
    val result = parseFromString("x = y")
    assertEquals(result, Atom(FOL("=", List(Var("x"), Var("y")))))
  }
  
  test("parse less than (infix)") {
    val result = parseFromString("x < y")
    assertEquals(result, Atom(FOL("<", List(Var("x"), Var("y")))))
  }
  
  test("parse less than or equal (infix)") {
    val result = parseFromString("x <= y")
    assertEquals(result, Atom(FOL("<=", List(Var("x"), Var("y")))))
  }
  
  test("parse greater than (infix)") {
    val result = parseFromString("x > y")
    assertEquals(result, Atom(FOL(">", List(Var("x"), Var("y")))))
  }
  
  test("parse greater than or equal (infix)") {
    val result = parseFromString("x >= y")
    assertEquals(result, Atom(FOL(">=", List(Var("x"), Var("y")))))
  }
  
  test("parse infix with complex terms: (x + y) < z") {
    val result = parseFromString("x + y < z")
    assertEquals(
      result,
      Atom(FOL("<", List(
        Fn("+", List(Var("x"), Var("y"))),
        Var("z")
      )))
    )
  }
  
  test("parse predicate with complex term args") {
    val result = parseFromString("P(f(x), g(y, z))")
    assertEquals(
      result,
      Atom(FOL("P", List(
        Fn("f", List(Var("x"))),
        Fn("g", List(Var("y"), Var("z")))
      )))
    )
  }
  
  // ========== Formula Parsing Tests ==========
  
  test("parse negation of predicate") {
    val result = parseFromString("~ P(x)")
    assertEquals(result, Not(Atom(FOL("P", List(Var("x"))))))
  }
  
  test("parse conjunction of predicates") {
    val result = parseFromString("P(x) /\\ Q(y)")
    assertEquals(
      result,
      And(
        Atom(FOL("P", List(Var("x")))),
        Atom(FOL("Q", List(Var("y"))))
      )
    )
  }
  
  test("parse disjunction of predicates") {
    val result = parseFromString("P(x) \\/ Q(y)")
    assertEquals(
      result,
      Or(
        Atom(FOL("P", List(Var("x")))),
        Atom(FOL("Q", List(Var("y"))))
      )
    )
  }
  
  test("parse implication with predicates") {
    val result = parseFromString("P(x) ==> Q(x)")
    assertEquals(
      result,
      Imp(
        Atom(FOL("P", List(Var("x")))),
        Atom(FOL("Q", List(Var("x"))))
      )
    )
  }
  
  test("parse universal quantification") {
    val result = parseFromString("forall x . P(x)")
    assertEquals(result, Forall("x", Atom(FOL("P", List(Var("x"))))))
  }
  
  test("parse existential quantification") {
    val result = parseFromString("exists x . P(x)")
    assertEquals(result, Exists("x", Atom(FOL("P", List(Var("x"))))))
  }
  
  test("parse forall with infix relation") {
    val result = parseFromString("forall x . x < 2")
    assertEquals(
      result,
      Forall("x", Atom(FOL("<", List(Var("x"), Fn("2", List())))))
    )
  }
  
  test("parse nested quantifiers with relation") {
    val result = parseFromString("forall x . exists y . x < y")
    assertEquals(
      result,
      Forall("x", Exists("y", Atom(FOL("<", List(Var("x"), Var("y"))))))
    )
  }
  
  test("parse multiple quantified variables") {
    val result = parseFromString("forall x y . exists z . x < z /\\ y < z")
    assertEquals(
      result,
      Forall("x", Forall("y", Exists("z",
        And(
          Atom(FOL("<", List(Var("x"), Var("z")))),
          Atom(FOL("<", List(Var("y"), Var("z"))))
        )
      )))
    )
  }
  
  test("parse complex FOL formula with all operators") {
    // forall x. P(x) ==> exists y. Q(x, y) /\ R(y)
    val result = parseFromString("forall x . P(x) ==> exists y . Q(x, y) /\\ R(y)")
    assertEquals(
      result,
      Forall("x",
        Imp(
          Atom(FOL("P", List(Var("x")))),
          Exists("y",
            And(
              Atom(FOL("Q", List(Var("x"), Var("y")))),
              Atom(FOL("R", List(Var("y"))))
            )
          )
        )
      )
    )
  }
  
  test("parse arithmetic in FOL: 2 * x + 1 = y") {
    val result = parseFromString("2 * x + 1 = y")
    assertEquals(
      result,
      Atom(FOL("=", List(
        Fn("+", List(
          Fn("*", List(Fn("2", List()), Var("x"))),
          Fn("1", List())
        )),
        Var("y")
      )))
    )
  }
  
  test("parse parenthesized infix relation") {
    val result = parseFromString("( x < y )")
    assertEquals(result, Atom(FOL("<", List(Var("x"), Var("y")))))
  }
  
  test("parse precedence: conjunction binds tighter than implication") {
    // P(x) /\ Q(x) ==> R(x) should parse as: (P(x) /\ Q(x)) ==> R(x)
    val result = parseFromString("P(x) /\\ Q(x) ==> R(x)")
    assertEquals(
      result,
      Imp(
        And(
          Atom(FOL("P", List(Var("x")))),
          Atom(FOL("Q", List(Var("x"))))
        ),
        Atom(FOL("R", List(Var("x"))))
      )
    )
  }
  
  test("parse true and false constants") {
    val result = parseFromString("true /\\ false")
    assertEquals(result, And(True, False))
  }
  
  test("parse mixed true/false with predicates") {
    val result = parseFromString("P(x) \\/ false")
    assertEquals(result, Or(Atom(FOL("P", List(Var("x")))), False))
  }
  
  test("parse negation of quantified formula") {
    val result = parseFromString("~ forall x . P(x)")
    assertEquals(result, Not(Forall("x", Atom(FOL("P", List(Var("x")))))))
  }
  
  test("parse double negation") {
    val result = parseFromString("~ ~ P(x)")
    assertEquals(result, Not(Not(Atom(FOL("P", List(Var("x")))))))
  }
  
  test("parse relation with function terms") {
    val result = parseFromString("f(x) = g(y)")
    assertEquals(
      result,
      Atom(FOL("=", List(
        Fn("f", List(Var("x"))),
        Fn("g", List(Var("y")))
      )))
    )
  }
  
  test("parse OCaml example: x + y < z") {
    val result = parseFromString("x + y < z")
    assertEquals(
      result,
      Atom(FOL("<", List(
        Fn("+", List(Var("x"), Var("y"))),
        Var("z")
      )))
    )
  }
  
  test("parse bi-implication with predicates") {
    val result = parseFromString("P(x) <=> Q(x)")
    assertEquals(
      result,
      Iff(
        Atom(FOL("P", List(Var("x")))),
        Atom(FOL("Q", List(Var("x"))))
      )
    )
  }
  
  test("parse complex nested quantifiers") {
    // exists x. forall y. exists z. P(x, y) ==> Q(y, z)
    val result = parseFromString("exists x . forall y . exists z . P(x, y) ==> Q(y, z)")
    assertEquals(
      result,
      Exists("x",
        Forall("y",
          Exists("z",
            Imp(
              Atom(FOL("P", List(Var("x"), Var("y")))),
              Atom(FOL("Q", List(Var("y"), Var("z"))))
            )
          )
        )
      )
    )
  }
  
  test("parse relation with arithmetic: (2 * x + 3) < (y - 1)") {
    val result = parseFromString("( 2 * x + 3 ) < ( y - 1 )")
    assertEquals(
      result,
      Atom(FOL("<", List(
        Fn("+", List(
          Fn("*", List(Fn("2", List()), Var("x"))),
          Fn("3", List())
        )),
        Fn("-", List(Var("y"), Fn("1", List())))
      )))
    )
  }
  
  test("fail on empty input") {
    intercept[Exception] {
      parseFromString("")
    }
  }
  
  test("fail on incomplete quantifier") {
    intercept[Exception] {
      parseFromString("forall x")
    }
  }
  
  test("fail on unparsed input") {
    intercept[Exception] {
      parseFromString("P(x) )")
    }
  }
