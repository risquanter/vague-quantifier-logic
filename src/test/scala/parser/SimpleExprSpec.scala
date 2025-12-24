package parser

import munit.FunSuite
import parser.SimpleExprParser.*
import parser.Expr.*

class SimpleExprSpec extends FunSuite:
  
  test("parse variable") {
    val result = parse("x")
    assertEquals(result, Var("x"))
  }
  
  test("parse constant") {
    val result = parse("42")
    assertEquals(result, Const(42))
  }
  
  test("parse simple addition") {
    val result = parse("x + 1")
    assertEquals(result, Add(Var("x"), Const(1)))
  }
  
  test("parse simple multiplication") {
    val result = parse("2 * x")
    assertEquals(result, Mul(Const(2), Var("x")))
  }
  
  test("parse addition right-associative (1 + 2 + 3)") {
    // Should parse as: 1 + (2 + 3) = Add(1, Add(2, 3))
    val result = parse("1 + 2 + 3")
    assertEquals(result, Add(Const(1), Add(Const(2), Const(3))))
  }
  
  test("parse multiplication right-associative (2 * 3 * 4)") {
    // Should parse as: 2 * (3 * 4) = Mul(2, Mul(3, 4))
    val result = parse("2 * 3 * 4")
    assertEquals(result, Mul(Const(2), Mul(Const(3), Const(4))))
  }
  
  test("parse with precedence (* binds tighter than +)") {
    // x + 2 * 3 should parse as: x + (2 * 3)
    val result = parse("x + 2 * 3")
    assertEquals(result, Add(Var("x"), Mul(Const(2), Const(3))))
  }
  
  test("parse with precedence (2 * x + 1)") {
    // 2 * x + 1 should parse as: (2 * x) + 1
    val result = parse("2 * x + 1")
    assertEquals(result, Add(Mul(Const(2), Var("x")), Const(1)))
  }
  
  test("parse parenthesized expression") {
    val result = parse("(x + 1)")
    assertEquals(result, Add(Var("x"), Const(1)))
  }
  
  test("parse parentheses override precedence") {
    // (x + 1) * 2 should parse as: Mul(Add(x, 1), 2)
    val result = parse("(x + 1) * 2")
    assertEquals(result, Mul(Add(Var("x"), Const(1)), Const(2)))
  }
  
  test("parse complex expression from OCaml example") {
    // (x1 + x2 + x3) * (1 + 2 + 3 * x + y)
    val result = parse("(x1 + x2 + x3) * (1 + 2 + 3 * x + y)")
    
    // Expected structure:
    // Mul(
    //   Add(x1, Add(x2, x3)),
    //   Add(1, Add(2, Add(Mul(3, x), y)))
    // )
    val left = Add(Var("x1"), Add(Var("x2"), Var("x3")))
    val right = Add(
      Const(1),
      Add(
        Const(2),
        Add(
          Mul(Const(3), Var("x")),
          Var("y")
        )
      )
    )
    assertEquals(result, Mul(left, right))
  }
  
  test("parse nested parentheses") {
    val result = parse("2 * ((var_1 + x) + 11)")
    
    // Expected: Mul(2, Add(Add(var_1, x), 11))
    val inner = Add(Add(Var("var_1"), Var("x")), Const(11))
    assertEquals(result, Mul(Const(2), inner))
  }
  
  test("parse variable with underscore and digits") {
    val result = parse("var_1")
    assertEquals(result, Var("var_1"))
  }
  
  test("parse variable with prime") {
    val result = parse("x'")
    assertEquals(result, Var("x'"))
  }
  
  test("fail on empty input") {
    intercept[Exception] {
      parse("")
    }
  }
  
  test("fail on unparsed input") {
    intercept[Exception] {
      parse("1 + 2 )")
    }
  }
  
  test("fail on missing closing paren") {
    intercept[Exception] {
      parse("(x + 1")
    }
  }
  
  test("fail on unexpected end after operator") {
    intercept[Exception] {
      parse("x +")
    }
  }
