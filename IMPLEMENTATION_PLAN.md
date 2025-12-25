# Implementation Plan: Paper-Compliant Vague Quantifier Queries

## Goal
Implement the paper's approach from Section 5.2: Extend FOL queries with vague quantifiers where the scope predicate is a FOL formula, not a Scala function.

## Architecture Principles

### OCaml Functional Design Patterns (Harrison's "Handbook")

The OCaml source code follows these functional programming patterns:

**1. Algebraic Data Types (ADTs) with pipe constructors:**
```ocaml
(* intro.ml - simple expression ADT *)
type expression =
   Var of string
 | Const of int
 | Add of expression * expression
 | Mul of expression * expression

(* formulas.ml - polymorphic formula ADT *)
type ('a)formula = False
                 | True
                 | Atom of 'a
                 | Not of ('a)formula
                 | And of ('a)formula * ('a)formula
                 (* ... more constructors *)
                 | Forall of string * ('a)formula
                 | Exists of string * ('a)formula

(* fol.ml - FOL atom type *)
type fol = R of string * term list
```

**2. Module-level functions (not methods on types):**
```ocaml
(* Functions operate on data, not encapsulated in types *)
let rec simplify expr =
  match expr with
    Add(e1,e2) -> simplify1(Add(simplify e1,simplify e2))
  | Mul(e1,e2) -> simplify1(Mul(simplify e1,simplify e2))
  | _ -> simplify1 expr

(* Semantics as function, not method *)
let rec holds (domain,func,pred as m) v fm =
  match fm with
    False -> false
  | True -> true
  | Atom(R(r,args)) -> pred r (map (termval m v) args)
  (* ... pattern matching on all cases *)
```

**3. Constructor helpers (OCaml can't use constructors as functions):**
```ocaml
(* formulas.ml - OCaml won't let us use the constructors *)
let mk_and p q = And(p,q) and mk_or p q = Or(p,q)
and mk_imp p q = Imp(p,q) and mk_iff p q = Iff(p,q)
and mk_forall x p = Forall(x,p) and mk_exists x p = Exists(x,p)
```

**4. Destructors via pattern matching:**
```ocaml
let dest_iff fm =
  match fm with Iff(p,q) -> (p,q) | _ -> failwith "dest_iff"

let rec conjuncts fm =
  match fm with And(p,q) -> conjuncts p @ conjuncts q | _ -> [fm]
```

**5. Recursive traversal with pattern matching:**
```ocaml
(* onatoms - apply function to atoms, preserve structure *)
let rec onatoms f fm =
  match fm with
    Atom a -> f a
  | Not(p) -> Not(onatoms f p)
  | And(p,q) -> And(onatoms f p,onatoms f q)
  (* ... recursively transform *)
  | Forall(x,p) -> Forall(x,onatoms f p)
  | _ -> fm
```

**6. Parser combinators with continuation style:**
```ocaml
(* Parsing returns (result, remaining_input) pairs *)
let rec parse_atomic_formula (ifn,afn) vs inp =
  match inp with
    [] -> failwith "formula expected"
  | "false"::rest -> False,rest
  | "true"::rest -> True,rest
  | "~"::rest -> papply (fun p -> Not p)
                        (parse_atomic_formula (ifn,afn) vs rest)
  (* ... *)
```

**7. Interpretation as triple (domain, func, pred):**
```ocaml
(* fol.ml - model as 3-tuple *)
let rec holds (domain,func,pred as m) v fm =
  (* pattern match on 'm' to extract components *)
  
let bool_interp =
  let func f args = match (f,args) with (* ... *)
  and pred p args = match (p,args) with (* ... *)
  in
  ([false; true], func, pred)
```

### Scala Translation Patterns (Existing FOL Codebase)

The Scala code translates OCaml idioms to idiomatic Scala 3:

**1. OCaml ADT → Scala 3 enum:**
```scala
// OCaml: type term = Var of string | Fn of string * term list
enum Term:
  case Var(name: String)
  case Fn(name: String, args: List[Term])
  case Const(name: String)  // Added for convenience

// OCaml: type ('a)formula = False | True | Atom of 'a | ...
enum Formula[+A]:
  case False
  case True
  case Atom(value: A)
  case Not(p: Formula[A])
  case And(p: Formula[A], q: Formula[A])
  // ... more constructors
```

**2. OCaml case class → Scala case class:**
```scala
// OCaml: type fol = R of string * term list
case class FOL(predicate: String, terms: List[Term])

// OCaml: (domain, func, pred) triple → case classes
case class Domain[D](elements: Set[D])
case class Interpretation[D](
  domain: Domain[D],
  funcInterp: Map[String, List[D] => D],
  predInterp: Map[String, List[D] => Boolean]
)
```

**3. OCaml module functions → Scala object with methods:**
```scala
// OCaml: let rec holds (domain,func,pred) v fm = ...
object FOLSemantics:
  def holds[D](
    formula: Formula[FOL],
    model: Model[D],
    valuation: Valuation[D]
  ): Boolean = formula match
    case Formula.False => false
    case Formula.True => true
    // ... pattern matching
```

**4. OCaml constructors → Scala companion smart constructors:**
```scala
// OCaml: let mk_and p q = And(p,q)
object Formula:
  def mkAnd[A](p: Formula[A], q: Formula[A]): Formula[A] = And(p, q)
  def mkOr[A](p: Formula[A], q: Formula[A]): Formula[A] = Or(p, q)
  def atom[A](a: A): Formula[A] = Atom(a)
```

**5. Type aliases for common instantiations:**
```scala
object FOL:
  type FOLFormula = Formula[FOL]  // Common pattern
```

**6. Package structure mirrors OCaml module organization:**
```
src/main/scala/
├── logic/           # Core ADTs (Term, Formula, FOL)
├── parser/          # Parsing (FOLParser, Combinators)
├── printer/         # Pretty printing
├── semantics/       # Model theory (Domain, Interpretation, FOLSemantics)
├── util/            # Utilities
└── examples/        # Demo files
```

**7. OCaml reference comments preserved:**
```scala
/** First-order logic atom: relations/predicates over terms
  * 
  * Corresponds to OCaml:
  *   type fol = R of string * term list
  */
case class FOL(predicate: String, terms: List[Term])
```

### Extending for Vague Quantifiers

Following the same OCaml→Scala patterns, we'll organize vague quantifier code:

**Key Principles:**
1. **ADTs as enums** - VagueQuantifierType as enum (like Term, Formula)
2. **Data as case classes** - VagueQuery as case class (like FOL, Domain)
3. **Logic as object methods** - VagueSemantics as object (like FOLSemantics)
4. **Pattern matching over enums** - evaluate() matches on VagueQuantifierType
5. **Companion smart constructors** - mkAbout, mkAtLeast, mkAtMost
6. **Module organization** - mirror logic/parser/semantics structure
7. **Reference comments** - cite paper sections (like OCaml references)

**Package Structure:**
```
src/main/scala/vague/
├── logic/           # NEW: Vague quantifier ADTs (extends logic/)
│   ├── Quantifier.scala  # enum (like Term, Formula)
│   └── VagueQuery.scala           # case class (like FOL)
├── parser/          # NEW: Vague query parsing (extends parser/)
│   └── VagueQueryParser.scala     # object (like FOLParser)
├── semantics/       # NEW: Vague query semantics (extends semantics/)
│   ├── RangeExtractor.scala       # object with functions
│   ├── ScopeEvaluator.scala       # object with functions
│   └── VagueSemantics.scala       # object (like FOLSemantics)
├── datastore/       # EXISTS: KB, Relations (unchanged)
├── sampling/        # EXISTS: Statistical sampling (unchanged)
├── quantifier/      # EXISTS: VagueQuantifier values (legacy)
├── bridge/          # EXISTS: KB↔Model (unchanged)
└── examples/        # Examples using paper syntax
```

**Design Rationale:**
- Parallel structure to FOL: `logic/` → `parser/` → `semantics/`
- ADT pattern: enums for sum types (VagueQuantifierType), case classes for products (VagueQuery)
- Functional style: objects with pure functions, not mutable classes
- Pattern matching: central evaluation logic via exhaustive matches
- Type safety: leverage Scala's type system like OCaml's type checker

---

## Current State Analysis

✅ **What We Have (OCaml-style):**
- `logic/Term.scala`, `logic/Formula.scala`, `logic/FOL.scala` - ADTs
- `parser/FOLParser.scala` - Parsing FOL formulas
- `semantics/FOLSemantics.scala` - Model theory evaluation
- `vague/datastore/KnowledgeBase.scala` - Relational data
- `vague/sampling/` - Statistical sampling
- `vague/quantifier/VagueQuantifier.scala` - Quantifier values
- `vague/bridge/KnowledgeBaseModel.scala` - KB↔Model translation

❌ **What's Missing (Paper's Approach):**
- `vague/logic/` - Vague quantifier ADTs as Formula extension
- `vague/parser/` - Parser for `Q x (R, φ)` syntax
- `vague/semantics/` - Query evaluation using FOLSemantics
- Integration: scope predicates as FOL formulas, not Scala functions

