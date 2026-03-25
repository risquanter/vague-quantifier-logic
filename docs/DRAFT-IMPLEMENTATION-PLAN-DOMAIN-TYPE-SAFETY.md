# Implementation Plan: Domain Type Safety (Option D)

> **Status: Plan — awaiting approval before implementation.**
>
> **Supersedes** the earlier Options A/B/C analysis and the previous
> draft of this document. See ADR-006 for the design decision that
> accepts the current `Any` erasure (to be superseded on completion).
>
> **All design decisions have been resolved.** This document is the
> implementation-ready plan. Each phase requires explicit owner approval
> before proceeding to the next.
>
> **Governance:** WORKING-INSTRUCTIONS.md from the register project
> applies to this work.

---

## 1. Problem Statement

### Current State

The FOL engine's core is fully generic:

```
Model[D], Interpretation[D], Valuation[D], EvaluationContext[D], ModelAugmenter[D]
```

But the datastore and bridge layers are monomorphic:

```
KnowledgeBase → RelationValue → toDomainValue → Any → Model[Any]
```

Register's typed domain objects (risk nodes with `lec: Double`,
exceedance probabilities) get serialized into flat `Const(String) |
Num(Int)` tuples, losing type information. The engine deserializes
them through `toDomainValue → Any` and does runtime type dispatch.

### Why This Matters

Harrison's OCaml implementation uses `string` uniformly — this was
never a design choice for Scala, just inherited.  The round-trip
through `Any` means:

- `Double` values silently truncate to `Int` (via `Num`)
- Pattern matches on `Any` are non-exhaustive — `MatchError` at runtime
- No compiler assistance when function return types change
- `asInstanceOf` casts scattered through evaluation code

### Options Considered and Rejected

| Option | Approach | Rejection Reason |
|---|---|---|
| **A** | `Model[RelationValue]` | Still stringly-typed; `Num` wraps `Int` only |
| **B** | Add `Real(Double)` variant | SRP violation; shotgun surgery; conflicts ADR-005 |
| **C** | Separate `DomainValue` ADT | Half-measure — consumer still serializes into `RelationValue` |

### Chosen: Option D — Generic `KnowledgeBase[D]`

The datastore layer becomes parameterized on the consumer's domain type.
No serialization, no deserialization, no runtime type dispatch.

---

## 2. Design Goal

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

## 3. The Domain Type Problem

### D Must Include Computed Values

In Harrison's model theory, `evalTerm` returns a value of type `D`:

```scala
def evalTerm[D](term: Term, interp: Interpretation[D], v: Valuation[D]): D
```

If a function like `p95(x)` returns a `Double`, then `Double` must
be representable as `D`. This means `D` cannot be just `String` —
it must be a sum type that encompasses both KB element values AND
computed values.

### Register's Domain Type

For register (sole consumer), the natural domain type is:

```scala
enum RiskDomain:
  case NodeId(id: String)     // KB element: a risk tree node
  case Score(value: Double)   // Computed value: p95, lec, literal
```

- KB stores only `RiskDomain.NodeId` values (structural facts)
- Model functions (`p95`, `lec`) return `RiskDomain.Score` values
- Comparison predicates (`>`, `<`) compare `Score` vs `Score`
- Cross-variant comparison (`NodeId` vs `Score`) is structurally
  unreachable — range predicates constrain `x` to `NodeId`, and
  comparison operands are always function results or literals

### What Changes vs `Any`

| Aspect | `Model[Any]` (today) | `Model[RiskDomain]` (Option D) |
|---|---|---|
| Pattern match | Non-exhaustive `case d: Double =>` | Exhaustive `case Score(d) =>` |
| Missing case | Runtime `MatchError` | Compile error |
| Value creation | `42.0: Any` (invisible) | `Score(42.0)` (explicit) |
| Cross-variant | `toDouble("alice")` throws | Compiler forces decision |
| Encoding | `Double → Num(_.toInt)` truncates | `Score(42.7)` preserves exact value |

---

## 4. Resolved Design Decisions

All decisions are final. Rationale documented here; implementation
follows in the phased plan below.

### D1a: Augmenter Strategy — Stdlib Type Classes

**Decision:** Split `NumericAugmenter` into three concerns:

1. **`ComparisonAugmenter[D : Ordering]`** — predicates `>`, `<`,
   `>=`, `<=` using stdlib `Ordering[D]`
2. **`ArithmeticAugmenter[D : Fractional]`** — functions `+`, `-`,
   `*`, `/` using stdlib `Fractional[D]`.  Opt-in; register does
   not need it (no arithmetic in ADR-028 queries).
3. **Literal resolution** — via `DomainCodec[D].fromNumericLiteral`
   installed as a function fallback

**Rationale:** Comparison operators are not inherently numeric.
`Ordering` is the minimal constraint. `Fractional` is only needed
when the consumer writes arithmetic FOL formulas. Separating these
avoids forcing consumers to define arithmetic on non-numeric variants.

**Cross-variant concern (resolved):** Register's `Ordering[RiskDomain]`
must define `NodeId` vs `Score` comparison. This case is structurally
unreachable (see §3) but the total-ordering contract requires it.
The consumer handles this with a deliberate throw or arbitrary
ordering — a 1-line decision at definition time, not a hidden
`MatchError` in production.

### S1-opt2: Schema Validation — Drop from Generic, Keep for RelationValue

**Decision:** Drop `PositionType` and `validates` from generic
`Relation`.  Keep arity-only validation.  Move `PositionType` to a
`RelationValue`-specific extension (companion object or extension
method).

**Rationale:** `PositionType` mirrors `RelationValue`'s two variants
(`Constant`/`Numeric`) and cannot generalize to arbitrary `D`.
Arity validation is universally useful; type validation is domain-
specific.

**Code archaeology:** `PositionType.allNumeric` and
`Relation.binaryMixed` are never called in production or tests.
`validates` is called only from `KnowledgeBase.addFact`.

### Q1: ResolvedQuery — Generic `ResolvedQuery[D]`

