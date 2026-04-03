package parser

import logic.Term
import logic.Term.*
import parser.Combinators.*
import util.StringUtil
import util.StringUtil.*

/** Term parser from fol.ml
  * 
  * This implements parsing of first-order logic terms with:
  * - Variables: x, y, z
  * - Constants: 42, nil (emitted as `Term.Const`; see [[isConstName]])
  * - Functions: f(x, y), +(x, y)
  * - 6 levels of infix operators with different associativity
  * 
  * OCaml implementation shows sophisticated precedence handling:
  *   parse_term chains 6 levels of operators from lowest to highest precedence
  *
  * Intentional deviations from Harrison OCaml are documented in [[isConstName]]
  * and [[parseAtomicTerm]].
  */
object TermParser:
  
  /** Check if a string is a constant name
    * 
    * OCaml implementation:
    *   let is_const_name s = forall numeric (explode s) or s = "nil"
    * 
    * A constant is either:
    * - All numeric digits (e.g., "42", "0", "10000000")
    * - The special name "nil"
    * - A decimal literal (e.g., "0.05", "3.14") — intentional deviation from
    *   Harrison OCaml (OCaml tokeniser keeps "." symbolic so decimals never
    *   appear as single tokens there; this library's B-lexer merges them upstream
    *   so TermParser must recognise the merged form as a constant).
    */
  private def isConstName(s: String): Boolean =
    s.forall(numeric) || s == "nil" || StringUtil.isDecimalLiteral(s)
  
  /** Parse atomic term (highest precedence / base case)
    * 
    * OCaml implementation:
    *   let rec parse_atomic_term vs inp =
    *     match inp with
    *       [] -> failwith "term expected"
    *     | "("::rest -> parse_bracketed (parse_term vs) ")" rest
    *     | "-"::rest -> papply (fun t -> Fn("-",[t])) (parse_atomic_term vs rest)
    *     | f::"("::")"::rest -> Fn(f,[]),rest
    *     | f::"("::rest ->
    *         papply (fun args -> Fn(f,args))
    *                (parse_bracketed (parse_list "," (parse_term vs)) ")" rest)
    *     | a::rest ->
    *         (if is_const_name a & not(mem a vs) then Fn(a,[]) else Var a),rest
    * 
    * This handles:
    * 1. Parenthesized terms: (x + y)
    * 2. Unary minus: -x
    * 3. Function with no args: f()
    * 4. Function with args: f(x, y, z)
    * 5. Variables or constants
    * 
    * @param vs List of bound variables (for distinguishing vars from constants)
    * @param inp Token stream
    */
  def parseAtomicTerm(vs: List[String])(inp: List[String]): ParseResult[Term] =
    inp match
      case Nil =>
        throw new Exception("term expected")
      
      case "(" :: rest =>
        // Parenthesized term
        parseBracketed(parseTerm(vs), ")")(rest)
      
      case "-" :: rest =>
        // Unary minus
        papply((t: Term) => Fn("-", List(t)))(parseAtomicTerm(vs)(rest))
      
      case f :: "(" :: ")" :: rest =>
        // Function with no arguments: f()
        (Fn(f, List()), rest)
      
      case f :: "(" :: rest =>
        // Function with arguments: f(arg1, arg2, ...)
        papply((args: List[Term]) => Fn(f, args))(
          parseBracketed(
            parseList(",")(parseTerm(vs)),
            ")"
          )(rest)
        )
      
      case a :: rest =>
        // Variable or constant
        // It's a constant if: all numeric OR "nil" AND not in bound variables.
        // NOTE: intentional deviation from Harrison OCaml — OCaml emits Fn(a,[]) for
        // zero-arity constants because its `term` type has no Const variant. This
        // Scala port adds Term.Const precisely so QueryBinder can distinguish inline
        // literals from zero-arity function applications at the bind phase.
        if isConstName(a) && !vs.contains(a) then
          (Term.Const(a), rest)
        else
          (Var(a), rest)
  
  /** Parse term with all infix operators
    * 
    * OCaml implementation:
    *   and parse_term vs inp =
    *     parse_right_infix "::" (fun (e1,e2) -> Fn("::",[e1;e2]))
    *       (parse_right_infix "+" (fun (e1,e2) -> Fn("+",[e1;e2]))
    *          (parse_left_infix "-" (fun (e1,e2) -> Fn("-",[e1;e2]))
    *             (parse_right_infix "*" (fun (e1,e2) -> Fn("*",[e1;e2]))
    *                (parse_left_infix "/" (fun (e1,e2) -> Fn("/",[e1;e2]))
    *                   (parse_left_infix "^" (fun (e1,e2) -> Fn("^",[e1;e2]))
    *                      (parse_atomic_term vs)))))) inp
    * 
    * SIX LEVELS OF OPERATORS (lowest to highest precedence):
    * 1. ::  (cons, list constructor)    - RIGHT associative
    * 2. +   (addition)                  - RIGHT associative
    * 3. -   (subtraction)               - LEFT associative
    * 4. *   (multiplication)            - RIGHT associative
    * 5. /   (division)                  - LEFT associative
    * 6. ^   (exponentiation)            - LEFT associative
    * 
    * Note: This is different from standard math!
    * - OCaml makes + right-associative (unusual)
    * - ^ is left-associative here (usually right in math)
    * 
    * Each level calls the next higher precedence as its subparser.
    * 
    * Example: x + y * z ^ 2
    * Parses as: x + (y * (z ^ 2))
    */
  def parseTerm(vs: List[String])(inp: List[String]): ParseResult[Term] =
    // Chain operators from lowest to highest precedence
    parseRightInfix("::", (e1: Term, e2: Term) => Fn("::", List(e1, e2)))(
      parseRightInfix("+", (e1: Term, e2: Term) => Fn("+", List(e1, e2)))(
        parseLeftInfix("-", (e1: Term, e2: Term) => Fn("-", List(e1, e2)))(
          parseRightInfix("*", (e1: Term, e2: Term) => Fn("*", List(e1, e2)))(
            parseLeftInfix("/", (e1: Term, e2: Term) => Fn("/", List(e1, e2)))(
              parseLeftInfix("^", (e1: Term, e2: Term) => Fn("^", List(e1, e2)))(
                parseAtomicTerm(vs)
              )
            )
          )
        )
      )
    )(inp)
  
  /** Convenience wrapper: parse term from token list with empty variable context
    * 
    * OCaml: let parset = make_parser (parse_term [])
    */
  def parse(tokens: List[String]): ParseResult[Term] =
    parseTerm(List())(tokens)
  
  /** Parse term from string (with lexing and exhaustion checking)
    * 
    * Convenience method that:
    * 1. Lexes the input
    * 2. Parses the term
    * 3. Checks all input was consumed
    */
  def parseFromString(s: String): Term =
    import lexer.Lexer.lex
    val tokens = lex(explode(s))
    val (result, rest) = parse(tokens)
    if rest.isEmpty then
      result
    else
      throw new Exception(s"Unparsed input: ${rest.mkString(" ")}")
