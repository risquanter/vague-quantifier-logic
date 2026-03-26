# Changelog — 24–26 March 2026

All changes from `30359c4` to `d4c285e` (HEAD).
67 files changed, +4333 / −2607 lines. 854 tests passing.

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

**New ADR:** ADR-011 (DSL Removal).

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

## Version

`0.2.0-SNAPSHOT` — up from `0.1.0-SNAPSHOT` (bumped during Phase 6
due to breaking API changes from the generic datastore migration).
