# Implementation Plan: Model Augmentation (ADR-005)

**Date:** 2026-03-23  
**ADR:** [ADR-005](docs/ADR-005.md) — Model Augmentation via Functional Composition  
**Goal:** Enable consumers to inject custom functions and predicates into
the FOL evaluation pipeline without modifying the library.

---

## Precondition

All 792 existing tests pass. Implementation is strictly additive — no
existing signatures change, no existing behaviour changes.

---

## Phase 1: `Interpretation` Composition Primitives

**File:** `src/main/scala/semantics/FOLSemantics.scala`

Add methods to `Interpretation[D]`:

```scala
def withFunctions(extra: Map[String, List[D] => D]): Interpretation[D] =
  copy(funcInterp = funcInterp ++ extra)

def withPredicates(extra: Map[String, List[D] => Boolean]): Interpretation[D] =
  copy(predInterp = predInterp ++ extra)

def withDomain(extra: Set[D]): Interpretation[D] =
  copy(domain = Domain(domain.elements ++ extra))

def combine(other: Interpretation[D]): Interpretation[D] =
  Interpretation(
    Domain(domain.elements ++ other.domain.elements),
    funcInterp ++ other.funcInterp,
    predInterp ++ other.predInterp
  )

def withFunctionFallback(
  fallback: String => Option[List[D] => D]
): Interpretation[D] =
  val outer = this
  new Interpretation[D](domain, funcInterp, predInterp):
    override def getFunction(name: String): List[D] => D =
      funcInterp.get(name)
        .orElse(fallback(name))
        .getOrElse(_ => throw new Exception(s"Uninterpreted function: $name"))
```

**Tests (new file):** `src/test/scala/semantics/InterpretationSpec.scala`

- `withFunctions` merges and right-biases on collision
- `withPredicates` merges and right-biases on collision
- `withDomain` unions domain elements
- `combine` associativity: `(a combine b) combine c == a combine (b combine c)`
- `combine` identity: `i combine empty == i`, `empty combine i == i`
- `withFunctionFallback` falls through to fallback on missing name
- `withFunctionFallback` map entries take priority over fallback

**Estimated tests:** ~10

---

## Phase 2: `ModelAugmenter` Case Class

**New file:** `src/main/scala/semantics/ModelAugmenter.scala`

```scala
package semantics

case class ModelAugmenter[D](run: Model[D] => Model[D]):
  def apply(model: Model[D]): Model[D] = run(model)
  def andThen(that: ModelAugmenter[D]): ModelAugmenter[D] =
    ModelAugmenter(m => that.run(this.run(m)))
  def compose(that: ModelAugmenter[D]): ModelAugmenter[D] =
    ModelAugmenter(m => this.run(that.run(m)))

object ModelAugmenter:
  def identity[D]: ModelAugmenter[D] = ModelAugmenter(m => m)

  def combine[D](first: ModelAugmenter[D], second: ModelAugmenter[D]): ModelAugmenter[D] =
    first andThen second

  def fromFunctions[D](fns: Map[String, List[D] => D]): ModelAugmenter[D] =
    ModelAugmenter(m => Model(m.interpretation.withFunctions(fns)))

  def fromPredicates[D](preds: Map[String, List[D] => Boolean]): ModelAugmenter[D] =
    ModelAugmenter(m => Model(m.interpretation.withPredicates(preds)))
```

**Tests (new file):** `src/test/scala/semantics/ModelAugmenterSpec.scala`

- `identity.apply(model) == model`
- `(a andThen b) andThen c == a andThen (b andThen c)` (associativity)
- `identity andThen a == a` (left identity)
- `a andThen identity == a` (right identity)
- `fromFunctions` merges into existing model
- `fromPredicates` merges into existing model
- `combine(a, b)` equivalent to `a andThen b`

**Estimated tests:** ~8

---

## Phase 3: `NumericAugmenter` — Built-In Comparisons + Numeric Literals

**New file:** `src/main/scala/fol/bridge/NumericAugmenter.scala`