**Decision:** Make `ResolvedQuery[D]` and `UnresolvedQuery[D]` generic.
ADR-001 §Decision 4 already amended to "One Type Flow — No Gratuitous
Generics" (commit `43d61c9`), permitting type parameters that carry
real domain information.

**Rationale:** When `KnowledgeSource[D]` varies, the type parameter
on `ResolvedQuery[D]` carries real information — it's the consumer's
domain type flowing through the shared IL to evaluation.
`ProportionEstimator.estimateWithSampling[A]` is already generic.

### Parser Codec: Separate `DomainCodec[D]`

**Decision:** `DomainCodec[D]` is a separate trait from `DomainElement[D]`.
Only the string-parsed path (VagueQueryParser → FOLBridge) needs it.
The typed-KB path (register building `KnowledgeBase[D]` directly)
does not.

```scala
trait DomainCodec[D]:
  def fromString(s: String): D
  def fromNumericLiteral(s: String): Option[D]
```

---

## 5. Type Architecture

### Type Class: `DomainElement[D]`

```scala
/** Minimal contract for a domain element type.
  * The engine needs: display (errors), equality (sets/maps), hashCode.
  */
trait DomainElement[D]:
  extension (d: D) def show: String
```

### Type Class: `DomainCodec[D]`

```scala
/** Conversion from parser tokens to domain values.
  * Only needed for the string-parsed query path.
  */
trait DomainCodec[D]:
  /** Convert a parsed string constant to domain value. */
  def fromString(s: String): D
  /** Convert a numeric literal string to domain value, if applicable. */
  def fromNumericLiteral(s: String): Option[D]
```

### `RelationValue` — Ready-Made Default

```scala
enum RelationValue:
  case Const(name: String)
  case Num(value: Int)

given DomainElement[RelationValue] with
  extension (d: RelationValue) def show: String = d.toString

given Ordering[RelationValue] with
  def compare(a: RelationValue, b: RelationValue): Int = (a, b) match
    case (RelationValue.Num(x), RelationValue.Num(y)) => x.compare(y)
    case (RelationValue.Const(x), RelationValue.Const(y)) => x.compare(y)
    case (RelationValue.Num(_), RelationValue.Const(_)) => -1
    case (RelationValue.Const(_), RelationValue.Num(_)) => 1

given DomainCodec[RelationValue] with
  def fromString(s: String): RelationValue = RelationValue.Const(s)
  def fromNumericLiteral(s: String): Option[RelationValue] =
    s.toIntOption.map(RelationValue.Num(_))
```

Consumers who don't need custom domain types use `RelationValue` and
everything works as before.

### Generic Datastore Types

```scala
case class RelationTuple[D](values: List[D])
case class KnowledgeBase[D](
  schema: Map[String, Relation],       // Relation is NOT parameterized
  facts: Map[String, Set[RelationTuple[D]]]
)
trait KnowledgeSource[D]
```

`Relation` stays non-generic — it holds metadata (name, arity) only.
`PositionType` and `validates` move to a `RelationValue`-specific
extension.

---

## 6. Cross-Cutting Dependency Map

The `Any` type currently flows through these layers. Each arrow is
a conversion boundary that Option D eliminates:

```
RelationValueUtil.toDomainValue: RelationValue → Any     ← ELIMINATED
         ↓
KnowledgeBaseModel.toModel → Model[Any]                  ← becomes Model[D]
         ↓
ModelAugmenter[Any] (NumericAugmenter)                   ← becomes [D]
         ↓
EvaluationContext[Any].holdsWithRelationValue             ← ELIMINATED
         ↓
ScopeEvaluator (all methods take Model[Any])             ← becomes Model[D]
         ↓
FOLBridge.scopeToPredicate (constructs Model[Any])       ← becomes Model[D]
         ↓
VagueSemantics (takes ModelAugmenter[Any])               ← becomes [D]
         ↓
ResolvedQuery (Set[RelationValue], RV => Boolean)        ← becomes Set[D], D => Boolean
```

---

## 7. Phased Implementation Plan

Following WORKING-INSTRUCTIONS.md governance: each phase requires
explicit approval before proceeding to the next.

---

### Phase 1: Type Class Definitions + `RelationValue` Instances

#### Objective

Define the type classes and provide instances for `RelationValue`.
**Purely additive — no existing code changes.**

#### ADR References

- ADR-004 (Tagless Initial): new file in appropriate layer
- ADR-005 (Model Augmentation): type classes enable typed augmenters

#### ADR Compliance Review (Planning)

| ADR | Status |
|---|---|
| ADR-001 | ✅ No query type changes |
| ADR-002 | ✅ Parser unaffected |
| ADR-003 | ✅ Sampling unaffected |
| ADR-004 | ✅ ADT in `fol/datastore/`, objects for operations |
| ADR-005 | ✅ Additive — no augmenter code changes |
| ADR-006 | ✅ No erasure changes yet |

#### Tasks

1. Create `src/main/scala/fol/datastore/DomainElement.scala`:
   - `trait DomainElement[D]` with `show` extension method
   - `given DomainElement[RelationValue]`
   - `given DomainElement[String]` (for foundation-layer tests)
   - `given DomainElement[Int]` (for `integerModel`)

2. Create `src/main/scala/fol/datastore/DomainCodec.scala`:
   - `trait DomainCodec[D]` with `fromString`, `fromNumericLiteral`
   - `given DomainCodec[RelationValue]`

3. Create `src/test/scala/fol/datastore/DomainElementSpec.scala`:
   - Test `show` for `RelationValue`, `String`, `Int`
   - Test `DomainCodec[RelationValue]` conversions

4. Verify: `sbt test` — all 855 tests pass unchanged.

#### Files Created

| File | Purpose |
|---|---|
| `fol/datastore/DomainElement.scala` | Type class + stdlib instances |
| `fol/datastore/DomainCodec.scala` | Parser-path codec |
| `fol/datastore/DomainElementSpec.scala` (test) | Type class tests |

#### Files Changed

