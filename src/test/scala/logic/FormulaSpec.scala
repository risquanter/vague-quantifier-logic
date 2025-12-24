package logic

import munit.FunSuite
import Formula.*

class FormulaSpec extends FunSuite:
  
  test("create True and False") {
    assert(True.isInstanceOf[Formula[?]])
    assert(False.isInstanceOf[Formula[?]])
  }
  
  test("create atomic formula") {
    val p = Atom("p")
    p match
      case Atom(value) => assertEquals(value, "p")
      case _ => fail("Should be Atom")
  }
  
  test("create negation") {
    val notP = Not(Atom("p"))
    notP match
      case Not(Atom("p")) => () // success
      case _ => fail("Should be Not(Atom)")
  }
  
  test("create conjunction: p /\\ q") {
    val pAndQ = And(Atom("p"), Atom("q"))
    pAndQ match
      case And(Atom("p"), Atom("q")) => () // success
      case _ => fail("Should be And")
  }
  
  test("create disjunction: p \\/ q") {
    val pOrQ = Or(Atom("p"), Atom("q"))
    pOrQ match
      case Or(Atom("p"), Atom("q")) => () // success
      case _ => fail("Should be Or")
  }
  
  test("create implication: p ==> q") {
    val pImpQ = Imp(Atom("p"), Atom("q"))
    pImpQ match
      case Imp(Atom("p"), Atom("q")) => () // success
      case _ => fail("Should be Imp")
  }
  
  test("create bi-implication: p <=> q") {
    val pIffQ = Iff(Atom("p"), Atom("q"))
    pIffQ match
      case Iff(Atom("p"), Atom("q")) => () // success
      case _ => fail("Should be Iff")
  }
  
  test("create universal quantification: forall x. P(x)") {
    val forallX = Forall("x", Atom("P"))
    forallX match
      case Forall("x", Atom("P")) => () // success
      case _ => fail("Should be Forall")
  }
  
  test("create existential quantification: exists x. P(x)") {
    val existsX = Exists("x", Atom("P"))
    existsX match
      case Exists("x", Atom("P")) => () // success
      case _ => fail("Should be Exists")
  }
  
  test("nested quantifiers: forall x. exists y. R(x, y)") {
    val formula = Forall("x", Exists("y", Atom(("x", "y"))))
    formula match
      case Forall("x", Exists("y", _)) => () // success
      case _ => fail("Should be nested quantifiers")
  }
  
  test("smart constructors work") {
    assertEquals(mkAnd(Atom("p"), Atom("q")), And(Atom("p"), Atom("q")))
    assertEquals(mkForall("x", Atom("P")), Forall("x", Atom("P")))
  }
  
  test("complex formula: (p /\\ q) ==> r") {
    val formula = Imp(And(Atom("p"), Atom("q")), Atom("r"))
    formula match
      case Imp(And(_, _), Atom("r")) => () // success
      case _ => fail("Should be Imp(And, Atom)")
  }