---

## Implementation Steps (OCaml-Style Architecture)

### **Phase 1: Vague Logic ADTs** (Mirrors `logic/`)
**Goal:** Define vague quantifier types following OCaml/Scala enum patterns

#### Step 1.1: Define VagueQuantifierType enum
**Files:** `src/main/scala/vague/logic/VagueQuantifierType.scala`

```scala
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
  * Paper reference: Definition 1 (Section 5.2)
  */
enum Quantifier:
  /** Approximately k/n (Q[~#]) - "about k/n" 
    * 
    * Satisfied when: |prop - k/n| ≤ ε
    */
  case About(k: Int, n: Int, tolerance: Double)
  
  /** At least about k/n (Q[≥]) - "at least about k/n" 
    * 
    * Satisfied when: prop ≥ k/n - ε
    */
  case AtLeast(k: Int, n: Int, tolerance: Double)
  
  /** At most about k/n (Q[≤]) - "at most about k/n" 
    * 
    * Satisfied when: prop ≤ k/n + ε
    */
  case AtMost(k: Int, n: Int, tolerance: Double)

object Quantifier:
  /** Smart constructors (OCaml pattern: mk_* functions)
    * 
    * OCaml reference: formulas.ml has mk_and, mk_or, etc.
    */
  def mkAbout(k: Int, n: Int, tol: Double = 0.1): Quantifier = 
    require(n > 0, "Denominator must be positive")
    require(k >= 0 && k <= n, "k must be in [0, n]")
    require(tol >= 0 && tol <= 1, "Tolerance must be in [0, 1]")
    About(k, n, tol)
  
  def mkAtLeast(k: Int, n: Int, tol: Double = 0.1): Quantifier = 
    require(n > 0, "Denominator must be positive")
    require(k >= 0 && k <= n, "k must be in [0, n]")
    require(tol >= 0 && tol <= 1, "Tolerance must be in [0, 1]")
    AtLeast(k, n, tol)
  
  def mkAtMost(k: Int, n: Int, tol: Double = 0.1): Quantifier = 
    require(n > 0, "Denominator must be positive")
    require(k >= 0 && k <= n, "k must be in [0, n]")
    require(tol >= 0 && tol <= 1, "Tolerance must be in [0, 1]")
    AtMost(k, n, tol)
  
  /** Common quantifiers from paper (paper uses m=4 for tolerance) */
  val almostAll: VagueQuantifierType = About(1, 1, 0.1)         // Q[~1]
  val aboutHalf: VagueQuantifierType = About(1, 2, 0.1)         // Q[~#]^{1/2}
  val aboutThreeQuarters: VagueQuantifierType = AtLeast(3, 4, 0.1)  // Q[≥]^{3/4}
  val aboutOneThird: VagueQuantifierType = About(1, 3, 0.1)     // Q[~#]^{1/3}
  
  /** Target proportion (k/n) */
  def targetProportion(q: Quantifier): Double = q match
    case About(k, n, _) => k.toDouble / n.toDouble
    case AtLeast(k, n, _) => k.toDouble / n.toDouble
    case AtMost(k, n, _) => k.toDouble / n.toDouble
  
  /** Check satisfaction (paper Definition 2)
    * 
    * @param q Quantifier type
    * @param prop Observed proportion
    * @param epsilon Precision parameter from sampling
    * @return true if quantifier accepts proportion
    */
  def accepts(q: Quantifier, prop: Double, epsilon: Double): Boolean = 
    q match
      case About(k, n, _) =>
        val target = k.toDouble / n.toDouble
        prop >= target - epsilon && prop <= target + epsilon
      case AtLeast(k, n, _) =>
        val threshold = k.toDouble / n.toDouble
        prop >= threshold - epsilon
      case AtMost(k, n, _) =>
        val threshold = k.toDouble / n.toDouble
        prop <= threshold + epsilon
```