None.

#### Checkpoint

- [ ] Compiles
- [ ] 855 tests pass unchanged
- [ ] New tests pass
- [ ] Commit (signed)

---

### Phase 2: Generic Datastore Layer

#### Objective

Parameterize `RelationTuple`, `KnowledgeBase`, `KnowledgeSource`, and
`InMemoryKnowledgeSource` on domain type `D`.  Refactor `Relation` to
be arity-only, with `PositionType`/`validates` moved to a
`RelationValue`-specific extension.

#### ADR References

- ADR-004: Datastore layer structure
- ADR-006: Begins superseding the type erasure trade-off

#### ADR Compliance Review (Planning)

| ADR | Status |
|---|---|
| ADR-001 | ✅ Query types not yet changed |
| ADR-002 | ✅ Parser unaffected |
| ADR-003 | ✅ Sampling types unchanged |
| ADR-004 | ✅ ADTs for data, objects for operations |
| ADR-005 | ✅ Augmenter layer untouched |
| ADR-006 | ⚠️ Begins superseding — `toDomainValue` still exists |

#### Tasks

##### 2a: Refactor `Relation` (schema-only)

1. Extract `PositionType` and `validates` to a separate extension:

   ```scala
   // Relation becomes arity-only:
   case class Relation(name: String, arity: Int)

   object Relation:
     def unary(name: String): Relation = Relation(name, 1)
     def binary(name: String): Relation = Relation(name, 2)
   ```

2. Create `RelationValueValidation.scala` with:

   ```scala
   object RelationValueValidation:
     // PositionType enum (moved here)
     // validates extension method on Relation for RelationValue tuples
     // Factory methods: binaryMixed, etc.
   ```

3. Update all call sites of `validates`, `positionTypes`,
   `PositionType` to use the new location.

##### 2b: Generic `RelationTuple[D]`

1. Parameterize `RelationTuple`:

   ```scala
   case class RelationTuple[D](values: List[D]):
     def arity: Int = values.length
     def apply(pos: Int): D = values(pos)
     def matches(pattern: List[Option[D]]): Boolean = ...
   ```

2. Move `fromConstants`, `fromNums`, `of` to a
   `RelationValue`-specific extension in companion or separate file.

##### 2c: Generic `KnowledgeBase[D]`

1. Parameterize `KnowledgeBase`:

   ```scala
   case class KnowledgeBase[D](
     schema: Map[String, Relation],
     facts: Map[String, Set[RelationTuple[D]]]
   )
   ```

2. `addFact` — validation becomes arity-only:

   ```scala
   def addFact(name: String, tuple: RelationTuple[D]): KnowledgeBase[D] =
     schema.get(name) match
       case None => throw ...
       case Some(rel) =>
         if tuple.arity != rel.arity then throw ...
         else copy(facts = ...)
   ```

3. `query` — pattern matching uses generic `D`:

   ```scala
   def query(name: String, pattern: List[Option[D]]): Set[RelationTuple[D]]
   ```

4. `getDomain` — returns `Set[D]`:

   ```scala
   def getDomain(name: String, position: Int): Set[D]
   ```

5. `activeDomain` — returns `Set[D]`:

   ```scala
   def activeDomain: Set[D]
   ```

6. Builder becomes `Builder[D]`:
   - `withFact(name, values: D*)` using varargs
   - Convenience methods (`withFact(name, strings: String*)`) move to
     `RelationValue`-specific extension

##### 2d: Generic `KnowledgeSource[D]`

1. Parameterize trait:

   ```scala
   trait KnowledgeSource[D]:
     def hasRelation(name: String): Boolean
     def getRelation(name: String): Option[Relation]
     def contains(name: String, tuple: RelationTuple[D]): Boolean
     def getDomain(name: String, position: Int): Set[D]
     def sampleDomain(name: String, position: Int, n: Int, seed: Option[Long]): Set[D]
     def query(name: String, pattern: List[Option[D]]): Set[RelationTuple[D]]
     def count(name: String): Int
     def activeDomain: Set[D]
     def sampleActiveDomain(n: Int, seed: Option[Long]): Set[D]
     def relationNames: Set[String]
   ```

2. `InMemoryKnowledgeSource[D]` delegates to `KnowledgeBase[D]`.

3. `KnowledgeSource.fromKnowledgeBase[D]` factory.

##### 2e: Update Tests

1. `KnowledgeBaseSpec.scala`:
   - All existing tests should compile with `KnowledgeBase[RelationValue]`
     inferred from `RelationTuple(List(RelationValue.Const("x")))`.
   - Move `RiskDomain` test data fixture to use new API.
   - PositionType validation tests → use `RelationValueValidation`.

2. `RelationValueUtilSpec.scala` — no changes yet (still used by bridge).

##### 2f: Type Alias for Migration

In `KnowledgeBase` companion:

```scala
object KnowledgeBase:
  /** Convenience alias for backward compatibility. */
  type Classic = KnowledgeBase[RelationValue]

  def empty[D]: KnowledgeBase[D] = KnowledgeBase(Map.empty, Map.empty)
```

#### Files Changed

| File | Change |
|---|---|
| `fol/datastore/Relation.scala` | Remove `PositionType`, `positionTypes` field, `validates`; keep `name`, `arity` |
| `fol/datastore/KnowledgeBase.scala` | Parameterize on `[D]`; arity-only validation |
| `fol/datastore/KnowledgeSource.scala` | Parameterize on `[D]`; `InMemoryKnowledgeSource[D]` |

#### Files Created

| File | Purpose |
|---|---|
| `fol/datastore/RelationValueValidation.scala` | `PositionType`, `validates`, RV-specific factories |

#### Files Changed (Tests)

| File | Change |
|---|---|
| `KnowledgeBaseSpec.scala` | `[RelationValue]` type params where needed; PositionType tests updated |

#### Checkpoint

- [ ] Compiles
- [ ] All datastore tests pass
- [ ] No `PositionType` references in generic datastore code
- [ ] Commit (signed)

---

