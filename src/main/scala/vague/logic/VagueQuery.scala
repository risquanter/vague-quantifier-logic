package vague.logic

import vague.error.VagueError
import logic.{FOL, Formula, Term, FOLUtil}

/** Vague quantifier query from paper Definition 1 (Section 5.2)
  * 
  * Syntax: Q x (R(x,y'), φ(x,y))
  * 
  * OCaml-style: case class for product type (like FOL)
  * OCaml reference: fol.ml has "type fol = R of string * term list"
  * 
  * Paper reference: Definition 1 (Section 5.2)
  * "A vague query is of the form Q x (R(x,y'), φ(x,y)) where:
  *  - Q is a vague quantifier Q[op]^{k/n}
  *  - x is the quantified variable
  *  - R(x,y') is the range predicate (FOL atom)
  *  - φ(x,y) is the scope predicate (FOL formula)
  *  - y are answer variables (y' ⊆ y)"
  * 
  * @param quantifier Type of vague quantifier (Q[op]^{k/n})
  * @param variable Quantified variable (x)
  * @param range Range predicate R(x,y') as FOL atom
  * @param scope Scope predicate φ(x,y) as FOL formula
  * @param answerVars Free variables y (answer variables)
  */
case class VagueQuery(
  quantifier: Quantifier,
  variable: String,
  range: FOL,
  scope: Formula[FOL],
  answerVars: List[String] = Nil
):
  /** Extract variables from range atom R(x,y') 
    * 
    * OCaml pattern: module-level function operating on data
    * OCaml reference: fol.ml has fvt for variable extraction
    */
  def rangeVars: Set[String] =
    def extractFromTerms(terms: List[Term]): Set[String] = 
      terms.flatMap {
        case Term.Var(name) => Set(name)
        case Term.Fn(_, args) => extractFromTerms(args)
        case Term.Const(_) => Set.empty[String]
      }.toSet
    extractFromTerms(range.terms)
  
  /** Extract all variables from scope formula φ(x,y)
    * 
    * Uses FOLUtil.fvFOL to get free variables, consistent with FOL infrastructure.
    * Returns all variables (free and bound) by using varFOL which includes quantified variables.
    * 
    * Note: We want ALL variables here (including bound ones) since this is used for
    * determining which variables appear in the scope formula, regardless of binding.
    */
  def scopeVars: Set[String] =
    FOLUtil.varFOL(scope).toSet
  
  /** Check if query is Boolean (no answer variables) 
    * 
    * Paper reference: Example 3 distinguishes Boolean vs unary queries
    */
  def isBoolean: Boolean = answerVars.isEmpty
  
  /** Check if query is unary (single answer variable) */
  def isUnary: Boolean = answerVars.length == 1

object VagueQuery:
  /** Smart constructor with validation (safe internal)
    * 
    * @param q Quantifier Q[op]^{k/n}
    * @param x Quantified variable
    * @param r Range predicate R(x,y')
    * @param phi Scope predicate φ(x,y)
    * @param y Answer variables
    * @return Either error or validated VagueQuery
    */
  def mkSafe(
    q: Quantifier,
    x: String,
    r: FOL,
    phi: Formula[FOL],
    y: List[String] = Nil
  ): Either[VagueError, VagueQuery] =
    val query = VagueQuery(q, x, r, phi, y)
    
    // Validation: x should appear in range
    if !query.rangeVars.contains(x) then
      return Left(VagueError.ValidationError(
        s"Quantified variable '$x' must appear in range predicate ${r.predicate}",
        "quantified_variable",
        Map(
          "variable" -> x,
          "range_predicate" -> r.predicate,
          "range_vars" -> query.rangeVars.mkString(", ")
        )
      ))
    
    // Paper constraint: y' ⊆ y (range vars minus x ⊆ answer vars)
    val rangeVarsMinusX = query.rangeVars - x
    if !rangeVarsMinusX.subsetOf(y.toSet) then
      return Left(VagueError.ValidationError(
        s"Range variables ${rangeVarsMinusX.mkString(",")} must be subset of answer variables ${y.mkString(",")}",
        "answer_variables",
        Map(
          "range_vars" -> rangeVarsMinusX.mkString(", "),
          "answer_vars" -> y.mkString(", "),
          "missing_vars" -> (rangeVarsMinusX -- y.toSet).mkString(", ")
        )
      ))
    
    Right(query)
  
  /** Smart constructor with validation (OCaml pattern)
    * 
    * OCaml reference: formulas.ml has mk_* constructors
    * OCaml pattern: let mk_and p q = And(p,q)
    * 
    * @param q Quantifier Q[op]^{k/n}
    * @param x Quantified variable
    * @param r Range predicate R(x,y')
    * @param phi Scope predicate φ(x,y)
    * @param y Answer variables
    * @return Validated VagueQuery
    * @throws VagueException if validation fails
    */
  def mk(
    q: Quantifier,
    x: String,
    r: FOL,
    phi: Formula[FOL],
    y: List[String] = Nil
  ): VagueQuery =
    mkSafe(q, x, r, phi, y) match
      case Right(query) => query
      case Left(error) => throw error.toThrowable
  
  /** Example: q₁ from paper (Boolean query)
    * 
    * Q[≥]^{3/4} x (country(x), ∃y (hasGDP_agr(x,y) ∧ y≤20))
    * 
    * Paper reference: Example 3, query q₁
    * "Do at least about three quarters of countries have agricultural GDP ≤ 20%?"
    */
  def example1: VagueQuery =
    import Formula.*, Term.*
    VagueQuery(
      quantifier = Quantifier.mkAtLeast(3, 4),
      variable = "x",
      range = FOL("country", List(Var("x"))),
      scope = Exists("y", And(
        Atom(FOL("hasGDP_agr", List(Var("x"), Var("y")))),
        Atom(FOL("<=", List(Var("y"), Const("20"))))
      )),
      answerVars = Nil  // Boolean query
    )
  
  /** Example: q₃ skeleton from paper (Unary query with answer variable)
    * 
    * Q[~#]^{1/2} x (capital(x), ...)(y)
    * 
    * Paper reference: Example 3, query q₃
    * "About half of capitals satisfy some property, grouped by country y"
    */
  def example3Skeleton: VagueQuery =
    import Formula.*, Term.*
    VagueQuery(
      quantifier = Quantifier.mkAbout(1, 2),
      variable = "x",
      range = FOL("capital", List(Var("x"))),
      scope = True,  // Placeholder - would be complex formula
      answerVars = List("y")
    )
  
  /** Simple example: about half of countries are large */
  def simpleExample: VagueQuery =
    import Formula.*, Term.*
    VagueQuery(
      quantifier = Quantifier.aboutHalf,
      variable = "x",
      range = FOL("country", List(Var("x"))),
      scope = Atom(FOL("large", List(Var("x")))),
      answerVars = Nil
    )