**Test:** `vague/logic/QuantifierSpec.scala`
```scala
class QuantifierSpec extends munit.FunSuite:
  import Quantifier.*
  
  test("create About quantifier") {
    val q = mkAbout(3, 4)
    assertEquals(targetProportion(q), 0.75)
  }
  
  test("validate constraints") {
    intercept[IllegalArgumentException] {
      mkAbout(5, 4)  // k > n
    }
  }
  
  test("About accepts within tolerance") {
    val q = About(1, 2, 0.1)  // "about half"
    assert(accepts(q, 0.5, 0.05))   // exactly half
    assert(accepts(q, 0.45, 0.05))  // within ε
    assert(!accepts(q, 0.3, 0.05))  // too far
  }
  
  test("AtLeast accepts above threshold") {
    val q = AtLeast(3, 4, 0.1)  // "at least about 3/4"
    assert(accepts(q, 0.75, 0.05))  // exactly
    assert(accepts(q, 0.8, 0.05))   // above
    assert(!accepts(q, 0.6, 0.05))  // below
  }
  
  test("common quantifiers") {
    assertEquals(targetProportion(almostAll), 1.0)
    assertEquals(targetProportion(aboutHalf), 0.5)
    assertEquals(targetProportion(aboutThreeQuarters), 0.75)
  }
```

**Deliverable:** ADT for vague quantifiers (OCaml-style enum with smart constructors) ✓

---

#### Step 1.2: Define VagueQuery ADT
**Files:** `src/main/scala/vague/logic/VagueQuery.scala`

```scala
package vague.logic

import logic.{FOL, Formula, Term}

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
    * OCaml reference: fol.ml has fvt/var for variable extraction
    */
  def scopeVars: Set[String] =
    def extractFromFormula(f: Formula[FOL]): Set[String] = f match
      case Formula.False | Formula.True => Set.empty
      case Formula.Atom(fol) => 
        fol.terms.flatMap {
          case Term.Var(name) => Set(name)
          case Term.Fn(_, args) => args.collect { case Term.Var(n) => n }
          case Term.Const(_) => Set.empty
        }.toSet
      case Formula.Not(p) => extractFromFormula(p)
      case Formula.And(p, q) => extractFromFormula(p) ++ extractFromFormula(q)
      case Formula.Or(p, q) => extractFromFormula(p) ++ extractFromFormula(q)
      case Formula.Imp(p, q) => extractFromFormula(p) ++ extractFromFormula(q)
      case Formula.Iff(p, q) => extractFromFormula(p) ++ extractFromFormula(q)
      case Formula.Forall(x, p) => extractFromFormula(p) + x
      case Formula.Exists(x, p) => extractFromFormula(p) + x
    extractFromFormula(scope)
  
  /** Check if query is Boolean (no answer variables) */
  def isBoolean: Boolean = answerVars.isEmpty

object VagueQuery:
  /** Smart constructor with validation (OCaml pattern)
    * 
    * OCaml reference: formulas.ml has mk_* constructors
    */
  def mk(
    q: VagueQuantifierType,
    x: String,
    r: FOL,
    phi: Formula[FOL],
    y: List[String] = Nil
  ): VagueQuery =
    val query = VagueQuery(q, x, r, phi, y)
    
    // Validation: x should appear in range
    require(
      query.rangeVars.contains(x),
      s"Quantified variable '$x' must appear in range predicate"
    )
    
    // Paper constraint: y' ⊆ y (range vars ⊆ answer vars + quantified var)
    val rangeVarsMinusX = query.rangeVars - x
    require(
      rangeVarsMinusX.subsetOf(y.toSet),
      s"Range variables ${rangeVarsMinusX} must be subset of answer variables $y"
    )
    
    query
  
  /** Example: q₁ from paper (Boolean query)
    * 
    * Q[≥]^{3/4} x (country(x), ∃y (hasGDP_agr(x,y) ∧ y≤20))
    * 
    * Paper reference: Example 3, query q₁
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
  
  /** Example: q₃ from paper (Unary query with answer variable)
    * 
    * Q[~#]^{1/2} x (capital(x), ...)(y)
    * 
    * Paper reference: Example 3, query q₃
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
```

