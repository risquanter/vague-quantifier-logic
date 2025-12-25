package vague.parser

import vague.logic.{VagueQuery, Quantifier}
import logic.{FOL, Formula, Term}
import parser.{FOLParser, TermParser, FOLAtomParser}
import lexer.Lexer
import util.StringUtil.explode

/** Parser for vague queries (paper Section 5.2)
  * 
  * Syntax: Q[op]^{k/n} x (R(x,y'), φ(x,y))(y₁, ..., yₘ)
  * 
  * Examples:
  *   Q[>=]^{3/4} x (country(x), exists y (hasGDP_agr(x,y) /\ y<=20))
  *   Q[~]^{1/2} x (capital(x, y), large(x))(y)
  *   Q[<=]^{1/3} x (city(x), populous(x))
  * 
  * OCaml-style: module with parsing functions (like FOLParser)
  * OCaml reference: parsing pattern from fol.ml
  * 
  * Paper reference: Definition 1 (Section 5.2)
  */
object VagueQueryParser:
  
  /** Parse vague query from string
    * 
    * Main entry point for parsing vague quantifier queries.
    * Handles full syntax: Q[op]^{k/n} x (R, φ)(answer vars)
    * 
    * @param s Query string in paper syntax
    * @return Parsed VagueQuery
    * @throws Exception if parsing fails
    */
  def parse(s: String): VagueQuery =
    val tokens = Lexer.lex(explode(s))
    val (query, remaining) = parseTokens(tokens)
    
    if remaining.nonEmpty then
      throw new Exception(s"Unexpected tokens after query: ${remaining.mkString(" ")}")
    
    query
  
  /** Parse from token list
    * 
    * @param tokens Pre-tokenized input
    * @return Tuple of (parsed query, remaining tokens)
    */
  def parseTokens(tokens: List[String]): (VagueQuery, List[String]) =
    // Parse: Q[op]^{k/n} x (R, φ)(y)
    
    // 1. Parse quantifier: Q[op]^{k/n}
    val (quantifier, afterQ) = parseQuantifier(tokens)
    
    // 2. Parse quantified variable: x
    val (variable, afterVar) = parseVariable(afterQ)
    
    // 3. Parse opening paren: (
    val afterParen1 = expect("(", afterVar)
    
    // 4. Parse range predicate: R(x,y')
    val (range, afterRange) = parseRangePredicate(afterParen1)
    
    // 5. Parse comma: ,
    val afterComma = expect(",", afterRange)
    
    // 6. Parse scope formula: φ(x,y)
    val (scope, afterScope) = parseScopeFormula(afterComma)
    
    // 7. Parse closing paren: )
    val afterParen2 = expect(")", afterScope)
    
    // 8. Parse optional answer variables: (y₁, ..., yₘ)
    val (answerVars, afterAnswer) = parseAnswerVars(afterParen2)
    
    // 9. Construct and validate query
    val query = VagueQuery.mk(quantifier, variable, range, scope, answerVars)
    
    (query, afterAnswer)
  
  /** Parse quantifier: Q[op]^{k/n}
    * 
    * Syntax:
    *   Q[~]^{k/n}   - About
    *   Q[>=]^{k/n}  - AtLeast
    *   Q[<=]^{k/n}  - AtMost
    * 
    * Optional tolerance can be specified: Q[~]^{k/n}[ε]
    */
  private def parseQuantifier(tokens: List[String]): (Quantifier, List[String]) =
    tokens match
      case "Q" :: "[" :: op :: "]" :: "^" :: "{" :: rest =>
        // Parse k/n
        val (k, afterK) = parseInteger(rest)
        val afterSlash = expect("/", afterK)
        val (n, afterN) = parseInteger(afterSlash)
        val afterBrace = expect("}", afterN)
        
        // Optional tolerance [ε]
        val (tolerance, afterTol) = afterBrace match
          case "[" :: tolStr :: "]" :: rest2 if isNumeric(tolStr) =>
            (tolStr.toDouble, rest2)
          case _ =>
            (0.1, afterBrace)  // Default tolerance
        
        // Construct quantifier based on operator
        val quantifier = op match
          case "~" | "~#" => Quantifier.About(k, n, tolerance)
          case ">=" | "≥" => Quantifier.AtLeast(k, n, tolerance)
          case "<=" | "≤" => Quantifier.AtMost(k, n, tolerance)
          case _ => throw new Exception(s"Invalid quantifier operator: $op")
        
        (quantifier, afterTol)
      
      case _ =>
        throw new Exception(s"Expected quantifier Q[op]^{{k/n}}, got: ${tokens.take(5).mkString(" ")}")
  
  /** Parse variable name (single identifier) */
  private def parseVariable(tokens: List[String]): (String, List[String]) =
    tokens match
      case v :: rest if isIdentifier(v) => (v, rest)
      case _ => throw new Exception(s"Expected variable, got: ${tokens.headOption.getOrElse("EOF")}")
  
  /** Parse range predicate as FOL atom
    * 
    * Parses: predicate(term1, term2, ...)
    * Example: country(x), capital(x, y)
    */
  private def parseRangePredicate(tokens: List[String]): (FOL, List[String]) =
    tokens match
      case pred :: "(" :: rest if isIdentifier(pred) =>
        // Parse terms until closing paren
        val (terms, afterTerms) = parseTermList(rest)
        val afterParen = expect(")", afterTerms)
        (FOL(pred, terms), afterParen)
      
      case pred :: rest if isIdentifier(pred) =>
        // Predicate with no arguments
        (FOL(pred, Nil), rest)
      
      case _ =>
        throw new Exception(s"Expected range predicate, got: ${tokens.take(3).mkString(" ")}")
  
  /** Parse scope formula using FOLParser
    * 
    * Delegates to existing FOL parser for full formula parsing.
    * Parses until closing paren that matches the opening paren of the query.
    */
  private def parseScopeFormula(tokens: List[String]): (Formula[FOL], List[String]) =
    // Find matching closing paren (accounting for nested parens)
    val (formulaTokens, remaining) = extractUntilMatchingParen(tokens, 0)
    
    if formulaTokens.isEmpty then
      throw new Exception("Expected scope formula")
    
    // Parse using FOLParser
    val formulaStr = formulaTokens.mkString(" ")
    val formula = FOLParser.parse(formulaStr)
    
    (formula, remaining)
  
  /** Parse optional answer variables: (y₁, ..., yₘ)
    * 
    * Returns empty list if no answer variables specified.
    */
  private def parseAnswerVars(tokens: List[String]): (List[String], List[String]) =
    tokens match
      case "(" :: rest =>
        // Parse comma-separated variable list
        val (vars, afterVars) = parseVariableList(rest)
        val afterClose = expect(")", afterVars)
        (vars, afterClose)
      
      case _ =>
        // No answer variables (Boolean query)
        (Nil, tokens)
  
  /** Parse comma-separated list of variables */
  private def parseVariableList(tokens: List[String]): (List[String], List[String]) =
    def parseList(tokens: List[String], acc: List[String]): (List[String], List[String]) =
      tokens match
        case v :: "," :: rest if isIdentifier(v) =>
          parseList(rest, acc :+ v)
        case v :: rest if isIdentifier(v) =>
          (acc :+ v, rest)
        case ")" :: _ =>
          (acc, tokens)  // Empty list or end of list
        case _ =>
          throw new Exception(s"Expected variable in list, got: ${tokens.headOption.getOrElse("EOF")}")
    
    parseList(tokens, Nil)
  
  // Helper functions
  
  /** Parse comma-separated list of terms */
  private def parseTermList(tokens: List[String]): (List[Term], List[String]) =
    def parseTerm(tokens: List[String]): (Term, List[String]) =
      tokens match
        case v :: rest if isIdentifier(v) =>
          (Term.Var(v), rest)
        case n :: rest if isNumeric(n) =>
          (Term.Const(n), rest)
        case _ =>
          throw new Exception(s"Expected term, got: ${tokens.headOption.getOrElse("EOF")}")
    
    def parseList(tokens: List[String], acc: List[Term]): (List[Term], List[String]) =
      tokens match
        case ")" :: _ =>
          (acc, tokens)
        case _ =>
          val (term, afterTerm) = parseTerm(tokens)
          afterTerm match
            case "," :: rest =>
              parseList(rest, acc :+ term)
            case ")" :: _ =>
              (acc :+ term, afterTerm)
            case _ =>
              throw new Exception(s"Expected ',' or ')' after term, got: ${afterTerm.headOption.getOrElse("EOF")}")
    
    // Handle empty list
    if tokens.headOption.contains(")") then
      (Nil, tokens)
    else
      parseList(tokens, Nil)
  
  /** Extract tokens until matching closing paren
    * 
    * Handles nested parentheses correctly.
    * 
    * @param tokens Token stream
    * @param depth Current nesting depth
    * @return (tokens inside parens, remaining tokens including closing paren)
    */
  private def extractUntilMatchingParen(tokens: List[String], depth: Int): (List[String], List[String]) =
    tokens match
      case Nil =>
        throw new Exception("Unmatched parenthesis")
      case "(" :: rest =>
        val (inner, remaining) = extractUntilMatchingParen(rest, depth + 1)
        ("(" :: inner, remaining)
      case ")" :: rest if depth == 1 =>
        (Nil, ")" :: rest)
      case ")" :: rest =>
        val (inner, remaining) = extractUntilMatchingParen(rest, depth - 1)
        (")" :: inner, remaining)
      case token :: rest =>
        val (inner, remaining) = extractUntilMatchingParen(rest, depth)
        (token :: inner, remaining)
  
  /** Expect specific token */
  private def expect(expected: String, tokens: List[String]): List[String] =
    tokens match
      case `expected` :: rest => rest
      case actual :: _ => throw new Exception(s"Expected '$expected', got '$actual'")
      case Nil => throw new Exception(s"Expected '$expected', got EOF")
  
  /** Parse integer token */
  private def parseInteger(tokens: List[String]): (Int, List[String]) =
    tokens match
      case num :: rest if num.forall(_.isDigit) => (num.toInt, rest)
      case _ => throw new Exception(s"Expected integer, got: ${tokens.headOption.getOrElse("EOF")}")
  
  /** Check if string is a valid identifier (alphanumeric, starts with letter) */
  private def isIdentifier(s: String): Boolean =
    s.nonEmpty && s.head.isLetter && s.forall(c => c.isLetterOrDigit || c == '_')
  
  /** Check if string is numeric (integer or decimal) */
  private def isNumeric(s: String): Boolean =
    s.nonEmpty && (s.forall(_.isDigit) || s.matches("""\d+\.\d+"""))
  
  // Convenience parsers for common quantifiers
  
  /** Parse About query: Q[~]^{k/n} x (R, φ)(y) */
  def parseAbout(s: String): VagueQuery =
    val query = parse(s)
    query.quantifier match
      case Quantifier.About(_, _, _) => query
      case _ => throw new Exception(s"Expected About quantifier, got: ${query.quantifier}")
  
  /** Parse AtLeast query: Q[>=]^{k/n} x (R, φ)(y) */
  def parseAtLeast(s: String): VagueQuery =
    val query = parse(s)
    query.quantifier match
      case Quantifier.AtLeast(_, _, _) => query
      case _ => throw new Exception(s"Expected AtLeast quantifier, got: ${query.quantifier}")
  
  /** Parse AtMost query: Q[<=]^{k/n} x (R, φ)(y) */
  def parseAtMost(s: String): VagueQuery =
    val query = parse(s)
    query.quantifier match
      case Quantifier.AtMost(_, _, _) => query
      case _ => throw new Exception(s"Expected AtMost quantifier, got: ${query.quantifier}")
