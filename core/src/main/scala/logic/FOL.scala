package logic

/** First-order logic atom: relations/predicates over terms
  * 
  * Corresponds to OCaml:
  *   type fol = R of string * term list
  * 
  * Examples:
  *   - R("=", List(Var("x"), Var("y")))  represents  x = y
  *   - R("<", List(Fn("+", ...), ...))   represents  x + y < z
  *   - R("P", List(Var("x")))            represents  P(x)
  */
case class FOL(predicate: String, terms: List[Term])

object FOL:
  /** Convenience constructor */
  def apply(pred: String, terms: Term*): FOL = 
    FOL(pred, terms.toList)
  
  /** Example from fol.ml: x + y < z */
  def example: Formula[FOL] =
    import Term.*
    Formula.Atom(
      FOL("<", List(
        Fn("+", List(Var("x"), Var("y"))),
        Var("z")
      ))
    )

  def parentExample: Formula[FOL] = 
    import Term.*
    Formula.Atom(
      FOL("parent", List(
        Var("alice"),
        Var("bob")
      ))
    )
  
  /** Type alias for convenience */
  type FOLFormula = Formula[FOL]