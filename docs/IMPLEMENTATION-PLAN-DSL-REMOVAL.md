# Implementation Plan: DSL Removal

**Date:** 2026-03-25
**Status:** Draft — decisions resolved, pending implementation
**Predecessor commits:** `8ef6374` (path convergence), `fe0c9d6` (Phase 6)

---

## Motivation

The typed DSL (`Query` builder, `UnresolvedQuery`, `DomainSpec`, `Predicates`)
provides a fluent API over the shared IL (`ResolvedQuery`).  Its added value
is cosmetic — the IL already carries the same type parameter `D`, the same
error semantics, and the same evaluation pipeline.

The DSL has cost two convergence incidents (empty-range divergence,
unknown-relation divergence) and requires every semantic decision to be
applied to two entry points.  The only identified use case — programmatic
hard-coded queries — is equally served by `ResolvedQuery` with a thin
factory method.

Removing the DSL eliminates a maintenance surface, closes TD-002
(ActiveDomain semantic smell), and simplifies TD-001 (reduces duplicated
`hasRelation` boundary checks from two call sites to one).

---

## Phase 1: Add `ResolvedQuery.fromRelation` Factory

Add a factory method on `ResolvedQuery` companion that replaces the
DSL's `resolve()` — fetches domain from `KnowledgeSource`, performs
`hasRelation` check, returns `Either[QueryError, ResolvedQuery[D]]`.

```scala
object ResolvedQuery:
  def fromRelation[D: ClassTag](
    source: KnowledgeSource[D],
    relationName: RelationName,
    position: Int,
    quantifier: VagueQuantifier,
    predicate: D => Boolean,
    params: SamplingParams = SamplingParams.exact,
    hdrConfig: HDRConfig = HDRConfig.default
  ): Either[QueryError, ResolvedQuery[D]]
```

**Decision (resolved):** No `fromActiveDomain` factory.  `ActiveDomain`
is deleted with the DSL.  `activeDomain` as a concept is a schema-dependent
union of all stored entities — any legitimate use case is better served by
an explicit unary relation (e.g. `server("serverA")`) queried via
`fromRelation`.  The `KnowledgeSource.activeDomain` method remains in the
core API (used by FOL evaluation and `KnowledgeSourceModel`), but no
programmatic entry point encourages its use as a quantification domain.

**Tests:** Add tests to `ResolvedQuerySpec` covering:
- `fromRelation` with valid relation → `Right`
- `fromRelation` with unknown relation → `Left(RelationNotFoundError)`
- `fromRelation` with empty-but-existing relation → `Right` (vacuously-false)

---

## Phase 2: Translate DSL Tests to IL

Translate test use cases that exercise meaningful behavior to use the
IL (`ResolvedQuery` directly or `ResolvedQuery.fromRelation`).  Tests
that only exercise DSL builder mechanics (fluent chaining, `whereConst`
unwrapping) are dropped — they test code that will be deleted.

### QueryDSLSpec.scala (57 tests)

| Category | Count | Action |
|----------|-------|--------|
| Builder mechanics (`where`, `whereConst`, `satisfying`, `over`, `overActiveDomain`) | 9 | **Drop.** Test deleted code. |
| Quantifier threshold tests (`most`, `many`, etc.) | 14 | **Drop.** Already covered by `VagueQuantifierSpec` (14 tests for the same thresholds). |
| `whereConst` unwrapping | 2 | **Drop.** Tests `RelationValue.Const` pattern match in deleted code. |
| Sampling params propagation | 3 | **Drop.** Tests builder wiring only. Sampling is tested in `SamplingSpec`, `HDRSamplerSpec`. |
| `Predicates.*` tests (standalone predicate checks) | 6 | **Translate** to a new `PredicateHelpersSpec` or merge into `ResolvedQuerySpec`. The predicate functions (`inRelation`, `hasRelation`, `relatedTo`) are useful standalone — they should move to a utility object (see Phase 3). |
| `Predicates.*` as DSL predicate (full chain) | 4 | **Translate** to `ResolvedQuery.fromRelation` + predicate helpers. |
| `source.execute` / `source.executeWithOutput` extensions | 4 | **Drop.** Extensions on deleted types. |
| Error handling (nonexistent relation) | 3 | **Drop.** Covered by `UnresolvedQuerySpec` translations and `ResolvedQuerySpec.fromRelation` tests. |
| Integration (evaluate, evaluateWithOutput, consistency, statistics) | 12 | **Translate** key coverage gaps to `ResolvedQuerySpec`. Most are already covered; translate: predicate-with-closure, combining-predicates, negated-predicate, multiple-queries-over-same-source. |

