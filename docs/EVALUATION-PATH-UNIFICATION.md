# Evaluation Path Unification — Full AST Consolidation

**Status:** Planned  
**Created:** 2026-03-16  
**Context:** This document captures the design and rationale for unifying
the two parallel evaluation paths in fol-engine into a single pipeline.

---

## Problem Statement

fol-engine currently has **two independent implementations** of the same
paper (Fermüller et al., Definition 2 — Sampled Answer Semantics):

| Aspect | String-parser path (`vague.semantics`) | Typed-DSL path (`vague.query` / `vague.quantifier`) |
|---|---|---|
| **Query AST** | `vague.logic.VagueQuery` | `vague.query.VagueQuery[A]` |
| **Quantifier** | `vague.logic.Quantifier` — enum with `About(k, n, tol)`, `AtLeast(k, n, tol)`, `AtMost(k, n, tol)` (ratio-based: k/n) | `vague.quantifier.VagueQuantifier` — trait with `Approximately(target, tol)`, `AtLeast(threshold)`, `AtMost(threshold)` (percentage-based) |
| **Evaluator** | `VagueSemantics.holds()` → `RangeExtractor` + `ScopeEvaluator` | `VagueQuantifier.evaluateWithSampling()` → `ProportionEstimator` + `HDRSampler` |
| **Domain type** | `Set[RelationValue]` (KB domain, untyped bridge) | `Set[A]` (generic Scala type) |
| **Sampling** | `HDRSampler` + `SampleSizeCalculator` (after A1 fix) | `HDRSampler` + `SampleSizeCalculator` (already correct) |
| **Result type** | `vague.semantics.QueryResult` (flat, 5 fields + satisfyingElements after A3) | `vague.query.QueryResult` wrapping `vague.quantifier.QuantifierResult` (with CI) |

Both paths implement identical semantics. The duplication is a
chronological artefact: `VagueSemantics` was written first (closer to the
OCaml reference), the typed DSL was built later with proper statistical
infrastructure, and the two were never unified.

### Why this matters

1. **Two result types in one JAR** — `vague.semantics.QueryResult` and
   `vague.query.QueryResult` represent the same concept with different
   capabilities. Callers must know which import to use.
2. **Quantifier logic duplicated** — `VagueSemantics.checkQuantifier` and
   `Quantifier.accepts` implement the same tolerance semantics.
3. **Maintenance burden** — any change to evaluation semantics must be
   applied in two places.
4. **Consumer confusion** — register must understand which path to call
   and why.

---

## Target Architecture

```
                     ┌──────────────────┐
  String ──parser──→ │  VagueQuery[A]   │──┐
                     │  (single type)   │  │     ┌─────────────────────┐
                     └──────────────────┘  ├───→ │  Single evaluator   │──→ VagueQueryResult
                     ┌──────────────────┐  │     │  (HDR + CI + §2)   │
  Typed DSL ───────→ │  VagueQuery[A]   │──┘     └─────────────────────┘
                     └──────────────────┘
```

The string parser emits the **same** `VagueQuery[A]` (with `A = RelationValue`
for the KB path), and evaluation flows through **one** pipeline that
produces **one** result type.

---

## Design Decisions

### D1 — Canonical quantifier representation

**Decision:** Keep `vague.logic.Quantifier` (ratio-based k/n + tolerance)
as the internal canonical form. The typed-DSL `VagueQuantifier` becomes a
user-facing builder that produces `Quantifier` values.

**Rationale:** The parser naturally produces k/n ratios from the query
syntax `Q[>=]^{2/3}`. The ratio form is also what the paper uses. The
percentage form (`target = 0.667`) is a convenience that can be a factory
method.

**Mapping:**
```
Approximately(target=0.5, tolerance=0.1) → Quantifier.About(k=1, n=2, tolerance=0.1)
AtLeast(threshold=0.7)                   → Quantifier.AtLeast(k=7, n=10, tolerance=0.1)
AtMost(threshold=0.3)                    → Quantifier.AtMost(k=3, n=10, tolerance=0.1)
```

The `VagueQuantifier` trait remains as the ergonomic Scala API but
delegates acceptance checking to `Quantifier.accepts`.

### D2 — Unified query type

**Decision:** `vague.query.VagueQuery[A]` remains the canonical query
type. The string parser produces `VagueQuery[RelationValue]`.

The key bridge: the scope formula (FOL `Formula`) is wrapped as a
predicate `RelationValue => Boolean` that closes over the `Model[Any]`
and calls `ScopeEvaluator.evaluateForElement` internally:

```scala
// Bridge: FOL formula → typed predicate
val scopePredicate: RelationValue => Boolean = elem =>
  ScopeEvaluator.evaluateForElement(formula, elem, variable, model, answerTuple)
```

This lets the string-parsed query feed into `VagueQuantifier.evaluateWithSampling`
which already handles sampling, CI, and tolerance correctly.

### D3 — Unified result type: `VagueQueryResult`

**Decision:** A single result type replaces both existing `QueryResult` types.

```scala
case class VagueQueryResult(
  satisfied: Boolean,
  proportion: Double,
  rangeSize: Int,
  sampleSize: Int,
  satisfyingCount: Int,
  satisfyingElements: Option[Set[Any]] = None,
  confidenceInterval: Option[(Double, Double)] = None,
  marginOfError: Option[Double] = None
)
```