### Phase 3: Generic Bridge Layer

#### Objective

Eliminate the erasure point. `KnowledgeBaseModel` and
`KnowledgeSourceModel` produce `Model[D]` instead of `Model[Any]`.
`RelationValueUtil` becomes unnecessary for the main path.

#### ADR References

- ADR-005: Augmenters compose on `Model[D]`
- ADR-006: Begins superseding — erasure eliminated at bridge

#### ADR Compliance Review (Planning)

| ADR | Status |
|---|---|
| ADR-001 | ✅ Query layer not yet changed |
| ADR-002 | ✅ Parser unaffected |
| ADR-003 | ✅ Sampling unaffected |
| ADR-004 | ✅ Bridge layer structure preserved |
| ADR-005 | ✅ `ModelAugmenter[D]` composition unchanged |
| ADR-006 | ⚠️ Actively superseding |

#### Tasks

##### 3a: `KnowledgeBaseModel` to `Model[D]`

```scala
object KnowledgeBaseModel:
  def toModel[D: DomainElement](kb: KnowledgeBase[D]): Model[D] =
    val domainElements: Set[D] = kb.activeDomain
    val domain = Domain(domainElements)

    // 0-ary functions: constant name → domain element
    val functions: Map[String, List[D] => D] = domainElements.map { d =>
      d.show -> ((_: List[D]) => d)
    }.toMap

    // Predicates: relation lookups
    val predicates = kb.schema.map { case (name, rel) =>
      name -> createPredicateFunction(kb, name, rel.arity)
    }.toMap

    Model(Interpretation(domain, functions, predicates))

  private def createPredicateFunction[D](
    kb: KnowledgeBase[D], name: String, arity: Int
  ): List[D] => Boolean =
    (args: List[D]) =>
      if args.length != arity then false
      else kb.contains(name, RelationTuple(args))
```

Key change: `fromDomainValue` calls eliminated.  Predicate lookup uses
`RelationTuple[D]` directly — structural equality on `D`.

##### 3b: `KnowledgeSourceModel` to `Model[D]`

Same pattern as 3a. All three `toModel` variants become generic.

##### 3c: `EvaluationContext` Extensions

The `holdsWithRelationValue` extension on `EvaluationContext[Any]`
becomes unnecessary for the generic path.

Strategy:
- Remove the `Any`-specific extension methods
- Add generic extension methods on `EvaluationContext[D]`:

  ```scala
  extension [D](ctx: EvaluationContext[D])
    /** Evaluate formula with domain element binding. */
    def holdsWithBinding(
      formula: Formula[FOL], variable: String, value: D
    ): Boolean =
      ctx.withBinding(variable, value).holds(formula)
  ```

- For backward compatibility during migration, keep
  `holdsWithRelationValue` as a type alias calling the generic method.

##### 3d: `NumericAugmenter` → Split into `ComparisonAugmenter` + `ArithmeticAugmenter`

1. Create `ComparisonAugmenter.scala`:

   ```scala
   object ComparisonAugmenter:
     def augmenter[D: Ordering]: ModelAugmenter[D] = ModelAugmenter { model =>
       val ord = summon[Ordering[D]]
       val preds = Map[String, List[D] => Boolean](
         ">"  -> { case List(a, b) => ord.gt(a, b) },
         "<"  -> { case List(a, b) => ord.lt(a, b) },
         ">=" -> { case List(a, b) => ord.gteq(a, b) },
         "<=" -> { case List(a, b) => ord.lteq(a, b) },
         "="  -> { case List(a, b) => ord.equiv(a, b) },
       )
       Model(model.interpretation.withPredicates(preds))
     }
   ```

2. Create `ArithmeticAugmenter.scala`:

   ```scala
   object ArithmeticAugmenter:
     def augmenter[D: Fractional]: ModelAugmenter[D] = ModelAugmenter { model =>
       val frac = summon[Fractional[D]]
       import frac.*
       val fns = Map[String, List[D] => D](
         "+" -> { case List(a, b) => frac.plus(a, b) },
         "-" -> {
           case List(a)    => frac.negate(a)
           case List(a, b) => frac.minus(a, b)
         },
         "*" -> { case List(a, b) => frac.times(a, b) },
         "/" -> { case List(a, b) => frac.div(a, b) },
       )
       Model(model.interpretation.withFunctions(fns))
     }
   ```

3. Create `LiteralResolver.scala`:

   ```scala
   object LiteralResolver:
     def augmenter[D: DomainCodec]: ModelAugmenter[D] = ModelAugmenter { model =>
       val codec = summon[DomainCodec[D]]
       val fallback: String => Option[List[D] => D] = name =>
         codec.fromNumericLiteral(name).map(d => (_: List[D]) => d)
       Model(model.interpretation.withFunctionFallback(fallback))
     }
   ```

4. Keep `NumericAugmenter.scala` as a **backward-compatible
   composition** of the three:

   ```scala
   object NumericAugmenter:
     /** Legacy augmenter for RelationValue-based models.
       * Composes comparison + arithmetic + literal resolution.
       */
     val augmenter: ModelAugmenter[RelationValue] =
       ComparisonAugmenter.augmenter[RelationValue]
         andThen ArithmeticAugmenter.augmenter[RelationValue]
         andThen LiteralResolver.augmenter[RelationValue]
   ```

   This requires `given Ordering[RelationValue]` and
   `given Fractional[RelationValue]` — define the `Fractional`
   instance in the `RelationValue` companion (wrapping the lossy
   `Int`-based arithmetic that exists today).

##### 3e: Update Tests

1. `NumericAugmenterSpec.scala` — split tests to cover:
   - `ComparisonAugmenter` with `Ordering[String]` or custom type
   - `ArithmeticAugmenter` with `Fractional[Double]`
   - `LiteralResolver` with `DomainCodec[RelationValue]`
   - `NumericAugmenter.augmenter` backward-compat (existing tests)

2. `EvaluationContextSpec.scala`:
   - Keep `holdsWithRelationValue` tests (now delegating)
   - Add `holdsWithBinding` tests

