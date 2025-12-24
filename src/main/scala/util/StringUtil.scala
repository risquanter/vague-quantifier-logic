package util

/** String utilities following OCaml style from lib.ml and intro.ml
  * 
  * OCaml uses character lists for string manipulation.
  * These functions bridge between Scala's String and List[Char].
  */
object StringUtil:
  
  /** Convert string to list of characters (OCaml: explode)
    * 
    * OCaml implementation:
    *   let explode s =
    *     let rec exap n l =
    *       if n < 0 then l else exap (n - 1) (s.[n] :: l) in
    *     exap (String.length s - 1) []
    * 
    * Example:
    *   explode("abc") == List('a', 'b', 'c')
    */
  def explode(s: String): List[Char] =
    s.toList
  
  /** Convert list of characters to string (OCaml: implode)
    * 
    * OCaml implementation:
    *   let implode l = itlist (^) l ""
    * 
    * Example:
    *   implode(List('a', 'b', 'c')) == "abc"
    */
  def implode(chars: List[Char]): String =
    chars.mkString
  
  /** Create character predicate function (OCaml: matches)
    * 
    * OCaml implementation:
    *   let matches s = let chars = explode s in fun c -> mem c chars
    * 
    * Returns a function that checks if a character is in the given string.
    * 
    * Example:
    *   val isVowel = matches("aeiou")
    *   isVowel('a') == true
    *   isVowel('b') == false
    */
  def matches(s: String): Char => Boolean =
    val chars = s.toSet
    c => chars.contains(c)
  
  /** Character classification predicates (from intro.ml) */
  
  /** Whitespace characters */
  val space: Char => Boolean = matches(" \t\n\r")
  
  /** Punctuation characters */
  val punctuation: Char => Boolean = matches("()[]{}.,;")
  
  /** Symbolic operator characters 
    * 
    * TODO: OVERLAP ISSUE - The characters . , ; appear in BOTH symbolic and punctuation!
    * This causes them to be treated as symbolic (checked first in lexer), allowing
    * multi-char tokens like "..." or ",,," instead of individual tokens.
    * 
    * Consider removing . , ; from symbolic to make them single-char only:
    *   val symbolic = matches("~`!@#$%^&*-+=|\\:<>?/")
    * 
    * This would ensure:
    *   - "forall x. P(x)" tokenizes . correctly
    *   - "f(x, y)" keeps , as single token
    *   - Statements ending with ; stay single-char
    */
  val symbolic: Char => Boolean = matches("~`!@#$%^&*-+=|\\:;<>.?/")
  
  /** Numeric digit characters */
  val numeric: Char => Boolean = matches("0123456789")
  
  /** Alphanumeric characters (letters, digits, underscore, apostrophe) */
  val alphanumeric: Char => Boolean = 
    matches("abcdefghijklmnopqrstuvwxyz_'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
  
  /** Check if all characters in a list satisfy a predicate
    * 
    * OCaml uses: forall numeric (explode s)
    * Scala: forall(numeric, explode(s))
    */
  def forall(pred: Char => Boolean, chars: List[Char]): Boolean =
    chars.forall(pred)
  
  /** Check if string consists only of numeric characters
    * Used in fol.ml: is_const_name
    */
  def isNumeric(s: String): Boolean =
    s.nonEmpty && forall(numeric, explode(s))
