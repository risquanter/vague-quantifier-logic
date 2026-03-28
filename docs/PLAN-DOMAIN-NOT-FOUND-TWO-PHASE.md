# Implementation Plan: DomainNotFoundError — Two-Phase Delivery

**Date:** 2026-03-27  
**Status:** Complete — Phase 1 (0.4.0-SNAPSHOT, 2026-03-28); Phase 2 (0.5.0-SNAPSHOT, 2026-03-28)  
**Relates to:** PROMPT-FOL-ENGINE-DOMAIN-NOT-FOUND-ERROR.md, ADR-001

---

## Problem

`VagueSemantics` wraps all failures from the typed pipeline in generic `QueryError.ValidationError`
values distinguished only by a `field` string (`"typed_query"`, `"typed_runtime_model"`).
Callers (e.g. `register`) cannot reliably distinguish:

- **User query error (400):** bind-time type-check failure — bad predicate name, arity mismatch, wrong sort
- **Wiring bug (500):** `validateAgainst` failure — dispatcher or domain not registered at startup  
- **Runtime gap (500):** evaluation-time missing domain — type registered but no domain in `RuntimeModel`

All three surface as `ValidationError`, making HTTP status code mapping fragile and
forcing callers to inspect the `field` string — a stringly-typed API contract.

---

## Phase 1 — Structured Errors (Unblocks Register)

### Goal
Replace all three `ValidationError` wrappers in `VagueSemantics` and the
`EvaluationError` domain-not-found sites in `TypedSemantics` with dedicated,
matchable `QueryError` subtypes. Register can pattern-match each case to
produce correct HTTP status codes without inspecting any string fields.

### What changes

**`fol/error/QueryError.scala`**  
Add one new case class to the `// FOL Semantics Errors` section, following the
`RelationNotFoundError` structural pattern:

```scala
/** Raised when a query quantifies over a type that has no registered domain
  * in the RuntimeModel. This indicates either a user error (quantifying over
  * a non-domain type) or a wiring bug (domain not registered at startup).
  */
case class DomainNotFoundError(
  typeName: String,
  availableTypes: Set[String]
) extends QueryError:
  def message = s"No domain found for type '$typeName'"
  override val context = Map(
    "type"      -> typeName,
    "available" -> availableTypes.mkString(", ")
  )
  override def formatted: String =
    s"No domain found for type '$typeName'. " +
    s"Available types with domains: ${availableTypes.mkString(", ")}"
```

**Field types are `String`/`Set[String]`, not `TypeId`/`Set[TypeId]`.**  
`QueryError` is in `fol.error`. `TypeId` is in `fol.typed`, which already imports
`fol.error`. Using `TypeId` here would create a circular package dependency.
`UninterpretedSymbolError` in the same file uses the same `String` convention.
This is a deliberate constraint, not an oversight.

Also add two further case classes (same section, same `List[String]` convention
forced by the same circular-dependency constraint):

```scala
/** Raised when a query fails the typed bind phase (type-check errors).
  * Indicates a user query error — all instances map to HTTP 400.
  * Individual error messages are rendered strings; raw TypeCheckError
  * detail is not carried here due to fol.error → fol.typed package constraint.
  */
case class BindError(
  errors: List[String]
) extends QueryError:
  def message = s"Query type-checking failed: ${errors.mkString("; ")}"
  override val context = Map("errors" -> errors.mkString("; "))

/** Raised when RuntimeModel.validateAgainst fails (dispatcher or domain
  * coverage gaps). Indicates a wiring/infra error — all instances map to HTTP 500.
  * Individual error messages are rendered strings; raw RuntimeModelError
  * detail is not carried here due to fol.error → fol.typed package constraint.
  */
case class ModelValidationError(
  errors: List[String]
) extends QueryError:
  def message = s"Runtime model validation failed: ${errors.mkString("; ")}"
  override val context = Map("errors" -> errors.mkString("; "))
```