3. `ModelAugmentationIntegrationSpec.scala`:
   - Update `ModelAugmenter[Any]` → `ModelAugmenter[RelationValue]`
   - Existing behavior unchanged

#### Files Changed

| File | Change |
|---|---|
| `fol/bridge/KnowledgeBaseModel.scala` | `toModel[D: DomainElement]` → `Model[D]`; eliminate `toDomainSet`/`fromDomainValue` |
| `fol/bridge/KnowledgeSourceModel.scala` | Same as KnowledgeBaseModel; all 3 `toModel` variants generic |
| `fol/bridge/NumericAugmenter.scala` | Compose legacy augmenter from new split parts |
| `semantics/EvaluationContext.scala` | Replace `Any`-specific extensions with generic methods |

#### Files Created

| File | Purpose |
|---|---|
| `fol/bridge/ComparisonAugmenter.scala` | `[D: Ordering]` — comparison predicates |
| `fol/bridge/ArithmeticAugmenter.scala` | `[D: Fractional]` — arithmetic functions |
| `fol/bridge/LiteralResolver.scala` | `[D: DomainCodec]` — numeric literal fallback |

#### Files Changed (Tests)

| File | Change |
|---|---|
| `fol/bridge/NumericAugmenterSpec.scala` | Split + add tests for new augmenters |
| `semantics/EvaluationContextSpec.scala` | Add generic extension tests |
| `fol/semantics/ModelAugmentationIntegrationSpec.scala` | `[Any]` → `[RelationValue]` |

#### Checkpoint

- [ ] Compiles
- [ ] All bridge + augmenter tests pass
- [ ] `Model[Any]` no longer appears in bridge production code
- [ ] Commit (signed)

---

### Phase 4: Generic Evaluation Pipeline

#### Objective

Thread `D` through the evaluation pipeline: `RangeExtractor`,
`ScopeEvaluator`, `FOLBridge`, and `VagueSemantics`.

#### ADR References

- ADR-001: Evaluation path unification — both paths now carry `D`
- ADR-003: `ProportionEstimator` already generic (`[A]`)
- ADR-005: `ModelAugmenter[D]` threads through `VagueSemantics`

#### ADR Compliance Review (Planning)

| ADR | Status |
|---|---|
| ADR-001 | ⚠️ Pipeline methods gain `[D]` — aligned with amended §4 |
| ADR-002 | ✅ Parser unaffected |
| ADR-003 | ✅ `estimateWithSampling[A]` already generic — `A` becomes `D` |
| ADR-004 | ✅ Objects for operations; tagless initial preserved |
| ADR-005 | ✅ `ModelAugmenter[D]` threading unchanged |
| ADR-006 | ⚠️ Actively superseding — `toDomainValue` removed from pipeline |

#### Tasks

##### 4a: `ScopeEvaluator` — Generic

All 5 public methods become generic on `D`:

```scala
object ScopeEvaluator:
  def evaluateForElement[D](
    formula: Formula[FOL], element: D, variable: String,
    model: Model[D], substitution: Map[String, D] = Map.empty
  ): Boolean

  def calculateProportion[D](
    sample: Set[D], formula: Formula[FOL], variable: String,
    model: Model[D], substitution: Map[String, D] = Map.empty
  ): Double

  def evaluateSample[D](
    sample: Set[D], formula: Formula[FOL], variable: String,
    model: Model[D], substitution: Map[String, D] = Map.empty
  ): (Set[D], Set[D])

  def evaluateElements[D](
    elements: Set[D], formula: Formula[FOL], variable: String,
    model: Model[D], substitution: Map[String, D] = Map.empty
  ): Map[D, Boolean]

  def countSatisfying[D](
    sample: Set[D], formula: Formula[FOL], variable: String,
    model: Model[D], substitution: Map[String, D] = Map.empty
  ): Int
```

Internal: replace `holdsWithRelationValue` with
`ctx.withBinding(variable, element).holds(formula)`.

##### 4b: `RangeExtractor` — Generic

```scala
object RangeExtractor:
  def extractRange[D: DomainCodec](
    source: KnowledgeSource[D], query: ParsedQuery,
    substitution: Map[String, D] = Map.empty
  ): Either[QueryError, Set[D]]
```

The `buildPattern` method currently creates
`List[Option[RelationValue]]` from FOL terms.  With generic `D`:
- `Term.Const(c)` → uses `DomainCodec[D].fromString(c)` to create `D`
- `Term.Var(v)` with substitution → `substitution.get(v): Option[D]`

Note: `DomainExtraction.scala` also becomes generic — all its methods
take `KnowledgeSource[D]` and return `Set[D]` / `Set[RelationTuple[D]]`.

##### 4c: `FOLBridge` — Generic

```scala
object FOLBridge:
  def scopeToPredicate[D: DomainElement: DomainCodec](
    formula: Formula[FOL], variable: String,
    source: KnowledgeSource[D],
    answerTuple: Map[String, D] = Map.empty,
    modelAugmenter: ModelAugmenter[D] = ModelAugmenter.identity
  ): D => Boolean

  def scopeToStringPredicate[D: DomainElement: DomainCodec](
    formula: Formula[FOL], variable: String,
    source: KnowledgeSource[D],
    answerTuple: Map[String, D] = Map.empty,
    modelAugmenter: ModelAugmenter[D] = ModelAugmenter.identity
  ): String => Boolean
```

The `substitution: Map[String, Any]` conversion disappears — now
`Map[String, D]` throughout.

##### 4d: `VagueSemantics` — Generic

```scala
object VagueSemantics:
  def holds[D: DomainElement: DomainCodec](
    query: ParsedQuery,
    source: KnowledgeSource[D],
    answerTuple: Map[String, D] = Map.empty,
    samplingParams: SamplingParams = SamplingParams.exact,
    hdrConfig: HDRConfig = HDRConfig.default,
    modelAugmenter: ModelAugmenter[D] = ModelAugmenter.identity
  )(using ClassTag[D]): Either[QueryError, VagueQueryResult]

  def evaluate[D: DomainElement: DomainCodec](...same...
  )(using ClassTag[D]): Either[QueryError, EvaluationOutput[D]]
```

