# Changelog — 24–28 March 2026

All changes from `30359c4` to HEAD (`09815e7` committed; `fol.typed` package staged).
~100 files changed. **873 JVM tests passing, 0 failures.**

---

## 1. Generic `KnowledgeBase[D]` (Phases 1–6)

**Commits:** `4f45663` → `fe0c9d6`

Replaced the stringly-typed `DomainValue` with a fully generic
datastore and evaluation pipeline.

- **Phase 1** (`4f45663`): `DomainElement` and `DomainCodec` type
  classes — bridges between user domain types and the FOL layer.
- **Phase 2** (`1f755bd`): `KnowledgeBase[D]`, `KnowledgeSource[D]`,
  `RelationTuple[D]` — generic datastore layer.
- **Phase 3** (`92b378d`): Generic bridge layer. Decomposed
  `NumericAugmenter` into `ArithmeticAugmenter`, `ComparisonAugmenter`,
  `LiteralResolver`.
- **Phase 4+5** (`694eba7`): Generic evaluation pipeline and query
  types. `ResolvedQuery`, `ScopeEvaluator`, `RangeExtractor`,
  `VagueSemantics` all parameterised on `[D]`.
- **Phase 6** (`fe0c9d6`): `RelationName` opaque type. Symmetric
  relation support. Dead code removal (`RelationValueUtil`,
  `RelationValueUtilSpec`).

**New ADRs:** ADR-007 (Generic KnowledgeBase), ADR-008 (DomainCodec),
ADR-009 (Bridge Decomposition), ADR-010 (RelationName Opaque Type).

**New test suites:** `DomainElementSpec`, `SymmetricRelationSpec`,
`SymmetricRelationIntegrationSpec`.

## 2. Path Convergence

**Commit:** `8ef6374`

Unified domain resolution semantics — `getDomain` and `activeDomain`
now agree on which values belong to a relation's positional domain.

## 3. DSL Removal

**Commits:** `3307423`, `697ae35`

Deleted the typed Query DSL (`Query.scala`, `VagueQueryPlayground.scala`,
`UnresolvedQuerySpec.scala`). Added `ResolvedQuery.fromRelation` factory
as the sole entry point for programmatic query construction.

**New docs:** `IMPLEMENTATION-PLAN-DSL-REMOVAL.md`,
`PROMPT-CODE-QUALITY-REVIEW.md`, `TECHNICAL-DEBT.md`,
`WORKING-INSTRUCTIONS.md`.

## 4. Cross-Compilation (JVM + ScalaJS)

**Commit:** `d4c285e`

Published `fol-engine` as a cross-compiled artifact so
`register/modules/common` can use `VagueQueryParser.parse()` on the
JS side.

- Created `project/plugins.sbt` — sbt-scalajs 1.20.0,
  sbt-scalajs-crossproject 1.3.2.
- Converted `build.sbt` to `crossProject(JVMPlatform, JSPlatform)`
  with `CrossType.Pure` and `.in(file("."))`. No source directory moves.
- `%%` → `%%%` for cross-compiled dependency resolution.
- Removed dead `sampleDomain` / `sampleActiveDomain` methods and
  `SQLKnowledgeSourceGuide` from `KnowledgeSource.scala`
  (`scala.util.Random`).
- Removed `Random`-based sampling block from `VagueSemanticsDemo.scala`.

**Artifacts:** `fol-engine_3` (JVM), `fol-engine_sjs1_3` (JS) —
both at `0.2.0-SNAPSHOT`.

---

## 5. TD-001: Either Migration — `KnowledgeBase[D]` and `KnowledgeSource[D]`

**Commit:** `09815e7`

Full `Either[QueryError, A]` migration for all state-dependent KB/KS
methods, implementing ADR-012 Option A.

- `KnowledgeBase`: `getDomain`, `addRelation`, `addFact`, `addFacts`,
  `contains`, `query`, `count` — all return `Either[QueryError, A]`.
- `KnowledgeSource` trait + `InMemoryKnowledgeSource`: `contains`,
  `getDomain`, `query`, `count` — same.
- `query` pre-validates pattern length against declared relation arity.
- `DomainExtraction`: 5 methods converted to `Either`.
- `RangeExtractor`: removed `hasRelation` guards; `extractRangeUnsafe`
  unwraps at OCaml-style boundary (ADR-007).
- `VagueSemantics.toResolved`: returns `Either`, trampoline
  (`Left → throw → catch → Left`) eliminated.
- New `PositionOutOfBoundsError` variant added to `QueryError`.
- 8 test specs updated; 3 new `PositionOutOfBoundsError` tests added.

**Test count after:** 857 (up from 854).

## 6. Many-Sorted Type System — `fol.typed` Package

**Status:** Implemented, staged (uncommitted).

Adds a mandatory bind/typecheck phase and sorted runtime execution
layer. Directly unblocks register's query-pane integration. See
ADR-001 (many-sorted query binding) and ADR-013 (typed result
projection).

### New source files (`src/main/scala/fol/typed/`)