**Test:** `vague/logic/VagueQuerySpec.scala`
```scala
class VagueQuerySpec extends munit.FunSuite:
  import VagueQuery.*, VagueQuantifierType.*, Formula.*, Term.*
  
  test("create simple query") {
    val q = VagueQuery(
      quantifier = mkAbout(1, 2),
      variable = "x",
      range = FOL("country", List(Var("x"))),
      scope = Atom(FOL("large", List(Var("x")))),
      answerVars = Nil
    )
    assert(q.isBoolean)
    assertEquals(q.rangeVars, Set("x"))
  }
  
  test("validate quantified variable in range") {
    intercept[IllegalArgumentException] {
      mk(
        mkAbout(1, 2),
        "x",
        FOL("country", List(Var("y"))),  // x not in range!
        True
      )
    }
  }
  
  test("extract range variables") {
    val q = VagueQuery(
      quantifier = mkAbout(1, 2),
      variable = "x",
      range = FOL("city_of", List(Var("x"), Var("y"))),
      scope = True,
      answerVars = List("y")
    )
    assertEquals(q.rangeVars, Set("x", "y"))
  }
  
  test("extract scope variables") {
    val q = example1
    assert(q.scopeVars.contains("x"))
    assert(q.scopeVars.contains("y"))
  }
  
  test("paper example q₁ structure") {
    val q1 = example1
    assertEquals(q1.variable, "x")
    assertEquals(q1.range.predicate, "country")
    assert(q1.isBoolean)
    q1.scope match
      case Exists(y, And(_, _)) => assertEquals(y, "y")
      case _ => fail("Expected ∃y (... ∧ ...)")
  }
```

**Deliverable:** Query ADT matching paper syntax (OCaml case class pattern) ✓

---

### **Phase 2: Range Extraction** (Mirrors `semantics/`)
**Goal:** Extract D_R from knowledge base (paper Definition 2)

#### Step 2.1: RangeExtractor
**Files:** `src/main/scala/vague/semantics/RangeExtractor.scala`

```scala
package vague.semantics

import logic.{FOL, Term}
import vague.datastore.{KnowledgeBase, RelationValue}

/** Extract range D_R from paper Definition 2
  * 
  * D_R = {c ∈ ADom(D) | R(c,σ(y')) ∈ D}
  * 
  * OCaml-style: module with functions (object with methods)
  */
object RangeExtractor:
  
  /** Extract range elements satisfying R(x, c')
    * 
    * @param kb Knowledge base (corresponds to D in paper)
    * @param range Range atom R(x,y')
    * @param substitution σ mapping y' to values c'
    * @return Set D_R of range elements
    */
  def extractRange(
    kb: KnowledgeBase,
    range: FOL,
    substitution: Map[String, RelationValue]
  ): Set[RelationValue] =
    // Implementation: query KB for tuples matching R(x, σ(y'))
    ???
  
  /** Sample from range (uses existing sampling infrastructure) */
  def sampleRange(
    range: Set[RelationValue],
    sampleSize: Int,
    seed: Option[Long] = None
  ): Set[RelationValue] =
    // Delegate to existing sampling code
    ???
```

**Test:** `vague/semantics/RangeExtractorSpec.scala` (mirrors `semantics/FOLSemanticsSpec.scala` style)
- Test unary relations: `country(x)`
- Test binary relations: `city_of(x, USA)`
- Test with substitution

**Deliverable:** Range extraction D_R ✓

---

### **Phase 3: Scope Evaluation** (Uses `semantics/FOLSemantics`)
**Goal:** Calculate Prop_D using FOL formula evaluation

#### Step 3.1: ScopeEvaluator
**Files:** `src/main/scala/vague/semantics/ScopeEvaluator.scala`

```scala
package vague.semantics

import logic.{FOL, Formula}
import semantics.{Model, Valuation, FOLSemantics}
import vague.datastore.RelationValue

/** Evaluate scope formula using FOL semantics (paper Definition 2)
  * 
  * Prop_D(S, φ(x,c)) = |{x ∈ S | D ⊨ φ(x,c)}| / |S|
  * 
  * OCaml-style: module with pure functions (object with methods)
  * OCaml reference: fol.ml has "let rec holds (domain,func,pred) v fm = ..."
  * 
  * Paper reference: Definition 2 (Section 5.2)
  * "For a sample S ⊆ D_R, Prop_D(S, φ(x,c)) is the proportion of 
  *  elements in S that satisfy φ under valuation σ{x ↦ element}"
  */
object ScopeEvaluator:
  
  /** Evaluate scope formula for single element
    * 
    * Check: D ⊨_σ φ where σ maps x ↦ element
    * 
    * OCaml pattern: recursive function with pattern matching
    * OCaml reference: fol.ml
    *   let rec holds (domain,func,pred as m) v fm =
    *     match fm with
    *       False -> false
    *     | True -> true
    *     | Atom(R(r,args)) -> pred r (map (termval m v) args)
    *     (* ... *)
    * 
    * @param formula Scope formula φ(x,y)
    * @param element Value for x from range
    * @param variable Name of quantified variable x
    * @param model FOL model (from KB via toModel)
    * @param substitution Values for answer variables y
    * @return true if D ⊨_σ φ (uses FOLSemantics.holds!)
    */
  def evaluateForElement(
    formula: Formula[FOL],
    element: RelationValue,
    variable: String,
    model: Model[Any],
    substitution: Map[String, Any] = Map.empty
  ): Boolean =
    // Convert RelationValue to domain value
    val elementValue: Any = element match
      case RelationValue.Const(name) => name
      case RelationValue.Num(value) => value
    
    // Build valuation: σ{x ↦ element} ∪ substitution
    // OCaml pattern: (x |-> a) v updates valuation
    val valuation = Valuation(
      Map(variable -> elementValue) ++ substitution
    )
    
    // THE KEY INTEGRATION: Use FOLSemantics.holds()
    // OCaml reference: holds (domain,func,pred) v fm
    // Scala translation: FOLSemantics.holds(formula, model, valuation)
    FOLSemantics.holds(formula, model, valuation)
  
  /** Calculate proportion (paper's Prop_D)
    * 
    * Prop_D(S, φ(x,c)) = |{x ∈ S | D ⊨ φ(x,c)}| / |S|
    * 
    * OCaml pattern: higher-order function (filter + count)
    * OCaml reference: lib.ml has filter, length
    *   filter (fun n -> holds (mod_interp n) undefined fm) (1--45)
    * 
    * @param sample Sample S ⊆ D_R
    * @param formula Scope formula φ(x,y)
    * @param variable Quantified variable x
    * @param model FOL model
    * @param substitution Values for y
    * @return Proportion in [0, 1]
    */
  def calculateProportion(
    sample: Set[RelationValue],
    formula: Formula[FOL],
    variable: String,
    model: Model[Any],
    substitution: Map[String, Any] = Map.empty
  ): Double =
    if sample.isEmpty then 0.0
    else
      // OCaml pattern: count elements satisfying predicate
      // OCaml: length (filter predicate list)
      val satisfying = sample.count(elem =>
        evaluateForElement(formula, elem, variable, model, substitution)
      )
      satisfying.toDouble / sample.size.toDouble
  
  /** Batch evaluation (optimization for large samples)
    * 
    * OCaml pattern: map then filter
    * OCaml: map f list |> filter p
    */
  def evaluateSample(
    sample: Set[RelationValue],
    formula: Formula[FOL],
    variable: String,
    model: Model[Any],
    substitution: Map[String, Any] = Map.empty
  ): (Set[RelationValue], Set[RelationValue]) =
    // Partition into satisfying and non-satisfying
    val (satisfying, nonSatisfying) = sample.partition(elem =>
      evaluateForElement(formula, elem, variable, model, substitution)
    )
    (satisfying, nonSatisfying)
```

