package parser

/** Parser combinator infrastructure following OCaml style from formulas.ml
  * 
  * Key concept: Parsers consume token lists and return (result, remaining_tokens).
  * This is the foundation of the entire parsing approach.
  */
object Combinators:
  
  /** Parse result type: (parsed_value, remaining_tokens)
    * 
    * OCaml uses tuples directly: 'a * string list
    * Scala: we make it explicit with a type alias
    */
  type ParseResult[A] = (A, List[String])
  
  /** Generic iterated infix parser (OCaml: parse_ginfix)
    * 
    * THIS IS THE HEART OF THE OCAML PARSING APPROACH!
    * 
    * OCaml implementation:
    *   let rec parse_ginfix opsym opupdate sof subparser inp =
    *     let e1,inp1 = subparser inp in
    *     if inp1 <> [] & hd inp1 = opsym then
    *        parse_ginfix opsym opupdate (opupdate sof e1) subparser (tl inp1)
    *     else sof e1,inp1
    * 
    * What it does:
    * - Parses a sequence of expressions separated by an operator
    * - Uses an accumulator function to build the result
    * - Handles both left and right associativity through opupdate
    * 
    * Parameters:
    * @param opsym - The operator symbol to look for (e.g., "+", "/\\")
    * @param opupdate - Function to update accumulator: (accumulator, new_elem, next_elem) => new_accumulator
    * @param sof - "Start of function" - initial accumulator
    * @param subparser - Parser for individual elements
    * @param inp - Input token stream
    * 
    * Example for left-associative addition (1 + 2 + 3):
    *   opupdate = (f, e1, e2) => (x => Add(f(x), e2))
    *   Initial: identity
    *   After "1 +": f = (x => x), e1 = 1 => new f = (x => Add(x, ...))
    *   After "2 +": accumulates left-to-right
    *   Result: Add(Add(1, 2), 3)
    * 
    * The genius: opupdate controls associativity WITHOUT changing the algorithm!
    */
  def parseGinfix[A](
    opsym: String,
    opupdate: (A => A, A) => (A => A),
    sof: A => A,
    subparser: List[String] => ParseResult[A]
  )(inp: List[String]): ParseResult[A] =
    val (e1, inp1) = subparser(inp)
    
    if inp1.nonEmpty && inp1.head == opsym then
      // Found the operator - recurse
      parseGinfix(opsym, opupdate, opupdate(sof, e1), subparser)(inp1.tail)
    else
      // No more operators - apply accumulated function
      (sof(e1), inp1)
  
  /** Parse left-associative infix operator (OCaml: parse_left_infix)
    * 
    * OCaml implementation:
    *   let parse_left_infix opsym opcon =
    *     parse_ginfix opsym (fun f e1 e2 -> opcon(f e1,e2)) (fun x -> x)
    * 
    * For expressions like: e1 op e2 op e3
    * Result: opcon(opcon(e1, e2), e3) - left-associative
    * 
    * Example: 1 - 2 - 3 = (1 - 2) - 3 = -4
    */
  def parseLeftInfix[A](
    opsym: String,
    opcon: (A, A) => A
  )(subparser: List[String] => ParseResult[A])(inp: List[String]): ParseResult[A] =
    parseGinfix(
      opsym,
      (f: A => A, e1: A) => ((x: A) => opcon(f(e1), x)),
      identity[A],
      subparser
    )(inp)
  
  /** Parse right-associative infix operator (OCaml: parse_right_infix)
    * 
    * OCaml implementation:
    *   let parse_right_infix opsym opcon =
    *     parse_ginfix opsym (fun f e1 e2 -> f(opcon(e1,e2))) (fun x -> x)
    * 
    * For expressions like: e1 op e2 op e3
    * Result: opcon(e1, opcon(e2, e3)) - right-associative
    * 
    * Example: 2 ^ 3 ^ 2 = 2 ^ (3 ^ 2) = 2 ^ 9 = 512
    * 
    * Note the difference from left-associative:
    * - Left builds: opcon(opcon(e1, e2), e3)
    * - Right builds: opcon(e1, opcon(e2, e3))
    */
  def parseRightInfix[A](
    opsym: String,
    opcon: (A, A) => A
  )(subparser: List[String] => ParseResult[A])(inp: List[String]): ParseResult[A] =
    parseGinfix(
      opsym,
      (f: A => A, e1: A) => ((x: A) => f(opcon(e1, x))),
      identity[A],
      subparser
    )(inp)
  
  /** Parse comma-separated list (OCaml: parse_list)
    * 
    * OCaml implementation:
    *   let parse_list opsym =
    *     parse_ginfix opsym (fun f e1 e2 -> (f e1)@[e2]) (fun x -> [x])
    * 
    * For: e1, e2, e3
    * Result: List(e1, e2, e3)
    */
  def parseList[A](
    opsym: String
  )(subparser: List[String] => ParseResult[A])(inp: List[String]): ParseResult[List[A]] =
    parseGinfix[List[A]](
      opsym,
      (f: List[A] => List[A], e1: List[A]) => ((x: List[A]) => f(e1) :+ x.head),
      (x: List[A]) => List(x.head),
      (inp: List[String]) => {
        val (result, rest) = subparser(inp)
        (List(result), rest)
      }
    )(inp)
  
  /** Apply function to parse result (OCaml: papply)
    * 
    * OCaml implementation:
    *   let papply f (ast,rest) = (f ast,rest)
    * 
    * Transforms the parsed value while keeping remaining tokens unchanged.
    * This is like functor map for parsers.
    */
  def papply[A, B](f: A => B)(result: ParseResult[A]): ParseResult[B] =
    val (ast, rest) = result
    (f(ast), rest)
  
  /** Check if next token matches (OCaml: nextin)
    * 
    * OCaml implementation:
    *   let nextin inp tok = inp <> [] & hd inp = tok
    * 
    * Returns true if the next token in the stream equals tok.
    */
  def nextin(inp: List[String], tok: String): Boolean =
    inp.nonEmpty && inp.head == tok
  
  /** Parse bracketed expression (OCaml: parse_bracketed)
    * 
    * OCaml implementation:
    *   let parse_bracketed subparser cbra inp =
    *     let ast,rest = subparser inp in
    *     if nextin rest cbra then ast,tl rest
    *     else failwith "Closing bracket expected"
    * 
    * Parses content with subparser, then expects closing bracket.
    * 
    * Example: parse_bracketed(parseExpr, ")", "(", "x", "+", "1", ")")
    * Result: (parsed_expr, remaining_tokens)
    */
  def parseBracketed[A](
    subparser: List[String] => ParseResult[A],
    cbra: String
  )(inp: List[String]): ParseResult[A] =
    val (ast, rest) = subparser(inp)
    if nextin(rest, cbra) then
      (ast, rest.tail)
    else
      throw new Exception(s"Closing bracket '$cbra' expected, but got: ${rest.headOption.getOrElse("end of input")}")
  
  /** Helper to check if token list is empty */
  def isEmpty(inp: List[String]): Boolean = inp.isEmpty
  
  /** Helper to get head of token list safely */
  def headOption(inp: List[String]): Option[String] = inp.headOption