| File | Purpose |
|---|---|
| `TypeDefs.scala` | `TypeId`, `SymbolName` opaque types; `TypeRepr[A]` trait |
| `TypeCatalog.scala` | Sort catalog — private constructor, `apply` returns `Either`, `unsafe` for tests |
| `BoundQuery.scala` | Typed IL: `BoundQuery`, `BoundVar`, `BoundTerm`, `BoundFormula` |
| `QueryBinder.scala` | `ParsedQuery → Either[List[TypeCheckError], BoundQuery]` |
| `TypeCheckError.scala` | Error ADT: `UnknownPredicate`, `UnknownFunction`, `ArityMismatch`, `UnknownConstantOrLiteral`, `TypeMismatch`, `UnboundAnswerVar`, `UnconstrainedVar`, `ConflictingTypes` |
| `RuntimeModelError.scala` | Error ADT for dispatcher coverage failures: `MissingFunctionImplementation`, `MissingPredicateImplementation` |
| `RuntimeModel.scala` | `Value(sort, raw)`, `RuntimeDispatcher`, `RuntimeModel`, `Value.as[A]` |
| `TypedSemantics.scala` | Evaluator over `BoundQuery` + `RuntimeModel` |

### Facade

`VagueSemantics.bindTyped` and `VagueSemantics.evaluateTyped` — public
entry points. `evaluateTyped` is the canonical sorted evaluation path:
`parse → bind → TypedSemantics.evaluate`.

### What this enables for register

| register need | Provided by |
|---|---|
| `Loss (Long)` sort without Int overflow | `Value(sort = TypeId("Loss"), raw: Long)` backed by dispatcher |
| `Probability (Double)` sort | `Value(sort = TypeId("Probability"), raw: Double)` backed by dispatcher |
| Decimal literals (`0.05`) | Binder resolves by argument-position sort from `TypeCatalog` |
| Sort-specific comparators (`gt_loss`, `gt_prob`) | `RuntimeDispatcher.evalPredicate` — native Long/Double comparison |
| Startup type-safety | `RuntimeModel.validateAgainst(catalog)` — coverage + arity check |

`RelationValue` and the existing `D`-parameterised pipeline are
unchanged and continue to back all 868 existing tests.

### New test files

- `fol/typed/TypeCatalogSpec.scala`
- `fol/typed/QueryBinderSpec.scala`
- `fol/semantics/VagueSemanticsTypedSpec.scala`

**Test count after:** 868 (up from 857).

## 7. Documentation Cleanup

**Commits:** `56aaad9` + this session (uncommitted).

- Deleted stale implementation plans, draft proposals, and historical
  prompt docs (−2606 lines).
- Added ADR-012, Architecture.md ADR table, TECHNICAL-DEBT.md expansion.
- ADR consolidation: ADR-001 replaced with many-sorted binding content;
  ADR-013 added for typed result projection; ADR-006 (superseded) and
  ADR-011 (DSL-removal changelog) deleted.
- ADR-008 stripped of commit-hash changelog content; architecture
  content retained.
- Dangling ADR-006 / ADR-011 references removed from Architecture.md,
  TECHNICAL-DEBT.md, MULTI-SORTED-TYPE-SYSTEM-V2.md,
  CANONICAL-RUNTIME-COMPLIANCE-REPORT-2026-03-26.md, CHANGELOG-48H.md.

---

## 8. Structured Typed-Pipeline Error Hierarchy (Phase 1)

**Session:** 2026-03-28 (this session, uncommitted).

Replaced stringly-typed `ValidationError(field = ...)` wrappers in the typed
pipeline with dedicated, matchable `QueryError` subtypes. Callers can now
pattern-match error categories without inspecting string fields.

### New error types in `fol/error/QueryError.scala`

| Type | Semantics | HTTP status intent |
|---|---|---|
| `BindError(errors: List[String])` | Query failed typed bind phase | 400 user error |
| `ModelValidationError(errors: List[String])` | `validateAgainst` failed — dispatcher/domain coverage gap | 500 infra error |
| `DomainNotFoundError(typeName, availableTypes)` | Evaluation reached a type with no registered domain | 400/500 |

Field types are `String`/`Set[String]` (not `TypeId`) due to `fol.error` → `fol.typed`
circular package constraint — same convention as `UninterpretedSymbolError`.

### Changed sites

- `VagueSemantics.bindTyped` — `ValidationError(field = "typed_query")` → `BindError`
- `VagueSemantics.evaluateTyped` — `ValidationError(field = "typed_runtime_model")` → `ModelValidationError`
- `TypedSemantics.evaluate` (Site 1) — `EvaluationError("No runtime domain …")` → `DomainNotFoundError`
- `TypedSemantics.evalFormula` Forall (Site 2) — same
- `TypedSemantics.evalFormula` Exists (Site 3) — same
- `VagueSemantics.renderTypeErrors` — return type `String` → `List[String]`
- `VagueSemantics.renderModelErrors` — new private helper, extracted from inline map

### Tests updated / added

- `VagueSemanticsSpec`: updated "bindTyped maps type-check errors" to expect `BindError`
- `VagueSemanticsTypedSpec`: updated "evaluateTyped returns error when model missing predicate" to expect `ModelValidationError`
- `VagueSemanticsTypedSpec`: +5 new tests — `BindError` (bindTyped + evaluateTyped paths), `DomainNotFoundError` (root, Forall, Exists)

**Test count after:** 873 (up from 868).

---

`0.4.0-SNAPSHOT` — up from `0.3.0-SNAPSHOT` (bumped for structured error hierarchy: `BindError`, `ModelValidationError`, `DomainNotFoundError`).