Note: `ClassTag[D]` is needed because `ProportionEstimator.estimateWithSampling`
requires it (for array operations in HDRSampler).

##### 4e: `EvaluationOutput` — Generic

```scala
case class EvaluationOutput[D](
  result: VagueQueryResult,
  rangeElements: Set[D],
  satisfyingElements: Set[D]
)
```

#### Files Changed

| File | Change |
|---|---|
| `fol/semantics/ScopeEvaluator.scala` | All 5 methods: `[D]` param, `Model[D]`, `Set[D]` |
| `fol/semantics/RangeExtractor.scala` | `[D: DomainCodec]`, `KnowledgeSource[D]`, `Set[D]` |
| `fol/semantics/DomainExtraction.scala` | All methods: `KnowledgeSource[D]`, `Set[D]`, `Set[RelationTuple[D]]` |
| `fol/bridge/FOLBridge.scala` | `[D: DomainElement: DomainCodec]`, `D => Boolean` |
| `fol/semantics/VagueSemantics.scala` | `[D: DomainElement: DomainCodec]`, `KnowledgeSource[D]` |
| `fol/result/EvaluationOutput.scala` | `EvaluationOutput[D]` |

#### Files Changed (Tests)

| File | Change |
|---|---|
| `fol/semantics/ScopeEvaluatorSpec.scala` | `RelationValue` type params; remove `toDomainValue` calls |
| `fol/semantics/RangeExtractorSpec.scala` | `KnowledgeSource[RelationValue]` type params |
| `fol/semantics/VagueSemanticsSpec.scala` | Add type params; existing behavior unchanged |
| `fol/semantics/ModelAugmentationIntegrationSpec.scala` | Thread `[RelationValue]` through |

#### Checkpoint

- [ ] Compiles
- [ ] Full pipeline tests pass
- [ ] `Model[Any]` does not appear in pipeline production code
- [ ] Commit (signed)

---

### Phase 5: Generic Query Types

#### Objective

Parameterize `ResolvedQuery[D]` and `UnresolvedQuery[D]` so the
shared IL carries the consumer's domain type through to evaluation.

#### ADR References

- ADR-001 §Decision 4 (amended): "One Type Flow — No Gratuitous Generics"
- ADR-003: `ProportionEstimator.estimateWithSampling[A]` — `A` = `D`

#### ADR Compliance Review (Planning)

| ADR | Status |
|---|---|
| ADR-001 | ✅ §4 amended to permit type parameter carrying real info |
| ADR-002 | ✅ Parser produces `ParsedQuery` (string-level, unchanged) |
| ADR-003 | ✅ `estimateWithSampling[D]` — already generic |
| ADR-004 | ✅ Case classes for data |
| ADR-005 | ✅ N/A — augmenters not in query types |
| ADR-006 | ⚠️ Final step of superseding |

#### Tasks

##### 5a: `ResolvedQuery[D]`

```scala
case class ResolvedQuery[D](
  quantifier: VagueQuantifier,
  elements: Set[D],
  predicate: D => Boolean,
  params: SamplingParams = SamplingParams.exact,
  hdrConfig: HDRConfig = HDRConfig.default
)(using ClassTag[D]):
  def evaluate(): VagueQueryResult = ...
  def evaluateWithOutput(): EvaluationOutput[D] = ...
```

Internal: `HDRSampler[D]`, `ProportionEstimator.estimateWithSampling[D]`
— types flow through naturally.

##### 5b: `UnresolvedQuery[D]`

> **Historical note:** Phase 5b was implemented but subsequently removed.
> The typed DSL (`UnresolvedQuery`, `Query` builder, `DomainSpec`, `Predicates`)
> was retired in favour of `ResolvedQuery.fromRelation` — see ADR-011.

```scala
case class UnresolvedQuery[D](
  quantifier: VagueQuantifier,
  domain: DomainSpec,
  predicate: D => Boolean,
  params: SamplingParams = SamplingParams.exact,
  hdrConfig: HDRConfig = HDRConfig.default
)(using ClassTag[D]):
  def resolve(source: KnowledgeSource[D]): Either[QueryError, ResolvedQuery[D]]
  def evaluate(source: KnowledgeSource[D]): Either[QueryError, VagueQueryResult]
  def evaluateWithOutput(source: KnowledgeSource[D]): Either[QueryError, EvaluationOutput[D]]
```

##### 5c: `Query` Builder

```scala
object Query:
  def quantifier(q: VagueQuantifier): QuantifierBuilder = ...

  class QuantifierBuilder(q: VagueQuantifier):
    def over(name: String, position: Int = 0): DomainBuilder = ...

  class DomainBuilder(q: VagueQuantifier, domain: DomainSpec):
    def where[D: ClassTag](pred: D => Boolean): UnresolvedQuery[D] = ...
```

Keep `whereConst` as a `RelationValue`-specific convenience:

```scala
    def whereConst(pred: String => Boolean): UnresolvedQuery[RelationValue] = ...
```

##### 5d: Update `VagueSemantics.toResolved`

```scala
private def toResolved[D: DomainElement: DomainCodec: ClassTag](
  query: ParsedQuery, source: KnowledgeSource[D], ...
): ResolvedQuery[D]
```

##### 5e: Update Tests

1. `ResolvedQuerySpec.scala`:
   - `ResolvedQuery[RelationValue]` type params
   - `evaluateWithOutput` returns `EvaluationOutput[RelationValue]`

2. `UnresolvedQuerySpec.scala`:
   - `UnresolvedQuery[RelationValue]` type params
   - `Query.quantifier(...).over(...).where[RelationValue](...)` syntax

#### Files Changed

