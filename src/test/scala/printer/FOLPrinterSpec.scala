package printer

import munit.FunSuite
import logic.{FOL, Formula, Term}
import printer.FOLPrinter.*
import parser.FOLParser

/** Test suite for FOL Pretty Printer - Phase 11
  * 
  * Tests:
  * - Term printing (variables, constants, functions, infix operators)
  * - Atom printing (predicates, infix relations)
  * - Formula printing (connectives, quantifiers, precedence)
  * - Round-trip parsing (parse then print should produce equivalent formula)
  */
class FOLPrinterSpec extends FunSuite:
  
  // ==================== Term Printing Tests ====================
  
  test("print variable") {
    val tm = Term.Var("x")
    assertEquals(printTerm(tm), "x")
  }
  
  test("print constant") {
    val tm = Term.Const("42")
    assertEquals(printTerm(tm), "42")
  }
  
  test("print nullary function") {
    val tm = Term.Fn("c", Nil)
    assertEquals(printTerm(tm), "c")
  }
  
  test("print unary function") {
    val tm = Term.Fn("f", List(Term.Var("x")))
    assertEquals(printTerm(tm), "f(x)")
  }
  
  test("print binary function") {
    val tm = Term.Fn("f", List(Term.Var("x"), Term.Var("y")))
    assertEquals(printTerm(tm), "f(x, y)")
  }
  
  test("print nested function") {
    val tm = Term.Fn("f", List(Term.Fn("g", List(Term.Var("x")))))
    assertEquals(printTerm(tm), "f(g(x))")
  }
  
  test("print infix plus") {
    val tm = Term.Fn("+", List(Term.Var("x"), Term.Var("y")))
    assertEquals(printTerm(tm), "x + y")
  }
  
  test("print infix multiply") {
    val tm = Term.Fn("*", List(Term.Var("x"), Term.Var("y")))
    assertEquals(printTerm(tm), "x * y")
  }
  
  test("print nested infix operators") {
    val tm = Term.Fn("+", List(
      Term.Var("x"),
      Term.Fn("*", List(Term.Var("y"), Term.Var("z")))
    ))
    assertEquals(printTerm(tm), "x + y * z")
  }
  
  test("print cons operator") {
    val tm = Term.Fn("::", List(Term.Var("x"), Term.Var("xs")))
    assertEquals(printTerm(tm), "x :: xs")
  }
  
  // ==================== Atom Printing Tests ====================
  
  test("print nullary predicate") {
    val atom = FOL("P", Nil)
    assertEquals(printAtom(atom), "P")
  }
  
  test("print unary predicate") {
    val atom = FOL("P", List(Term.Var("x")))
    assertEquals(printAtom(atom), "P(x)")
  }
  
  test("print binary predicate") {
    val atom = FOL("P", List(Term.Var("x"), Term.Var("y")))
    assertEquals(printAtom(atom), "P(x, y)")
  }
  
  test("print equality") {
    val atom = FOL("=", List(Term.Var("x"), Term.Var("y")))
    assertEquals(printAtom(atom), "x = y")
  }
  
  test("print less than") {
    val atom = FOL("<", List(Term.Var("x"), Term.Var("y")))
    assertEquals(printAtom(atom), "x < y")
  }
  
  test("print less than or equal") {
    val atom = FOL("<=", List(Term.Var("x"), Term.Var("y")))
    assertEquals(printAtom(atom), "x <= y")
  }
  
  test("print greater than") {
    val atom = FOL(">", List(Term.Var("x"), Term.Var("y")))
    assertEquals(printAtom(atom), "x > y")
  }
  
  test("print complex infix relation") {
    val atom = FOL("<", List(
      Term.Var("x"),
      Term.Fn("+", List(Term.Var("y"), Term.Const("1")))
    ))
    assertEquals(printAtom(atom), "x < y + 1")
  }
  
  // ==================== Formula Printing Tests ====================
  
  test("print false") {
    assertEquals(printFormula(Formula.False), "false")
  }
  
  test("print true") {
    assertEquals(printFormula(Formula.True), "true")
  }
  
  test("print atomic formula") {
    val fm = Formula.Atom(FOL("P", List(Term.Var("x"))))
    assertEquals(printFormula(fm), "P(x)")
  }
  
  test("print negation") {
    val fm = Formula.Not(Formula.Atom(FOL("P", List(Term.Var("x")))))
    assertEquals(printFormula(fm), "~P(x)")
  }
  
  test("print conjunction") {
    val fm = Formula.And(
      Formula.Atom(FOL("P", List(Term.Var("x")))),
      Formula.Atom(FOL("Q", List(Term.Var("y"))))
    )
    assertEquals(printFormula(fm), "P(x) /\\ Q(y)")
  }
  
  test("print disjunction") {
    val fm = Formula.Or(
      Formula.Atom(FOL("P", List(Term.Var("x")))),
      Formula.Atom(FOL("Q", List(Term.Var("y"))))
    )
    assertEquals(printFormula(fm), "P(x) \\/ Q(y)")
  }
  
  test("print implication") {
    val fm = Formula.Imp(
      Formula.Atom(FOL("P", List(Term.Var("x")))),
      Formula.Atom(FOL("Q", List(Term.Var("x"))))
    )
    assertEquals(printFormula(fm), "P(x) ==> Q(x)")
  }
  
  test("print iff") {
    val fm = Formula.Iff(
      Formula.Atom(FOL("P", List(Term.Var("x")))),
      Formula.Atom(FOL("Q", List(Term.Var("x"))))
    )
    assertEquals(printFormula(fm), "P(x) <=> Q(x)")
  }
  
  test("print forall") {
    val fm = Formula.Forall("x", Formula.Atom(FOL("P", List(Term.Var("x")))))
    assertEquals(printFormula(fm), "forall x. P(x)")
  }
  
  test("print exists") {
    val fm = Formula.Exists("x", Formula.Atom(FOL("P", List(Term.Var("x")))))
    assertEquals(printFormula(fm), "exists x. P(x)")
  }
  
  test("print nested quantifiers") {
    val fm = Formula.Forall("x", 
      Formula.Forall("y", 
        Formula.Atom(FOL("P", List(Term.Var("x"), Term.Var("y"))))
      )
    )
    assertEquals(printFormula(fm), "forall x y. P(x, y)")
  }
  
  test("print mixed quantifiers") {
    val fm = Formula.Forall("x",
      Formula.Exists("y",
        Formula.Atom(FOL("<", List(Term.Var("x"), Term.Var("y"))))
      )
    )
    assertEquals(printFormula(fm), "forall x. exists y. x < y")
  }
  
  // ==================== Precedence Tests ====================
  
  test("print precedence: and over or") {
    val fm = Formula.Or(
      Formula.And(
        Formula.Atom(FOL("P", Nil)),
        Formula.Atom(FOL("Q", Nil))
      ),
      Formula.Atom(FOL("R", Nil))
    )
    assertEquals(printFormula(fm), "P /\\ Q \\/ R")
  }
  
  test("print precedence: or over imp") {
    val fm = Formula.Imp(
      Formula.Or(
        Formula.Atom(FOL("P", Nil)),
        Formula.Atom(FOL("Q", Nil))
      ),
      Formula.Atom(FOL("R", Nil))
    )
    assertEquals(printFormula(fm), "P \\/ Q ==> R")
  }
  
  test("print precedence: imp over iff") {
    val fm = Formula.Iff(
      Formula.Imp(
        Formula.Atom(FOL("P", Nil)),
        Formula.Atom(FOL("Q", Nil))
      ),
      Formula.Imp(
        Formula.Atom(FOL("Q", Nil)),
        Formula.Atom(FOL("P", Nil))
      )
    )
    assertEquals(printFormula(fm), "P ==> Q <=> Q ==> P")
  }
  
  test("print precedence: not binds tightest") {
    val fm = Formula.And(
      Formula.Not(Formula.Atom(FOL("P", Nil))),
      Formula.Atom(FOL("Q", Nil))
    )
    assertEquals(printFormula(fm), "~P /\\ Q")
  }
  
  // ==================== Round-trip Tests ====================
  
  test("round-trip: simple atom") {
    val input = "P(x)"
    val parsed = FOLParser.parse(input)
    val printed = printFormula(parsed)
    val reparsed = FOLParser.parse(printed)
    assertEquals(parsed, reparsed)
  }
  
  test("round-trip: conjunction") {
    val input = "P(x) /\\ Q(y)"
    val parsed = FOLParser.parse(input)
    val printed = printFormula(parsed)
    val reparsed = FOLParser.parse(printed)
    assertEquals(parsed, reparsed)
  }
  
  test("round-trip: implication") {
    val input = "P(x) ==> Q(x)"
    val parsed = FOLParser.parse(input)
    val printed = printFormula(parsed)
    val reparsed = FOLParser.parse(printed)
    assertEquals(parsed, reparsed)
  }
  
  test("round-trip: quantifiers") {
    val input = "forall x. exists y. x < y"
    val parsed = FOLParser.parse(input)
    val printed = printFormula(parsed)
    val reparsed = FOLParser.parse(printed)
    assertEquals(parsed, reparsed)
  }
  
  test("round-trip: complex formula") {
    val input = "forall x. P(x) ==> exists y. Q(x, y) /\\ R(y)"
    val parsed = FOLParser.parse(input)
    val printed = printFormula(parsed)
    val reparsed = FOLParser.parse(printed)
    assertEquals(parsed, reparsed)
  }
  
  test("round-trip: nested implications") {
    val input = "(P ==> Q) ==> R"
    val parsed = FOLParser.parse(input)
    val printed = printFormula(parsed)
    val reparsed = FOLParser.parse(printed)
    assertEquals(parsed, reparsed)
  }
  
  test("round-trip: equality with functions") {
    val input = "f(x) = g(y, z)"
    val parsed = FOLParser.parse(input)
    val printed = printFormula(parsed)
    val reparsed = FOLParser.parse(printed)
    assertEquals(parsed, reparsed)
  }
  
  test("round-trip: negation") {
    val input = "~(P(x) /\\ Q(x))"
    val parsed = FOLParser.parse(input)
    val printed = printFormula(parsed)
    val reparsed = FOLParser.parse(printed)
    assertEquals(parsed, reparsed)
  }
  
  // ==================== Edge Cases ====================
  
  test("print deeply nested formula") {
    val fm = Formula.Forall("x",
      Formula.Exists("y",
        Formula.Imp(
          Formula.And(
            Formula.Atom(FOL("P", List(Term.Var("x")))),
            Formula.Atom(FOL("Q", List(Term.Var("y"))))
          ),
          Formula.Or(
            Formula.Atom(FOL("R", List(Term.Var("x")))),
            Formula.Not(Formula.Atom(FOL("S", List(Term.Var("y")))))
          )
        )
      )
    )
    val printed = printFormula(fm)
    assert(printed.contains("forall"))
    assert(printed.contains("exists"))
    assert(printed.contains("==>"))
  }
  
  test("print multiple consecutive foralls") {
    val fm = Formula.Forall("x",
      Formula.Forall("y",
        Formula.Forall("z",
          Formula.Atom(FOL("P", List(Term.Var("x"), Term.Var("y"), Term.Var("z"))))
        )
      )
    )
    assertEquals(printFormula(fm), "forall x y z. P(x, y, z)")
  }
  
  test("print multiple consecutive exists") {
    val fm = Formula.Exists("x",
      Formula.Exists("y",
        Formula.Exists("z",
          Formula.Atom(FOL("P", List(Term.Var("x"), Term.Var("y"), Term.Var("z"))))
        )
      )
    )
    assertEquals(printFormula(fm), "exists x y z. P(x, y, z)")
  }
  
  test("convenience method: print formula") {
    val fm = Formula.Atom(FOL("P", List(Term.Var("x"))))
    assertEquals(print(fm), "P(x)")
  }
  
  test("convenience method: print term") {
    val tm = Term.Fn("f", List(Term.Var("x")))
    assertEquals(print(tm), "f(x)")
  }