### UnresolvedQuerySpec.scala (20 tests)

| Category | Count | Action |
|----------|-------|--------|
| Default params | 2 | **Drop.** Tests builder defaults. |
| `resolve` → `ResolvedQuery` | 3 | **Translate** to `ResolvedQuery.fromRelation` tests (Phase 1). |
| `evaluate` / `evaluateWithOutput` / error handling | 6 | **Drop.** Thin delegations to `resolve().map(_.evaluate())` — the IL half is tested by `ResolvedQuerySpec`. |
| `source.execute` / `source.executeWithOutput` / consistency | 6 | **Drop.** Extension methods on deleted types. |
| Builder DSL (`whereConst`, `where`, `overActiveDomain`) | 3 | **Drop.** Tests deleted code. |

### SymmetricRelationIntegrationSpec.scala (8 tests)

| Test | Uses DSL? | Action |
|------|-----------|--------|
| `Predicates.relatedTo works in BOTH directions on symmetric relation` | Yes (`Predicates`) | **Translate** to predicate helper + `ResolvedQuery.fromRelation`. |
| `Predicates.hasRelation works symmetrically` | Yes (`Predicates`) | **Translate** — same approach. |
| `DSL query: 'Most countries border France' — symmetric makes reverse visible` | Yes (full DSL) | **Translate** to `ResolvedQuery.fromRelation` with predicate helper. |
| `DSL query: executeWithOutput shows symmetric satisfying sets` | Yes (full DSL) | **Translate** — same approach. |
| `toModel: symmetric relation evaluates true in both argument orders` | No | **Keep.** |
| `toModel: non-existent symmetric pair evaluates false` | No | **Keep.** |
| `toModel: existential over symmetric relation finds both directions` | No | **Keep.** |
| `DSL and FOL produce consistent results for symmetric queries` | Yes (compares DSL and FOL) | **Translate** to IL ↔ FOL consistency test (same approach, use `fromRelation` instead of DSL). |

**Net test count change:** ~82 tests removed, ~20 translated.  Remaining
coverage is provided by existing `ResolvedQuerySpec` (9), `VagueQuantifierSpec`,
`SamplingSpec`, `VagueSemanticsSpec`, and `RangeExtractorSpec`.

---

## Phase 3: ~~Relocate Predicate Helpers~~ — Drop

`Predicates.inRelation`, `Predicates.hasRelation`, `Predicates.relatedTo`
are deleted with the DSL.  They are one-line wrappers over
`KnowledgeSource.query` / `.contains` pattern-match calls.  Callers
write inline closures:

```scala
// inRelation(source, "critical") becomes:
d => source.contains(RelationName("critical"), RelationTuple(List(d)))

// relatedTo(source, "borders", "France") becomes:
d => source.query(RelationName("borders"), List(Some(d), Some("France"))).nonEmpty
```

**Decision (resolved):** Drop entirely.  No migration.

---

## Phase 4: Delete DSL Code

| File | Action | Lines removed |
|------|--------|---------------|
| `src/main/scala/fol/query/Query.scala` | **Delete** | ~288 |
| `src/main/scala/examples/VagueQueryPlayground.scala` | **Delete** | ~348 |
| `src/test/scala/fol/query/QueryDSLSpec.scala` | **Delete** | ~798 |
| `src/test/scala/fol/query/UnresolvedQuerySpec.scala` | **Delete** | ~253 |

| File | Action | Change |
|------|--------|--------|
| `src/main/scala/fol/query/ResolvedQuery.scala` | **Modify** | Remove comment referencing `UnresolvedQuery` (line 12) |
| `src/main/scala/fol/semantics/DomainExtraction.scala` | **Modify** | Remove comment referencing `DomainSpec.ActiveDomain` |
| `src/test/scala/fol/datastore/SymmetricRelationIntegrationSpec.scala` | **Modify** | Replace 5 DSL tests with IL equivalents (Phase 2) |

---

