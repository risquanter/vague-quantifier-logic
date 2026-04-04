# Implementation Plan: Function Return Normalisation at the Dispatcher Boundary

**Status:** Awaiting approval  
**Date:** 2026-04-04  
**Related ADRs:** ADR-015, ADR-001, ADR-006, ADR-014  
**Related TODOs:** T-003 (typed literal pipeline normaliser)

---

## Objective

Close the remaining half of the injection boundary defined in ADR-015.

ADR-015 §1 establishes that inline query literals must reach dispatcher lambdas as
`LiteralValue` (not raw strings). That is implemented and committed. However,
function return values still bypass this boundary: a lambda computing `lec(asset,
threshold)` constructs `Value(tProb, 0.07)` itself — `raw` is a native `Double`.
A downstream `gt_prob` lambda receiving that value alongside an inline literal
`FloatLiteral(0.05)` sees two structurally different `raw` types for the same
sort `Probability`. The `rawToDouble` helper in `MapDispatcherSpec` is the live
symptom.

**Goal:** for any `ValueType` sort, every `Value.raw` a dispatcher lambda receives
is always a `LiteralValue` variant — regardless of whether the value originated
from an inline literal or from a prior function call. The framework owns
`Value` construction for function returns; lambdas never call `Value(...)` directly.

---

## Gap Analysis

### What ADR-015 closed (committed, c1643bb)

| Source | Before | After |
|---|---|---|
| Inline literal `"0.05"` | `ConstRef.raw: String` → `Value(tProb, "0.05")` | `ConstRef.raw: LiteralValue` → `Value(tProb, FloatLiteral(0.05))` |
| Inline literal `"10000000"` | `ConstRef.raw: String` → `Value(tLoss, "10000000")` | `ConstRef.raw: LiteralValue` → `Value(tLoss, IntLiteral(10000000L))` |

### What remains open (this plan)

| Source | Current | After this plan |
|---|---|---|
| `lec(asset, 10000000L)` return | `Value(tProb, 0.07: Double)` — consumer-constructed | `Value(tProb, FloatLiteral(0.07))` — framework-constructed |
| `p95(asset)` return | `Value(tLoss, 5000000L: Long)` — consumer-constructed | `Value(tLoss, IntLiteral(5000000L))` — framework-constructed |

### Symptoms still present after c1643bb

- `rawToDouble` helper in `MapDispatcherSpec` — exists only because `gt_prob`
  must handle both `Double` (from `lec` return) and `FloatLiteral` (from literal).
  After this plan: `gt_prob` always receives `FloatLiteral` — `rawToDouble` is
  deleted; a direct `case FloatLiteral(d) => d` match suffices.