**Test:** `vague/semantics/ScopeEvaluatorSpec.scala`
```scala
class ScopeEvaluatorSpec extends munit.FunSuite:
  import ScopeEvaluator.*
  import Formula.*, Term.*, RelationValue.*
  
  // Setup test KB and model
  val kb = createTestKB()
  val model = kb.toModel
  
  test("evaluate simple predicate: country(x)") {
    val formula = Atom(FOL("country", List(Var("x"))))
    val element = Const("USA")
    
    val result = evaluateForElement(
      formula, element, "x", model
    )
    assert(result)  // USA is a country
  }
  
  test("evaluate complex formula: ∃y (hasGDP_agr(x,y) ∧ y≤20)") {
    val formula = Exists("y", And(
      Atom(FOL("hasGDP_agr", List(Var("x"), Var("y")))),
      Atom(FOL("<=", List(Var("y"), Const("20"))))
    ))
    
    val element = Const("CountryWithLowGDP")
    val result = evaluateForElement(
      formula, element, "x", model
    )
    // Test based on KB content
  }
  
  test("calculate proportion for sample") {
    val formula = Atom(FOL("large", List(Var("x"))))
    val sample = Set(
      Const("USA"),      // large
      Const("China"),    // large
      Const("Luxembourg") // not large
    )
    
    val prop = calculateProportion(
      sample, formula, "x", model
    )
    assertEquals(prop, 2.0/3.0, 0.01)
  }
  
  test("empty sample returns 0.0") {
    val prop = calculateProportion(
      Set.empty, Atom(FOL("test", List(Var("x")))), "x", model
    )
    assertEquals(prop, 0.0)
  }
  
  test("batch evaluation partitions correctly") {
    val formula = Atom(FOL("large", List(Var("x"))))
    val sample = Set(Const("USA"), Const("Luxembourg"))
    
    val (sat, nonSat) = evaluateSample(
      sample, formula, "x", model
    )
    assertEquals(sat.size + nonSat.size, sample.size)
  }
```

**Deliverable:** Scope evaluation using FOL semantics (THE KEY INTEGRATION!) ✓

---

### **Phase 4: Vague Query Semantics** (Mirrors `semantics/FOLSemantics`)
**Goal:** Complete query evaluation (paper Definition 2)

#### Step 4.1: VagueSemantics
**Files:** `src/main/scala/vague/semantics/VagueSemantics.scala`

```scala
package vague.semantics

import logic.{FOL, Formula}
import semantics.Model
import vague.logic.{VagueQuery, VagueQuantifierType}
import vague.datastore.{KnowledgeBase, RelationValue}
import vague.sampling.SamplingParams

/** Vague query semantics from paper Definition 2
  * 
  * Sampled Answer Semantics:
  * D ⊨_{S,ε} Q x (R(x,y'), φ(x,y))
  * 
  * OCaml-style: module with semantic evaluation (like FOLSemantics)
  */
object VagueSemantics:
  
  /** Result of vague query evaluation */
  case class VagueResult(
    satisfied: Boolean,
    proportion: Double,
    confidenceInterval: (Double, Double),
    sampleSize: Int
  )
  
  /** Evaluate vague query (paper Definition 2)
    * 
    * Check: D ⊨_{S,ε} Q x (R, φ)
    * 
    * @param query Vague query Q x (R, φ)
    * @param kb Knowledge base D
    * @param answerTuple Values for answer variables (c in paper)
    * @param params Sampling parameters (ε, α)
    * @return Query result with satisfaction
    */
  def holds(
    query: VagueQuery,
    kb: KnowledgeBase,
    answerTuple: Map[String, RelationValue] = Map.empty,
    params: SamplingParams = SamplingParams()
  ): VagueResult =
    // 1. Extract range D_R
    val range = RangeExtractor.extractRange(
      kb, query.range, answerTuple
    )
    
    // 2. Sample from range
    val sampleSize = calculateSampleSize(params)
    val sample = RangeExtractor.sampleRange(
      range, sampleSize, params.seed
    )
    
    // 3. Translate KB to Model
    import vague.bridge.toModel
    val model = kb.toModel
    
    // 4. Calculate proportion using FOL semantics
    val answerSubst = answerTuple.map { case (k, v) =>
      k -> (v match {
        case RelationValue.Const(n) => n
        case RelationValue.Num(n) => n
      })
    }
    
    val proportion = ScopeEvaluator.calculateProportion(
      sample,
      query.scope,
      query.variable,
      model,
      answerSubst
    )
    
    // 5. Check quantifier acceptance
    val satisfied = checkQuantifier(
      query.quantifier,
      proportion,
      params.epsilon
    )
    
    VagueResult(
      satisfied = satisfied,
      proportion = proportion,
      confidenceInterval = (0.0, 1.0), // TODO: proper CI
      sampleSize = sample.size
    )
  
  /** Check if proportion satisfies quantifier */
  private def checkQuantifier(
    q: Quantifier,
    prop: Double,
    epsilon: Double
  ): Boolean = q match
    case VagueQuantifierType.About(k, n, _) =>
      val target = k.toDouble / n.toDouble
      prop >= target - epsilon && prop <= target + epsilon
    case VagueQuantifierType.AtLeast(k, n, _) =>
      val threshold = k.toDouble / n.toDouble
      prop >= threshold - epsilon
    case VagueQuantifierType.AtMost(k, n, _) =>
      val threshold = k.toDouble / n.toDouble
      prop <= threshold + epsilon
```

