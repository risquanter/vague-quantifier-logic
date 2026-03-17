package vague.logic

/** Vague quantifier types from paper Section 5.2
  * 
  * Corresponds to paper's notation:
  *   Q[~#]^{k/n}  - "about k/n"
  *   Q[≥]^{k/n}   - "at least about k/n"  
  *   Q[≤]^{k/n}   - "at most about k/n"
  * 
  * OCaml-style ADT pattern: enum for sum type
  * (like Term, Formula from Harrison's formulas.ml)
  * 
  * OCaml reference: formulas.ml
  *   type ('a)formula = False | True | Atom of 'a | ...
  * 
  * Paper reference: Definition 1 (Section 5.2)
  * "We consider vague quantifiers of the form Q[op]^{k/n} where:
  *  - op ∈ {~#, ≥, ≤} specifies the type
  *  - k/n is the target proportion
  *  - tolerance ε determines acceptance region"
  */
enum Quantifier:
  /** Approximately k/n (Q[~#]) - "about k/n" 
    * 
    * Satisfied when: |prop - k/n| ≤ ε
    * 
    * Example: Q[~#]^{1/2} means "about half"
    */
  case About(k: Int, n: Int, tolerance: Double)
  
  /** At least about k/n (Q[≥]) - "at least about k/n" 
    * 
    * Satisfied when: prop ≥ k/n - ε
    * 
    * Example: Q[≥]^{3/4} means "at least about three quarters"
    */
  case AtLeast(k: Int, n: Int, tolerance: Double)
  
  /** At most about k/n (Q[≤]) - "at most about k/n" 
    * 
    * Satisfied when: prop ≤ k/n + ε
    * 
    * Example: Q[≤]^{1/4} means "at most about one quarter"
    */
  case AtMost(k: Int, n: Int, tolerance: Double)

object Quantifier:
  /** Smart constructors (OCaml pattern: mk_* functions)
    * 
    * OCaml reference: formulas.ml
    *   let mk_and p q = And(p,q) and mk_or p q = Or(p,q)
    * 
    * These provide validation and maintain invariants.
    */
  def mkAbout(k: Int, n: Int, tol: Double = 0.1): Quantifier = 
    require(n > 0, "Denominator must be positive")
    require(k >= 0 && k <= n, s"Numerator must be in [0, n]: got k=$k, n=$n")
    require(tol >= 0 && tol <= 1, s"Tolerance must be in [0, 1]: got $tol")
    About(k, n, tol)
  
  def mkAtLeast(k: Int, n: Int, tol: Double = 0.1): Quantifier = 
    require(n > 0, "Denominator must be positive")
    require(k >= 0 && k <= n, s"Numerator must be in [0, n]: got k=$k, n=$n")
    require(tol >= 0 && tol <= 1, s"Tolerance must be in [0, 1]: got $tol")
    AtLeast(k, n, tol)
  
  def mkAtMost(k: Int, n: Int, tol: Double = 0.1): Quantifier = 
    require(n > 0, "Denominator must be positive")
    require(k >= 0 && k <= n, s"Numerator must be in [0, n]: got k=$k, n=$n")
    require(tol >= 0 && tol <= 1, s"Tolerance must be in [0, 1]: got $tol")
    AtMost(k, n, tol)
  
  /** Common quantifiers from paper
    * 
    * Paper reference: Example 3 (Section 5.2)
    * "We use the following quantifiers in our examples..."
    */
  val almostAll: Quantifier = About(1, 1, 0.1)         // Q[~1]
  val aboutHalf: Quantifier = About(1, 2, 0.1)         // Q[~#]^{1/2}
  val aboutThreeQuarters: Quantifier = AtLeast(3, 4, 0.1)  // Q[≥]^{3/4}
  val aboutOneThird: Quantifier = About(1, 3, 0.1)     // Q[~#]^{1/3}
  val aboutTwoThirds: Quantifier = AtLeast(2, 3, 0.1) // Q[≥]^{2/3}
  
  /** Target proportion (k/n) for a quantifier
    * 
    * OCaml pattern: accessor function
    * OCaml reference: fol.ml has helper functions for data extraction
    */
  def targetProportion(q: Quantifier): Double = q match
    case About(k, n, _) => k.toDouble / n.toDouble
    case AtLeast(k, n, _) => k.toDouble / n.toDouble
    case AtMost(k, n, _) => k.toDouble / n.toDouble
  
  /** Check if quantifier accepts a proportion (paper Definition 2)
    * 
    * OCaml pattern: predicate function with pattern matching
    * OCaml reference: fol.ml
    *   let rec holds (domain,func,pred as m) v fm =
    *     match fm with ...
    * 
    * Paper reference: Definition 2 (Section 5.2)
    * "A vague query Q x (R, φ) is satisfied with precision ε when:
    *  - Q[~#]^{k/n}: |Prop_D - k/n| ≤ ε
    *  - Q[≥]^{k/n}:  Prop_D ≥ k/n - ε
    *  - Q[≤]^{k/n}:  Prop_D ≤ k/n + ε"
    * 
    * @param q Quantifier type
    * @param prop Observed proportion from sample
    * @param epsilon Precision parameter from sampling (Section 4)
    * @return true if quantifier accepts proportion
    */
  def accepts(q: Quantifier, prop: Double, epsilon: Double): Boolean = 
    require(prop >= 0.0 && prop <= 1.0, s"Proportion must be in [0, 1]: got $prop")
    require(epsilon >= 0.0 && epsilon <= 1.0, s"Epsilon must be in [0, 1]: got $epsilon")
    
    q match
      case About(k, n, _) =>
        val target = k.toDouble / n.toDouble
        // Satisfied when: |prop - target| ≤ ε
        val diff = math.abs(prop - target)
        diff <= epsilon
        
      case AtLeast(k, n, _) =>
        val threshold = k.toDouble / n.toDouble
        // Satisfied when: prop ≥ threshold - ε
        prop >= threshold - epsilon
        
      case AtMost(k, n, _) =>
        val threshold = k.toDouble / n.toDouble
        // Satisfied when: prop ≤ threshold + ε
        prop <= threshold + epsilon
  
  /** Get tolerance parameter from quantifier */
  def tolerance(q: Quantifier): Double = q match
    case About(_, _, tol) => tol
    case AtLeast(_, _, tol) => tol
    case AtMost(_, _, tol) => tol
  
  /** Acceptance range [lower, upper] for a quantifier.
    *
    * Returns the proportion interval within which `accepts` returns true.
    * Clamped to [0, 1].
    *
    * - About(k,n,ε):   [k/n - ε, k/n + ε]
    * - AtLeast(k,n,ε):  [k/n - ε, 1.0]
    * - AtMost(k,n,ε):   [0.0, k/n + ε]
    */
  def acceptanceRange(q: Quantifier): (Double, Double) =
    val target = targetProportion(q)
    val tol = tolerance(q)
    q match
      case _: About   => (math.max(0.0, target - tol), math.min(1.0, target + tol))
      case _: AtLeast => (math.max(0.0, target - tol), 1.0)
      case _: AtMost  => (0.0, math.min(1.0, target + tol))
  
  /** Pretty print quantifier in paper notation
    * 
    * OCaml pattern: printer function
    * OCaml reference: fol.ml has print_fol_formula
    */
  def prettyPrint(q: Quantifier): String = q match
    case About(k, n, tol) => s"Q[~#]^{$k/$n}[tol=$tol]"
    case AtLeast(k, n, tol) => s"Q[≥]^{$k/$n}[tol=$tol]"
    case AtMost(k, n, tol) => s"Q[≤]^{$k/$n}[tol=$tol]"