```scala
package fol.bridge

import semantics.{Model, ModelAugmenter}

object NumericAugmenter:
  def augmenter: ModelAugmenter[Any] = ModelAugmenter(model =>
    val preds = Map[String, List[Any] => Boolean](
      ">"  -> { case List(a, b) => toDouble(a) > toDouble(b) },
      "<"  -> { case List(a, b) => toDouble(a) < toDouble(b) },
      ">=" -> { case List(a, b) => toDouble(a) >= toDouble(b) },
      "<=" -> { case List(a, b) => toDouble(a) <= toDouble(b) },
      "="  -> { case List(a, b) => toDouble(a) == toDouble(b) },
    )
    val withPreds = model.interpretation.withPredicates(preds)
    Model(withPreds.withFunctionFallback(numericLiteral))
  )

  private def numericLiteral(name: String): Option[List[Any] => Any] =
    name.toDoubleOption.map(d => (_: List[Any]) => d)

  private def toDouble(v: Any): Double = v match
    case d: Double      => d
    case i: Int         => i.toDouble
    case l: Long        => l.toDouble
    case bd: BigDecimal => bd.toDouble
    case s: String      => s.toDouble
    case other          =>
      throw new Exception(s"Cannot convert to Double: $other (${other.getClass.getName})")
```

**Tests (new file):** `src/test/scala/fol/bridge/NumericAugmenterSpec.scala`

- Each comparison predicate: `> true`, `> false`, `>= boundary`
- Mixed types: `Int > Long`, `Double >= BigDecimal`, `String "42" < Int 100`
- Numeric literal resolution: `"5000000"` resolves to `5000000.0`
- Negative literals: `"-100"` resolves correctly
- Decimal literals: `"3.14"` resolves correctly
- Non-numeric string: `"alice"` → exception
- Augmenter does not clobber existing KB predicates (right-bias merge)
- Augmenter does not clobber existing KB functions (fallback only)

**Estimated tests:** ~15

---

## Phase 4: Thread Augmenter Through Pipeline

### 4a. `FOLBridge.scopeToPredicate`

**File:** `src/main/scala/fol/bridge/FOLBridge.scala`

Add `modelAugmenter` parameter with default `ModelAugmenter.identity`:

```scala
def scopeToPredicate(
  formula: Formula[FOL],
  variable: String,
  source: KnowledgeSource,
  answerTuple: Map[String, RelationValue] = Map.empty,
  modelAugmenter: ModelAugmenter[Any] = ModelAugmenter.identity
): RelationValue => Boolean =
  val model = modelAugmenter(KnowledgeSourceModel.toModel(source))
  // ... rest unchanged, close over augmented model
```

Similarly for `scopeToStringPredicate`.

### 4b. `VagueSemantics.holds` / `evaluate` / `toResolved`

**File:** `src/main/scala/fol/semantics/VagueSemantics.scala`

Add `modelAugmenter` parameter to `toResolved` (private), `holds`,
and `evaluate`:

```scala
private def toResolved(
  query: ParsedQuery,
  source: KnowledgeSource,
  answerTuple: Map[String, RelationValue],
  samplingParams: SamplingParams,
  hdrConfig: HDRConfig,
  modelAugmenter: ModelAugmenter[Any] = ModelAugmenter.identity
): ResolvedQuery =
  // ... same as before, pass modelAugmenter to FOLBridge.scopeToPredicate

def holds(
  query: ParsedQuery,
  source: KnowledgeSource,
  answerTuple: Map[String, RelationValue] = Map.empty,
  samplingParams: SamplingParams = SamplingParams.exact,
  hdrConfig: HDRConfig = HDRConfig.default,
  modelAugmenter: ModelAugmenter[Any] = ModelAugmenter.identity
): Either[QueryError, VagueQueryResult]

def evaluate(
  query: ParsedQuery,
  source: KnowledgeSource,
  answerTuple: Map[String, RelationValue] = Map.empty,
  samplingParams: SamplingParams = SamplingParams.exact,
  hdrConfig: HDRConfig = HDRConfig.default,
  modelAugmenter: ModelAugmenter[Any] = ModelAugmenter.identity
): Either[QueryError, EvaluationOutput]
```

**Tests (new file):** `src/test/scala/fol/semantics/ModelAugmentationIntegrationSpec.scala`

