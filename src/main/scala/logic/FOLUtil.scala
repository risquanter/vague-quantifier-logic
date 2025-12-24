package logic

/** Utility functions for FOL terms and formulas - Phase 10
  * 
  * From fol.ml, this implements:
  * - Free variables (fv, fvt, var)
  * - Substitution (subst, tsubst)
  * - Variable renaming (variant)
  * - Universal closure (generalize)
  */
object FOLUtil:
  
  /** Helper: union of lists (set union, removing duplicates)
    * 
    * OCaml: union (via lib.ml)
    */
  def union[A](xs: List[A], ys: List[A]): List[A] =
    xs.foldLeft(ys)((acc, x) => if acc.contains(x) then acc else x :: acc)
  
  /** Helper: unions - union of multiple lists
    * 
    * OCaml: unions (via lib.ml)
    */
  def unions[A](xss: List[List[A]]): List[A] =
    xss.foldLeft(List.empty[A])((acc, xs) => union(xs, acc))
  
  /** Helper: subtract list elements
    * 
    * OCaml: subtract (via lib.ml)
    */
  def subtract[A](xs: List[A], ys: List[A]): List[A] =
    xs.filterNot(ys.contains)
  
  /** Helper: insert element into list (like set insert)
    * 
    * OCaml: insert (via lib.ml)
    */
  def insert[A](x: A, xs: List[A]): List[A] =
    if xs.contains(x) then xs else x :: xs
  
  // ==================== Free Variables in Terms ====================
  
  /** Free variables in a term
    * 
    * OCaml implementation:
    *   let rec fvt tm =
    *     match tm with
    *       Var x -> [x]
    *     | Fn(f,args) -> unions (map fvt args)
    * 
    * A term's free variables are:
    * - For Var(x): just [x]
    * - For Fn(f, args): union of free vars in all args
    * 
    * @param tm The term to analyze
    * @return List of variable names (as strings)
    */
  def fvt(tm: Term): List[String] =
    tm match
      case Term.Var(x) => List(x)
      case Term.Fn(f, args) => unions(args.map(fvt))
      case Term.Const(_) => List()
  
  // ==================== Variables in Formulas ====================
  
  /** All variables (free and bound) in a formula
    * 
    * OCaml implementation:
    *   let rec var fm =
    *      match fm with
    *       False | True -> []
    *     | Atom(R(p,args)) -> unions (map fvt args)
    *     | Not(p) -> var p
    *     | And(p,q) | Or(p,q) | Imp(p,q) | Iff(p,q) -> union (var p) (var q)
    *     | Forall(x,p) | Exists(x,p) -> insert x (var p)
    * 
    * This returns ALL variables, including bound ones.
    * Quantified variables are included.
    */
  def varFormula[A](fm: Formula[A], atomVars: A => List[String]): List[String] =
    fm match
      case Formula.False | Formula.True => List()
      case Formula.Atom(atom) => atomVars(atom)
      case Formula.Not(p) => varFormula(p, atomVars)
      case Formula.And(p, q) =>
        union(varFormula(p, atomVars), varFormula(q, atomVars))
      case Formula.Or(p, q) =>
        union(varFormula(p, atomVars), varFormula(q, atomVars))
      case Formula.Imp(p, q) =>
        union(varFormula(p, atomVars), varFormula(q, atomVars))
      case Formula.Iff(p, q) =>
        union(varFormula(p, atomVars), varFormula(q, atomVars))
      case Formula.Forall(x, p) =>
        insert(x, varFormula(p, atomVars))
      case Formula.Exists(x, p) =>
        insert(x, varFormula(p, atomVars))
  
  /** All variables in a FOL formula */
  def varFOL(fm: Formula[FOL]): List[String] =
    varFormula(fm, fol => unions(fol.terms.map(fvt)))
  
  /** Free variables in a formula
    * 
    * OCaml implementation:
    *   let rec fv fm =
    *     match fm with
    *       False | True -> []
    *     | Atom(R(p,args)) -> unions (map fvt args)
    *     | Not(p) -> fv p
    *     | And(p,q) | Or(p,q) | Imp(p,q) | Iff(p,q) -> union (fv p) (fv q)
    *     | Forall(x,p) | Exists(x,p) -> subtract (fv p) [x]
    * 
    * Free variables are those NOT bound by quantifiers.
    * Key difference from var: quantified variables are SUBTRACTED.
    */
  def fv[A](fm: Formula[A], atomVars: A => List[String]): List[String] =
    fm match
      case Formula.False | Formula.True => List()
      case Formula.Atom(atom) => atomVars(atom)
      case Formula.Not(p) => fv(p, atomVars)
      case Formula.And(p, q) =>
        union(fv(p, atomVars), fv(q, atomVars))
      case Formula.Or(p, q) =>
        union(fv(p, atomVars), fv(q, atomVars))
      case Formula.Imp(p, q) =>
        union(fv(p, atomVars), fv(q, atomVars))
      case Formula.Iff(p, q) =>
        union(fv(p, atomVars), fv(q, atomVars))
      case Formula.Forall(x, p) =>
        subtract(fv(p, atomVars), List(x))
      case Formula.Exists(x, p) =>
        subtract(fv(p, atomVars), List(x))
  
  /** Free variables in a FOL formula */
  def fvFOL(fm: Formula[FOL]): List[String] =
    fv(fm, fol => unions(fol.terms.map(fvt)))
  
  // ==================== Universal Closure ====================
  
  /** Universal closure: quantify all free variables
    * 
    * OCaml implementation:
    *   let generalize fm = itlist mk_forall (fv fm) fm
    * 
    * Converts: P(x, y) /\ Q(z)
    * Into: forall x y z. P(x, y) /\ Q(z)
    */
  def generalize[A](fm: Formula[A], atomVars: A => List[String]): Formula[A] =
    val freeVars = fv(fm, atomVars)
    freeVars.foldRight(fm)((x, f) => Formula.Forall(x, f))
  
  /** Universal closure for FOL formulas */
  def generalizeFOL(fm: Formula[FOL]): Formula[FOL] =
    generalize(fm, fol => unions(fol.terms.map(fvt)))
  
  // ==================== Substitution in Terms ====================
  
  /** Substitution in terms
    * 
    * OCaml implementation:
    *   let rec tsubst sfn tm =
    *     match tm with
    *       Var x -> tryapplyd sfn x tm
    *     | Fn(f,args) -> Fn(f,map (tsubst sfn) args)
    * 
    * Replaces variables according to substitution map.
    * 
    * @param subst Map from variable names to terms
    * @param tm The term to substitute into
    * @return The term with substitutions applied
    */
  def tsubst(subst: Map[String, Term], tm: Term): Term =
    tm match
      case Term.Var(x) => subst.getOrElse(x, tm)
      case Term.Fn(f, args) => Term.Fn(f, args.map(tsubst(subst, _)))
      case Term.Const(_) => tm
  
  // ==================== Variable Renaming ====================
  
  /** Generate fresh variable name not in given list
    * 
    * OCaml implementation:
    *   let rec variant x vars =
    *     if mem x vars then variant (x^"'") vars else x
    * 
    * Adds primes (') until the name is unique.
    * 
    * Example:
    *   variant("x", List("x", "x'")) => "x''"
    */
  def variant(x: String, vars: List[String]): String =
    if vars.contains(x) then variant(x + "'", vars) else x
  
  // ==================== Substitution in Formulas ====================
  
  /** Substitution in formulas with variable renaming
    * 
    * OCaml implementation:
    *   let rec subst subfn fm =
    *     match fm with
    *       False -> False
    *     | True -> True
    *     | Atom(R(p,args)) -> Atom(R(p,map (tsubst subfn) args))
    *     | Not(p) -> Not(subst subfn p)
    *     | And(p,q) -> And(subst subfn p,subst subfn q)
    *     | Or(p,q) -> Or(subst subfn p,subst subfn q)
    *     | Imp(p,q) -> Imp(subst subfn p,subst subfn q)
    *     | Iff(p,q) -> Iff(subst subfn p,subst subfn q)
    *     | Forall(x,p) -> substq subfn mk_forall x p
    *     | Exists(x,p) -> substq subfn mk_exists x p
    * 
    *   and substq subfn quant x p =
    *     let x' = if exists (fun y -> mem x (fvt(tryapplyd subfn y (Var y))))
    *                        (subtract (fv p) [x])
    *              then variant x (fv(subst (undefine x subfn) p)) else x in
    *     quant x' (subst ((x |-> Var x') subfn) p)
    * 
    * Key feature: VARIABLE CAPTURE AVOIDANCE
    * If substitution would capture a variable, rename the bound variable.
    * 
    * Example:
    *   subst(Map("y" -> Var("x")), forall x. x = y)
    *   => forall x'. x' = x  (renamed to avoid capture)
    */
  def subst(substMap: Map[String, Term], fm: Formula[FOL]): Formula[FOL] =
    fm match
      case Formula.False => Formula.False
      case Formula.True => Formula.True
      case Formula.Atom(FOL(p, args)) =>
        Formula.Atom(FOL(p, args.map(tsubst(substMap, _))))
      case Formula.Not(p) =>
        Formula.Not(subst(substMap, p))
      case Formula.And(p, q) =>
        Formula.And(subst(substMap, p), subst(substMap, q))
      case Formula.Or(p, q) =>
        Formula.Or(subst(substMap, p), subst(substMap, q))
      case Formula.Imp(p, q) =>
        Formula.Imp(subst(substMap, p), subst(substMap, q))
      case Formula.Iff(p, q) =>
        Formula.Iff(subst(substMap, p), subst(substMap, q))
      case Formula.Forall(x, p) =>
        substQuant(substMap, x, p, Formula.Forall.apply)
      case Formula.Exists(x, p) =>
        substQuant(substMap, x, p, Formula.Exists.apply)
  
  /** Helper for substitution in quantified formulas
    * 
    * Checks if substitution would capture the quantified variable.
    * If so, renames the variable to avoid capture.
    */
  private def substQuant(
    substMap: Map[String, Term],
    x: String,
    p: Formula[FOL],
    quant: (String, Formula[FOL]) => Formula[FOL]
  ): Formula[FOL] =
    // Check if x appears free in any substitution target
    val captureRisk = subtract(fvFOL(p), List(x)).exists { y =>
      substMap.get(y).exists(tm => fvt(tm).contains(x))
    }
    
    val x2 = if captureRisk then
      // Need to rename: compute free vars after substituting (with x removed from map)
      val substMapWithoutX = substMap - x
      val freeAfterSubst = fvFOL(subst(substMapWithoutX, p))
      variant(x, freeAfterSubst)
    else
      x
    
    // Build new substitution: map x to Var(x'), and keep other mappings
    val newSubstMap = substMap + (x -> Term.Var(x2))
    quant(x2, subst(newSubstMap, p))