## Phase 5: Update Documentation

### ADRs

| ADR | Change |
|-----|--------|
| **ADR-001** | Significant update.  Phase 1 diagram becomes single-entry (string → `ParsedQuery` → `ResolvedQuery`).  Remove `UnresolvedQuery` from type flow table, call chain, naming policy.  Domain resolution semantics: remove `UnresolvedQuery.resolve()` reference — `RangeExtractor.extractRange()` is the sole FOL-path boundary; `ResolvedQuery.fromRelation()` is the programmatic boundary.  Implementation table: remove `UnresolvedQuery.resolve(source)` row. |
| **ADR-004** | Minor.  Remove `UnresolvedQuery` from package map. |
| **ADR-008** | Moderate.  Remove backward-compatibility DSL section.  Simplify `ClassTag` propagation note — only `ResolvedQuery[D]` needs it. |
| **ADR-009** | Moderate.  Remove DSL from integration spec table and downstream-changes list. |
| **ADR-010** | Moderate.  Remove DSL examples from §3 convenience boundaries; remove `Query.scala` from implementation table. |

### Other Docs

| Document | Change |
|----------|--------|
| `Architecture.md` | Remove `UnresolvedQuery` from package map and layer diagram.  "Two Entry Points" becomes single-entry for the typed path.  The programmatic entry is now `ResolvedQuery.fromRelation`. |
| `IMPLEMENTATION_PLAN.md` | Remove "Completes the DSL experience" line.  Add note that DSL was removed in favour of IL-direct construction. |
| `DRAFT-IMPLEMENTATION-PLAN-DOMAIN-TYPE-SAFETY.md` | Add historical note: "Phase 5b (generic `UnresolvedQuery[D]`) was implemented but subsequently removed — see ADR-0XX." |

### New or Updated ADR

No existing ADR is dedicated to "why a DSL exists."  ADR-001 describes the
two-path architecture including the DSL as one path, but its primary concern
is evaluation-path unification / shared IL, not whether the DSL should exist.

**Action:** Create **ADR-011** documenting:
- Decision: remove DSL in favour of IL-direct construction
- Context: marginal benefit over `ResolvedQuery`, recurring convergence cost,
  semantic surface mismatch (`ActiveDomain`)
- The programmatic entry point is now `ResolvedQuery.fromRelation`
- ADR-001 remains the authoritative document for path unification; this ADR
  records why the DSL path was retired

---

## Phase 6: Update TECHNICAL-DEBT.md

| Item | Change |
|------|--------|
| **TD-001** | Downgrade.  With `UnresolvedQuery.resolve()` deleted, there is only one boundary check site (`RangeExtractor.extractRange()`).  Plus `ResolvedQuery.fromRelation` adds a second, but both are on `ResolvedQuery`'s companion — co-located.  The "duplication" concern is reduced.  Update description to reflect current state. |
| **TD-002** | **Close.**  `DomainSpec.ActiveDomain` deleted with the DSL.  No replacement factory. |

---

## Execution Order

1. Phase 1 — `fromRelation` factory (no `fromActiveDomain`)
2. Phase 2 — Translate tests
3. Phase 3 — Drop predicate helpers (deleted with DSL)
4. Phase 4 — Delete DSL code
5. Phase 5 — Update docs and ADRs
6. Phase 6 — Update TECHNICAL-DEBT.md
7. Compile and run all tests
8. Commit

---

## Decision Points (all resolved)

1. **`fromActiveDomain`:** ~~Include factory~~ → **No.  Delete ActiveDomain
   entirely.**  Schema-dependent union; use explicit unary relations instead.
2. **Predicate helpers:** ~~Relocate~~ → **Drop.**  One-line wrappers over
   `KnowledgeSource` methods; callers write inline closures.
3. **ADR numbering:** **ADR-011.**

---

## Risk

- **Test coverage gap:** The DSL tests exercise the full stack
  (KnowledgeSource → domain fetch → predicate application → ResolvedQuery →
  evaluate).  After removal, this integration is covered by
  `VagueSemanticsSpec` (FOL path) and the new `fromRelation` tests.
  The `Predicates.*` functions are the main coverage gap if dropped.
- **External consumers:** If any downstream code imports `Query.*` or
  `UnresolvedQuery`, it breaks.  Currently no known external consumers.
