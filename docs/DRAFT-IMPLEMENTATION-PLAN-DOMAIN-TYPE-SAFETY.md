# DRAFT — Implementation Plan: Domain Type Safety

> **Status: DRAFT — Not approved for implementation.**
> This document captures a potential future refactoring to replace
> `Model[Any]` with a properly typed domain. It exists to record scope,
> cost, and trade-offs while the analysis is fresh. The refactoring is
> not currently scheduled.
>
> See ADR-006 for the design decision that accepts the current `Any`
> erasure as a pragmatic shortcut.

---

## Problem Statement

`RelationValue` is already a proper Scala 3 ADT:

```scala
enum RelationValue:
  case Const(name: String)
  case Num(value: Int)
```

Yet `RelationValueUtil.toDomainValue` unwraps it into `Any`, discarding
the sum type. Every `Model[Any]` in the system exists because this
single function returns `Any`. Downstream code (`NumericAugmenter.toDouble`,
`fromDomainValue`) relies on runtime type dispatch with no compiler
assistance on missing cases.

**This is a pragmatic shortcut, not a fundamental FOL constraint.** FOL
model theory requires a single domain type `D`, but `D` could be
`RelationValue` (or a richer sum type) rather than `Any`.

---

## Decision: Domain Type Strategy

Two viable approaches exist. This section documents both; the choice
should be made at implementation time based on whether augmenters need
to produce values outside `RelationValue`'s current variants.

### Option A: `RelationValue` as Domain Type Directly

Use `RelationValue` itself as `D`. Simplest change — no new types.

**Limitation**: `NumericAugmenter` produces `Double` values (numeric
literal resolution: `"3.14" → 3.14`). These would need to be wrapped
as `RelationValue.Num` (lossy — `Num` wraps `Int`) or a new variant
added to `RelationValue`.

### Option B: Extend `RelationValue` with a `Real` Variant (Recommended)

Add `case Real(value: Double)` to `RelationValue`. This keeps one ADT,
avoids a new type hierarchy, and gives `NumericAugmenter` a typed slot
for its outputs:

```scala
enum RelationValue:
  case Const(name: String)
  case Num(value: Int)
  case Real(value: Double)   // NEW — for augmenter-produced decimals
```

`toDomainValue` and `fromDomainValue` become identity (or are removed).
`toDouble` becomes exhaustive:

```scala
def toDouble(v: RelationValue): Double = v match
  case RelationValue.Num(i)    => i.toDouble
  case RelationValue.Real(d)   => d
  case RelationValue.Const(s)  => s.toDouble  // throws on non-numeric
// Compiler verifies all three cases covered
```

---

## Impact Analysis

### Source Files (10 files, ~90+ `Any` occurrences)

| File | Change Type | Effort |
|---|---|---|
| `fol/datastore/RelationValueUtil.scala` | Ground zero — `toDomainValue`/`fromDomainValue` become identity or removed; `toDomainSet`/`toDomainList` return `Set[RelationValue]`; `toDomainSetTyped` eliminated | High |
| `fol/bridge/KnowledgeSourceModel.scala` | `toModel` returns `Model[RelationValue]`; all `Set[Any]`/`List[Any] => Any`/`List[Any] => Boolean` retyped; ~12 type annotations | High |
| `fol/bridge/KnowledgeBaseModel.scala` | Same pattern as `KnowledgeSourceModel`; ~8 type annotations | High |
| `fol/bridge/FOLBridge.scala` | `modelAugmenter: ModelAugmenter[RelationValue]`; substitution map types; ~4 annotations | Medium |
| `fol/bridge/NumericAugmenter.scala` | `ModelAugmenter[RelationValue]`; `toDouble(v: RelationValue)`; `numericLiteral` returns `RelationValue`; predicates receive `List[RelationValue]` | Medium |
| `fol/semantics/VagueSemantics.scala` | 3 method signatures: `ModelAugmenter[RelationValue]` | Low |
| `fol/semantics/ScopeEvaluator.scala` | 5 method signatures: `Model[RelationValue]` | Low |
| `semantics/EvaluationContext.scala` | Extension block retargets from `EvaluationContext[Any]` to `EvaluationContext[RelationValue]`; `toDomainValue` calls become identity | Low |
| `semantics/ModelAugmenter.scala` | No change — fully generic `ModelAugmenter[D]` | None |
| `examples/FOLDemo.scala` | All `case List(a: Int, b: Int) =>` patterns become `case List(Num(a), Num(b)) =>` etc. | Medium |

### Test Files (6 files)

| File | Impact |
|---|---|
| `RelationValueUtilSpec.scala` | ~30 tests — assertions against `Any` values change; `toDomainSetTyped` tests removed |
| `EvaluationContextSpec.scala` | Manual `Model[Any]` fixtures retyped; 2 `asInstanceOf` casts eliminated |
| `NumericAugmenterSpec.scala` | `Model[Any]` fixtures retyped; 8 `result.asInstanceOf[Double]` → pattern match on `Real` |
| `ModelAugmenterSpec.scala` | `Domain(Set[Any](...))` → `Domain(Set[RelationValue](...))`, lambda types |
| `ModelAugmentationIntegrationSpec.scala` | `ModelAugmenter[Any]` → `ModelAugmenter[RelationValue]`, function return types |
| `ScopeEvaluatorSpec.scala` | Low — uses bridge methods, changes propagate automatically |