**Test:** `vague/semantics/VagueSemanticsSpec.scala`
- Test Boolean queries (q₁ from paper)
- Test unary queries (q₃ from paper)
- Verify sampling convergence
- Test different ε and α

**Deliverable:** Complete vague query evaluation ✓

---

### **Phase 5: Vague Query Parser** (Mirrors `parser/`)
**Goal:** Parse `Q x (R, φ)` syntax

#### Step 5.1: VagueQueryParser
**Files:** `src/main/scala/vague/parser/VagueQueryParser.scala`

```scala
package vague.parser

import vague.logic.{VagueQuery, VagueQuantifierType}
import logic.{FOL, Formula}
import parser.FOLParser

/** Parser for vague queries (paper Section 5.2)
  * 
  * Syntax: Q[op]^{k/n} x (R(x,y'), φ(x,y))
  * 
  * OCaml-style: module with parsing functions (like FOLParser)
  */
object VagueQueryParser:
  
  /** Parse vague query from string
    * 
    * Examples:
    * - "Q[>=]^{3/4} x (country(x), exists y (hasGDP_agr(x,y) /\\ y<=20))"
    * - "Q[~]^{1/2} x (city(x), exists y (has_pop(x,y) /\\ y>200000))"
    * 
    * @param s Query string
    * @return Parsed VagueQuery
    */
  def parse(s: String): VagueQuery =
    // Parse quantifier: Q[op]^{k/n}
    // Parse variable: x
    // Parse range: R(x,y')
    // Parse scope: φ using FOLParser
    ???
  
  /** Parse just the quantifier part */
  private def parseQuantifier(tokens: List[String]): (VagueQuantifierType, List[String]) =
    ???
```

**Test:** `vague/parser/VagueQueryParserSpec.scala`
- Test parsing paper examples
- Test quantifier parsing
- Test error handling

**Deliverable:** Parser for paper syntax ✓

---

### **Phase 6: Examples** (Mirrors `examples/`)
**Goal:** Create working examples from paper

#### Step 6.1: Paper Examples
**Files:** `src/main/scala/examples/PaperCompliantDemo.scala`

```scala
package examples

import vague.logic.VagueQuery
import vague.parser.VagueQueryParser
import vague.semantics.VagueSemantics
import vague.datastore.RiskDomain

/** Examples from paper Section 5.2 (Example 3)
  * 
  * OCaml-style: executable with main entry point
  */
@main def PaperCompliantDemo(): Unit =
  println("Paper-Compliant Vague Query Demo")
  println("=" * 80)
  
  // Use RiskDomain as proxy for MONDIAL
  val kb = RiskDomain.createKnowledgeBase
  
  // q₁: Boolean query
  val q1 = VagueQueryParser.parse(
    """Q[>=]^{3/4} x (country(x), 
       exists y (hasGDP_agr(x,y) /\\ y<=20))"""
  )
  val result1 = VagueSemantics.holds(q1, kb)
  println(s"q₁ satisfied: ${result1.satisfied}")
  
  // More examples...
```

**Test:** Integration test
- Run full examples
- Verify output matches expectations

**Deliverable:** Working paper examples ✓

---

## File Organization Summary

**Following OCaml→Scala translation patterns:**

```
src/main/scala/
├── logic/                    # Core FOL (OCaml-transpiled from Harrison)
│   ├── Term.scala           # enum Term (OCaml: type term = Var | Fn)
│   ├── Formula.scala        # enum Formula[A] (OCaml: type 'a formula)
│   └── FOL.scala            # case class FOL (OCaml: type fol = R of ...)
├── parser/                   # FOL parsing
│   ├── FOLParser.scala      # object FOLParser (OCaml: let parse = ...)
│   └── Combinators.scala    # Parser combinators
├── semantics/                # FOL semantics
│   └── FOLSemantics.scala   # object FOLSemantics (OCaml: let rec holds = ...)
├── vague/
│   ├── logic/               # NEW: Vague ADTs (mirrors logic/)
│   │   ├── Quantifier.scala   # enum (pattern: Term, Formula)
│   │   └── VagueQuery.scala            # case class (pattern: FOL)
│   ├── parser/              # NEW: Vague parsing (mirrors parser/)
│   │   └── VagueQueryParser.scala      # object (pattern: FOLParser)
│   ├── semantics/           # NEW: Vague semantics (mirrors semantics/)
│   │   ├── RangeExtractor.scala        # object (pattern: module functions)
│   │   ├── ScopeEvaluator.scala        # object (pattern: FOLSemantics)
│   │   └── VagueSemantics.scala        # object (pattern: FOLSemantics)
│   ├── datastore/           # EXISTS: unchanged
│   ├── sampling/            # EXISTS: unchanged
│   └── quantifier/          # EXISTS: unchanged (legacy values)
└── examples/
    ├── FOLDemo.scala        # EXISTS: FOL examples
    └── PaperCompliantDemo.scala  # NEW: Paper examples

src/test/scala/              # Test structure mirrors main/
├── logic/
│   ├── TermSpec.scala
│   ├── FormulaSpec.scala
│   └── FOLSpec.scala
├── vague/
│   ├── logic/
│   │   ├── QuantifierSpec.scala
│   │   └── VagueQuerySpec.scala
│   └── semantics/
│       ├── RangeExtractorSpec.scala
│       ├── ScopeEvaluatorSpec.scala
│       └── VagueSemanticsSpec.scala
```

