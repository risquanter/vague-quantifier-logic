package printer

import munit.FunSuite
import printer.PrinterUtil.*

/** Test suite for PrinterUtil
  * 
  * Tests the shared precedence and infix printing utilities used by FOLPrinter.
  * These are pure functions with no side effects, making them easy to test.
  */
class PrinterUtilSpec extends FunSuite:
  
  // ==================== parenthesize Tests ====================
  
  test("parenthesize: no parens when child has higher precedence") {
    // Child precedence 10, parent precedence 5
    // Child binds tighter, no parens needed
    assertEquals(parenthesize(10, 5, "~P"), "~P")
  }
  
  test("parenthesize: no parens when equal precedence") {
    assertEquals(parenthesize(8, 8, "P /\\ Q"), "P /\\ Q")
  }
  
  test("parenthesize: add parens when parent has higher precedence") {
    // Child precedence 6, parent precedence 8
    // Parent binds tighter, need parens
    assertEquals(parenthesize(6, 8, "P \\/ Q"), "(P \\/ Q)")
  }
  
  test("parenthesize: boundary case - parent exactly one higher") {
    assertEquals(parenthesize(7, 8, "expr"), "(expr)")
  }
  
  test("parenthesize: zero precedence (quantifiers)") {
    // Quantifiers at precedence 0, any non-zero parent needs parens
    assertEquals(parenthesize(0, 1, "forall x. P(x)"), "(forall x. P(x))")
    assertEquals(parenthesize(0, 0, "forall x. P(x)"), "forall x. P(x)")
  }
  
  test("parenthesize: preserves inner structure") {
    val complexExpr = "P /\\ Q \\/ R"
    assertEquals(parenthesize(10, 5, complexExpr), complexExpr)
    assertEquals(parenthesize(5, 10, complexExpr), s"($complexExpr)")
  }
  
  // ==================== printBinaryInfix Tests ====================
  
  test("printBinaryInfix: simple right-associative operator") {
    val result = printBinaryInfix(
      opPrec = 4,
      op = "==>",
      leftPrinter = prec => "P",
      rightPrinter = prec => "Q",
      contextPrec = 0,
      rightAssoc = true
    )
    assertEquals(result, "P ==> Q")
  }
  
  test("printBinaryInfix: right-associative with parenthesization") {
    // Context at higher precedence forces parens
    val result = printBinaryInfix(
      opPrec = 4,
      op = "==>",
      leftPrinter = prec => "P",
      rightPrinter = prec => "Q",
      contextPrec = 6,
      rightAssoc = true
    )
    assertEquals(result, "(P ==> Q)")
  }
  
  test("printBinaryInfix: right-associative left side gets higher prec") {
    var leftPrec = 0
    var rightPrec = 0
    
    printBinaryInfix(
      opPrec = 4,
      op = "==>",
      leftPrinter = prec => { leftPrec = prec; "P" },
      rightPrinter = prec => { rightPrec = prec; "Q" },
      contextPrec = 0,
      rightAssoc = true
    )
    
    assertEquals(leftPrec, 5)   // opPrec + 1
    assertEquals(rightPrec, 4)  // opPrec
  }
  
  test("printBinaryInfix: left-associative operator") {
    var leftPrec = 0
    var rightPrec = 0
    
    printBinaryInfix(
      opPrec = 3,
      op = "-",
      leftPrinter = prec => { leftPrec = prec; "x" },
      rightPrinter = prec => { rightPrec = prec; "y" },
      contextPrec = 0,
      rightAssoc = false
    )
    
    assertEquals(leftPrec, 3)   // opPrec
    assertEquals(rightPrec, 4)  // opPrec + 1
  }
  
  test("printBinaryInfix: nested expressions with precedence") {
    // Simulate: (P /\\ Q) \\/ R where /\\ has prec 8, \\/ has prec 6
    val result = printBinaryInfix(
      opPrec = 6,
      op = "\\/",
      leftPrinter = prec => {
        // Left is P /\\ Q at prec 8, parent wants prec 7
        // Since 8 > 7, no parens on P /\\ Q
        "P /\\ Q"
      },
      rightPrinter = prec => "R",
      contextPrec = 0,
      rightAssoc = true
    )
    assertEquals(result, "P /\\ Q \\/ R")
  }
  
  test("printBinaryInfix: handles spaces in operator") {
    val result = printBinaryInfix(
      opPrec = 4,
      op = "==>",
      leftPrinter = _ => "P",
      rightPrinter = _ => "Q",
      contextPrec = 0,
      rightAssoc = true
    )
    assertEquals(result, "P ==> Q")
  }
  
  test("printBinaryInfix: empty operands") {
    val result = printBinaryInfix(
      opPrec = 5,
      op = "+",
      leftPrinter = _ => "",
      rightPrinter = _ => "",
      contextPrec = 0,
      rightAssoc = true
    )
    assertEquals(result, " + ")
  }
  
  // ==================== isInfixOp Tests ====================
  
  test("isInfixOp: operator in set") {
    val ops = Set("+", "-", "*", "/")
    assert(isInfixOp("+", ops))
    assert(isInfixOp("*", ops))
  }
  
  test("isInfixOp: operator not in set") {
    val ops = Set("+", "-", "*", "/")
    assert(!isInfixOp("^", ops))
    assert(!isInfixOp("f", ops))
  }
  
  test("isInfixOp: empty set") {
    val ops = Set.empty[String]
    assert(!isInfixOp("+", ops))
  }
  
  test("isInfixOp: case sensitivity") {
    val ops = Set("AND", "OR")
    assert(isInfixOp("AND", ops))
    assert(!isInfixOp("and", ops))  // Case matters
  }
  
  test("isInfixOp: multi-char operators") {
    val ops = Set("==>", "<=>", "/\\", "\\/")
    assert(isInfixOp("==>", ops))
    assert(isInfixOp("\\/", ops))
    assert(!isInfixOp("=>", ops))
  }
  
  // ==================== Integration Tests ====================
  
  test("integration: simulate formula printing precedence") {
    // Simulate printing: P ==> Q ==> R (right-associative)
    // Should print as: P ==> Q ==> R (no parens on right)
    
    val innerResult = printBinaryInfix(
      opPrec = 4,
      op = "==>",
      leftPrinter = _ => "Q",
      rightPrinter = _ => "R",
      contextPrec = 4,  // Same as outer
      rightAssoc = true
    )
    // Inner at prec 4, outer wants 4, so: Q ==> R (no parens)
    assertEquals(innerResult, "Q ==> R")
    
    val outerResult = printBinaryInfix(
      opPrec = 4,
      op = "==>",
      leftPrinter = _ => "P",
      rightPrinter = _ => innerResult,
      contextPrec = 0,
      rightAssoc = true
    )
    assertEquals(outerResult, "P ==> Q ==> R")
  }
  
  test("integration: simulate term printing with precedence") {
    // Simulate: x + y * z where + is prec 2, * is prec 3
    // Should print as: x + y * z (no parens needed)
    
    val multResult = printBinaryInfix(
      opPrec = 3,
      op = "*",
      leftPrinter = _ => "y",
      rightPrinter = _ => "z",
      contextPrec = 2,  // Being used in + context
      rightAssoc = true
    )
    // * has higher prec than +, no parens
    assertEquals(multResult, "y * z")
    
    val addResult = printBinaryInfix(
      opPrec = 2,
      op = "+",
      leftPrinter = _ => "x",
      rightPrinter = _ => multResult,
      contextPrec = 0,
      rightAssoc = true
    )
    assertEquals(addResult, "x + y * z")
  }
  
  test("integration: parentheses forced by outer precedence") {
    // Simulate: (x + y) * z where + is prec 2, * is prec 3
    // Outer * forces parens on inner +
    
    val addResult = printBinaryInfix(
      opPrec = 2,
      op = "+",
      leftPrinter = _ => "x",
      rightPrinter = _ => "y",
      contextPrec = 3,  // Being used in * context (higher prec)
      rightAssoc = true
    )
    assertEquals(addResult, "(x + y)")
  }
