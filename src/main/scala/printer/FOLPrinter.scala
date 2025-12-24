package printer

import logic.{FOL, Formula, Term}

/** Pretty printer for FOL formulas and terms - Phase 11
  * 
  * From fol.ml print_term and print_fol_formula functions.
  * Converts parsed structures back into readable strings.
  * 
  * Key features:
  * - Handles operator precedence and parenthesization
  * - Supports infix notation for common operators
  * - Produces parseable output (round-trip property)
  * 
  * OCaml reference functions:
  * - print_term: prints terms with function application
  * - print_fol_formula: prints FOL formulas with proper precedence
  * - print_atom: prints atomic formulas (predicates and relations)
  */
object FOLPrinter:
  
  // ==================== Term Printing ====================
  
  /** Print a term as a string
    * 
    * OCaml implementation (simplified):
    *   let rec print_term prec fm =
    *     match fm with
    *       Var x -> print_string x
    *     | Fn(f,[]) -> print_string f
    *     | Fn(f,[t]) -> (print_string f; print_string "("; 
    *                     print_term 0 t; print_string ")")
    *     | Fn(f,args) -> (print_string f; print_string "(";
    *                      print_term 0 (hd args);
    *                      do_list (fun t -> print_string ","; print_term 0 t) (tl args);
    *                      print_string ")")
    * 
    * Examples:
    *   Var("x") => "x"
    *   Fn("f", []) => "f"
    *   Fn("f", [Var("x")]) => "f(x)"
    *   Fn("f", [Var("x"), Var("y")]) => "f(x, y)"
    *   Fn("+", [Var("x"), Fn("*", [Var("y"), Var("z")])]) => "x + y * z"
    * 
    * @param tm The term to print
    * @param prec Current precedence level (for parenthesization)
    * @return String representation
    */
  def printTerm(tm: Term, prec: Int = 0): String =
    tm match
      case Term.Var(x) => x
      case Term.Const(c) => c.toString
      case Term.Fn(f, Nil) => f
      case Term.Fn(f, args) =>
        // Check if it's an infix operator
        if isInfixOp(f) && args.length == 2 then
          printInfixTerm(f, args(0), args(1), prec)
        else
          // Function application: f(arg1, arg2, ...)
          s"$f(${args.map(printTerm(_, 0)).mkString(", ")})"
  
  /** Check if a function name is an infix operator */
  private def isInfixOp(f: String): Boolean =
    f match
      case "+" | "-" | "*" | "/" | "^" | "::" => true
      case _ => false
  
  /** Get precedence level for infix operators
    * 
    * Matches the precedence from TermParser:
    * 1. :: (right assoc)
    * 2. + - (right assoc)
    * 3. * / (right assoc)  
    * 4. ^ (left assoc)
    * 5. Function application (highest)
    */
  private def infixPrec(op: String): Int =
    op match
      case "::" => 1
      case "+" | "-" => 2
      case "*" | "/" => 3
      case "^" => 4
      case _ => 5
  
  /** Print an infix term with proper precedence and parentheses */
  private def printInfixTerm(op: String, left: Term, right: Term, prec: Int): String =
    val opPrec = infixPrec(op)
    val leftStr = printTerm(left, opPrec)
    val rightStr = printTerm(right, opPrec)
    val result = s"$leftStr $op $rightStr"
    if prec > opPrec then s"($result)" else result
  
  // ==================== FOL Atom Printing ====================
  
  /** Print a FOL atom (predicate or infix relation)
    * 
    * Examples:
    *   FOL("P", [Var("x")]) => "P(x)"
    *   FOL("=", [Var("x"), Var("y")]) => "x = y"
    *   FOL("<", [Var("x"), Fn("+", [Var("y"), Const(1)])]) => "x < y + 1"
    */
  def printAtom(atom: FOL): String =
    val FOL(pred, args) = atom
    
    // Check if it's an infix relation
    if isInfixRel(pred) && args.length == 2 then
      s"${printTerm(args(0))} $pred ${printTerm(args(1))}"
    else if args.isEmpty then
      pred
    else
      s"$pred(${args.map(printTerm(_)).mkString(", ")})"
  
  /** Check if a predicate is an infix relation */
  private def isInfixRel(pred: String): Boolean =
    pred match
      case "=" | "<" | "<=" | ">" | ">=" => true
      case _ => false
  
  // ==================== Formula Printing ====================
  
  /** Print a FOL formula with proper precedence
    * 
    * OCaml implementation (simplified structure):
    *   let rec print_formula prec fm =
    *     match fm with
    *       False -> print_string "false"
    *     | True -> print_string "true"
    *     | Atom(R(p,args)) -> print_atom ...
    *     | Not(p) -> print_prefix 10 "~" p
    *     | And(p,q) -> print_infix 8 "/\\" p q
    *     | Or(p,q) -> print_infix 6 "\\/" p q
    *     | Imp(p,q) -> print_infix 4 "==>" p q
    *     | Iff(p,q) -> print_infix 2 "<=>" p q
    *     | Forall(x,p) -> print_qnt "forall" x p
    *     | Exists(x,p) -> print_qnt "exists" x p
    * 
    * Precedence levels (higher = binds tighter):
    * 0. Quantifiers (lowest)
    * 2. <=> (iff)
    * 4. ==> (imp)
    * 6. \/ (or)
    * 8. /\ (and)
    * 10. ~ (not)
    * 11. Atoms (highest)
    * 
    * @param fm Formula to print
    * @param prec Current precedence level
    * @return String representation
    */
  def printFormula(fm: Formula[FOL], prec: Int = 0): String =
    fm match
      case Formula.False => "false"
      case Formula.True => "true"
      case Formula.Atom(atom) => printAtom(atom)
      case Formula.Not(p) =>
        printPrefix(10, "~", p, prec)
      case Formula.And(p, q) =>
        printInfix(8, "/\\", p, q, prec)
      case Formula.Or(p, q) =>
        printInfix(6, "\\/", p, q, prec)
      case Formula.Imp(p, q) =>
        printInfix(4, "==>", p, q, prec)
      case Formula.Iff(p, q) =>
        printInfix(2, "<=>", p, q, prec)
      case Formula.Forall(x, p) =>
        printQuantifier("forall", x, p, prec)
      case Formula.Exists(x, p) =>
        printQuantifier("exists", x, p, prec)
  
  /** Print a prefix operator (negation) */
  private def printPrefix(opPrec: Int, op: String, p: Formula[FOL], prec: Int): String =
    val result = s"$op${printFormula(p, opPrec)}"
    if prec > opPrec then s"($result)" else result
  
  /** Print an infix binary operator
    * 
    * For RIGHT-associative operators (==>, <=>, \/, /\), we need to:
    * - Print left side with HIGHER precedence (to force parens if needed)
    * - Print right side with SAME precedence (allows right-associativity)
    * 
    * This ensures: (P ==> Q) ==> R prints with parens
    * But: P ==> (Q ==> R) prints without inner parens as: P ==> Q ==> R
    */
  private def printInfix(
    opPrec: Int, 
    op: String, 
    left: Formula[FOL], 
    right: Formula[FOL], 
    prec: Int
  ): String =
    // For right-associative operators, left side needs higher precedence
    val leftStr = printFormula(left, opPrec + 1)
    val rightStr = printFormula(right, opPrec)
    val result = s"$leftStr $op $rightStr"
    if prec > opPrec then s"($result)" else result
  
  /** Print a quantified formula
    * 
    * Examples:
    *   Forall("x", Atom(P(x))) => "forall x. P(x)"
    *   Exists("y", x < y) => "exists y. x < y"
    * 
    * Multiple quantifiers of same type are grouped:
    *   Forall("x", Forall("y", ...)) => "forall x y. ..."
    */
  private def printQuantifier(
    quant: String, 
    x: String, 
    body: Formula[FOL], 
    prec: Int
  ): String =
    // Collect consecutive quantifiers of same type
    val (vars, innerBody) = collectQuantifiers(quant, x, body)
    val result = s"$quant ${vars.mkString(" ")}. ${printFormula(innerBody, 0)}"
    if prec > 0 then s"($result)" else result
  
  /** Collect consecutive quantifiers of the same type
    * 
    * Converts: forall x. forall y. forall z. P(x,y,z)
    * Into: (List("x", "y", "z"), P(x,y,z))
    */
  private def collectQuantifiers(
    quant: String, 
    firstVar: String, 
    body: Formula[FOL]
  ): (List[String], Formula[FOL]) =
    body match
      case Formula.Forall(x, p) if quant == "forall" =>
        val (vars, innerBody) = collectQuantifiers(quant, x, p)
        (firstVar :: vars, innerBody)
      case Formula.Exists(x, p) if quant == "exists" =>
        val (vars, innerBody) = collectQuantifiers(quant, x, p)
        (firstVar :: vars, innerBody)
      case _ =>
        (List(firstVar), body)
  
  // ==================== Convenience Methods ====================
  
  /** Print a formula to string (default precedence) */
  def print(fm: Formula[FOL]): String = printFormula(fm, 0)
  
  /** Print a term to string (default precedence) */
  def print(tm: Term): String = printTerm(tm, 0)