**Architectural consistency principles:**

| OCaml Pattern | Scala Translation | Example |
|--------------|-------------------|---------|
| `type t = A \| B \| C` | `enum T: case A, B, C` | `VagueQuantifierType` |
| `type t = R of string * 'a list` | `case class T(s: String, xs: List[A])` | `VagueQuery` |
| `let rec f x = match x with ...` | `object O: def f(x: T) = x match ...` | `ScopeEvaluator.evaluateForElement` |
| `let mk_foo x y = Foo(x, y)` | `def mkFoo(x: X, y: Y) = Foo(x, y)` | `VagueQuantifierType.mkAbout` |
| `(* OCaml comment *)` | `/** Paper reference: Section X */` | Reference comments |

**Key architectural decisions:**
1. ✅ Enums for sum types (Quantifier like Term, Formula) - IMPLEMENTED
2. ✅ Case classes for product types (VagueQuery like FOL) - IMPLEMENTED
3. ✅ Objects for modules (VagueSemantics like FOLSemantics) - IMPLEMENTED
4. ✅ Pattern matching for evaluation (exhaustive matches) - IMPLEMENTED
5. ✅ Companion smart constructors (mk* pattern) - IMPLEMENTED
6. ✅ Reference comments (paper sections like OCaml references) - IMPLEMENTED
7. ✅ Test structure mirrors source structure - IMPLEMENTED

---

## Success Criteria (Updated)

### ✅ **Phase 1-4: Core Implementation** - COMPLETE
- ✅ Architectural consistency with OCaml→Scala patterns
- ✅ Enum/case class/object design implemented
- ✅ FOL integration via FOLSemantics.holds()
- ✅ 229 tests passing (100% success rate)
- ✅ Type-safe formula handling (Formula[FOL])
- ✅ Statistical evaluation with sampling support
- ✅ Numeric constant handling
- ✅ FOLUtil integration for variable extraction

### 📋 **Phase 5: Parser** - PENDING
- [ ] Parse paper syntax: `Q[op]^{k/n} x (R, φ)(y)`
- [ ] Lexer extensions for special tokens
- [ ] Combinator-based parsing following FOLParser patterns
- [ ] Integration tests with paper examples
- [ ] Error handling and validation

### 📋 **Phase 6: Paper Examples** - PENDING
- [ ] MONDIAL-style geography dataset
- [ ] Paper queries q₁, q₃ implemented
- [ ] Exact and sampling evaluation demonstrations
- [ ] Output formatting and visualization
- [ ] Validation against paper expected results

### 📋 **Phase 7: Documentation** - PENDING
- [ ] README.md updated with vague quantifier section
- [ ] Detailed guide in docs/VagueQuantifiers.md
- [ ] Scaladoc review and completion
- [ ] Examples README with usage instructions
- [ ] Architecture diagram

---

## Test Coverage Summary

| Component | Tests | Status |
|-----------|-------|--------|
| Quantifier | 40 | ✅ Passing |
| VagueQuery | 29 | ✅ Passing |
| RangeExtractor | 21 | ✅ Passing |
| ScopeEvaluator | 26 | ✅ Passing |
| VagueSemantics | 15 | ✅ Passing |
| Other vague components | 98 | ✅ Passing |
| **Total** | **229** | **✅ 100%** |

Core FOL infrastructure: 358+ tests (all passing)
**Grand Total: 587+ tests passing**

---

## Next Recommended Action

**Option A: Phase 5 (Parser)** - Most logical next step
- Enables paper syntax usage
- Completes the DSL experience
- Medium complexity, well-defined scope
- Can leverage existing FOLParser infrastructure

**Option B: Phase 6 (Examples) without parser** - Validate functionality
- Use VagueQuery constructors directly
- Demonstrate end-to-end evaluation
- Defer parsing as "nice to have"
- Lower complexity, immediate value

**Option C: Phase 7 (Documentation)** - Polish what exists
- Document current functionality
- Prepare for release/sharing
- Can add parser later
- Lowest technical risk

**Recommendation: Option A (Parser)** for completeness, then B, then C.

---

## Implementation Status (Updated: December 25, 2025)

### ✅ Completed Phases

#### **Phase 1: Vague Logic ADTs** ✅ COMPLETE
- ✅ Step 1.1: `Quantifier.scala` enum with About/AtLeast/AtMost variants (40 tests passing)
- ✅ Step 1.2: `VagueQuery.scala` case class with validation (29 tests passing)
- **Total: 69 tests, all passing**

#### **Phase 2: Range Extraction** ✅ COMPLETE
- ✅ `RangeExtractor.scala` - Extracts D_R from KB (21 tests passing)
- ✅ Handles substitutions for answer variables
- ✅ Supports Boolean and unary queries
- ✅ Numeric constant handling implemented

#### **Phase 3: Scope Evaluation** ✅ COMPLETE
- ✅ `ScopeEvaluator.scala` - Evaluates scope formulas using FOLSemantics (26 tests passing)
- ✅ Integration with FOL model theory via `KnowledgeBaseModel`
- ✅ Statistical proportion calculation