| File | Change |
|---|---|
| `fol/query/ResolvedQuery.scala` | `ResolvedQuery[D]`, `EvaluationOutput[D]` |
| `fol/query/Query.scala` | `UnresolvedQuery[D]`, generic builder |
| `fol/semantics/VagueSemantics.scala` | `toResolved[D]` produces `ResolvedQuery[D]` |

#### Files Changed (Tests)

| File | Change |
|---|---|
| `fol/query/ResolvedQuerySpec.scala` | `[RelationValue]` type params |
| `fol/query/UnresolvedQuerySpec.scala` | `[RelationValue]` type params |

#### Checkpoint

- [ ] Compiles
- [ ] Full test suite passes (855+ tests)
- [ ] `RelationValue` appears only in instances, tests, and example code
- [ ] Commit (signed)

---

### Phase 6: Cleanup + Migration Support

#### Objective

Remove dead code, update ADRs, prepare migration guide for register,
version bump, and verify cross-project compilation.

#### Tasks

##### 6a: Remove Dead Code

1. Delete `RelationValueUtil.toDomainValue`, `fromDomainValue`,
   `toDomainSet`, `toDomainSetTyped`, `toDomainList`.
   If the file is empty, delete it entirely.

2. Remove `RelationValueUtilSpec.scala` (or reduce to any remaining
   utility tests).

3. Remove `holdsWithRelationValue` extension if fully replaced by
   generic `holdsWithBinding`.

##### 6b: Update Example Code

1. `FOLDemo.scala` — update pattern matches, use `[RelationValue]`
   or `[String]`.
2. `VagueQueryPlayground.scala` — same.
3. `VagueSemanticsDemo.scala` — same.
4. `VagueQuantifierDemo.scala`, `CyberSecurityDomain.scala`,
   `CyberSecurityExamples.scala` — same.
5. `RiskDomain.scala` (example fixture) — update to new API.

##### 6c: ADR Updates

1. **ADR-006** — Status: "Superseded by Option D implementation."
   Add supersedence note with commit reference.

2. Create **ADR-007** (or next available number) — "Domain Type Safety"
   documenting the Option D decision, rationale, and type architecture.

##### 6d: Version Bump

Update `build.sbt`:
```scala
version := "0.2.0-SNAPSHOT"   // binary-incompatible API change
```

##### 6e: Publish + Register Verification

1. `sbt publishLocal`
2. In register project, update `fol-engine` version dependency.
3. Verify `sbt compile` passes in register (even if register's
   own integration code is not yet migrated — only checking that
   the library API is consumable).

##### 6f: Register Migration Guide

Document the migration steps for register (in register project):

1. Define `RiskDomain` enum with type class instances:
   ```scala
   enum RiskDomain:
     case NodeId(id: String)
     case Score(value: Double)

   given DomainElement[RiskDomain] with
     extension (d: RiskDomain) def show: String = d match
       case NodeId(id) => id
       case Score(v)   => v.toString

   given Ordering[RiskDomain] with
     def compare(a: RiskDomain, b: RiskDomain): Int = (a, b) match
       case (Score(x), Score(y))   => x.compare(y)
       case (NodeId(x), NodeId(y)) => x.compare(y)
       case _ => throw IllegalArgumentException("Cross-variant comparison")

   given DomainCodec[RiskDomain] with
     def fromString(s: String): RiskDomain = NodeId(s)
     def fromNumericLiteral(s: String): Option[RiskDomain] =
       s.toDoubleOption.map(Score(_))
   ```

2. Replace `KnowledgeBase` → `KnowledgeBase[RiskDomain]`
3. Replace `ModelAugmenter[Any]` → `ModelAugmenter[RiskDomain]`
4. Remove all `asInstanceOf[Double]` casts in `QueryService`
5. Update `QueryResponseBuilder` to pattern-match on `RiskDomain.NodeId`

#### Files Changed

| File | Change |
|---|---|
| `fol/datastore/RelationValueUtil.scala` | Delete or gut |
| `build.sbt` | Version bump |
| `docs/ADR-006.md` | Status: Superseded |

#### Files Deleted

| File | Reason |
|---|---|
| `RelationValueUtilSpec.scala` | No longer needed |
| `RelationValueUtil.scala` | Dead code (if fully empty) |

#### Files Created

| File | Purpose |
|---|---|
| `docs/ADR-007.md` | Domain Type Safety decision record |

#### Checkpoint

- [ ] Clean build — no warnings
- [ ] Full test suite passes
- [ ] No `Any` in domain-typed production code paths
- [ ] `grep -r "Model\[Any\]" src/main/` returns no results
- [ ] `sbt publishLocal` succeeds
- [ ] Register `sbt compile` succeeds
- [ ] Commit (signed) + push

---

## 8. ADR Compatibility Summary

| ADR | Phase Affected | Impact | Action |
|---|---|---|---|
| **ADR-001** | Phase 5 | §4 already amended; `ResolvedQuery[D]` = type param carries real info | None — pre-approved |
| **ADR-002** | None | Parser operates at string level; unaffected | None |
| **ADR-003** | Phase 4-5 | `estimateWithSampling[A]` already generic; A becomes D | Natural fit |
| **ADR-004** | Phase 2-3 | Extends tagless initial to datastore layer | Aligned |
| **ADR-005** | Phase 3 | `NumericAugmenter` split into typed components | Compatible — `ModelAugmenter[D]` already generic |
| **ADR-006** | Phase 6 | Superseded — accepted trade-off eliminated | Update status |

---

## 9. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Binary-incompatible API change | High | Version bump `0.2.0-SNAPSHOT`. Register updated simultaneously. |
| Type parameter proliferation | Medium | `[D: DomainElement]` on ~15 methods. Mitigated by minimal type class + type aliases. |
| Register migration effort | Medium | ~1-2 hours. Migration guide provided. |
| Scala 3 inference failures | Medium | Test early with real register code in Phase 6e. |
| `ClassTag[D]` threading | Medium | Required by `HDRSampler`. Context bounds propagate — no `asInstanceOf`. |
| Performance | Low | No boxing change — already boxed in `RelationValue`/`Any`. |
| Harrison's `integerModel` | None | Stays `Model[Int]` — unaffected. |

