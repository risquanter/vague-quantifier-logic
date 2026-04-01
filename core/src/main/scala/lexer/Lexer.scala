package lexer

import util.StringUtil.*

/** Lexical analysis following OCaml style from intro.ml
  * 
  * Converts character streams into token streams.
  * Key OCaml technique: accumulator-based recursion with token threading.
  */
object Lexer:
  
  /** Consume characters while predicate holds (OCaml: lexwhile)
    * 
    * OCaml implementation:
    *   let rec lexwhile prop inp =
    *     match inp with
    *       c::cs when prop c -> let tok,rest = lexwhile prop cs in c^tok,rest
    *     | _ -> "",inp
    * 
    * This is the core tokenization primitive:
    * - Takes characters while predicate is true
    * - Accumulates them into a token string
    * - Returns (token, remaining_input)
    * 
    * Example:
    *   lexwhile(numeric, explode("123abc"))
    *   => ("123", List('a', 'b', 'c'))
    * 
    * Note the OCaml pattern:
    * - Recursive call first: lexwhile prop cs
    * - Then prepend current char: c^tok
    * - Builds string from right to left (like explode!)
    */
  def lexwhile(prop: Char => Boolean, inp: List[Char]): (String, List[Char]) =
    inp match
      case c :: cs if prop(c) =>
        val (tok, rest) = lexwhile(prop, cs)
        (c.toString + tok, rest)
      case _ => 
        ("", inp)
  
  /** Main lexer function (OCaml: lex)
    * 
    * OCaml implementation:
    *   let rec lex inp =
    *     match snd(lexwhile space inp) with
    *       [] -> []
    *     | c::cs -> let prop = if alphanumeric(c) then alphanumeric
    *                           else if symbolic(c) then symbolic
    *                           else fun c -> false in
    *                let toktl,rest = lexwhile prop cs in
    *                (c^toktl)::lex rest
    * 
    * Algorithm:
    * 1. Skip whitespace using lexwhile
    * 2. If empty, done
    * 3. Look at first non-space character
    * 4. Determine token type (alphanumeric, symbolic, or single char)
    * 5. Consume all chars of that type
    * 6. Recurse on remaining input
    * 
    * Example:
    *   lex(explode("x + 1"))
    *   => List("x", "+", "1")
    */
  def lex(inp: List[Char]): List[String] =
    // Skip whitespace: snd(lexwhile space inp)
    val (_, afterSpace) = lexwhile(space, inp)
    
    afterSpace match
      case Nil => Nil
      
      case c :: cs =>
        // Determine token type based on first character
        // NOTE: This follows OCaml's permissive approach - any character not
        // alphanumeric or symbolic becomes a single-char token via (_ => false).
        // This implicitly handles punctuation: (, ), [, ], ,, ., ;
        //
        // TODO: Consider more explicit approach for clarity:
        //   if alphanumeric(c) then alphanumeric
        //   else if symbolic(c) then symbolic
        //   else if punctuation(c) then _ => false  // explicit punctuation
        //   else throw new Exception(s"Invalid character: '$c'")
        // This would be self-documenting and catch invalid input.
        val prop: Char => Boolean = 
          if alphanumeric(c) then alphanumeric
          else if symbolic(c) then symbolic
          else _ => false  // single character token (catches punctuation)
        
        // Consume rest of token
        val (toktl, rest) = lexwhile(prop, cs)
        
        // Build complete token and recurse
        val token = c.toString + toktl
        token :: lex(rest)
  
  /** Convenience function: lex from string directly */
  def lexString(s: String): List[String] =
    lex(explode(s))
