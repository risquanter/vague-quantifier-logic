package logic

import munit.FunSuite
import Term.*
import Formula.*

class FOLSpec extends FunSuite:
  
  test("create relation: x = y") {
    val eq = FOL("=", List(Var("x"), Var("y")))
    assertEquals(eq.predicate, "=")
    assertEquals(eq.terms, List(Var("x"), Var("y")))
  }
  
  test("create relation: x < y") {
    val lt = FOL("<", Var("x"), Var("y"))
    assertEquals(lt.predicate, "<")
    assertEquals(lt.terms, List(Var("x"), Var("y")))
  }
  
  test("create predicate: P(x)") {
    val p = FOL("P", Var("x"))
    assertEquals(p.predicate, "P")
    assertEquals(p.terms, List(Var("x")))
  }
  
  test("predicate with multiple arguments: R(x, y, z)") {
    val r = FOL("R", Var("x"), Var("y"), Var("z"))
    assertEquals(r.terms.length, 3)
  }
  
  test("FOL formula: x + y < z (example from OCaml)") {
    val formula = FOL.example
    formula match
      case Atom(FOL("<", terms)) => 
        assertEquals(terms.length, 2)
        terms.head match
          case Fn("+", _) => () // success
          case _ => fail("First term should be addition")
      case _ => fail("Should be atomic formula")
  }
  
  test("FOL formula with quantifiers: forall x. x < 2") {
    val formula: FOL.FOLFormula = 
      Forall("x", Atom(FOL("<", Var("x"), Fn("2", Nil))))
    
    formula match
      case Forall("x", Atom(FOL("<", List(Var("x"), Fn("2", Nil))))) => () // success
      case _ => fail("Should match pattern")
  }
  
  test("complex FOL: forall x y. exists z. x < z /\\ y < z") {
    val formula: FOL.FOLFormula = 
      Forall("x", Forall("y", 
        Exists("z", 
          And(
            Atom(FOL("<", Var("x"), Var("z"))),
            Atom(FOL("<", Var("y"), Var("z")))
          )
        )
      ))
    
    formula match
      case Forall("x", Forall("y", Exists("z", And(_, _)))) => () // success
      case _ => fail("Should match nested quantifiers with conjunction")
  }