#### **Phase 4: Vague Semantics** ✅ COMPLETE
- ✅ `VagueSemantics.scala` - Complete query evaluation orchestration (15 tests passing)
- ✅ Sampling and exact evaluation modes
- ✅ Quantifier satisfaction checking
- ✅ `VagueResult` with confidence metadata

#### **Recent Optimizations** ✅ COMPLETE
- ✅ Numeric constant handling in RangeExtractor (parse integers as RelationValue.Num)
- ✅ Variable extraction using FOLUtil.varFOL (consistency with FOL infrastructure)
- ⚠️ getDomain optimization deferred (requires position-aware analysis)

**Current Test Status: 229 tests passing (all vague domain tests)**

---

### 🔄 Remaining Phases

#### **Phase 5: Vague Query Parser** 📋 NEXT
**Goal:** Parse paper syntax `Q[op]^{k/n} x (R(x,y'), φ(x,y))(y)` into VagueQuery objects

**Files to create:**
- `src/main/scala/vague/parser/VagueQueryParser.scala`
- `src/test/scala/vague/parser/VagueQueryParserSpec.scala`

**Requirements:**
1. Parse quantifier notation: `Q[>=]^{3/4}`, `Q[~]^{1/2}`, `Q[<=]^{1/3}`
2. Parse variable binding: `x` (quantified variable)
3. Parse range predicate: `R(x,y')` using existing FOL term parsing
4. Parse scope formula: `φ(x,y)` using `FOLParser.parse()`
5. Parse optional answer variables: `(y)` or `(y1, y2, ...)`
6. Validate syntax and create VagueQuery instance

**Example inputs:**
```scala
// Boolean query (no answer variables)
"Q[>=]^{3/4} x (country(x), exists y (hasGDP_agr(x,y) /\\ y<=20))"

// Unary query with answer variable
"Q[~]^{1/2} x (capital(x, y), large(x))(y)"

// Multiple answer variables
"Q[>=]^{2/3} x (borders(x, y, z), conflict(x))(y, z)"
```

**Design approach:**
1. Lexer extension for special tokens: `Q`, `[`, `]`, `^`, `{`, `/`, `}`
2. Combinator-based parsing following existing `FOLParser` patterns
3. Reuse FOL term/formula parsing infrastructure
4. Smart constructor validation via `VagueQuery.mk()`

**Estimated complexity:** Medium (reuses FOL parsing infrastructure)

---

#### **Phase 6: Paper Examples with MONDIAL-Style Data** 📋 PENDING
**Goal:** Implement complete examples from paper Section 5.2 with realistic data

**Files to create:**
- `src/main/scala/examples/PaperExamplesDemo.scala`
- `src/main/scala/vague/datastore/MONDIALProxy.scala` (geography dataset)
- `src/test/scala/examples/PaperExamplesSpec.scala`

**Requirements:**
1. Create MONDIAL-style geography dataset:
   - Countries (name, continent, gdp_agr, population)
   - Cities (name, country, population, latitude, longitude)
   - Relations: borders, capital, hasGDP_agr, largeCountry, largeCity

2. Implement paper queries programmatically:
   - **q₁**: Boolean query - "At least about 3/4 of countries have low agricultural GDP"
   - **q₃**: Unary query - "About half of capital cities are large"
   - Additional queries demonstrating all quantifier types

3. Demonstrate both evaluation modes:
   - Exact evaluation (small D_R)
   - Sampling evaluation (large D_R with confidence intervals)

4. Output formatting:
   - Query syntax display
   - Evaluation results (satisfied/not satisfied)
   - Proportion and confidence intervals
   - Sample details

**Design approach:**
- Use existing `KnowledgeBase` infrastructure
- Build queries using `VagueQuery` constructors (parser in Phase 5)
- Reuse `VagueSemantics.holds()` for evaluation
- Compare with paper expected results

**Estimated complexity:** Medium (data creation + query design)

---

#### **Phase 7: Documentation and Polish** 📋 PENDING
**Goal:** Comprehensive documentation for vague quantifier extension

**Files to update/create:**
- Update `README.md` with vague quantifier section
- Create `docs/VagueQuantifiers.md` - detailed guide
- Add paper references and examples
- Code documentation review

**Requirements:**
1. README updates:
   - Add "Vague Quantifiers" section
   - Quick start examples
   - Architecture overview
   - Test status (229+ tests)

2. Detailed guide (`docs/VagueQuantifiers.md`):
   - Paper background (Definition 1, Definition 2)
   - Architecture explanation (logic → semantics → evaluation)
   - API documentation with examples
   - Sampling vs exact evaluation guide
   - Integration with FOL semantics
   - Performance considerations

3. Code documentation:
   - Ensure all public APIs have scaladoc
   - Paper section references in comments
   - Usage examples in class/object docs

4. Examples README:
   - How to run paper examples
   - Expected output
   - Data format documentation

**Estimated complexity:** Low-Medium (mostly writing)

---

## Priority and Dependencies

```
Phase 1-4: ✅ COMPLETE (229 tests passing)
    ↓
Phase 5: Parser 📋 NEXT (no dependencies, can start immediately)
    ↓
Phase 6: Paper Examples (depends on Phase 5 for parsing, or can use constructors)
    ↓
Phase 7: Documentation (depends on Phases 5-6 for completeness)
```

**Recommended Next Steps:**
1. **Immediate**: Phase 5 (Parser) - enables paper syntax usage
2. **Near-term**: Phase 6 (Paper Examples) - validates end-to-end functionality
3. **Final**: Phase 7 (Documentation) - polish and release preparation

**Alternative Approach:**
- Can proceed with Phase 6 using `VagueQuery` constructors (skip parser temporarily)
- Demonstrates functionality while deferring parsing complexity
- Parser can be added later as syntactic sugar

---

## Success Criteria (Updated)
