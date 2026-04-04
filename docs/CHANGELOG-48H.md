# Changelog — 24 March – 4 April 2026

All changes from `30359c4` to HEAD (`979bdd2`).
~150 files changed. **914 JVM+JS tests passing, 0 failures.**

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

`0.5.0-SNAPSHOT` — up from `0.4.0-SNAPSHOT` (bumped for Phase 2: enumerability enforcement — `TypeCatalog.enumerableTypes`, `NonEnumerableType` bind-time rejection, `RuntimeModel.validateAgainst` domain coverage, `MissingDomainForEnumerableType`; `DomainNotFoundError` demoted to defensive fallback).

---

## 9. Enumerability Enforcement — Bind-Time and Model-Validation (Phase 2)

**Session:** 2026-03-28 (this session).

Added static enumerability constraints so that a query quantifying over a type
with no registered domain is rejected as early as possible — at bind time if the
catalog marks the type as non-enumerable, or at model-validation time if the
model omits the required domain. `DomainNotFoundError` is now a defensive
fallback unreachable through the normal pipeline.

### New mechanisms

| Component | Change |
|---|---|
| `TypeCatalog.enumerableTypes: Set[TypeId]` | Subset of `types`; defaults to all types. Types not in this set cannot be quantified over. |
| `TypeCheckError.NonEnumerableType(name: String)` | Bind-time rejection when root or nested quantifier variable resolves to a non-enumerable type. |
| `RuntimeModelError.MissingDomainForEnumerableType(typeName: TypeId)` | Model-validation error when an enumerable type has no registered domain in `RuntimeModel`. |
| `RuntimeModel.validateAgainst` | Extended to check `enumerableTypes.diff(domains.keySet)`. |
| `VagueSemantics.renderTypeErrors` | New `NonEnumerableType` arm. |
| `VagueSemantics.renderModelErrors` | New `MissingDomainForEnumerableType` arm. |
| `QueryError.DomainNotFoundError` scaladoc | Updated: now documented as defensive fallback. |
| ADR-001 | Context §3–4 and Decision §4 clarified: dispatcher misconfiguration vs missing domain elements are distinct concerns. New Code Smell added. |

### Tests added / updated

- `TypeCatalogSpec`: +2 (enumerableTypes subset rejection; valid subset accepted)
- `QueryBinderSpec`: +2 (NonEnumerableType root variable; nested Forall)
- `VagueSemanticsTypedSpec`: 3 tests updated (DomainNotFoundError → ModelValidationError); +1 new (defensive fallback via direct `TypedSemantics.evaluate`)

**Test count after:** 878 (up from 873).

---

## 10. `TypeDecl` ADT — Single-Declaration Domain Type API

**Commit:** `9413297` · **Version:** `0.7.0-SNAPSHOT`

Replaced the dual-parameter `TypeCatalog` construction (`types: Set[TypeId]` + `domainTypes: Option[Set[TypeId]]`) with a single `Set[TypeDecl]`. Each entry is now tagged at the declaration site.

- `TypeDefs.scala`: `sealed trait TypeDecl`; `case class DomainType(typeId: TypeId)`, `case class ValueType(typeId: TypeId)`.
- `TypeCatalog`: `domainTypes` and `typeIds` become derived methods; the `domainTypes ⊆ types` subset check is structurally impossible and removed.
- All `TypeCatalog.unsafe`/`.apply` call sites updated.
- `TypeCatalogSpec`: structurally-impossible test deleted; `domainTypes` derivation test added.
- `ADR-014` updated throughout.

**Test count after:** 880 (up from 878).

---

## 11. `core/` Layout Migration + Quality Fixes

**Commit:** `e094edf` · **Version:** `0.8.0-SNAPSHOT`

Migrated all sources from `src/` to `core/src/` to match the `CrossType.Pure` / `crossProject` convention and eliminate root-aggregate compile-scope collisions.

### Quality fixes (S-1 / S-2 / S-3)

- **S-1 (And/Or/Imp short-circuit):** `TypedSemantics` `And`/`Or`/`Imp` cases now short-circuit on truth value, not only on error — consistent with `Forall`/`Exists`. Unnecessary dispatcher calls eliminated for `And(false,_)`, `Or(true,_)`, `Imp(false,_)`.
- **S-2 (`TypeDecl.id → typeId`):** Renamed throughout for consistency with `TypeRepr[A].typeId`.
- **S-3 (`UnknownType` location field):** `TypeCatalogError.UnknownType` gains `location: String`; every diagnostic names its signature site. One error emitted per site — no cross-site deduplication.

