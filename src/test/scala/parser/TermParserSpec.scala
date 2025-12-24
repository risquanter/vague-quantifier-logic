package parser

import munit.FunSuite
import logic.Term
import logic.Term.*
import parser.TermParser.*

class TermParserSpec extends FunSuite:
  
  test("parse variable") {
    val result = parseFromString("x")
    assertEquals(result, Var("x"))
  }
  
  test("parse constant (numeric)") {
    val result = parseFromString("42")
    assertEquals(result, Fn("42", List()))
  }
  
  test("parse constant (nil)") {
    val result = parseFromString("nil")
    assertEquals(result, Fn("nil", List()))
  }
  
  test("parse function with no args") {
    val result = parseFromString("f()")
    assertEquals(result, Fn("f", List()))
  }
  
  test("parse function with one arg") {
    val result = parseFromString("f(x)")
    assertEquals(result, Fn("f", List(Var("x"))))
  }
  
  test("parse function with multiple args") {
    val result = parseFromString("f(x, y, z)")
    assertEquals(result, Fn("f", List(Var("x"), Var("y"), Var("z"))))
  }
  
  test("parse parenthesized term") {
    val result = parseFromString("(x)")
    assertEquals(result, Var("x"))
  }
  
  test("parse unary minus") {
    val result = parseFromString("- x")
    assertEquals(result, Fn("-", List(Var("x"))))
  }
  
  test("parse unary minus with constant") {
    val result = parseFromString("- 5")
    assertEquals(result, Fn("-", List(Fn("5", List()))))
  }
  
  test("parse addition (infix)") {
    val result = parseFromString("x + y")
    assertEquals(result, Fn("+", List(Var("x"), Var("y"))))
  }
  
  test("parse subtraction (infix)") {
    val result = parseFromString("x - y")
    assertEquals(result, Fn("-", List(Var("x"), Var("y"))))
  }
  
  test("parse multiplication (infix)") {
    val result = parseFromString("x * y")
    assertEquals(result, Fn("*", List(Var("x"), Var("y"))))
  }
  
  test("parse division (infix)") {
    val result = parseFromString("x / y")
    assertEquals(result, Fn("/", List(Var("x"), Var("y"))))
  }
  
  test("parse exponentiation (infix)") {
    val result = parseFromString("x ^ y")
    assertEquals(result, Fn("^", List(Var("x"), Var("y"))))
  }
  
  test("parse cons (infix)") {
    val result = parseFromString("x :: y")
    assertEquals(result, Fn("::", List(Var("x"), Var("y"))))
  }
  
  test("parse addition right-associative (x + y + z)") {
    // + is right-associative: x + y + z = x + (y + z)
    val result = parseFromString("x + y + z")
    assertEquals(result, Fn("+", List(Var("x"), Fn("+", List(Var("y"), Var("z"))))))
  }
  
  test("parse subtraction left-associative (x - y - z)") {
    // - is left-associative: x - y - z = (x - y) - z
    val result = parseFromString("x - y - z")
    assertEquals(result, Fn("-", List(Fn("-", List(Var("x"), Var("y"))), Var("z"))))
  }
  
  test("parse multiplication right-associative (x * y * z)") {
    // * is right-associative: x * y * z = x * (y * z)
    val result = parseFromString("x * y * z")
    assertEquals(result, Fn("*", List(Var("x"), Fn("*", List(Var("y"), Var("z"))))))
  }
  
  test("parse division left-associative (x / y / z)") {
    // / is left-associative: x / y / z = (x / y) / z
    val result = parseFromString("x / y / z")
    assertEquals(result, Fn("/", List(Fn("/", List(Var("x"), Var("y"))), Var("z"))))
  }
  
  test("parse exponentiation left-associative (x ^ y ^ z)") {
    // ^ is left-associative in OCaml: x ^ y ^ z = (x ^ y) ^ z
    val result = parseFromString("x ^ y ^ z")
    assertEquals(result, Fn("^", List(Fn("^", List(Var("x"), Var("y"))), Var("z"))))
  }
  
  test("parse cons right-associative (x :: y :: z)") {
    // :: is right-associative: x :: y :: z = x :: (y :: z)
    val result = parseFromString("x :: y :: z")
    assertEquals(result, Fn("::", List(Var("x"), Fn("::", List(Var("y"), Var("z"))))))
  }
  
  test("parse precedence: ^ binds tighter than /") {
    // x / y ^ z should parse as: x / (y ^ z)
    val result = parseFromString("x / y ^ z")
    assertEquals(result, Fn("/", List(Var("x"), Fn("^", List(Var("y"), Var("z"))))))
  }
  
  test("parse precedence: / binds tighter than *") {
    // x * y / z should parse as: x * (y / z)
    val result = parseFromString("x * y / z")
    assertEquals(result, Fn("*", List(Var("x"), Fn("/", List(Var("y"), Var("z"))))))
  }
  
  test("parse precedence: * binds tighter than -") {
    // x - y * z should parse as: x - (y * z)
    val result = parseFromString("x - y * z")
    assertEquals(result, Fn("-", List(Var("x"), Fn("*", List(Var("y"), Var("z"))))))
  }
  
  test("parse precedence: - binds tighter than +") {
    // x + y - z should parse as: x + (y - z)
    val result = parseFromString("x + y - z")
    assertEquals(result, Fn("+", List(Var("x"), Fn("-", List(Var("y"), Var("z"))))))
  }
  
  test("parse precedence: + binds tighter than ::") {
    // x :: y + z should parse as: x :: (y + z)
    val result = parseFromString("x :: y + z")
    assertEquals(result, Fn("::", List(Var("x"), Fn("+", List(Var("y"), Var("z"))))))
  }
  
  test("parse parentheses override precedence") {
    // (x + y) * z - parentheses force + to be evaluated first
    val result = parseFromString("( x + y ) * z")
    assertEquals(result, Fn("*", List(Fn("+", List(Var("x"), Var("y"))), Var("z"))))
  }
  
  test("parse nested function calls") {
    val result = parseFromString("f(g(x), h(y))")
    assertEquals(
      result,
      Fn("f", List(
        Fn("g", List(Var("x"))),
        Fn("h", List(Var("y")))
      ))
    )
  }
  
  test("parse complex nested expression") {
    // f(x + y, g(z))
    val result = parseFromString("f( x + y , g(z) )")
    assertEquals(
      result,
      Fn("f", List(
        Fn("+", List(Var("x"), Var("y"))),
        Fn("g", List(Var("z")))
      ))
    )
  }
  
  test("parse OCaml example: sqrt(1 - cos(power(x + y, 2)))") {
    // Simplified version without sqrt
    val result = parseFromString("1 - cos( power( x + y , 2 ) )")
    assertEquals(
      result,
      Fn("-", List(
        Fn("1", List()),
        Fn("cos", List(
          Fn("power", List(
            Fn("+", List(Var("x"), Var("y"))),
            Fn("2", List())
          ))
        ))
      ))
    )
  }
  
  test("parse arithmetic: 2 * x + 3") {
    // Should parse as: 2 * x + 3 = (2 * x) + 3
    // Wait, no: + is lower precedence, so: 2 * (x + 3)? 
    // Actually: * binds tighter than +, but + is right-assoc
    // So: 2 * x + 3 needs careful analysis
    // Precedence: * > +, so: (2 * x) + 3? But + is right-assoc...
    // Actually with right-assoc: a + b + c = a + (b + c)
    // But different operators: 2 * x + 3
    // * has higher precedence, so: (2 * x) + 3
    // But + calls * as subparser, so it becomes: 2 * (x + 3)? No!
    // Let me trace: parseRightInfix("+") calls parseRightInfix("*") as subparser
    // parseRightInfix("*") parses "2" "*" "x", sees "+", stops, returns (2*x)
    // Then parseRightInfix("+") sees (2*x) "+" "3", continues
    // Result should be: 2 * x + 3 = (2 * x) + 3
    // But wait, + is right-assoc, so single + doesn't matter
    // Let me just test it
    val result = parseFromString("2 * x + 3")
    // Based on precedence: * > +, should be: (2 * x) + 3
    // But + is right-assoc, and calls * as subparser
    // So first parse with * subparser: gets (2 * x)
    // Then sees +, so continues: (2 * x) + 3
    // But right-assoc would make it: 2 * (x + 3)?
    // No! Right-assoc only matters for SAME operator
    // For different precedence: higher binds first
    // Answer: Fn("+", List(Fn("*", List(Fn("2"), Var("x"))), Fn("3")))
    assertEquals(
      result,
      Fn("+", List(
        Fn("*", List(Fn("2", List()), Var("x"))),
        Fn("3", List())
      ))
    )
  }
  
  test("parse list construction: 1 :: 2 :: nil") {
    val result = parseFromString("1 :: 2 :: nil")
    // :: is right-associative: 1 :: (2 :: nil)
    assertEquals(
      result,
      Fn("::", List(
        Fn("1", List()),
        Fn("::", List(
          Fn("2", List()),
          Fn("nil", List())
        ))
      ))
    )
  }
  
  test("fail on empty input") {
    intercept[Exception] {
      parseFromString("")
    }
  }
  
  test("fail on unparsed input") {
    intercept[Exception] {
      parseFromString("x + y )")
    }
  }
  
  test("fail on missing closing paren in function") {
    intercept[Exception] {
      parseFromString("f( x")
    }
  }
  
  test("fail on missing comma in function args") {
    intercept[Exception] {
      parseFromString("f(x y)")
    }
  }
  
  test("parse multiple unary minus") {
    val result = parseFromString("- - x")
    assertEquals(result, Fn("-", List(Fn("-", List(Var("x"))))))
  }
  
  test("parse mixed operators with all precedence levels") {
    // x :: y + z - w * u / v ^ 2
    // Precedence (low to high): :: < + < - < * < / < ^
    // So: x :: (y + (z - (w * (u / (v ^ 2)))))
    val result = parseFromString("x :: y + z - w * u / v ^ 2")
    assertEquals(
      result,
      Fn("::", List(
        Var("x"),
        Fn("+", List(
          Var("y"),
          Fn("-", List(
            Var("z"),
            Fn("*", List(
              Var("w"),
              Fn("/", List(
                Var("u"),
                Fn("^", List(Var("v"), Fn("2", List())))
              ))
            ))
          ))
        ))
      ))
    )
  }
