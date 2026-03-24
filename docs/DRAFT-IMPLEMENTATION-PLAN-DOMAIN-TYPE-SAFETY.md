# DRAFT — Implementation Plan: Domain Type Safety (Option D)

> **Status: DRAFT — Not approved for implementation.**
> This document captures a potential future refactoring to make the
> knowledge base and evaluation pipeline generic on domain type `D`,
> eliminating the `Any` erasure end-to-end. The refactoring is not
> currently scheduled.
>
> **Supersedes** the earlier Options A/B/C analysis. See ADR-006 for
> the design decision that accepts the current `Any` erasure.
>
> **All design decisions and ambiguities flagged herein are for the
> project owner to resolve before implementation begins.**

---

## Problem Statement

Register (the sole consumer) has typed domain objects — risk nodes with
`lec: Double`, scenarios with `probability: BigDecimal`, components with
typed fields. These get serialized into flat `Const(String) | Num(Int)`
tuples to fit the `KnowledgeBase` schema, losing all type information.
The FOL engine then deserializes them back through `toDomainValue → Any`
and does runtime type dispatch to recover what was known at compile time.

This is a **round-trip through untyped territory for no fundamental
reason.** The FOL engine's core (`Model[D]`, `Interpretation[D]`,
`Valuation[D]`, `EvaluationContext[D]`, `ModelAugmenter[D]`) is already
fully generic on `D`. The type erasure is introduced by a monomorphic
datastore layer (`KnowledgeBase`, `KnowledgeSource`, `RelationValue`)
and a bridge that converts `RelationValue → Any`.

### Options Considered and Rejected

| Option | Approach | Rejection Reason |
|---|---|---|
| **A: `Model[RelationValue]`** | Use `RelationValue` as domain type | Still encodes `Double` as `Const(String)` — stringly-typed. `Num` wraps `Int` only. |
| **B: Add `Real(Double)` variant** | Extend `RelationValue` enum | SRP violation: KB storage type grows to serve computation concerns. Shotgun surgery on all existing `RelationValue` matches. Conflicts with ADR-005's additive extension model. |
| **C: Separate `DomainValue` ADT** | New evaluation-layer sum type | Replaces untyped indirection (`Any`) with typed indirection, but consumer still serializes into `RelationValue` for KB storage. Half-measure. |

**Option D: Generic `KnowledgeBase[D]`** eliminates the root cause.
The datastore layer becomes parameterized on the consumer's domain type.
No serialization, no deserialization, no runtime type dispatch.

---

## Design Goal

**End-to-end type safety from consumer domain objects through KB
storage, model construction, augmentation, and FOL evaluation — with
the consumer defining its own domain type `D`.**

```
Consumer domain type D
    ↓
KnowledgeBase[D] / KnowledgeSource[D]    ← stores D directly
    ↓
KnowledgeSourceModel.toModel[D]          ← D flows through, no erasure
    ↓
Model[D] / Interpretation[D]             ← already generic (unchanged)
    ↓
ModelAugmenter[D]                        ← already generic (unchanged)
    ↓
EvaluationContext[D]                     ← already generic (unchanged)
    ↓
FOLSemantics.holds[D]                    ← already generic (unchanged)
```

---

## Type Architecture

### Core Change: Parameterize the Datastore Layer

```scala
// Currently:
case class RelationTuple(values: List[RelationValue])
case class KnowledgeBase(schema: Map[String, Relation], facts: Map[String, Set[RelationTuple]])
trait KnowledgeSource

// Proposed:
case class RelationTuple[D](values: List[D])
case class KnowledgeBase[D](schema: Map[String, Relation], facts: Map[String, Set[RelationTuple[D]]])
trait KnowledgeSource[D]
```

### Type Class: `DomainElement`

The FOL engine needs certain capabilities from `D` without knowing its
concrete type. A type class provides these constraints:

```scala
/** Minimal contract for a domain element type.
  *
  * The FOL engine needs to:
  * 1. Display domain values in error messages (Show)
  * 2. Compare for equality (standard Scala equals)
  * 3. Use as Map keys / Set elements (standard Scala hashCode)
  *
  * That's it. No numeric extraction, no string parsing —
  * those are augmenter-level concerns, not engine-level.
  */
trait DomainElement[D]:
  extension (d: D) def show: String
```

### ⚠️ DECISION REQUIRED: Numeric Support Strategy

`NumericAugmenter` needs to extract `Double` from `D` and create `D`
from parsed numeric literals. Two approaches:

**Option D1: Type class constraint on `NumericAugmenter`**

```scala
trait NumericDomain[D]:
  def toDouble(d: D): Double
  def fromDouble(d: Double): D

object NumericAugmenter:
  def augmenter[D: NumericDomain]: ModelAugmenter[D] = ...
```