### Unchanged

| Component | Why |
|---|---|
| `FOLSemantics.scala` (`Interpretation[D]`, `Model[D]`) | Fully generic — parameterised on `D`, no `Any` references |
| `ModelAugmenter.scala` | Fully generic |
| `integerModel` (in `FOLSemantics`) | Already `Model[Int]` — type-safe, unaffected |
| All FOL core (`Formula`, `Term`, parser, printer) | String-level, no domain type dependency |
| `VagueQuantifier`, `ResolvedQuery`, `SamplingParams` | Independent of domain type |

---

## Phased Implementation

### Phase 0: Extend `RelationValue` ADT

Add `case Real(value: Double)` to `RelationValue`. Update `toString`,
pattern matches in existing code that switch on `RelationValue` variants.
Run all tests — this is additive and should break nothing.

**Checkpoint**: 855 tests still pass.

### Phase 1: Eliminate Erasure in `RelationValueUtil`

- `toDomainValue` → returns `RelationValue` (identity, or inlined away)
- `fromDomainValue` → identity (or removed)
- `toDomainSet` → `Set[RelationValue]`
- `toDomainSetTyped` → removed (unsafe cast eliminated)
- `toDomainList` → `List[RelationValue]`

**Checkpoint**: `RelationValueUtilSpec` green with updated assertions.

### Phase 2: Retype Bridge Layer

- `KnowledgeSourceModel.toModel` → `Model[RelationValue]`
- `KnowledgeBaseModel.toModel` → `Model[RelationValue]`
- All internal `Set[Any]`, `List[Any] => Any`, `List[Any] => Boolean`
  become `Set[RelationValue]`, `List[RelationValue] => RelationValue`,
  `List[RelationValue] => Boolean`

**Checkpoint**: Bridge tests green; downstream compile errors expected.

### Phase 3: Retype Augmenter System

- `NumericAugmenter.augmenter` → `ModelAugmenter[RelationValue]`
- `toDouble(v: Any)` → `toDouble(v: RelationValue)` — exhaustive match
- `numericLiteral` returns `List[RelationValue] => RelationValue`
  (wrapping result in `Real(...)`)
- Comparison lambda args: `List[RelationValue]` instead of `List[Any]`

**Checkpoint**: `NumericAugmenterSpec` green with pattern match assertions.

### Phase 4: Retype Evaluation Pipeline

- `FOLBridge.scopeToPredicate` / `scopeToStringPredicate` params
- `VagueSemantics.holds` / `evaluate` / `toResolved` params
- `ScopeEvaluator` method signatures
- `EvaluationContext[Any]` extension → `EvaluationContext[RelationValue]`

**Checkpoint**: Full suite green — 855+ tests pass.

### Phase 5: Clean Up

- Remove dead `toDomainValue` / `fromDomainValue` if inlined
- Update `FOLDemo.scala` pattern matches
- Update ADR-006 status from "Accepted" to "Superseded"
- Verify `publishLocal` succeeds (API change — bump version?)

---

## Risks and Open Questions

| Risk | Mitigation |
|---|---|
| **Binary-incompatible API change** | `Model[Any]` → `Model[RelationValue]` in public signatures. Requires version bump. Register (sole consumer) must update simultaneously. |
| **`Real` variant pollutes KB layer** | `RelationValue.Real` exists only for augmenter outputs; KB data never produces it. Acceptable if documented, or use a separate `DomainValue` supertype (Option A variant). |
| **Performance** | Wrapping every `Int`/`String` in a case class adds allocation. Likely negligible for current domain sizes (< 10k elements). Benchmark before/after if concerned. |
| **Harrison's `integerModel`** | Currently `Model[Int]` — remains unchanged. Mixed models (KB + integer) would need a bridge. Not currently a use case. |
| **Consumer augmenters in register** | Register's `riskTreeAugmenter` builds `ModelAugmenter[Any]` with lambdas receiving raw `Double`/`String`. Must be updated to receive/return `RelationValue`. |

## Decision Criteria for Activation

This refactoring becomes worth the cost when **any** of:

1. A third `RelationValue` variant is added (e.g., `Bool`, `Timestamp`)
   — the `Any` dispatch becomes a real maintenance hazard
2. A runtime `ClassCastException` or `MatchError` is traced to the
   `Any` erasure — the pragmatic shortcut has a concrete cost
3. A consumer needs exhaustiveness checking on domain values — e.g.,
   serialisation, cross-compilation, or code generation over the domain

Until then, ADR-006 documents the accepted trade-off.

---

## References

- ADR-006: Domain Type Erasure in KB-Backed Models
- ADR-005: Model Augmentation via Functional Composition
- `RelationValueUtil.scala` — the single erasure gateway
- Harrison (2009) — OCaml uses `string` uniformly, avoiding the problem