**`fol/semantics/VagueSemantics.scala`** — `bindTyped` wrapper:

```scala
// Before:
QueryError.ValidationError(
  message = "Query type-checking failed",
  field = "typed_query",
  context = Map("errors" -> renderTypeErrors(errors))
)

// After:
QueryError.BindError(errors = renderTypeErrors(errors))
```

`renderTypeErrors` is refactored to return `List[String]` directly (Option A) —
cleaner than splitting a joined string at the call site. The private method
signature changes from `String` to `List[String]`; nothing outside `VagueSemantics`
is affected.
```

**`fol/semantics/VagueSemantics.scala`** — `evaluateTyped` `validateAgainst` wrapper:

```scala
// Before:
QueryError.ValidationError(
  message = "Runtime model validation failed",
  field = "typed_runtime_model",
  ...
)

// After:
QueryError.ModelValidationError(errors = renderModelErrors(errors))
```

A private `renderModelErrors(errors: List[RuntimeModelError]): List[String]` helper
is extracted from the inline map — parallel to `renderTypeErrors`, same rationale.
```

**`fol/typed/TypedSemantics.scala`** — three sites:

```scala
// Site 1 — evaluate(), root domain
model.domains.get(query.variable.sort)
  .toRight(QueryError.DomainNotFoundError(
    typeName       = query.variable.sort.value,
    availableTypes = model.domains.keySet.map(_.value)
  ))

// Site 2 — evalFormula Forall case
case None => Left(QueryError.DomainNotFoundError(
  typeName       = v.sort.value,
  availableTypes = model.domains.keySet.map(_.value)
))

// Site 3 — evalFormula Exists case (same transformation)
```

### Tests

Add to the appropriate typed semantics spec:

- `DomainNotFoundError` returned when root quantified variable's type has no domain
- `DomainNotFoundError` returned in nested `Forall` over a type with no domain
- `DomainNotFoundError` returned in nested `Exists` over a type with no domain
- `bindTyped` returns `BindError` (not `ValidationError`) on type-check failure
- `evaluateTyped` returns `BindError` (not `ValidationError`) for a malformed query
- `evaluateTyped` returns `ModelValidationError` (not `ValidationError`) when model wiring is incomplete

Net test count: +5 new tests, 2 existing tests updated (868 → 873).

### Delivery

- Version bump: `0.3.0-SNAPSHOT` → `0.4.0-SNAPSHOT`
- `sbt folEngineJVM/publishLocal`
- Deliver Phase 1 usage prompt to `register` agent (see §Usage Prompt below)

### Register usage after Phase 1

```scala
case e: QueryError.BindError            => // HTTP 400 — user query error
case e: QueryError.ModelValidationError => // HTTP 500 — infra wiring bug
case e: QueryError.DomainNotFoundError  => // HTTP 400/500 — missing domain
```

Register removes its existing `field`-string inspections and replaces them with
these type-safe match arms. No other Register changes required.

---

## Phase 2 — Structural Prevention (Internal Engineering Improvement)

### Goal
Make `DomainNotFoundError` structurally unreachable in a correctly configured
system by:

1. Teaching `TypeCatalog` the distinction between enumerable and non-enumerable types
2. Rejecting at bind time any query that quantifies over a non-enumerable type
3. Extending `RuntimeModel.validateAgainst` to verify all enumerable types have
   registered domains — so if `validateAgainst` returns `Right`, evaluation can
   never hit `DomainNotFoundError`

After Phase 2, `DomainNotFoundError` remains in `QueryError` as a **defensive
fallback** for callers who construct `RuntimeModel` directly without going through
the validated pipeline. It is no longer reachable via `VagueSemantics.evaluateTyped`.

### Invariant established by Phase 2

> If `model.validateAgainst(catalog)` returns `Right`, no `DomainNotFoundError`
> can occur during evaluation of any `BoundQuery` bound against the same catalog.