Consumer provides the instance. Engine has no opinion on how `D`
encodes numbers.

**Option D2: `NumericAugmenter` stays `Any`-based, consumer bridges**

Keep `NumericAugmenter` as `ModelAugmenter[Any]` (an escape hatch).
Consumer responsible for the `D → Any → D` bridge when composing
augmenters. Pragmatic but reintroduces `Any` at the augmenter level.

**Tradeoffs**: D1 is pure and type-safe but requires every consumer to
provide `NumericDomain[D]`. D2 is pragmatic but creates a typed/untyped
seam inside the augmenter system.

### ⚠️ DECISION REQUIRED: Schema Validation

`Relation.validates(tuple)` currently pattern-matches on `RelationValue`
variants against `PositionType`:

```scala
case (RelationValue.Const(_), PositionType.Constant) => true
case (RelationValue.Num(_), PositionType.Numeric) => true
```

With generic `D`, the engine cannot pattern-match on the consumer's
type. Options:

**Option S1: Drop schema validation from the engine**

`Relation` becomes metadata-only (name, arity). Validation is the
consumer's responsibility. Simplest change.

**Option S2: Type class for validation**

```scala
trait Validatable[D]:
  def positionType(d: D): PositionType
```

**Option S3: Keep `PositionType` as optional metadata, no enforcement**

Schema carries `PositionType` hints for documentation/tooling but
`validates` is removed. Middle ground.

### ⚠️ DECISION REQUIRED: `ResolvedQuery` and `RelationValue`

ADR-001 mandates: *"No `[A]` parameter anywhere"* on query types.
Currently `ResolvedQuery` uses `Set[RelationValue]` for elements and
`RelationValue => Boolean` for predicates.

**Option Q1: Make `ResolvedQuery` generic — `ResolvedQuery[D]`**

Amend ADR-001. The shared IL carries the consumer's domain type through
to evaluation. Clean, but changes a foundational decision.

**Option Q2: Keep `ResolvedQuery` using `RelationValue`, convert at bridge**

The bridge converts `D → RelationValue` for the IL. Preserves ADR-001
but requires a codec and reintroduces a conversion boundary. Partially
defeats the purpose of Option D.

**Option Q3: Split the IL**