**New tests:** 5 (`TypeCatalogSpec`: location format + per-site emission; `VagueSemanticsTypedSpec`: And/Or/Imp short-circuit via sentinel dispatcher).

**Test count after:** 885 (up from 880).

---

## 12. `MapDispatcher` + ADR-015: Value Type Boundaries

**Commit:** `45ef687`

Introduced a concrete `RuntimeDispatcher` implementation built from plain `Map` keys, closing the intra-dispatcher symbol-set coherence gap.

- `MapDispatcher(predicates, functions)` — map-keyed dispatcher; `evalPredicate`/`evalFunction` delegate to map entries.
- `MapDispatcherSpec` — 20 tests documenting the three `Value.raw` populations and full integration pipeline.
- **ADR-015** (new): _Value Type Boundaries_ — establishes the `LiteralValue` injection boundary (function/literal args) and the `TypeRepr[A]` extraction boundary (query results).
- Removed 5 superseded planning/prompt documents (−1 604 lines).

**Test count after:** 905 (up from 885).

---

## 13. `LiteralValue` Injection Pipeline (ADR-015 §1)

**Commit:** `c1643bb`

Closed the first half of the ADR-015 injection boundary: inline query literals now travel as `LiteralValue` all the way to dispatcher lambdas.

### Parser (P1)

- `TermParser` emits `Term.Const` for all constant tokens (Fix A — intentional deviation from Harrison OCaml).
- `isConstName` extended to accept decimal literals (e.g. `0.05`).
- `VagueQueryParser` adds `mergeDecimalTokens` post-processor so `0.05` arrives as one token in all formula positions; dead three-token tolerance clause removed.

### Typed pipeline (P2)

- `TypeDefs`: `sealed trait LiteralValue` / `IntLiteral` / `FloatLiteral` / `TextLiteral`.
- `TypeCatalog`: `literalValidators: Map[TypeId, String => Option[LiteralValue]]` — sort-keyed literal parsers registered at catalog construction.
- `BoundQuery.ConstRef`: `name` renamed `sourceText`; `raw: LiteralValue` field added.
- `QueryBinder`: extracts `LiteralValue` from the validator; stores in `ConstRef.raw`.
- `TypedSemantics`: passes `ConstRef.raw` directly as `Value.raw`.
- `TypeCatalogSpec`, `QueryBinderSpec`, `MapDispatcherSpec`: updated to `Option[LiteralValue]` validator pattern.
- Parser specs (`TermParserSpec`, `FOLAtomParserSpec`, `FOLParserSpec`): numeric literal expectations updated from `Fn(n, Nil)` to `Const(n)`.

### Documentation (P3)

- `ADR-001`: `BoundTerm.ConstRef` IL shape updated; cross-reference to ADR-015 added.
- `TODOS.md`: T-002 (named constants design gap) and T-003 (typed literal pipeline normaliser) added.

**Test count after:** 905 (unchanged — new tests offset removed dead ones).

---

## 14. ADR-006 Enum Encoding + Parser Bug Fixes

**Commit:** `9643648`

Applied the ADR-006 ADT encoding convention (`enum` for pure-data sum types; `sealed trait` for behavioural hierarchies) to the newly introduced types, and fixed a latent parser regex bug.

### Enum conversions

- `TypeDecl`: `sealed trait` + top-level case classes → `enum TypeDecl` with derived `def typeId`.
- `LiteralValue`: `sealed trait` + top-level cases → `enum LiteralValue`.
- `import TypeDecl.*` / `import LiteralValue.*` added to all affected sources and tests.
- `ADR-014` and `ADR-015` implementation tables updated to reflect `enum` encoding.

### Parser fixes

- `isConstName` regex was `\d+\.\d+` (literal backslashes — never matched decimals). Fixed by delegating to new `StringUtil.isDecimalLiteral` with correct `\d+\.\d+` pattern.
- `StringUtil.isDecimalLiteral` extracted and shared by `TermParser` and `VagueQueryParser`, eliminating the divergence that caused the bug.
- `isConstName` made `private` (no external callers).
- `TextLiteral` scaladoc documents its T-002 stopgap nature.
- `asInstanceOf[IntLiteral]` in `MapDispatcherSpec` replaced with a sealed match.

**New test:** `TermParserSpec` — parse constant (decimal, pre-merged token).

**Test count after:** 906 (up from 905).

---

## 15. `FolModel` — Validated Catalog/Model Pairing

**Commit:** `3f7ed4d`

Introduced `FolModel` as a smart constructor that validates dispatcher coverage and domain registration once at construction time, eliminating per-query re-validation inside `evaluateTyped`.

