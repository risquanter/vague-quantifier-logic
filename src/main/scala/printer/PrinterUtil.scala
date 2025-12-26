package printer

/** Shared utilities for pretty printing with precedence handling.
  * 
  * Provides generic functions for:
  * - Parenthesization based on precedence
  * - Binary infix operator printing
  * - Operator classification
  * 
  * Used by FOLPrinter and potentially future printers (vague queries, etc.)
  * All functions are pure with no side effects.
  */
object PrinterUtil:
  
  /** Add parentheses to expression if needed based on precedence.
    * 
    * Rule: Child expression needs parens if parent context has higher precedence.
    * 
    * Examples:
    * - parenthesize(8, 6, "P /\\ Q") = "P /\\ Q" (no parens needed)
    * - parenthesize(6, 8, "P \\/ Q") = "(P \\/ Q)" (parens needed)
    * 
    * @param childPrec Precedence level of the child expression
    * @param parentPrec Precedence level of the parent context
    * @param expr String representation of child expression
    * @return Expression with parens added if parentPrec > childPrec
    */
  def parenthesize(childPrec: Int, parentPrec: Int, expr: String): String =
    if parentPrec > childPrec then s"($expr)" else expr
  
  /** Print binary infix operator with proper precedence and associativity.
    * 
    * Handles parenthesization for both left and right operands based on:
    * - Operator precedence (opPrec)
    * - Context precedence (contextPrec)
    * - Associativity (rightAssoc parameter)
    * 
    * For right-associative operators (most logical and arithmetic ops):
    * - Left operand uses higher precedence (forces parens if same op)
    * - Right operand uses same precedence (allows chaining)
    * 
    * For left-associative operators:
    * - Left operand uses same precedence (allows chaining)
    * - Right operand uses higher precedence (forces parens if same op)
    * 
    * Examples (right-associative):
    * - "P ==> Q ==> R" prints without parens (right operand at same prec)
    * - "(P ==> Q) ==> R" prints with parens (left operand needs higher prec)
    * 
    * @param opPrec Precedence level of this operator
    * @param op Operator symbol (e.g., "==>", "/\\", "+")
    * @param leftPrinter Function to print left operand at given precedence
    * @param rightPrinter Function to print right operand at given precedence
    * @param contextPrec Precedence of surrounding context
    * @param rightAssoc Is this operator right-associative? (default: true)
    * @return String representation with proper parenthesization
    */
  def printBinaryInfix(
    opPrec: Int,
    op: String,
    leftPrinter: Int => String,
    rightPrinter: Int => String,
    contextPrec: Int,
    rightAssoc: Boolean = true
  ): String =
    // For right-associative: left needs higher prec, right uses same prec
    // For left-associative: left uses same prec, right needs higher prec
    val leftPrec = if rightAssoc then opPrec + 1 else opPrec
    val rightPrec = if rightAssoc then opPrec else opPrec + 1
    
    val leftStr = leftPrinter(leftPrec)
    val rightStr = rightPrinter(rightPrec)
    val result = s"$leftStr $op $rightStr"
    
    parenthesize(opPrec, contextPrec, result)
  
  /** Check if a name is an infix operator.
    * 
    * Tests if the name appears in the given set of infix operators.
    * Used to decide between infix notation (x + y) vs prefix notation (+(x, y)).
    * 
    * @param name Function or operator name
    * @param infixOps Set of names that should be printed infix
    * @return true if name should use infix notation
    */
  def isInfixOp(name: String, infixOps: Set[String]): Boolean =
    infixOps.contains(name)