`ResolvedQuery[D]` for the typed path (register's primary use case).
Non-generic `ResolvedQuery` using `RelationValue` for the string-parsed
path. Two ILs — more complexity, but each path is type-safe.

### ⚠️ DECISION REQUIRED: Parser-Produced Queries

`VagueQueryParser` produces `ParsedQuery` containing `Formula[FOL]`
with string constants. The bridge must convert parsed string tokens
into domain values of type `D`. This requires:

```scala
trait DomainCodec[D]:
  def fromString(s: String): D           // "alice" → consumer's type
  def fromInt(i: Int): D                 // 42 → consumer's type
```

**Question**: Should this be the same type class as `DomainElement`, or
separate? The parser path is the only place that needs `fromString/fromInt`
— the typed KB path (register building `KnowledgeBase[D]` directly)
does not need it at all.

### `RelationValue` Becomes A Default, Not The Only Option

```scala
// fol-engine provides RelationValue as a ready-made domain type
// with DomainElement, NumericDomain, DomainCodec instances
enum RelationValue:
  case Const(name: String)
  case Num(value: Int)

given DomainElement[RelationValue] with
  extension (d: RelationValue) def show: String = d.toString

given NumericDomain[RelationValue] with
  def toDouble(d: RelationValue): Double = d match
    case RelationValue.Num(i) => i.toDouble
    case RelationValue.Const(s) => s.toDouble
  def fromDouble(d: Double): RelationValue =
    RelationValue.Num(d.toInt) // lossy — known limitation

given DomainCodec[RelationValue] with
  def fromString(s: String): RelationValue = RelationValue.Const(s)
  def fromInt(i: Int): RelationValue = RelationValue.Num(i)
```

Consumers who don't need custom domain types use `RelationValue` and
everything works as before. Consumers who need richer types define
their own `D` with instances.

---

## Impact Analysis

### Layer 1: Datastore (High — fundamental change)

| File | Change |
|---|---|
| `Relation.scala` | `RelationTuple[D]`; `PositionType` / `validates` — see Decision S1/S2/S3 |
| `KnowledgeBase.scala` | `KnowledgeBase[D]`; Builder becomes `Builder[D]`; all methods parameterized |
| `KnowledgeSource.scala` | `KnowledgeSource[D]`; all return types: `Set[D]`, `RelationTuple[D]` |
| `InMemoryKnowledgeSource` | `InMemoryKnowledgeSource[D]`; delegates to `KnowledgeBase[D]` |
| `RelationValueUtil.scala` | Largely eliminated — `toDomainValue`/`fromDomainValue` no longer needed |

### Layer 2: Bridge (High — erasure elimination)

| File | Change |
|---|---|
| `KnowledgeBaseModel.scala` | `toModel[D: DomainElement]` returns `Model[D]`; all `Any` → `D` |
| `KnowledgeSourceModel.scala` | `toModel[D: DomainElement]` returns `Model[D]`; all `Any` → `D` |
| `FOLBridge.scala` | `scopeToPredicate[D: DomainElement]`; `ModelAugmenter[D]`; substitution `Map[String, D]` |
| `NumericAugmenter.scala` | See Decision D1/D2 — either generic or stays `Any`-based |

### Layer 3: Evaluation Pipeline (Medium — signature changes)

| File | Change |
|---|---|
| `VagueSemantics.scala` | 3 methods gain `[D: DomainElement]` or take `KnowledgeSource[D]` |
| `ScopeEvaluator.scala` | 5 methods: `Model[D]` instead of `Model[Any]` |
| `EvaluationContext.scala` | Extensions retarget from `EvaluationContext[Any]` to generic; `toDomainValue` eliminated |
| `RangeExtractor.scala` | Takes `KnowledgeSource[D]`; returns `Set[D]` |

### Layer 4: Query Types (depends on Decision Q1/Q2/Q3)

| File | Change |
|---|---|
| `ResolvedQuery.scala` | Either `ResolvedQuery[D]` (Q1) or unchanged (Q2) |
| `Query.scala` (UnresolvedQuery) | Either `UnresolvedQuery[D]` (Q1) or unchanged with codec (Q2) |

### Unchanged (already generic or independent)

| Component | Why |
|---|---|
| `FOLSemantics.scala` | Already `Model[D]`, `Interpretation[D]` — fully generic |
| `ModelAugmenter.scala` | Already `ModelAugmenter[D]` — fully generic |
| `integerModel` | Already `Model[Int]` — type-safe, unaffected |
| FOL core (Formula, Term, parser, printer) | String-level, no domain type dependency |
| `VagueQuantifier`, `SamplingParams`, `HDRConfig` | Independent of domain type |

### Test Files (~8 files)

| File | Impact |
|---|---|
| `KnowledgeBaseSpec.scala` | `KnowledgeBase[RelationValue]` — explicit type param, otherwise unchanged |
| `RelationValueUtilSpec.scala` | Largely deleted or simplified |
| `EvaluationContextSpec.scala` | `Model[RelationValue]` fixtures; `asInstanceOf` casts eliminated |
| `NumericAugmenterSpec.scala` | Depends on Decision D1/D2 |
| `ModelAugmenterSpec.scala` | `Model[RelationValue]` or `Model[String]` fixtures |
| `ModelAugmentationIntegrationSpec.scala` | `ModelAugmenter[RelationValue]` |
| `ScopeEvaluatorSpec.scala` | Uses bridge — changes propagate automatically |
| `VagueSemanticsSpec.scala` | Uses bridge — changes propagate automatically |

---

## Phased Implementation

### Phase 1: Type Classes and `RelationValue` Instances

Define `DomainElement[D]` (and optionally `NumericDomain[D]`,
`DomainCodec[D]` depending on decisions). Provide instances for
`RelationValue`. **No existing code changes** — purely additive.

**Checkpoint**: Compiles, 855 tests pass unchanged.

### Phase 2: Generic Datastore Layer

- `RelationTuple[D]`, `KnowledgeBase[D]`, `KnowledgeSource[D]`
- `InMemoryKnowledgeSource[D]`
- Schema validation: implement chosen option (S1/S2/S3)
- Type alias: `type ClassicKB = KnowledgeBase[RelationValue]` for
  migration convenience

**Checkpoint**: Datastore tests green with `[RelationValue]` type param.

### Phase 3: Generic Bridge Layer

- `KnowledgeBaseModel.toModel[D: DomainElement]` → `Model[D]`
- `KnowledgeSourceModel.toModel[D: DomainElement]` → `Model[D]`
- `RelationValueUtil` — remove or reduce to convenience functions
- `EvaluationContext` extensions — retarget from `Any` to generic `D`

**Checkpoint**: Bridge tests green. `Model[Any]` no longer exists in
production code.

### Phase 4: Generic Evaluation Pipeline

- `FOLBridge.scopeToPredicate[D]` / `scopeToStringPredicate[D]`
- `VagueSemantics.holds[D]` / `evaluate[D]` / `toResolved[D]`
- `ScopeEvaluator` — all 5 methods parameterized on `D`
- `RangeExtractor` — parameterized on `D`
- `NumericAugmenter` — implement chosen option (D1/D2)

**Checkpoint**: Full pipeline compiles with `D`. Integration tests green.

### Phase 5: Query Types (depends on Decision Q1/Q2/Q3)

- If Q1: `ResolvedQuery[D]`, `UnresolvedQuery[D]`, amend ADR-001
- If Q2: Add `DomainCodec[D]` bridge at query resolution boundary
- If Q3: Split IL, provide both typed and string-parsed paths

**Checkpoint**: Full suite green — 855+ tests pass. Parser-path and
typed-DSL-path both functional.

### Phase 6: Clean Up and Migration Support

- Remove `RelationValueUtil.toDomainValue` / `fromDomainValue`
- Remove `toDomainSet` / `toDomainSetTyped` / `toDomainList`
- Update `FOLDemo.scala` pattern matches
- Update ADR-006 status to "Superseded"
- Provide migration guide for register:
  - Define `RiskDomain` sum type with type class instances
  - Replace `KnowledgeBase` construction with `KnowledgeBase[RiskDomain]`
  - Replace `ModelAugmenter[Any]` with `ModelAugmenter[RiskDomain]`
- Version bump (binary-incompatible API change)
- `publishLocal` and verify register compiles

**Checkpoint**: Clean build, no `Any` in domain-typed code paths.

---

## ADR Compatibility

| ADR | Impact | Action Required |
|---|---|---|
| **ADR-001** (Evaluation Path Unification) | Depends on Decision Q1/Q2/Q3. Q1 amends it; Q2/Q3 preserve it. | Owner decision |
| **ADR-002** (Parser-Combinator Style) | Unaffected — parser operates at string level | None |
| **ADR-003** (HDR Deterministic Sampling) | `ProportionEstimator` uses `Set[RelationValue]`, `RelationValue => Boolean`. Becomes `Set[D]`, `D => Boolean`. | Parameterize |
| **ADR-004** (Tagless Initial) | Aligns — FOL layer is already generic. This plan extends genericity to the datastore layer. | None — compatible |
| **ADR-005** (Model Augmentation) | `ModelAugmenter[D]` already generic. `NumericAugmenter` depends on Decision D1/D2. | Depends |
| **ADR-006** (Domain Type Erasure) | Superseded — the accepted trade-off is eliminated. | Update status |

---

## Risks

| Risk | Severity | Mitigation |
|---|---|---|
| **Binary-incompatible API change** | High | Version bump. Register updated simultaneously. |
| **Type parameter proliferation** | Medium | `[D: DomainElement]` appears on many methods. Mitigate with type aliases and keeping type class constraints minimal. |
| **Register migration effort** | Medium | Define `RiskDomain` sum type, provide type class instances, update KB construction. ~1-2 hours register-side. |
| **Inference failures** | Medium | Scala 3 type inference may struggle with deeply nested generic pipelines. Test early with real register code. |
| **Performance** | Low | No boxing change — domain values were already boxed (in `RelationValue` or `Any`). Generic dispatch via type class is monomorphized by the JVM. |
| **Harrison's `integerModel`** | Low | Remains `Model[Int]` unchanged. Not used with KB-backed models. |

---

## Effort Estimate

| Phase | Estimated Time |
|---|---|
| Phase 1: Type classes | 30 min |
| Phase 2: Generic datastore | 1-2 hours |
| Phase 3: Generic bridge | 1-2 hours |
| Phase 4: Generic pipeline | 1 hour |
| Phase 5: Query types | 30 min – 1.5 hours (depends on Q decision) |
| Phase 6: Cleanup + migration | 1 hour |
| Compile-fix + test cycles | 1-2 hours |
| **Total** | **~5-9 hours** |

Plus register-side migration: ~1-2 hours.

---

## Decision Summary

Decisions required before implementation:

| ID | Decision | Options | Impact |
|---|---|---|---|
| **D1/D2** | Numeric augmenter strategy | D1: type class (pure) / D2: `Any` escape hatch | Affects `NumericAugmenter`, register augmenter code |
| **S1/S2/S3** | Schema validation | S1: drop / S2: type class / S3: metadata-only | Affects `Relation`, `KnowledgeBase` |
| **Q1/Q2/Q3** | `ResolvedQuery` genericity | Q1: generic / Q2: keep `RelationValue` / Q3: split IL | Affects query types, ADR-001 |
| **Parser** | `DomainCodec` scope | Same as `DomainElement` or separate? | Affects type class design |

---

## References

- ADR-001 through ADR-006 — existing design decisions
- ADR-005 §Decision 2 — `ModelAugmenter[D]` already generic
- ADR-006 — documents the `Any` erasure (to be superseded)
- Harrison (2009) — OCaml uses `string` uniformly (this plan moves away from that heritage)
