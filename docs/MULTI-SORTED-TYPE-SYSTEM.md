# Multi-Sorted Type System for fol-engine

**Status:** Proposal  
**Date:** 2025-07-26  
**Scope:** fol-engine library (vague-quantifier-logic)  

---

## Table of Contents

1. [Problem Description](#1-problem-description)
2. [User Requirements](#2-user-requirements)
3. [Security Requirements](#3-security-requirements)
4. [Proposed Solution](#4-proposed-solution)
5. [Worked Examples](#5-worked-examples)
6. [Refactor Scope Estimate](#6-refactor-scope-estimate)
7. [Rejected Alternatives](#7-rejected-alternatives)
8. [Open Questions](#8-open-questions)

---

## 1. Problem Description

### 1.1 Single-Sorted FOL

fol-engine implements standard single-sorted first-order logic. The entire evaluation pipeline is parameterised on a single domain type `D`:

```scala
// Every symbol maps to/from the same D
case class Interpretation[D](
  domain:       Domain[D],
  funcInterp:   Map[String, List[D] => D],
  predInterp:   Map[String, List[D] => Boolean],
  funcFallback: String => Option[List[D] => D]
)
```

Term evaluation returns `D` regardless of the term's semantic role:

```scala
def evalTerm[D](term: Term, interp: Interpretation[D], valuation: Valuation[D]): D =
  term match
    case Term.Var(x)       => valuation(x)              // D
    case Term.Const(c)     => interp.getFunction(c)(Nil) // D
    case Term.Fn(f, args)  => interp.getFunction(f)(argValues) // D
```

`D` must simultaneously represent:

- **Entity identifiers** — node IDs, asset names, country codes (string-like)
- **Numeric values** — GDP figures, loss amounts, probabilities (number-like)
- **Function return values** — `p95(x)` returns a loss, `lec(x, threshold)` returns a probability

### 1.2 Untyped AST

The AST carries zero sort information:

| Node | Definition | Sort info |
|------|-----------|-----------|
| `Term.Var` | `Var(name: String)` | None |
| `Term.Const` | `Const(name: String)` | None |
| `Term.Fn` | `Fn(name: String, args: List[Term])` | None |
| `FOL` | `FOL(predicate: String, terms: List[Term])` | None |
| `Formula.Forall` | `Forall(x: String, body: Formula[A])` | None |
| `Formula.Exists` | `Exists(x: String, body: Formula[A])` | None |

Variables in quantifiers are bare strings. There is no declaration of what sort a variable ranges over. The evaluator iterates `domain.elements` (all of `D`) for every quantifier — it cannot restrict iteration to a particular sort.

### 1.3 No Pre-Evaluation Type Checking

The current pipeline is:

```
query string → parser → untyped AST → evaluator(model) → Boolean
```

There is no phase between parsing and evaluation that validates sort consistency. Sort violations manifest as runtime exceptions:

- Pattern-match failures in augmenter functions (e.g., `+` receiving `Const` instead of `Num`)
- Nonsensical comparisons (e.g., `>` applied to entity names)
- Quantification over the full domain (iterating losses when only assets are relevant)

### 1.4 The `RelationValue` Ceiling

The built-in domain type is `RelationValue`:

```scala
enum RelationValue:
  case Const(name: String)
  case Num(value: Int)
```

This is a two-variant sum where:

- `Const` holds string identifiers
- `Num` holds a 32-bit integer

Any consumer needing `Long`, `Double`, or multiple distinct numeric representations cannot use `RelationValue` without widening it. The `[D]` parameterisation was introduced specifically to free consumers from this limitation — but the single-sorted constraint remains: whatever `D` they choose must still hold *all* values simultaneously.

### 1.5 Root Cause

The root cause is not a missing variant on `RelationValue`. It is that `Interpretation[D]` is single-sorted: `funcInterp: Map[String, List[D] => D]` forces every function to accept `D` and return `D`. Every predicate takes `List[D]`. Every variable holds `D`. There is no mechanism for the library to express, enforce, or exploit the fact that `p95` returns a loss while `country` ranges over entities.

---

## 2. User Requirements

### 2.1 Register's Query Language (from ADR-028)

The downstream consumer (register) needs queries such as:

**Query A — Loss comparison:**
```
Q[>=]^{2/3} x (leaf(x), >(p95(x), 5000000))
```
- `x` ranges over **Asset** entities
- `leaf` is a predicate on **Asset**
- `p95` is a function **Asset → Loss**
- `5000000` is a **Loss** literal
- `>` compares **Loss × Loss → Boolean**

**Query B — Probability comparison:**
```
Q[<=]^{1/4} x (leaf(x), >(lec(x, 10000000), 0.05))
```
- `lec` is a function **Asset × Loss → Probability**
- `10000000` is a **Loss** literal
- `0.05` is a **Probability** literal
- `>` compares **Probability × Probability → Boolean**

**Query C — Cross-entity relationships:**
```
Q[>=]^{1/2} x (asset(x), ∃r (has_risk(x, r) ∧ mitigated(r)))
```
- `x` ranges over **Asset** entities
- `r` ranges over **Risk** entities
- `has_risk` relates **Asset × Risk**
- `mitigated` is a predicate on **Risk**

### 2.2 Multiple Entity Sorts

Register's domain has at minimum:

| Sort | Runtime representation | Examples |
|------|----------------------|----------|
| Asset | `String` (node ID) | `"asset-001"`, `"server-gamma"` |
| Risk | `String` (node ID) | `"risk-042"`, `"data-breach"` |
| Mitigation | `String` (node ID) | `"mit-007"`, `"firewall-upgrade"` |
| Portfolio | `String` (node ID) | `"portfolio-alpha"` |

These are **semantically distinct** even though they share a runtime representation. `has_risk(Asset, Risk)` must not accept `has_risk(Risk, Asset)`.

### 2.3 Multiple Numeric Sorts

| Sort | Runtime representation | Precision | Examples |
|------|----------------------|-----------|----------|
| Loss | `Long` | Exact integer cents | `5000000`, `10000000` |
| Probability | `Double` | IEEE 754 | `0.05`, `0.99` |

These are **semantically distinct**. `>(Loss, Probability)` is a type error, not a valid comparison.

### 2.4 Extensibility

Adding a new sort (e.g., `Duration`, `Percentage`, `Region`) must be a mechanical change on the consumer side:

1. Declare the sort
2. Register its runtime representation
3. Add relevant function/predicate signatures to the catalog

It must **not** require modifying the fol-engine library.

### 2.5 No Predicate Overloading

Distinct predicates and functions must have distinct names. There is no `>` that is overloaded for both `Loss` and `Probability`. Instead:

- `gt_loss : Loss × Loss → Boolean`
- `gt_prob : Probability × Probability → Boolean`

Or, if a consumer defines a single `>` with a specific signature (say, `Loss × Loss`), then applying `>` to `Probability × Probability` is a type error. The consumer must use a different name for the probability comparison.

---

## 3. Security Requirements

### 3.1 Query String as Trust Boundary

Query strings arrive from users or configuration. The pipeline must ensure that a syntactically valid, well-typed query cannot produce nonsensical results or crash the evaluator.

```
untrusted query string
        │
        ▼
    ┌────────┐
    │ Parser │  → untyped AST (structural validity)
    └────┬───┘
         │
         ▼
    ┌──────────────┐
    │ Type Checker  │  → typed IL (semantic validity) or structured error
    │ + SortCatalog │
    └──────┬───────┘
           │
           ▼
    ┌────────────┐
    │ Evaluator  │  → result (guaranteed well-sorted)
    └────────────┘
```

The type-checking phase is the security boundary. After it, the evaluator can assume every term, function application, and predicate application is sort-consistent.

### 3.2 Structural Correctness Guarantees

The type checker must reject:

| Error class | Example | Today's behaviour |
|-------------|---------|-------------------|
| Sort mismatch in function args | `p95("string-literal")` | Runtime `ClassCastException` or wrong result |
| Sort mismatch in predicate args | `has_risk(risk, asset)` (reversed) | Silently returns false (no matching tuple) |
| Sort mismatch in comparison | `>(loss_value, probability_value)` | Runtime exception or wrong comparison |
| Quantifier over wrong sort | `∀x:Probability (leaf(x), ...)` | Iterates entire domain, nonsensical results |
| Undeclared symbol | `foo(x)` where `foo` not in catalog | Runtime "uninterpreted function" exception |

After type-checking, none of these can occur.

### 3.3 SQL Analogy

This follows the same architecture as SQL databases:

| SQL | fol-engine (proposed) |
|-----|----------------------|
| `CREATE TABLE` / DDL | `SortCatalog` definition |
| Schema catalog | `SortCatalog` instance |
| Query planner type-checking | `TypeChecker.check(ast, catalog)` |
| Query execution | `Evaluator.holds(typedFormula, model)` |

---

## 4. Proposed Solution

### 4.1 Design Principles

1. **Sorts are opaque tags** — the library provides the `Sort` type and sort-checking machinery; consumers define the actual sorts.
2. **Library provides mechanism, not policy** — the sort catalog structure, type checker, and evaluator are library code. Which sorts exist and what they mean is consumer code.
3. **No overloading** — each function/predicate name maps to exactly one signature.
4. **Typed intermediate language (IL)** — the type checker produces a new AST where every node carries its sort. The evaluator operates on this typed IL, not the untyped parse tree.
5. **Consumer-provided runtime representations** — the library does not prescribe how `Loss` is represented at runtime. Consumers provide a mapping from sort to runtime type via a type class or configuration.

### 4.2 Sort Definition

```scala
/** Opaque sort tag — identity is by name.
  * Defined by consumers, manipulated by library.
  */
opaque type Sort = String

object Sort:
  def apply(name: String): Sort = name
  extension (s: Sort) def name: String = s
```

Consumer-side:

```scala
object RegisterSorts:
  val Asset       = Sort("Asset")
  val Risk        = Sort("Risk")
  val Mitigation  = Sort("Mitigation")
  val Portfolio   = Sort("Portfolio")
  val Loss        = Sort("Loss")
  val Probability = Sort("Probability")
```

### 4.3 Sort Catalog

```scala
/** Signature of a function symbol: parameter sorts → return sort */
case class FunctionSig(params: List[Sort], returns: Sort)

/** Signature of a predicate symbol: parameter sorts (returns Boolean implicitly) */
case class PredicateSig(params: List[Sort])

/** Complete catalog of sorts, functions, and predicates.
  *
  * Analogous to a SQL schema catalog. Defines the "type system"
  * for a particular consumer domain.
  */
case class SortCatalog(
  sorts:      Set[Sort],
  functions:  Map[String, FunctionSig],
  predicates: Map[String, PredicateSig],
  constants:  Map[String, Sort]
):
  /** Validate internal consistency:
    * - All sorts referenced in signatures exist in `sorts`
    * - No name collisions between functions and predicates
    */
  def validate: Either[List[String], SortCatalog] = ???
```

Consumer-side catalog for register:

```scala
import RegisterSorts.*

val registerCatalog = SortCatalog(
  sorts = Set(Asset, Risk, Mitigation, Portfolio, Loss, Probability),

  functions = Map(
    "p95"  -> FunctionSig(List(Asset), Loss),
    "p99"  -> FunctionSig(List(Asset), Loss),
    "lec"  -> FunctionSig(List(Asset, Loss), Probability),
    "mean" -> FunctionSig(List(Asset), Loss),
  ),

  predicates = Map(
    "asset"     -> PredicateSig(List(Asset)),
    "risk"      -> PredicateSig(List(Risk)),
    "leaf"      -> PredicateSig(List(Asset)),
    "has_risk"  -> PredicateSig(List(Asset, Risk)),
    "mitigates" -> PredicateSig(List(Mitigation, Risk)),
    "gt_loss"   -> PredicateSig(List(Loss, Loss)),
    "gt_prob"   -> PredicateSig(List(Probability, Probability)),
    "gte_loss"  -> PredicateSig(List(Loss, Loss)),
    "lte_prob"  -> PredicateSig(List(Probability, Probability)),
  ),

  constants = Map(
    // Numeric literals are resolved by a different mechanism (see §4.7)
  )
)
```

### 4.4 Optional Sort Hierarchy

Some consumers may want subsort relationships:

```scala
case class SortCatalog(
  // ...existing fields...
  subtypeOf: Map[Sort, Set[Sort]] = Map.empty  // child → parents
)
```

Example: `Asset <: Node`, `Risk <: Node` — a predicate expecting `Node` accepts both. This is optional; consumers who don't need it leave `subtypeOf` empty and get flat multi-sorted logic.

### 4.5 Typed Intermediate Language

The type checker transforms the untyped AST into a typed IL where every node carries sort metadata.

#### 4.5.1 Typed Terms

```scala
/** Term with sort annotation at every node */
enum TypedTerm:
  case TypedVar(name: String, sort: Sort)
  case TypedConst(value: String, sort: Sort)
  case TypedFn(name: String, args: List[TypedTerm], sort: Sort)

  def sort: Sort  // every term knows its sort
```

#### 4.5.2 Typed Atoms and Formulas

```scala
/** Predicate application with sort-checked arguments */
case class TypedAtom(predicate: String, terms: List[TypedTerm])

/** Formula over typed atoms — quantifiers carry sort declarations */
enum TypedFormula:
  case True
  case False
  case Atom(value: TypedAtom)
  case Not(p: TypedFormula)
  case And(p: TypedFormula, q: TypedFormula)
  case Or(p: TypedFormula, q: TypedFormula)
  case Imp(p: TypedFormula, q: TypedFormula)
  case Iff(p: TypedFormula, q: TypedFormula)
  case Forall(x: String, sort: Sort, body: TypedFormula)
  case Exists(x: String, sort: Sort, body: TypedFormula)
```

Key difference from the untyped AST: `Forall` and `Exists` now carry a `sort: Sort` that declares what sort the bound variable ranges over. This enables sort-restricted quantifier evaluation (§4.8).

#### 4.5.3 Typed Parsed Query

```scala
case class TypedParsedQuery(
  quantifier: Quantifier,
  variable: String,
  variableSort: Sort,
  range: TypedAtom,
  scope: TypedFormula,
  answerVars: List[(String, Sort)] = Nil
)
```

### 4.6 Type Checker

The type checker walks the untyped AST with the sort catalog as context, producing either a typed IL or a structured error.

```scala
object TypeChecker:
  /** Type environment: maps variable names to their sorts */
  type Env = Map[String, Sort]

  /** Check a complete query against a catalog.
    *
    * @return Right(TypedParsedQuery) if well-sorted, Left(errors) otherwise
    */
  def check(
    query: ParsedQuery,
    catalog: SortCatalog
  ): Either[List[SortError], TypedParsedQuery] = ???

  /** Infer the sort of a term given an environment and catalog.
    *
    * - Var: look up in environment
    * - Const: look up in catalog.constants, or apply literal resolution
    * - Fn: look up FunctionSig, check arg sorts, return result sort
    */
  def inferTerm(
    term: Term,
    env: Env,
    catalog: SortCatalog
  ): Either[List[SortError], TypedTerm] = ???

  /** Check that a formula is well-sorted, producing a TypedFormula.
    *
    * - Atom: look up PredicateSig, infer each arg's sort, check match
    * - Forall/Exists: infer variable sort from range predicate (or require annotation)
    * - Connectives: recurse
    */
  def checkFormula(
    formula: Formula[FOL],
    env: Env,
    catalog: SortCatalog
  ): Either[List[SortError], TypedFormula] = ???
```

#### 4.6.1 Sort Error Type

```scala
enum SortError:
  case UndeclaredFunction(name: String, position: SourcePosition)
  case UndeclaredPredicate(name: String, position: SourcePosition)
  case ArgSortMismatch(
    symbol: String,
    argIndex: Int,
    expected: Sort,
    actual: Sort,
    position: SourcePosition
  )
  case UnboundVariable(name: String, position: SourcePosition)
  case AmbiguousLiteral(value: String, candidates: Set[Sort], position: SourcePosition)
  case SortHierarchyViolation(child: Sort, expected: Sort, position: SourcePosition)
```

### 4.7 Numeric Literal Resolution

Numeric literals (e.g., `5000000`, `0.05`) appear in queries as `Term.Const("5000000")`. The type checker must determine their sort. With no overloading, this is context-driven:

**Strategy:** When a literal appears as an argument to a function or predicate with a known signature, the expected sort at that position determines the literal's sort.

Example for `gt_loss(p95(x), 5000000)`:
1. `gt_loss` has signature `PredicateSig(List(Loss, Loss))`
2. Argument 0: `p95(x)` — `p95` has signature `FunctionSig(List(Asset), Loss)` → sort is `Loss` ✓
3. Argument 1: `5000000` — expected sort is `Loss`, literal resolves to `TypedConst("5000000", Loss)` ✓

If a literal appears in an ambiguous position (no constraining context), the type checker emits `AmbiguousLiteral`.

### 4.8 Multi-Sorted Evaluator

The evaluator operates on the typed IL. Sort information enables two key improvements:

#### 4.8.1 Sort-Restricted Domain Iteration

Current (single-sorted):

```scala
// Iterates ALL domain elements for every quantifier
case Formula.Forall(x, body) =>
  model.domain.elements.forall(d =>
    holds(body, model, valuation.updated(x, d))
  )
```

Proposed (multi-sorted):

```scala
// Iterates only elements of the declared sort
case TypedFormula.Forall(x, sort, body) =>
  model.domainOf(sort).forall(d =>
    holds(body, model, valuation.updated(x, d))
  )
```

This is a correctness improvement (prevents iterating losses when quantifying over assets) and a performance improvement (smaller iteration set).

#### 4.8.2 Multi-Sorted Model

```scala
/** Model with per-sort domains */
case class SortedModel(
  domains:    Map[Sort, Set[Any]],  // per-sort runtime values
  functions:  Map[String, List[Any] => Any],
  predicates: Map[String, List[Any] => Boolean]
)
```

> **Note on `Any`:** Within the evaluator, after type-checking guarantees sort consistency, the runtime values are `Any` because different sorts have different JVM types (`String`, `Long`, `Double`). This is safe *because the type checker has already validated all sort constraints*. The `Any` is an implementation detail confined to the evaluator's internals — it does not leak into the public API.

#### 4.8.3 Consumer-Facing Type-Safe Model Builder

Consumers never work with `Any` directly. A builder API preserves type safety at construction time:

```scala
class SortedModelBuilder:
  def addDomain[A](sort: Sort, elements: Set[A]): SortedModelBuilder = ???

  def addFunction[A, B](
    name: String,
    sig: FunctionSig,
    impl: List[A] => B
  ): SortedModelBuilder = ???

  def addPredicate[A](
    name: String,
    sig: PredicateSig,
    impl: List[A] => Boolean
  ): SortedModelBuilder = ???

  def build(catalog: SortCatalog): Either[List[String], SortedModel] = ???
```

### 4.9 Complete Pipeline

```
                    CONSUMER                              LIBRARY
                    ────────                              ───────

       Sort definitions ─────┐
       Function/predicate    │
         signatures ─────────┼──→ SortCatalog
       Sort hierarchy ───────┘         │
                                       │
       Query string ──────────────→ Parser ──→ untyped AST
                                       │            │
                                       │            ▼
                                       └───→ TypeChecker ──→ TypedParsedQuery
                                                   │              OR
                                                   │         List[SortError]
                                                   │
       Per-sort domains ─────┐                     │
       Function impls ───────┼──→ SortedModel      │
       Predicate impls ──────┘        │            │
                                      ▼            ▼
                                Multi-Sorted Evaluator ──→ Either[QueryError, Boolean]
```

### 4.10 Backward Compatibility

The existing single-sorted API (`Model[D]`, `Interpretation[D]`, `holds[D]`) remains intact. The multi-sorted system is an additional, opt-in layer. Consumers who don't need multi-sorted logic continue using `Model[D]` with zero changes.

Migration path for consumers wanting multi-sorted:
1. Define sorts and catalog
2. Replace `Model[D]` construction with `SortedModelBuilder`
3. Pipe parsed queries through `TypeChecker.check` before evaluation
4. Use `SortedEvaluator.holds` instead of `FOLSemantics.holds`

---

## 5. Worked Examples

### 5.1 Accepted Query: Loss Comparison

**Query:** `Q[>=]^{2/3} x (leaf(x), gt_loss(p95(x), 5000000))`

**Untyped AST (from parser):**
```
ParsedQuery(
  quantifier = Quantifier.AtLeast(2, 3),
  variable = "x",
  range = FOL("leaf", [Var("x")]),
  scope = Atom(FOL("gt_loss", [Fn("p95", [Var("x")]), Const("5000000")])),
  answerVars = []
)
```

**Type-checking trace:**

```
1. Range predicate: leaf(x)
   Catalog: leaf : PredicateSig(List(Asset))
   → x : Asset
   ✓ env = { x → Asset }

2. Scope: gt_loss(p95(x), 5000000)
   Catalog: gt_loss : PredicateSig(List(Loss, Loss))

   2a. Arg 0: p95(x)
       Catalog: p95 : FunctionSig(List(Asset), Loss)
       → p95 arg 0: x is Asset, expected Asset ✓
       → p95 returns Loss
       → arg 0 sort: Loss ✓ (matches gt_loss param 0)

   2b. Arg 1: 5000000
       → expected sort at position 1: Loss
       → literal "5000000" resolves to TypedConst("5000000", Loss) ✓

3. Result: well-sorted ✓
```

**Typed IL output:**
```scala
TypedParsedQuery(
  quantifier = Quantifier.AtLeast(2, 3),
  variable = "x",
  variableSort = Asset,
  range = TypedAtom("leaf", List(TypedVar("x", Asset))),
  scope = TypedFormula.Atom(TypedAtom("gt_loss", List(
    TypedFn("p95", List(TypedVar("x", Asset)), Loss),
    TypedConst("5000000", Loss)
  )))
)
```

### 5.2 Rejected Query: Sort Mismatch

**Query:** `Q[>=]^{1/2} x (leaf(x), gt_loss(p95(x), 0.05))`

**Type-checking trace:**

```
1. Range: leaf(x) → x : Asset ✓

2. Scope: gt_loss(p95(x), 0.05)
   Catalog: gt_loss : PredicateSig(List(Loss, Loss))

   2a. Arg 0: p95(x) → Loss ✓

   2b. Arg 1: 0.05
       → expected sort at position 1: Loss
       → "0.05" cannot be parsed as Loss (not an integer)

3. Result: SortError.ArgSortMismatch(
     symbol = "gt_loss",
     argIndex = 1,
     expected = Loss,
     actual = ???,      // literal cannot resolve
     position = ...
   )
```

**Error message:** `Sort mismatch in gt_loss at argument 2: expected Loss, but literal "0.05" cannot be interpreted as Loss`

### 5.3 Rejected Query: Reversed Relation Arguments

**Query:** `Q[>=]^{1/2} r (risk(r), has_risk(r, x))`

Assuming `x` is a free variable bound to an `Asset` in the outer context:

```
1. Range: risk(r) → r : Risk ✓

2. Scope: has_risk(r, x)
   Catalog: has_risk : PredicateSig(List(Asset, Risk))

   2a. Arg 0: r is Risk, expected Asset
       → MISMATCH

3. Result: SortError.ArgSortMismatch(
     symbol = "has_risk",
     argIndex = 0,
     expected = Asset,
     actual = Risk,
     position = ...
   )
```

**Error message:** `Sort mismatch in has_risk at argument 1: expected Asset, got Risk`

### 5.4 Cross-Entity Query with Existential

**Query:** `Q[>=]^{1/2} x (asset(x), ∃r (has_risk(x, r) ∧ mitigated(r)))`

**Type-checking trace:**

```
1. Range: asset(x) → x : Asset ✓

2. Scope: ∃r (has_risk(x, r) ∧ mitigated(r))
   Need to infer sort of r.

   2a. has_risk(x, r)
       Catalog: has_risk : PredicateSig(List(Asset, Risk))
       → arg 0: x is Asset, expected Asset ✓
       → arg 1: r must be Risk
       → r : Risk ✓

   2b. mitigated(r)
       Catalog: mitigated : PredicateSig(List(Risk))
       → arg 0: r is Risk, expected Risk ✓

3. Result: well-sorted ✓
   Existential becomes: Exists("r", Risk, ...)
   → evaluator iterates only Risk-sorted domain elements
```

### 5.5 Register's Runtime Model

```scala
// Consumer defines sort-specific domains
val assetDomain: Set[String] = Set("asset-001", "asset-002", "server-gamma")
val riskDomain:  Set[String] = Set("risk-042", "data-breach")
val lossDomain:  Set[Long]   = Set(5000000L, 10000000L, 50000L)
val probDomain:  Set[Double] = Set(0.05, 0.95, 0.5)

// Consumer builds the model
val model = SortedModelBuilder()
  .addDomain(Asset, assetDomain)
  .addDomain(Risk, riskDomain)
  .addDomain(Loss, lossDomain)
  .addDomain(Probability, probDomain)
  .addFunction("p95", FunctionSig(List(Asset), Loss),
    (args: List[Any]) => lookupP95(args.head.asInstanceOf[String]))
  .addPredicate("leaf", PredicateSig(List(Asset)),
    (args: List[Any]) => isLeafAsset(args.head.asInstanceOf[String]))
  .addPredicate("gt_loss", PredicateSig(List(Loss, Loss)),
    (args: List[Any]) =>
      args(0).asInstanceOf[Long] > args(1).asInstanceOf[Long])
  .build(registerCatalog)
```

The `asInstanceOf` casts are safe because the type checker has already validated that `p95` only receives `Asset`-sorted values and `gt_loss` only receives `Loss`-sorted values. The builder's `build` method verifies at construction time that every function and predicate declared in the catalog has a corresponding implementation.

---

## 6. Refactor Scope Estimate

### 6.1 New Files

| File | Purpose | Est. lines |
|------|---------|-----------|
| `Sort.scala` | `Sort` opaque type | ~20 |
| `SortCatalog.scala` | Catalog + signatures + validation | ~120 |
| `SortError.scala` | Structured sort error ADT | ~60 |
| `TypedTerm.scala` | Typed term IL | ~40 |
| `TypedFormula.scala` | Typed formula IL | ~50 |
| `TypedParsedQuery.scala` | Typed query | ~30 |
| `TypeChecker.scala` | Sort inference + checking | ~250 |
| `SortedModel.scala` | Multi-sorted model + builder | ~150 |
| `SortedEvaluator.scala` | Multi-sorted `holds`/`evalTerm` | ~200 |

**Estimated new code: ~920 lines**

### 6.2 Modified Files

| File | Change | Est. delta |
|------|--------|-----------|
| `VagueSemantics.scala` | Add sorted overloads alongside existing API | +40 |
| `FOLBridge.scala` | Add sorted model construction path | +30 |
| `ScopeEvaluator.scala` | Add sorted evaluation path | +50 |
| `ParsedQuery.scala` | No change (untyped, feeds into TypeChecker) | 0 |

**Estimated modifications: ~120 lines**

### 6.3 Test Files

| File | Purpose | Est. lines |
|------|---------|-----------|
| `SortCatalogSpec.scala` | Catalog validation | ~100 |
| `TypeCheckerSpec.scala` | Sort checking: accepted, rejected, edge cases | ~300 |
| `SortedEvaluatorSpec.scala` | End-to-end multi-sorted evaluation | ~200 |
| `SortedModelBuilderSpec.scala` | Builder validation, type safety | ~100 |

**Estimated test code: ~700 lines**

### 6.4 Total

~1,740 lines of new/modified code across ~13 new files and ~3 modified files. Existing single-sorted tests and API remain untouched.

---

## 7. Rejected Alternatives

### 7.1 `Model[Any]`

Register's ADR-028 appendix proposed instantiating the library at `Model[Any]` and casting at the boundary. This discards the type parameterisation that `[D]` provides, making every function `List[Any] => Any` everywhere — not just in the evaluator internals. Sort violations become invisible and produce wrong answers instead of errors. **Rejected.**

### 7.2 Widen `RelationValue` with `Dec(BigDecimal)`

Adding a `Dec(BigDecimal)` variant to `RelationValue` would handle `Double` values but:
- `BigDecimal` arithmetic overhead is unjustified for Monte Carlo simulation precision
- Does not solve the sort discipline problem (still single-sorted, still `D = RelationValue`)
- Locks future consumers into `BigDecimal` semantics

**Rejected.**

### 7.3 Simple Entity/Numeric Binary Split

A two-sort system (`Entity` + `Numeric`) is too rigid:
- Register needs multiple entity sorts (`Asset`, `Risk`, `Mitigation`) with distinct predicates
- Register needs multiple numeric sorts (`Loss` as `Long`, `Probability` as `Double`)
- A binary split collapses to the same problem: all entities are interchangeable, all numbers are interchangeable

**Rejected.**

### 7.4 Predicate Overloading

Allowing multiple signatures for the same predicate name (e.g., `>` for both `Loss` and `Probability`) would require:
- Overload resolution logic in the type checker
- Ambiguity rules for literals
- Complex error messages when resolution fails

This adds significant complexity for minimal benefit. Distinct names (e.g., `gt_loss`, `gt_prob`) are clearer and simpler. **Rejected.**

---

## 8. Open Questions

1. **Quantifier variable sort inference:** Should the type checker infer a variable's sort from its range predicate (e.g., `leaf(x)` implies `x : Asset`), or should the parser require explicit sort annotations (e.g., `∀x:Asset`)? Inference is more ergonomic but may be ambiguous if a variable appears in multiple predicates.

2. **Sort hierarchy depth:** Is single-level subsort (`Asset <: Node`) sufficient, or does register need transitive hierarchies (`LeafAsset <: Asset <: Node`)?

3. **Literal disambiguation strategy:** When a literal like `100` could be either `Loss` or some other numeric sort, should the type checker:
   - Require explicit sort annotation on the literal?
   - Infer from enclosing context (parent predicate/function signature)?
   - Reject as ambiguous if context is insufficient?

4. **Phasing:** Should this be implemented as:
   - A single large PR (all-or-nothing)?
   - Phase 1: Sort + Catalog + TypeChecker (parse-time checking only, no runtime changes)?
   - Phase 2: SortedModel + SortedEvaluator (runtime multi-sorted evaluation)?

5. **`ModelAugmenter` in multi-sorted world:** The existing `ModelAugmenter[D]` pattern (endomorphism `Model[D] => Model[D]`) does not directly apply to `SortedModel` (which has no `D` parameter). Should there be a `SortedModelAugmenter`? Or should augmentation be replaced by catalog-driven model building?

6. **Backward compatibility contract:** Is "existing single-sorted API remains unchanged" sufficient, or should there be a migration adapter (e.g., `Model[D] => SortedModel` with a single sort)?
