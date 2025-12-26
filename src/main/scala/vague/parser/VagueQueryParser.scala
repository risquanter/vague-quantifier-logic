package vague.parser

import vague.logic.{VagueQuery, Quantifier}
import vague.error.{VagueError, VagueException}
import vague.result.VagueResult
import logic.{FOL, Formula, Term}
import parser.{FOLAtomParser, FormulaParser}
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
  
  /** Parse vague query from string (safe internal implementation)
    * 
    * @param s Query string in paper syntax
    * @return Either error or parsed VagueQuery
    */
  private def parseInternal(s: String): Either[VagueError, VagueQuery] =
    try
      val tokens = Lexer.lex(explode(s))
      parseTokensInternal(tokens) match
        case Right((query, remaining)) =>
          if remaining.nonEmpty then
            Left(VagueError.ParseError(
              s"Unexpected tokens after query: ${remaining.mkString(" ")}",
              s,
              Some(s.length - remaining.mkString(" ").length),
              Map("remaining_tokens" -> remaining.mkString(" "))
            ))
          else
            Right(query)
        case Left(error) => Left(error)
    catch
      case e: VagueException => Left(e.error)
      case e: Exception =>
        Left(VagueError.ParseError(
          s"Unexpected error during parsing: ${e.getMessage}",
          s,
          None,
          Map("exception" -> e.getClass.getSimpleName)
        ))
  
  /** Parse vague query from string (Either API for Scala/FP users)
    * 
    * Main entry point for parsing vague quantifier queries with structured error handling.
    * Handles full syntax: Q[op]^{k/n} x (R, φ)(answer vars)
    * 
    * @param s Query string in paper syntax
    * @return Either[VagueError, VagueQuery] - Left if parsing fails, Right with parsed query
    * 
    * Example:
    * {{{
    * parseEither("Q[~]^{1/2} x (city(x), large(x))") match
    *   case Right(query) => // Use query
    *   case Left(error) => // Handle error
    * }}}
    */
  def parseEither(s: String): Either[VagueError, VagueQuery] =
    parseInternal(s)
  
  /** Parse vague query from string (VagueResult API for composition)
    * 
    * Functional result type for composable error handling.
    * Works seamlessly with for-comprehensions and effect systems.
    * 
    * @param s Query string in paper syntax
    * @return VagueResult[VagueQuery] - Failure if parsing fails, Success with parsed query
    * 
    * Example:
    * {{{
    * for
    *   query1 <- parseResult(str1)
    *   query2 <- parseResult(str2)
    * yield (query1, query2)
    * }}}
    */
  def parseResult(s: String): VagueResult[VagueQuery] =
    VagueResult.fromEither(parseInternal(s))
  
  /** Parse vague query from string (throwing API for Java/Kotlin compatibility)
    * 
    * Legacy API that throws exceptions on parse errors.
    * Provided for backward compatibility with Java/Kotlin clients.
    * Scala/FP users should prefer parseEither or parseResult.
    * 
    * @param s Query string in paper syntax
    * @return Parsed VagueQuery
    * @throws VagueException if parsing fails
    */
  def parse(s: String): VagueQuery =
    parseInternal(s) match
      case Right(query) => query
      case Left(error) => throw error.toThrowable
  
  /** Parse from token list (safe internal implementation)
    * 
    * @param tokens Pre-tokenized input
    * @return Either error or tuple of (parsed query, remaining tokens)
    */
  private def parseTokensInternal(tokens: List[String]): Either[VagueError, (VagueQuery, List[String])] =
    try
      // Parse: Q[op]^{k/n} x (R, φ)(y)
      
      for
        // 1. Parse quantifier: Q[op]^{k/n}
        quantifierPair <- parseQuantifierInternal(tokens)
        (quantifier, afterQ) = quantifierPair
        
        // 2. Parse quantified variable: x
        variablePair <- parseVariableInternal(afterQ)
        (variable, afterVar) = variablePair
        
        // 3. Parse opening paren: (
        afterParen1 <- expectInternal("(", afterVar, "range predicate")
        
        // 4. Parse range predicate: R(x,y') using FOL atom parser
        rangePair <- parseAtomSafe(afterParen1)
        (range, afterRange) = rangePair
        
        // 5. Parse comma: ,
        afterComma <- expectInternal(",", afterRange, "scope formula")
        
        // 6. Parse scope formula: φ(x,y) using FormulaParser
        scopePair <- parseFormulaSafe(afterComma)
        (scope, afterScope) = scopePair
        
        // 7. Parse closing paren: )
        afterParen2 <- expectInternal(")", afterScope, "answer variables")
        
        // 8. Parse optional answer variables: (y₁, ..., yₘ)
        answerPair <- parseAnswerVarsInternal(afterParen2)
        (answerVars, afterAnswer) = answerPair
        
        // 9. Construct and validate query
        query <- VagueQuery.mkSafe(quantifier, variable, range, scope, answerVars)
      yield
        (query, afterAnswer)
    catch
      case e: VagueException => Left(e.error)
      case e: Exception =>
        Left(VagueError.ParseError(
          s"Unexpected error parsing tokens: ${e.getMessage}",
          tokens.mkString(" "),
          None,
          Map("exception" -> e.getClass.getSimpleName)
        ))
  
  /** Parse from token list (public API, throws on error)
    * 
    * @param tokens Pre-tokenized input
    * @return Tuple of (parsed query, remaining tokens)
    * @throws VagueException if parsing fails
    */
  def parseTokens(tokens: List[String]): (VagueQuery, List[String]) =
    parseTokensInternal(tokens) match
      case Right(result) => result
      case Left(error) => throw error.toThrowable
  
  /** Parse quantifier: Q[op]^{k/n} (safe internal)
    * 
    * Syntax:
    *   Q[~]^{k/n}   - About
    *   Q[>=]^{k/n}  - AtLeast
    *   Q[<=]^{k/n}  - AtMost
    * 
    * Optional tolerance can be specified: Q[~]^{k/n}[ε]
    */
  private def parseQuantifierInternal(tokens: List[String]): Either[VagueError, (Quantifier, List[String])] =
    tokens match
      case "Q" :: "[" :: op :: "]" :: "^" :: "{" :: rest =>
        try
          // Parse k/n
          for
            kPair <- parseIntegerInternal(rest, "numerator k")
            (k, afterK) = kPair
            afterSlash <- expectInternal("/", afterK, "denominator")
            nPair <- parseIntegerInternal(afterSlash, "denominator n")
            (n, afterN) = nPair
            afterBrace <- expectInternal("}", afterN, "tolerance or end")
            
            // Optional tolerance [ε]
            (tolerance, afterTol) = afterBrace match
              case "[" :: tolStr :: "]" :: rest2 if isNumeric(tolStr) =>
                (tolStr.toDouble, rest2)
              case "[" :: intPart :: "." :: fracPart :: "]" :: rest2 
                if intPart.forall(_.isDigit) && fracPart.forall(_.isDigit) =>
                val tolStr = s"$intPart.$fracPart"
                (tolStr.toDouble, rest2)
              case _ =>
                (0.1, afterBrace)  // Default tolerance
            
            // Construct quantifier based on operator
            quantifierResult <- op match
              case "~" | "~#" => Right(Quantifier.About(k, n, tolerance))
              case ">=" | "≥" => Right(Quantifier.AtLeast(k, n, tolerance))
              case "<=" | "≤" => Right(Quantifier.AtMost(k, n, tolerance))
              case _ => 
                Left(VagueError.ParseError(
                  s"Invalid quantifier operator: $op (expected ~, >=, or <=)",
                  tokens.mkString(" "),
                  None,
                  Map("operator" -> op, "valid_operators" -> "~, >=, <=")
                ))
          yield
            (quantifierResult, afterTol)
        catch
          case e: VagueException => Left(e.error)
          case e: Exception =>
            Left(VagueError.ParseError(
              s"Error parsing quantifier: ${e.getMessage}",
              tokens.mkString(" "),
              None,
              Map("exception" -> e.getClass.getSimpleName)
            ))
      
      case _ =>
        Left(VagueError.ParseError(
          s"Expected quantifier Q[op]^{{k/n}}, got: ${tokens.take(7).mkString(" ")}",
          tokens.mkString(" "),
          None,
          Map("expected" -> "Q[op]^{k/n}", "got" -> tokens.take(7).mkString(" "))
        ))
  
  /** Parse quantifier (public API, throws on error) */
  private def parseQuantifier(tokens: List[String]): (Quantifier, List[String]) =
    parseQuantifierInternal(tokens) match
      case Right(result) => result
      case Left(error) => throw error.toThrowable
  
  /** Parse variable name (safe internal) */
  private def parseVariableInternal(tokens: List[String]): Either[VagueError, (String, List[String])] =
    tokens match
      case v :: rest if isIdentifier(v) => Right((v, rest))
      case head :: _ =>
        Left(VagueError.ParseError(
          s"Expected variable identifier, got: $head",
          tokens.mkString(" "),
          None,
          Map("got" -> head, "expected" -> "variable identifier")
        ))
      case Nil =>
        Left(VagueError.ParseError(
          "Expected variable identifier, got end of input",
          "",
          None,
          Map("expected" -> "variable identifier")
        ))
  
  /** Parse variable name (public API, throws on error) */
  private def parseVariable(tokens: List[String]): (String, List[String]) =
    parseVariableInternal(tokens) match
      case Right(result) => result
      case Left(error) => throw error.toThrowable
  

  

  
  /** Parse optional answer variables (safe internal): (y₁, ..., yₘ)
    * 
    * Returns empty list if no answer variables specified.
    */
  private def parseAnswerVarsInternal(tokens: List[String]): Either[VagueError, (List[String], List[String])] =
    tokens match
      case "(" :: rest =>
        // Parse comma-separated variable list
        for
          varsPair <- parseVariableListInternal(rest)
          (vars, afterVars) = varsPair
          afterClose <- expectInternal(")", afterVars, "end of answer variables")
        yield
          (vars, afterClose)
      
      case _ =>
        // No answer variables (Boolean query)
        Right((Nil, tokens))
  
  /** Parse optional answer variables (public API, throws on error) */
  private def parseAnswerVars(tokens: List[String]): (List[String], List[String]) =
    parseAnswerVarsInternal(tokens) match
      case Right(result) => result
      case Left(error) => throw error.toThrowable
  
  /** Parse comma-separated list of variables (safe internal) */
  private def parseVariableListInternal(tokens: List[String]): Either[VagueError, (List[String], List[String])] =
    def parseList(tokens: List[String], acc: List[String]): Either[VagueError, (List[String], List[String])] =
      tokens match
        case v :: "," :: rest if isIdentifier(v) =>
          parseList(rest, acc :+ v)
        case v :: rest if isIdentifier(v) =>
          Right((acc :+ v, rest))
        case ")" :: _ =>
          Right((acc, tokens))  // Empty list or end of list
        case head :: _ =>
          Left(VagueError.ParseError(
            s"Expected variable in list, got: $head",
            tokens.mkString(" "),
            None,
            Map("got" -> head, "expected" -> "variable identifier")
          ))
        case Nil =>
          Left(VagueError.ParseError(
            "Expected variable in list, got end of input",
            "",
            None,
            Map("expected" -> "variable identifier")
          ))
    
    parseList(tokens, Nil)
  
  /** Parse comma-separated list of variables (public API, throws on error) */
  private def parseVariableList(tokens: List[String]): (List[String], List[String]) =
    parseVariableListInternal(tokens) match
      case Right(result) => result
      case Left(error) => throw error.toThrowable
  
  // Helper functions (OCaml-style)
  
  /** Expect specific token (safe internal)
    * 
    * @param expected Token to expect
    * @param tokens Remaining tokens
    * @param context Description of what we're parsing (for error messages)
    * @return Remaining tokens after expected token, or error
    */
  private def expectInternal(expected: String, tokens: List[String], context: String): Either[VagueError, List[String]] =
    tokens match
      case `expected` :: rest => Right(rest)
      case actual :: _ =>
        Left(VagueError.ParseError(
          s"Expected '$expected' before $context, got '$actual'",
          tokens.mkString(" "),
          None,
          Map("expected" -> expected, "got" -> actual, "context" -> context)
        ))
      case Nil =>
        Left(VagueError.ParseError(
          s"Expected '$expected' before $context, got end of input",
          "",
          None,
          Map("expected" -> expected, "context" -> context)
        ))
  
  /** Expect specific token (public API, throws on error)
    * 
    * OCaml equivalent pattern from fol.ml
    */
  private def expect(expected: String, tokens: List[String]): List[String] =
    expectInternal(expected, tokens, "unknown") match
      case Right(rest) => rest
      case Left(error) => throw error.toThrowable
  
  /** Parse integer token (safe internal) */
  private def parseIntegerInternal(tokens: List[String], context: String): Either[VagueError, (Int, List[String])] =
    tokens match
      case num :: rest if num.forall(_.isDigit) =>
        try
          Right((num.toInt, rest))
        catch
          case e: NumberFormatException =>
            Left(VagueError.ParseError(
              s"Integer out of range for $context: $num",
              tokens.mkString(" "),
              None,
              Map("value" -> num, "context" -> context)
            ))
      case head :: _ =>
        Left(VagueError.ParseError(
          s"Expected integer for $context, got: $head",
          tokens.mkString(" "),
          None,
          Map("got" -> head, "expected" -> "integer", "context" -> context)
        ))
      case Nil =>
        Left(VagueError.ParseError(
          s"Expected integer for $context, got end of input",
          "",
          None,
          Map("expected" -> "integer", "context" -> context)
        ))
  
  /** Parse integer token (public API, throws on error) */
  private def parseInteger(tokens: List[String]): (Int, List[String]) =
    parseIntegerInternal(tokens, "unknown") match
      case Right(result) => result
      case Left(error) => throw error.toThrowable
  
  /** Check if string is a valid identifier (alphanumeric, starts with letter) */
  private def isIdentifier(s: String): Boolean =
    s.nonEmpty && s.head.isLetter && s.forall(c => c.isLetterOrDigit || c == '_')
  
  /** Check if string is numeric (integer or decimal) */
  private def isNumeric(s: String): Boolean =
    s.nonEmpty && (s.forall(_.isDigit) || s.matches("""\d+\.\d+"""))
  
  /** Safe wrapper for FOL atom parser */
  private def parseAtomSafe(tokens: List[String]): Either[VagueError, (FOL, List[String])] =
    try
      Right(FOLAtomParser.parseAtom(List(), tokens))
    catch
      case e: VagueException => Left(e.error)
      case e: Exception =>
        Left(VagueError.ParseError(
          s"Error parsing range predicate: ${e.getMessage}",
          tokens.mkString(" "),
          None,
          Map("exception" -> e.getClass.getSimpleName, "message" -> e.getMessage)
        ))
  
  /** Safe wrapper for formula parser */
  private def parseFormulaSafe(tokens: List[String]): Either[VagueError, (Formula[FOL], List[String])] =
    try
      Right(FormulaParser.parse(
        FOLAtomParser.parseInfixAtom,
        FOLAtomParser.parseAtom
      )(tokens))
    catch
      case e: VagueException => Left(e.error)
      case e: Exception =>
        Left(VagueError.ParseError(
          s"Error parsing scope formula: ${e.getMessage}",
          tokens.mkString(" "),
          None,
          Map("exception" -> e.getClass.getSimpleName, "message" -> e.getMessage)
        ))
  
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