---

## 10. Effort Estimate

| Phase | Estimated Time |
|---|---|
| Phase 1: Type classes | 30 min |
| Phase 2: Generic datastore | 1.5–2 hours |
| Phase 3: Generic bridge + augmenter split | 2–3 hours |
| Phase 4: Generic pipeline | 1–1.5 hours |
| Phase 5: Query types | 30 min–1 hour |
| Phase 6: Cleanup + migration | 1–1.5 hours |
| Compile-fix + test cycles | 1–2 hours |
| **Total (fol-engine)** | **~7–11 hours** |
| Register-side migration | 1–2 hours |

---

## 11. Summary of Changes by File

### Production Files — Complete Inventory

| File | Phase | Change Summary |
|---|---|---|
| `fol/datastore/DomainElement.scala` | 1 | **NEW** — type class + instances |
| `fol/datastore/DomainCodec.scala` | 1 | **NEW** — parser codec |
| `fol/datastore/Relation.scala` | 2 | Remove `PositionType`, `positionTypes`, `validates` |
| `fol/datastore/RelationValueValidation.scala` | 2 | **NEW** — moved `PositionType` + `validates` |
| `fol/datastore/KnowledgeBase.scala` | 2 | `KnowledgeBase[D]`, arity-only validation |
| `fol/datastore/KnowledgeSource.scala` | 2 | `KnowledgeSource[D]`, `InMemoryKnowledgeSource[D]` |
| `fol/bridge/KnowledgeBaseModel.scala` | 3 | `toModel[D: DomainElement]` → `Model[D]` |
| `fol/bridge/KnowledgeSourceModel.scala` | 3 | `toModel[D: DomainElement]` → `Model[D]` |
| `fol/bridge/ComparisonAugmenter.scala` | 3 | **NEW** — `[D: Ordering]` |
| `fol/bridge/ArithmeticAugmenter.scala` | 3 | **NEW** — `[D: Fractional]` |
| `fol/bridge/LiteralResolver.scala` | 3 | **NEW** — `[D: DomainCodec]` |
| `fol/bridge/NumericAugmenter.scala` | 3 | Compose from new parts; `[RelationValue]` |
| `semantics/EvaluationContext.scala` | 3 | Generic extensions; remove `Any`-specific |
| `fol/semantics/ScopeEvaluator.scala` | 4 | All methods `[D]`; `Model[D]`, `Set[D]` |
| `fol/semantics/RangeExtractor.scala` | 4 | `[D: DomainCodec]`; `KnowledgeSource[D]` |
| `fol/semantics/DomainExtraction.scala` | 4 | `KnowledgeSource[D]`; `Set[D]` |
| `fol/bridge/FOLBridge.scala` | 4 | `[D: DomainElement: DomainCodec]`; `D => Boolean` |
| `fol/semantics/VagueSemantics.scala` | 4 | `[D: DomainElement: DomainCodec]` |
| `fol/result/EvaluationOutput.scala` | 4 | `EvaluationOutput[D]` |
| `fol/query/ResolvedQuery.scala` | 5 | `ResolvedQuery[D]` |
| `fol/query/Query.scala` | 5 | `UnresolvedQuery[D]`; generic builder |
| `fol/datastore/RelationValueUtil.scala` | 6 | **DELETE** |
| `build.sbt` | 6 | Version bump to `0.2.0-SNAPSHOT` |
| `docs/ADR-006.md` | 6 | Status: Superseded |
| `docs/ADR-007.md` | 6 | **NEW** — Domain Type Safety ADR |

### Test Files — Complete Inventory

| File | Phase | Change Summary |
|---|---|---|
| `fol/datastore/DomainElementSpec.scala` | 1 | **NEW** — type class tests |
| `fol/datastore/KnowledgeBaseSpec.scala` | 2 | `[RelationValue]` type params; PositionType tests |
| `fol/bridge/NumericAugmenterSpec.scala` | 3 | Split; add augmenter-specific tests |
| `semantics/EvaluationContextSpec.scala` | 3 | Generic extension tests |
| `fol/semantics/ModelAugmentationIntegrationSpec.scala` | 3 | `[Any]` → `[RelationValue]` |
| `fol/semantics/ScopeEvaluatorSpec.scala` | 4 | `[RelationValue]` params; remove `toDomainValue` |
| `fol/semantics/RangeExtractorSpec.scala` | 4 | `KnowledgeSource[RelationValue]` |
| `fol/semantics/VagueSemanticsSpec.scala` | 4 | Type params throughout |
| `fol/query/ResolvedQuerySpec.scala` | 5 | `ResolvedQuery[RelationValue]` |
| `fol/query/UnresolvedQuerySpec.scala` | 5 | `UnresolvedQuery[RelationValue]` |
| `fol/datastore/RelationValueUtilSpec.scala` | 6 | **DELETE** |

### Unchanged Files (18 production + 14 test)

FOL core (`logic/`, `parser/`, `printer/`, `lexer/`), `FOLSemantics.scala`,
`ModelAugmenter.scala`, `VagueQuantifier.scala`, sampling infrastructure
(`SamplingParams`, `HDRConfig`, `HDRSampler`, `SampleSizeCalculator`,
`NormalApprox`, `ProportionEstimator`), `QueryError.scala`,
`VagueQueryResult.scala`, `ParsedQuery.scala`, `Quantifier.scala`.

---

## 12. References

- ADR-001 through ADR-006 — existing design decisions
- ADR-001 §Decision 4 (amended) — "One Type Flow — No Gratuitous Generics"
- ADR-005 §Decision 2 — `ModelAugmenter[D]` already generic
- ADR-006 — documents the `Any` erasure (to be superseded)
- ADR-028 (register) — vague quantifier query pane design
- ADR-028 appendix §8 — query patterns that confirm structural separation
- Harrison (2009) — OCaml `string`-uniform approach (this plan departs)
- WORKING-INSTRUCTIONS.md (register) — governance protocol