- `FolModel.scala`: `apply` only — runs `RuntimeModel.validateAgainst(catalog)`; returns `Either[QueryError, FolModel]`.
- `RuntimeModelError.message`: rendering logic moved into the error type.
- `VagueSemantics.evaluateTyped`: new `FolModel` overload is the canonical signature; `(catalog, model)` pair overload removed.
- `VagueSemanticsTypedSpec` and `MapDispatcherSpec`: all call sites adapted to `FolModel`; bad-path tests assert on `FolModel` construction directly.

**Test count after:** 906 (unchanged — refactor only).

---

## 16. Function Return Normalisation

**Commits:** `f19c881` (feature) · `c5774f2` (post-impl review)

Closed the second half of the ADR-015 injection boundary: function return values now arrive at downstream dispatcher lambdas as `LiteralValue`, not as consumer-constructed `Value` with opaque `raw` types.

### Interface change

- `RuntimeDispatcher.evalFunction`: `Either[String, Value]` → `Either[String, LiteralValue]`.
- `TypedSemantics.evalTerm` `FnApp` branch: framework constructs `Value(resultSort, literalResult)` — sort correctness check on return value removed (enforced statically by the `FunctionReturnIsDomainType` catalog guard).

### New components

- `TypedFunctionImpl.of[A]`: ergonomic combinator — consumer declares `impl: List[Value] => Either[String, A]` and `wrap: A => LiteralValue`; framework handles `LiteralValue` construction at every call site.
- `TypeCatalog.FunctionReturnIsDomainType`: new `collectErrors` guard rejects functions declared with a `DomainType` return sort at construction time (T-004 tracks the `EntityRef`/domain-returning-function deferred gap).
- `MapDispatcher.functions` field type updated to `List[Value] => Either[String, LiteralValue]`.

### Cleanup

- `rawToDouble` helper in `MapDispatcherSpec` deleted; `gt_prob` lambda simplified to a direct `FloatLiteral` match (both args uniformly `FloatLiteral` after pipeline normalisation).
- `VagueSemanticsTypedSpec`: all anonymous `RuntimeDispatcher` `evalFunction` overrides updated.
- `ADR-015`: implementation table updated; new code smell for raw `Value` construction inside dispatcher lambdas.
- `TypedFunctionImplSpec`: 5 tests (Right path for Double/Long/String; Left short-circuit; args forwarding).

**New tests:** 8 (`f19c881`: +2 `FunctionReturnIsDomainType`; `c5774f2`: +5 `TypedFunctionImplSpec`, +1 restored `ADR-015` code smell section).

**Test count after:** 913 (up from 906).

---

## 17. Parser/Interpreter Review Refactors

**Commit:** `979bdd2`

Address findings from a targeted parser/interpreter code review. All changes are non-functional refactors and package reorganisations.

### M-1 — `BoundTerm.sort` dead branch eliminated (Option A)

`ConstRef`'s second parameter was named `sort`, auto-generating a `val sort` that shadowed the parent `def sort` match body — leaving the `ConstRef` branch dead. Fixed by renaming: `ConstRef(sourceText, typeId, raw)` and `FnApp(name, args, resultSort)`. `def sort` now has three live branches. Zero call-site impact (all positional).

### M-2 — `ParseFailure` sentinel

Added `class ParseFailure(msg: String) extends Exception(msg)` to `Combinators` as the OCaml `Failure _` analogue. All parser throw sites that signal a recoverable parse miss now throw `ParseFailure`; catch sites narrowed from `_: Exception` to `_: ParseFailure`. `Combinators.bracketed` and the `FormulaParserSpec.parseNoInfix` test stub both updated.

### S-2 — `@tailrec` Forall/Exists

`TypedSemantics.evalFormula` `Forall`/`Exists` branches replaced with `@tailrec` `allOf`/`anyOf` helpers for true short-circuit without stack accumulation.

### S-5 — `Term.const` removed

`Term.const` (an alias for `Fn(name, Nil)`) deleted; `Term.example` migrated to `Term.Const`; `TermSpec` updated with two distinct tests: inline literal constant and zero-arity function application.

### C-1 / C-2 — Package moves

- `EvaluationContext` moved from `semantics` to `fol.semantics` package; old file deleted.
- `Quantifier` moved from `fol.logic` to `fol.quantifier` package; old file deleted.
- `VagueQuantifier` de-aliased (`LogicQuantifier` alias removed).
- All import sites updated (~15 files).

### C-4 — `QueryError` comment

Explicit scaladoc on `QueryError` sealed trait explaining why `sealed trait` is used over `enum` (per-variant `formatted`/`context` body logic).

**Test count after:** 914 (up from 913).
