package parser

import logic.Formula
import logic.Formula.*
import parser.Combinators.*

/** Generic formula parser from formulas.ml
  * 
  * This is the KEY ABSTRACTION in the OCaml parsing approach!
  * 
  * The parser is PARAMETRIZED by atom parsers:
  * - ifn: infix atom parser (for things like "x < y")
  * - afn: general atom parser (for things like "P(x)")
  * 
  * This allows the same formula parsing logic to work with:
  * - Propositional logic (atoms are just strings)
  * - First-order logic (atoms are predicates with terms)
  * - Any other logic you want!
  * 
  * OCaml type signatures:
  *   parse_atomic_formula : 
  *     (string list -> 'a * string list) * 
  *     (string list -> 'a * string list) ->
  *     string list -> 'a formula * string list
  */
object FormulaParser:
  
  /** Type alias for atom parsers
    * 
    * An atom parser takes:
    * - vs: List of bound variables (for scope tracking)
    * - inp: Token stream
    * Returns: (parsed atom, remaining tokens)
    */
  type AtomParser[A] = (List[String], List[String]) => ParseResult[A]
  
  /** Parse atomic formula (base case for formula parsing)
    * 
    * OCaml implementation:
    *   let rec parse_atomic_formula (ifn,afn) vs inp =
    *     match inp with
    *       [] -> failwith "formula expected"
    *     | "false"::rest -> False,rest
    *     | "true"::rest -> True,rest
    *     | "("::rest -> (try ifn vs inp with Failure _ ->
    *                     parse_bracketed (parse_formula (ifn,afn) vs) ")" rest)
    *     | "~"::rest -> papply (fun p -> Not p)
    *                           (parse_atomic_formula (ifn,afn) vs rest)
    *     | "forall"::x::rest ->
    *           parse_quant (ifn,afn) (x::vs) (fun (x,p) -> Forall(x,p)) x rest
    *     | "exists"::x::rest ->
    *           parse_quant (ifn,afn) (x::vs) (fun (x,p) -> Exists(x,p)) x rest
    *     | _ -> afn vs inp
    * 
    * This handles:
    * 1. true/false constants
    * 2. Parenthesized formulas (with infix atom fallback)
    * 3. Negation
    * 4. Quantifiers (forall/exists)
    * 5. Atoms (delegated to afn)
    * 
    * @param ifn Infix atom parser (tries to parse infix relations)
    * @param afn General atom parser (fallback for atoms)
    * @param vs List of bound variables (for scope tracking)
    * @param inp Token stream
    */
  def parseAtomicFormula[A](
    ifn: AtomParser[A],
    afn: AtomParser[A]
  )(vs: List[String])(inp: List[String]): ParseResult[Formula[A]] =
    inp match
      case Nil =>
        throw new Exception("formula expected")
      
      case "false" :: rest =>
        (False, rest)
      
      case "true" :: rest =>
        (True, rest)
      
      case "(" :: rest =>
        // Try infix atom parser first (for things like "(x < y)")
        // If that fails, parse as bracketed formula
        try
          papply((a: A) => Atom(a))(ifn(vs, inp))
        catch
          case _: Exception =>
            parseBracketed(parseFormula(ifn, afn)(vs), ")")(rest)
      
      case "~" :: rest =>
        // Negation
        papply((p: Formula[A]) => Not(p))(parseAtomicFormula(ifn, afn)(vs)(rest))
      
      case "forall" :: x :: rest =>
        // Universal quantification
        parseQuant(ifn, afn)(x :: vs)((x: String, p: Formula[A]) => Forall(x, p))(x)(rest)
      
      case "exists" :: x :: rest =>
        // Existential quantification
        parseQuant(ifn, afn)(x :: vs)((x: String, p: Formula[A]) => Exists(x, p))(x)(rest)
      
      case _ =>
        // Default: parse as atom
        papply((a: A) => Atom(a))(afn(vs, inp))
  
  /** Parse quantified formula body
    * 
    * OCaml implementation:
    *   and parse_quant (ifn,afn) vs qcon x inp =
    *      match inp with
    *        [] -> failwith "Body of quantified term expected"
    *      | y::rest ->
    *           papply (fun fm -> qcon(x,fm))
    *                  (if y = "." then parse_formula (ifn,afn) vs rest
    *                   else parse_quant (ifn,afn) (y::vs) qcon y rest)
    * 
    * This handles multiple quantified variables:
    *   forall x y z. P(x,y,z)
    * is the same as:
    *   forall x. forall y. forall z. P(x,y,z)
    * 
    * The dot "." marks the end of variable list and start of body.
    * 
    * @param ifn Infix atom parser
    * @param afn General atom parser
    * @param vs List of bound variables
    * @param qcon Quantifier constructor (Forall or Exists)
    * @param x Current variable being quantified
    * @param inp Token stream
    */
  def parseQuant[A](
    ifn: AtomParser[A],
    afn: AtomParser[A]
  )(vs: List[String])(
    qcon: (String, Formula[A]) => Formula[A]
  )(x: String)(inp: List[String]): ParseResult[Formula[A]] =
    inp match
      case Nil =>
        throw new Exception("Body of quantified term expected")
      
      case y :: rest =>
        if y == "." then
          // Dot marks end of variables, parse body
          papply((fm: Formula[A]) => qcon(x, fm))(parseFormula(ifn, afn)(vs)(rest))
        else
          // Another variable, recurse
          papply((fm: Formula[A]) => qcon(x, fm))(
            parseQuant(ifn, afn)(y :: vs)(qcon)(y)(rest)
          )
  
  /** Parse complete formula with all logical operators
    * 
    * OCaml implementation:
    *   and parse_formula (ifn,afn) vs inp =
    *      parse_right_infix "<=>" (fun (p,q) -> Iff(p,q))
    *        (parse_right_infix "==>" (fun (p,q) -> Imp(p,q))
    *            (parse_right_infix "\\/" (fun (p,q) -> Or(p,q))
    *                (parse_right_infix "/\\" (fun (p,q) -> And(p,q))
    *                     (parse_atomic_formula (ifn,afn) vs)))) inp
    * 
    * This chains the operators with RIGHT ASSOCIATIVITY and precedence:
    * 1. /\  (and)     - highest precedence
    * 2. \/  (or)
    * 3. ==> (implies)
    * 4. <=> (iff)     - lowest precedence
    * 
    * Example: p /\ q \/ r ==> s <=> t
    * Parses as: ((p /\ q) \/ r) ==> (s <=> t)
    * 
    * Each level calls the next higher precedence as its subparser.
    */
  def parseFormula[A](
    ifn: AtomParser[A],
    afn: AtomParser[A]
  )(vs: List[String])(inp: List[String]): ParseResult[Formula[A]] =
    // Chain the operators in order of precedence (lowest to highest)
    parseRightInfix("<=>", (p: Formula[A], q: Formula[A]) => Iff(p, q))(
      parseRightInfix("==>", (p: Formula[A], q: Formula[A]) => Imp(p, q))(
        parseRightInfix("\\/", (p: Formula[A], q: Formula[A]) => Or(p, q))(
          parseRightInfix("/\\", (p: Formula[A], q: Formula[A]) => And(p, q))(
            parseAtomicFormula(ifn, afn)(vs)
          )
        )
      )
    )(inp)
  
  /** Convenience wrapper: parse formula from token list with empty variable context
    * 
    * This is what you'll typically call from outside.
    */
  def parse[A](
    ifn: AtomParser[A],
    afn: AtomParser[A]
  )(tokens: List[String]): ParseResult[Formula[A]] =
    parseFormula(ifn, afn)(List())(tokens)