- Exact evaluation: `sampleSize == rangeSize`, CI fields are `None`
- Sampled evaluation: CI populated from `ProportionEstimate`
- `satisfyingElements`: populated when caller needs the actual set (e.g.
  register's `matchingNodeIds`); `None` when only the count is needed

### D4 — `VagueSemantics` becomes a thin facade

`VagueSemantics.holds()` becomes a convenience entry point that:
1. Calls `RangeExtractor` to get the range set
2. Builds the scope predicate bridge (D2)
3. Delegates to the unified evaluation pipeline
4. Returns `VagueQueryResult`

This preserves backward compatibility for existing callers while
eliminating the internal duplication.

---

## Implementation Steps

### Step 1 — Unify quantifier acceptance

- Add conversion methods: `VagueQuantifier.toQuantifier: Quantifier` and
  `Quantifier.toVagueQuantifier: VagueQuantifier`
- Make `VagueQuantifier.evaluate(proportion)` delegate to
  `Quantifier.accepts(toQuantifier, proportion, tolerance)`
- Remove duplicated tolerance logic from `VagueQuantifier` trait
- **Tests:** Existing `VagueQuantifierSpec` + `VagueSemanticsSpec` —
  same inputs, same expected outputs

### Step 2 — Create `VagueQueryResult`

- Define in `vague.result.VagueQueryResult` (new file)
- Add `satisfyingElements: Option[Set[Any]]` field
- Add `confidenceInterval`, `marginOfError` optional fields
- **Tests:** New property tests: round-trip from `QuantifierResult`,
  round-trip from old `vague.semantics.QueryResult`

### Step 3 — Bridge FOL formulas into typed predicates

- In `VagueSemantics`, create the scope predicate closure (D2)
- Wire through `ProportionEstimator.estimateWithSampling` instead of
  inline `ScopeEvaluator.calculateProportion` + `selectSample`
- **Tests:** `VagueSemanticsSpec` — same KBs, same queries, same results

### Step 4 — Update `VagueSemantics` to return `VagueQueryResult`

- Replace `vague.semantics.QueryResult` with `VagueQueryResult`
- Use `ScopeEvaluator.evaluateSample` internally to get the satisfying
  set for the `satisfyingElements` field
- Remove `checkQuantifier` (delegate to `Quantifier.accepts`)
- Remove `EvaluationParams` (replaced by `SamplingParams` + `HDRConfig`)
- Remove `selectSample` (replaced by `HDRSampler` through `ProportionEstimator`)
- Remove `import scala.util.Random`
- **Tests:** `VagueSemanticsSpec` — mechanical signature updates

### Step 5 — Update `VagueQuery[A].evaluate` to return `VagueQueryResult`

- Remove `vague.query.QueryResult` wrapper type
- `VagueQuery[A].evaluate()` returns `VagueQueryResult` directly
- **Tests:** Update `VagueQueryPlayground` and any query DSL tests

### Step 6 — Remove deprecated KB wrappers

- Delete `holdsOnKB`, `holdsExactOnKB`, `holdsWithSamplingOnKB`
- **Tests:** Remove any tests that call these

### Step 7 — Build cleanup

- Remove `commons-math3` from `build.sbt`
- Remove `simulation.util` from `build.sbt` (replaced by `hdr-rng`)
- Verify `sbt test` — all tests pass

### Step 8 — Documentation

- Update `docs/Architecture.md` to reflect single evaluation path
- Update `README.md` with usage examples covering three modes:
  1. **Exact evaluation** — all elements checked, no sampling
  2. **Sampled evaluation** — statistical sampling with HDR + CI
  3. **Exact with element set** — all elements checked, satisfying set returned

---

## Effort Estimate

| Step | Scope | ~Hours |
|---|---|---|
| 1 — Quantifier unification | 2 types, ~6 tests | 1.5 |
| 2 — VagueQueryResult | 1 new file, property tests | 0.5 |
| 3 — FOL→predicate bridge | VagueSemantics internals | 1.0 |
| 4 — VagueSemantics rewrite | 1 file + test updates | 1.5 |
| 5 — VagueQuery[A] update | Query.scala + playground | 1.0 |
| 6 — KB wrapper removal | VagueSemantics tail | 0.5 |
| 7 — Build cleanup | build.sbt verification | 0.5 |
| 8 — Documentation | 3 files | 1.0 |
| **Total** | | **~7.5 hours** |

---

## Validation Strategy

Existing tests serve as a **golden master**:

- `VagueSemanticsSpec` (15 tests) — same KB setup, same queries, same
  expected proportions and satisfaction. Rewrite call sites from
  `VagueSemantics.holdsExact(...)` to the new API; assertions stay
  identical.
- `VagueQuantifierSpec` (14 tests) — become the canonical typed-path
  tests. Already updated for `SamplingParams` + `HDRConfig`.
- `FisherYatesHDRSpec` (41 tests), `SamplingSpec`, `NormalApproxSpec`
  — untouched (infrastructure tests).
- `ScopeEvaluatorSpec`, `RangeExtractorSpec` — untouched (component tests).

The key invariant: **for every existing test, the query input and expected
output do not change.** Only the API surface through which they are called
changes.

---

## Relationship to Register Integration

This unification is tracked as a prerequisite step in the register
[Implementation Plan: Query Pane](../../../register/docs/IMPLEMENTATION-PLAN-QUERY-PANE.md),
task **TG-1b**. After completion:

- Register's `QueryService` calls `VagueSemantics.holds()` (or
  `holdsEither`) which returns `VagueQueryResult` including
  `satisfyingElements`
- `QueryResponseBuilder` takes a `VagueQueryResult` instead of raw
  `Set[RelationValue]`
- No bypass of the library's evaluation pipeline is needed
- Cross-compilation (A6) can proceed since all dependencies are pure Scala
