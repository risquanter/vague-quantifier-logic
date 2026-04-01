package logic

import munit.FunSuite
import Term.*

class TermSpec extends FunSuite:
  
  test("create variable") {
    val x = Var("x")
    x match
      case Var(name) => assertEquals(name, "x")
      case _ => fail("Should be Var")
  }
  
  test("create constant (function with no args)") {
    val zero = Fn("0", Nil)
    zero match
      case Fn(name, args) =>
        assertEquals(name, "0")
        assertEquals(args, Nil)
      case _ => fail("Should be Fn")
  }
  
  test("create function application") {
    val fx = Fn("f", List(Var("x")))
    fx match
      case Fn(name, args) =>
        assertEquals(name, "f")
        assertEquals(args, List(Var("x")))
      case _ => fail("Should be Fn")
  }
  
  test("nested function application: f(x, g(y))") {
    val term = Fn("f", List(
      Var("x"),
      Fn("g", List(Var("y")))
    ))
    
    term match
      case Fn("f", List(Var("x"), Fn("g", List(Var("y"))))) => () // success
      case _ => fail("Pattern match failed")
  }
  
  test("complex example from OCaml") {
    val example = Term.example
    example match
      case Fn("sqrt", _) => () // success
      case _ => fail("Should be sqrt function")
  }
  
  test("arithmetic expression: 2 * x + 3") {
    val expr = Fn("+", List(
      Fn("*", List(Fn("2", Nil), Var("x"))),
      Fn("3", Nil)
    ))
    
    expr match
      case Fn(name, args) =>
        assertEquals(name, "+")
        assertEquals(args.length, 2)
      case _ => fail("Should be Fn")
  }