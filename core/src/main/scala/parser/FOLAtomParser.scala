package parser

import logic.{FOL, Term, Formula}
import logic.Formula.*
import parser.Combinators.*
import parser.TermParser.*
import parser.FormulaParser.AtomParser
import util.StringUtil.*

/** FOL atom parser from fol.ml
  * 
  * This implements parsing of FOL atoms (predicates and relations):
  * - Infix relations: x < y, x = y, x <= y
  * - Predicates: P(x), Q(x, y, z)
  * - Nullary predicates: P
  * 
  * These atom parsers will be plugged into the generic FormulaParser
  * to parse complete FOL formulas.
  */
object FOLAtomParser:
  
  /** Parse infix relational atom
    * 
    * OCaml implementation:
    *   let parse_infix_atom vs inp =       
    *     let tm,rest = parse_term vs inp in
    *     if exists (nextin rest) ["="; "<"; "<="; ">"; ">="] then                     
    *           papply (fun tm' -> Atom(R(hd rest,[tm;tm'])))                          
    *                  (parse_term vs (tl rest))                                       
    *     else failwith ""
    * 
    * This tries to parse an infix relation like:
    * - x = y
    * - x < y
    * - x <= y
    * 
    * Strategy:
    * 1. Parse first term
    * 2. Check if next token is a relational operator
    * 3. If yes, parse second term and build relation
    * 4. If no, fail (let other parser handle it)
    * 
    * @param vs List of bound variables
    * @param inp Token stream
    * @return Parsed FOL atom wrapped in Atom()
    */
  def parseInfixAtom(vs: List[String], inp: List[String]): ParseResult[FOL] =
    val (tm, rest) = parseTerm(vs)(inp)
    
    // Check if next token is a relational operator
    val relOps = List("=", "<", "<=", ">", ">=")
    if rest.nonEmpty && relOps.contains(rest.head) then
      val op = rest.head
      // Parse second term
      papply((tm2: Term) => FOL(op, List(tm, tm2)))(parseTerm(vs)(rest.tail))
    else
      throw new Exception("Not an infix atom")
  
  /** Parse general atom (predicate)
    * 
    * OCaml implementation:
    *   let parse_atom vs inp =
    *     try parse_infix_atom vs inp with Failure _ ->                                
    *     match inp with                                                               
    *     | p::"("::")"::rest -> Atom(R(p,[])),rest                                    
    *     | p::"("::rest ->
    *         papply (fun args -> Atom(R(p,args)))
    *                (parse_bracketed (parse_list "," (parse_term vs)) ")" rest)
    *     | p::rest when p <> "(" -> Atom(R(p,[])),rest
    *     | _ -> failwith "parse_atom"
    * 
    * This handles:
    * 1. Try infix atom first (delegated to parseInfixAtom)
    * 2. Predicate with no args: P()
    * 3. Predicate with args: P(x, y, z)
    * 4. Nullary predicate: P (no parens)
    * 
    * @param vs List of bound variables
    * @param inp Token stream
    * @return Parsed FOL atom
    */
  def parseAtom(vs: List[String], inp: List[String]): ParseResult[FOL] =
    // Try infix atom first
    try
      parseInfixAtom(vs, inp)
    catch
      case _: Exception =>
        // Not infix, try predicate forms
        inp match
          case Nil =>
            throw new Exception("parse_atom: expected atom")
          
          case p :: "(" :: ")" :: rest =>
            // Predicate with no arguments: P()
            (FOL(p, List()), rest)
          
          case p :: "(" :: rest =>
            // Predicate with arguments: P(x, y, z)
            papply((args: List[Term]) => FOL(p, args))(
              parseBracketed(
                parseList(",")(parseTerm(vs)),
                ")"
              )(rest)
            )
          
          case p :: rest if p != "(" =>
            // Nullary predicate: P (no parentheses)
            (FOL(p, List()), rest)
          
          case _ =>
            throw new Exception("parse_atom")
  
  /** Complete FOL formula parser
    * 
    * OCaml implementation:
    *   let parse = make_parser                                                        
    *     (parse_formula (parse_infix_atom,parse_atom) [])
    * 
    * This wires together:
    * - FormulaParser (generic formula parsing)
    * - parseInfixAtom (for infix relations)
    * - parseAtom (for predicates)
    * 
    * The result parses complete FOL formulas like:
    * - forall x. exists y. x < y
    * - P(x) /\ Q(y) ==> R(x, y)
    */
  def parse(tokens: List[String]): ParseResult[Formula[FOL]] =
    FormulaParser.parse(parseInfixAtom, parseAtom)(tokens)
  
  /** Parse FOL formula from string (with lexing and exhaustion checking)
    * 
    * Convenience method that:
    * 1. Lexes the input
    * 2. Parses the formula
    * 3. Checks all input was consumed
    */
  def parseFromString(s: String): Formula[FOL] =
    import lexer.Lexer.lex
    val tokens = lex(explode(s))
    val (result, rest) = parse(tokens)
    if rest.isEmpty then
      result
    else
      throw new Exception(s"Unparsed input: ${rest.mkString(" ")}")