- End-to-end: custom `score` function + `>(score(x), 50)` → correct evaluation
- End-to-end: NumericAugmenter enables numeric comparisons in scope
- `identity` augmenter: all existing KB-only queries unchanged
- Composed augmenter: numeric + custom domain functions together
- Error case: missing function without augmenter → meaningful error

**Estimated tests:** ~8

---

## Phase 5: Verify and Publish

1. Run full test suite: `sbt test` — 792 existing + ~41 new all pass
2. `sbt publishLocal` — `com.risquanter::fol-engine:0.1.0-SNAPSHOT`
3. Verify register can depend on updated artifact (no breaking changes)

---

## Test Summary

| Phase | Spec File | Tests |
|---|---|---|
| 1 | `semantics/InterpretationSpec.scala` | ~10 |
| 2 | `semantics/ModelAugmenterSpec.scala` | ~8 |
| 3 | `fol/bridge/NumericAugmenterSpec.scala` | ~15 |
| 4 | `fol/semantics/ModelAugmentationIntegrationSpec.scala` | ~8 |
| **Total new** | | **~41** |
| **Existing unchanged** | | **792** |

---

## File Change Summary

| Action | File |
|---|---|
| Modify | `src/main/scala/semantics/FOLSemantics.scala` — `Interpretation` composition methods |
| Create | `src/main/scala/semantics/ModelAugmenter.scala` |
| Create | `src/main/scala/fol/bridge/NumericAugmenter.scala` |
| Modify | `src/main/scala/fol/bridge/FOLBridge.scala` — `modelAugmenter` param |
| Modify | `src/main/scala/fol/semantics/VagueSemantics.scala` — `modelAugmenter` param |
| Create | `src/test/scala/semantics/InterpretationSpec.scala` |
| Create | `src/test/scala/semantics/ModelAugmenterSpec.scala` |
| Create | `src/test/scala/fol/bridge/NumericAugmenterSpec.scala` |
| Create | `src/test/scala/fol/semantics/ModelAugmentationIntegrationSpec.scala` |

**Modified:** 3 files  
**Created:** 6 files  
**Deleted:** 0 files

---

## Consumer Integration (register-side, not in this plan)

After `publishLocal`, register builds its augmenter:

```scala
// RiskTreeKnowledgeBase.scala (register-side)
val riskTreeAugmenter = ModelAugmenter.fromFunctions[Any](Map(
  "p95"  -> { case List(id) => getQuantile(id.toString, "p95") },
  "p90"  -> { case List(id) => getQuantile(id.toString, "p90") },
  "p50"  -> { case List(id) => getQuantile(id.toString, "p50") },
  "p99"  -> { case List(id) => getQuantile(id.toString, "p99") },
  "lec"  -> { case List(id, t) => probOfExceedance(id.toString, toLong(t)) },
))

val augmenter = NumericAugmenter.augmenter andThen riskTreeAugmenter

VagueSemantics.evaluate(query, source, modelAugmenter = augmenter)
```

Optional ZIO Prelude interop (3 lines):

```scala
given Identity[ModelAugmenter[Any]] with
  def identity: ModelAugmenter[Any] = ModelAugmenter.identity
  def combine(l: => ModelAugmenter[Any], r: => ModelAugmenter[Any]): ModelAugmenter[Any] =
    l andThen r
```

---

## Design Rationale

**No type class library dependency.** fol-engine provides monoid
*operations* (`identity`, `andThen`, `combine`). The `ModelAugmenter`
case class wrapper (mirroring register's `RiskTransform` pattern)
gives consumers a nominal type to declare type class instances in their
preferred framework without fol-engine importing it. Register uses
ZIO Prelude's `Identity`; a Cats consumer would provide `given Monoid`;
fol-engine remains framework-agnostic.

**Three-layer monoid composition.** All composition points satisfy
monoid laws by construction:

| Layer | Structure | Identity | Combine | Laws |
|---|---|---|---|---|
| `Interpretation.combine` | `Map.++` / `Set.++` | Empty maps/set | Right-biased merge | Assoc: `Map.++`; Id: empty `Map` |
| `ModelAugmenter[D]` | Endomorphism | `m => m` | `andThen` | Assoc: function composition; Id: identity function |
| `getFunction` fallback | `Option.orElse` | `_ => None` | `orElse` chain | Assoc: `Option.orElse`; Id: `_ => None` |