- Any consumer implementing a function dispatcher (e.g. register's `lec`) must
  document which `raw` type their return `Value` carries — this is invisible to
  downstream lambdas and unverifiable by the framework.

### What this plan does NOT close

- **T-002 (named constants):** query constants that are not inline literals (e.g.
  symbolic constant references from a constant table) bypass `literalValidators`
  entirely and would arrive as `ConstRef` with a different raw shape. T-002 is
  deferred — no design decision yet.
- **T-003 (typed literal pipeline normaliser):** generalised normalisation across
  all literal representations is deferred pending T-002 resolution.
- **DomainType args remain native:** `asInstanceOf[AssetId]` (or `asInstanceOf[String]`
  in tests) for domain element args is intentional — domain elements are
  open-ended consumer types the library cannot enumerate. This asymmetry is by
  design and documented in ADR-015 §3.

---

## ADR Compliance Review (Planning Phase)

**Reviewed ADRs:** ADR-001, ADR-006, ADR-013, ADR-014, ADR-015

### ADR-001

⚠️ **Breaking change to `RuntimeDispatcher` service interface.**

`RuntimeDispatcher.evalFunction` currently returns `Either[String, Value]`.
The proposal changes this to `Either[String, LiteralValue]`. This is a
**service interface change** — a CRITICAL STOP POINT per working instructions.

All existing implementations of `RuntimeDispatcher` in this repo are:
1. `MapDispatcher` (library) — `functions` field type changes
2. Anonymous-class dispatchers in `VagueSemanticsTypedSpec` — all function
   lambdas currently return `Left(...)` only (no function symbols), so the
   type change is compile-forced but trivially satisfied
3. Anonymous-class dispatchers in `MapDispatcherSpec` — these call `evalFunction`
   directly in `validateAgainst` tests, not through function registration;
   the integration test lambdas do return `Right(Value(...))` and must be updated

There are no external consumers of `RuntimeDispatcher` in this repo. The
downstream consumer (register) does not yet exist.

**Decision required** on this interface change — see §Open Questions below.

### ADR-006

`TypedFunctionImpl` is a pure-utility `object` with a single generic `def` — no
ADT involved. Compliant.

### ADR-013

No change to `TypeRepr[A]` or `Value.as[A]`. Compliant.

### ADR-014

No change to `DomainType`/`ValueType` distinction or quantifiability checks.
Compliant. The `DomainType`-vs-`ValueType` asymmetry in arg handling is preserved.

### ADR-015

This plan completes §1 of ADR-015. The ADR's implementation table will need
one new row: `RuntimeDispatcher.evalFunction` return type + `TypedSemantics`
FnApp wrapping. ADR-015 status remains Accepted — the table is additive.

---

## Proposed Design

### 1. Change `RuntimeDispatcher.evalFunction` return type

```scala
// Before
trait RuntimeDispatcher:
  def evalFunction(name: SymbolName, args: List[Value]): Either[String, Value]
  ...

// After
trait RuntimeDispatcher:
  def evalFunction(name: SymbolName, args: List[Value]): Either[String, LiteralValue]
  ...
```

### 2. `TypedFunctionImpl` combinator (new, library-provided)

Ergonomic helper so consumers don't construct `LiteralValue` inline:

```scala
object TypedFunctionImpl:
  def of[A](
    impl: List[Value] => Either[String, A],
    wrap: A => LiteralValue
  ): List[Value] => Either[String, LiteralValue] =
    args => impl(args).map(wrap)
```

Placed in `fol/typed/TypedFunctionImpl.scala`.

### 3. `TypedSemantics.evalTerm` for `FnApp` — framework wraps

```scala
// Before
case BoundTerm.FnApp(name, args, resultSort) =>
  for
    argValues <- evalTerms(args, env, model)
    value <- model.dispatcher.evalFunction(name, argValues).left.map(...)
    _ <- sortCheck(value.sort, resultSort, name)
  yield value

// After — dispatcher returns LiteralValue; framework constructs Value
case BoundTerm.FnApp(name, args, resultSort) =>
  for
    argValues   <- evalTerms(args, env, model)
    literalResult <- model.dispatcher.evalFunction(name, argValues).left.map(...)
  yield Value(resultSort, literalResult)
```

The explicit sort check on the returned `Value` is eliminated — the sort is now
always `resultSort` because the framework constructs the `Value`. The catalog
already guarantees `resultSort` is valid at bind time.

### 4. `MapDispatcher.functions` field type update

```scala
// Before
case class MapDispatcher(
  predicates: Map[SymbolName, List[Value] => Either[String, Boolean]],
  functions:  Map[SymbolName, List[Value] => Either[String, Value]] = Map.empty
)

// After
case class MapDispatcher(
  predicates: Map[SymbolName, List[Value] => Either[String, Boolean]],
  functions:  Map[SymbolName, List[Value] => Either[String, LiteralValue]] = Map.empty
)
```

### 5. `MapDispatcherSpec` call site updates

The integration-pipeline tests currently construct function lambdas like:

```scala
symLec -> (_ => Right(Value(tProb, lec)))   // current
symLec -> TypedFunctionImpl.of(_ => ..., FloatLiteral(_))  // after
// or equivalently:
symLec -> (_ => Right(FloatLiteral(0.07)))  // direct, without combinator
```

`rawToDouble` is deleted. `gt_prob` lambda becomes:

```scala
symGtProb -> { args =>
  def toDouble(v: Value): Double = v.raw match
    case FloatLiteral(d) => d
    case IntLiteral(n)   => n.toDouble
  Right(toDouble(args(0)) > toDouble(args(1)))
}
// AFTER — both args are always LiteralValue; rawToDouble only needs to handle LiteralValue cases
// (DomainType arg pattern stays as asInstanceOf — intentional)
```

Actually after this change, both `args(0)` (from `lec` return) and `args(1)`
(from inline literal) are `FloatLiteral`. So `gt_prob` becomes:

```scala
symGtProb -> { args =>
  (args(0).raw, args(1).raw) match
    case (FloatLiteral(a), FloatLiteral(b)) => Right(a > b)
    case other => Left(s"gt_prob: unexpected arg types: $other")
}
```

No helper needed. `rawToDouble` is deleted.

---

## Tasks

1. **`RuntimeModel.scala`** — change `RuntimeDispatcher.evalFunction` return type to `Either[String, LiteralValue]`
2. **`TypedFunctionImpl.scala`** — create new file with `TypedFunctionImpl.of[A]` combinator
3. **`TypedSemantics.scala`** — update `evalTerm` for `FnApp`: remove sort check on return, use `Value(resultSort, literalResult)`
4. **`MapDispatcher.scala`** — update `functions` field type; update `evalFunction` delegation
5. **`MapDispatcherSpec.scala`** — update all function lambdas to return `LiteralValue`; delete `rawToDouble`; simplify `gt_prob` and `lec` lambda bodies
6. **`VagueSemanticsTypedSpec.scala`** — update any function lambdas (currently none return `Right(Value(...))` — confirm at implementation time)
7. **`ADR-015.md`** — add one row to implementation table; add `TypedFunctionImpl` to code smells / good patterns
8. **Run tests** — 906 pass required
9. **Commit**

---

## Decisions

### Q1: `RuntimeDispatcher` interface change — RESOLVED

**Decision: Option A.** Change `RuntimeDispatcher.evalFunction` at the trait
level to return `Either[String, LiteralValue]`. Consumer declares the wrap
(`A => LiteralValue`), library owns the `Value` construction. Aligns with
library philosophy. No external consumers exist; blast radius is zero.

### Q2: Function returning a DomainType sort — RESOLVED (deferred)

**Decision:** DomainType-returning functions are out of scope for this plan.
The `EntityRef` identity problem (see **T-004** in `TODOS.md`) has no clean
resolution until a real use case exists. The guard `TypeCatalogError.FunctionReturnIsDomainType`
is added during `TypeCatalog` construction to make the constraint explicit and
enforce it at startup rather than leaving it as a silent assumption.

The prerequisite catalog validation guard (T-003) is tracked separately.

---

## Approval Checkpoint

- [x] Q1 resolved: Option A — trait-level interface change approved
- [x] Q2 resolved: deferred via T-004; `FunctionReturnIsDomainType` catalog guard added
- [ ] Plan approved — proceed to implementation