### What changes

**`fol/typed/TypeCatalog.scala`**  
Add `enumerableTypes: Set[TypeId]` parameter with default `= types`:

```scala
case class TypeCatalog private (
  types: Set[TypeId],
  enumerableTypes: Set[TypeId],   // subset of types; may appear as quantified-variable sorts
  constants: Map[String, TypeId],
  functions: Map[SymbolName, FunctionSig],
  predicates: Map[SymbolName, PredicateSig],
  literalValidators: Map[TypeId, String => Boolean]
)
```

Default `enumerableTypes = types` is **backward compatible**: all existing call
sites compile unchanged and treat all types as enumerable until explicitly narrowed.

Validation in `collectErrors` adds:
```scala
val nonSubset = enumerableTypes.diff(types).toList
  .map(t => TypeCatalogError.UnknownType(t.value))
```

**`fol/typed/TypeCheckError.scala`**  
Add one variant:
```scala
case NonEnumerableType(name: String)
```

**`fol/typed/QueryBinder.scala`**  
In `bind` (root variable) and `bindQuantified` (nested variables), after resolving
the variable's sort, verify `sort ∈ catalog.enumerableTypes`:

```scala
if !catalog.enumerableTypes.contains(quantifiedVarSort) then
  Left(List(TypeCheckError.NonEnumerableType(quantifiedVarSort.value)))
```

**`fol/typed/RuntimeModel.scala` — `validateAgainst`**  
Extend the existing coverage check:

```scala
val missingDomains = catalog.enumerableTypes
  .diff(domains.keySet).toList
  .map(t => RuntimeModelError.MissingDomainForEnumerableType(t.value))
```

Add `MissingDomainForEnumerableType(typeName: TypeId)` to `RuntimeModelError`.
`RuntimeModelError` is in `fol.typed` — same package as `TypeId` — so no circular
dependency arises. This is consistent with the existing `SymbolName`-typed variants
(`MissingFunctionImplementation`, `MissingPredicateImplementation`).

**`fol/semantics/VagueSemantics.scala` — `renderTypeErrors`**  
Add a case for `TypeCheckError.NonEnumerableType` — **hard dependency**. Without
this, adding the new variant produces a Scala 3 non-exhaustive match warning and
a missing/empty entry in `BindError.errors` at runtime:

```scala
case TypeCheckError.NonEnumerableType(name) =>
  s"type '$name' is not enumerable and cannot be quantified over"
```

**`fol/semantics/VagueSemantics.scala` — `renderModelErrors`**  
Add a case for `RuntimeModelError.MissingDomainForEnumerableType` — **hard
dependency** for the same reason. Without this, the new variant produces a
non-exhaustive match warning and a missing entry in `ModelValidationError.errors`:

```scala
case RuntimeModelError.MissingDomainForEnumerableType(t) =>
  s"enumerable type '${t.value}' has no registered domain"
```

**`fol/typed/TypedSemantics.scala`**  
The three `DomainNotFoundError` sites remain unchanged. They are now defensive
fallbacks; they cannot be reached if `validateAgainst` was called at startup.

**`fol/error/QueryError.scala` — `DomainNotFoundError` scaladoc update**  
Update the scaladoc from the Phase 1 wording ("user error or wiring bug") to reflect
the Phase 2 status:

```scala
/** Defensive fallback raised when a query quantifies over a type that has no
  * registered domain in the RuntimeModel. In a correctly configured system
  * (TypeCatalog with enumerableTypes declared, RuntimeModel validated via
  * validateAgainst), this error is unreachable. It may arise if RuntimeModel
  * is constructed directly without calling validateAgainst.
  */
```

### Tests

- `TypeCatalog` rejects `enumerableTypes` that are not a subset of `types`
- `QueryBinder.bind` returns `NonEnumerableType` for root variable with non-enumerable sort
- `QueryBinder.bind` returns `NonEnumerableType` for nested `Forall`/`Exists`
- `RuntimeModel.validateAgainst` returns `MissingDomainForEnumerableType` when
  an enumerable type has no domain registered
- End-to-end: correctly configured catalog + model → `validateAgainst` returns `Right`
  → evaluation succeeds without `DomainNotFoundError`
- Defensive fallback: `DomainNotFoundError` is still returned when `RuntimeModel` is
  constructed directly (bypassing `validateAgainst`) with `enumerableTypes` declared
  but no domain registered — confirms the fallback sites are not accidentally removed

Net test count: +5–7 (from post-phase-1 total).

### Implementation NOTE — sort resolution ordering in `bindQuantified`

In `QueryBinder.bindQuantified`, the quantified variable's sort is **inferred from
its usage in the body** — it is not declared up-front. The sort is only available
after `bindFormula(body, ...)` completes. The `NonEnumerableType` check must
therefore be placed **after** body binding, at the point where `envAfterBody.get(name)`
is matched. Placing it before body binding would silently skip the check for
variables whose sort cannot yet be resolved.

### Existing call sites

4 `TypeCatalog.unsafe` / `TypeCatalog.apply` call sites (all in tests) — no changes
required because `enumerableTypes` parameter defaults to `= types`.

### Register impact

**None.** Register's Phase 1 code compiles and works identically after Phase 2.
The `DomainNotFoundError` match arm remains valid (it is now a defensive fallback
that will never be triggered if the model is correctly configured, but keeping it
is correct and harmless).

### Delivery

- Version bump: `0.4.0-SNAPSHOT` → `0.5.0-SNAPSHOT`
- `sbt folEngineJVM/publishLocal`
- Deliver Phase 2 notification prompt to `register` agent

---

## Phase 1 Usage Prompt (for Register Agent)

```
fol-engine 0.4.0-SNAPSHOT replaces all ValidationError(field=...) wrappers in the
typed pipeline with three dedicated, matchable QueryError subtypes.

New types in fol.error.QueryError:

  BindError(errors: List[String])
    — query failed type-checking at bind time (user error → HTTP 400)

  ModelValidationError(errors: List[String])
    — RuntimeModel.validateAgainst failed at startup (wiring bug → HTTP 500)

  DomainNotFoundError(typeName: String, availableTypes: Set[String])
    — evaluation reached a type with no registered domain (user or wiring error)

Required changes in AppError.scala — replace any existing ValidationError
field-string inspection with:

  case e: QueryError.BindError            => /* 400 user error */
  case e: QueryError.ModelValidationError => /* 500 infra error */
  case e: QueryError.DomainNotFoundError  => FolDomainNotQuantifiable(e.typeName, e.availableTypes)

The old ValidationError(field = "typed_query") and
ValidationError(field = "typed_runtime_model") are no longer emitted.
Dependency: com.risquanter:fol-engine_3:0.4.0-SNAPSHOT
```

---

## Summary Table

| | Phase 1 | Phase 2 |
|---|---|---|
| **What it adds** | `DomainNotFoundError`, `BindError`, `ModelValidationError` | `enumerableTypes` on `TypeCatalog`; bind-time + startup rejection; `renderTypeErrors` update |
| **Error location** | Evaluation time (`DomainNotFoundError`); bind/startup (`BindError`, `ModelValidationError`) | Bind time + startup (evaluation fallback retained) |
| **Register change** | Replace `field`-string checks with 3 typed match arms | None |
| **Breaking** | No | No (default `enumerableTypes = types`) |
| **Version** | `0.4.0-SNAPSHOT` | `0.5.0-SNAPSHOT` |
| **Test delta** | +5 new, 2 updated (868 → 873) | +5–7 |
| **Invariant gained** | All typed-pipeline errors are matchable without string inspection | `validateAgainst` → `Right` ⟹ no `DomainNotFoundError` |
