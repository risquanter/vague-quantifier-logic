# ADR-015 Internal-Consistency Review (Phase 0)

**Date:** 2026-05-02
**Reviewer:** GitHub Copilot (Claude) — session 8e95394b
**Subject:** `docs/ADR-015.md` (2026-05-01 Proposed revision)
**Plan reference:** `docs/PLAN-symmetric-value-boundaries.md` §2 Phase 0
**Verdict:** **PASS with two MINOR observations** (no blocker; Phase 1 may proceed at user's go-ahead).

---

## Pass / Fail by checklist item

### 1. Every Decision §1–§5 has a corresponding row in § Implementation — **PASS**

| Decision | Implementation row(s) |
|---|---|
| §1 `LiteralParser[A]` | `fol/typed/LiteralParser.scala (NEW)` |
| §2 `Extract[A]` | `fol/typed/Extract.scala (NEW)` |
| §3 Single declaration site per (sort, carrier) | covered by "Consumer registration" row (`literalValidators` + `given Extract[A]`) |
| §4 `QueryBinder` named-constant routing | `fol/typed/QueryBinder.scala` row + `BoundQuery.scala` row |
| §5 Boundary ownership | meta-rule reflected across "Library" vs "Consumer registration" rows |

§3 and §5 are governance/discipline statements rather than code units, so their "implementation" is correctly distributed across the rows that realise the discipline. No code surface is unaccounted for.

### 2. Every § Code Smell maps to a positive Decision that prevents it — **PASS (with MINOR-1 below)**

| Code Smell | Preventing Decision |
|---|---|
| Bare `asInstanceOf` at dispatcher | §2 + §3 (`extract[A]` via typeclass; missing instance is a compile error) |
| Per-sort hand-written extract helper | §2 (single library extension) |
| Constructing `Value` directly in lambda | §5 (boundary ownership: framework wraps) — see **MINOR-1** |
| `String => Boolean` validators | §1 (`LiteralParser` parses; validator IS the parser) |
| Two carrier shapes for same sort | §1 + §4 (validator output is the carrier shape used everywhere) |

**MINOR-1:** "Constructing `Value` directly in a function lambda" is prevented only implicitly — by the Implementation-table rows (`MapDispatcher.scala`: `functions: List[Value] => Either[String, Any]`; `RuntimeModel.scala`: `evalFunction returns Either[String, Any]`) which together force the framework to wrap. No Decision §1–§5 names this discipline directly. **Recommendation:** add one sentence to §5 explicitly stating that consumer function lambdas return raw carriers (`Either[String, Any]`) and never construct `Value`. Non-blocking; can be folded into the Phase 6 ADR-acceptance pass.

### 3. § Cross-ADR Relationship clauses consistent with referenced ADRs — **PASS**

- **ADR-001** (compile-time sort correctness at bind time, upstream of runtime carrier check): consistent with `docs/ADR-001.md` (Accepted, 2026-03-27). Sort correctness at bind time is exactly the precondition that makes `Extract[A]` failures structurally impossible for well-formed queries.
- **ADR-013** ("subsumed by §2 of this ADR; mark superseded **when** ADR-015 is Accepted"): the conditional phrasing is correct. ADR-013 currently Accepted (will be transitioned in Phase 6). No premature claim.
- **ADR-006** ("governance shifts to `BoundTerm`, `Formula` once `LiteralValue` is removed"): consistent with `docs/ADR-006.md` (Accepted) and with `BoundQuery.scala` having `BoundTerm` as a sealed enum.
- **ADR-008** (referenced in Alternatives Rejected via the `KnowledgeBase[D]` ambition): consistent — the closed-world rejection is grounded in ADR-008's open-domain `D`.

### 4. § Alternatives Rejected entries remain rejected — **PASS**

Cross-checked against `docs/ADR-015-REVISIT-NOTES.md`:

- Sealed `LiteralValue` enum on injection — REVISIT-NOTES §"Why not keep the enum" reinforces the rejection (T-002 is a structural defect, not a bug).
- `String => Option[Any]` + dispatcher `asInstanceOf` — rejected for cast-site placement; REVISIT-NOTES does not contradict.
- Match-typed `Value[S <: TypeId]` — rejected for runtime-built `TypeCatalog`; no contradiction.
- Closed-world specialisation of `Value` — rejected for ADR-008 contradiction; REVISIT-NOTES §35 explicitly reaffirms `Value.raw: Any` as unavoidable for the open-world consumer constraint.

### 5. `literalValidators: Map[TypeId, String => Option[Any]]` signature consistent across §4, Implementation, Alternatives Rejected — **PASS (with MINOR-2 below)**

- Decision §4 example: `catalog.literalValidators.get(expected).flatMap(_(name))` — implies `String => Option[Any]`. ✓
- Implementation row (`TypeCatalog.scala`): `literalValidators: Map[TypeId, String => Option[Any]]`. ✓ exact match.
- Alternatives Rejected: "`String => Option[Any]` validators with bare `asInstanceOf` at dispatcher" — same outer signature as §4.

The rejected alternative differs from §4 in **mechanism around the signature**, not in the signature itself: §4 pairs the registry with `Extract[A]` so the dispatcher never casts; the rejected alternative omits `Extract[A]` and forces dispatcher-side `asInstanceOf`. The rejection reason "moves the cast site rather than eliminating it" is correct — the cast in §4 lives once at `LiteralParser[A].parse: String => Option[A]` widening to `String => Option[Any]` via subtyping (no `asInstanceOf`); the rejected alternative scatters casts across every dispatcher arm.

**MINOR-2:** the rejection blurb could spell this out more sharply ("the rejected alternative omits `Extract[A]`; §4 pairs the same registry signature with `Extract[A]` so the dispatcher reads through the typeclass and the carrier-side cast is confined to one library-internal subtyping widen at registration"). Reader-clarity improvement; not a logical inconsistency. Defer to Phase 6 ADR pass.

### 6. `BindError.UnparseableConstant` appears in §4 and Implementation; no other new error variants introduced silently — **PASS**

- §4 code block: `Left(BindError.UnparseableConstant(name, expected))`. ✓
- Implementation row (`QueryBinder.scala`): "new `BindError.UnparseableConstant`". ✓
- Full-document grep for `BindError\.` returns only `UnparseableConstant`. No silent siblings.

### 7. § Status Note (Pre-Acceptance) sequencing matches plan phase structure — **PASS**

| Status Note step | Plan phase |
|---|---|
| (1) Internal consistency review | Phase 0 (this document) |
| (2) Code consistency review | Phase 6 (post-implementation, before `Accepted` promotion) |

Sequencing is identical. No phase orphaned, no review unaccounted for.

### 8. References list complete; every referenced file exists — **PASS (with one observation)**

Files verified to exist:
- `docs/ADR-001.md` ✓
- `docs/ADR-008.md` ✓
- `docs/ADR-013.md` ✓
- `docs/ADR-015.backup-2026-05-01.md` ✓
- `docs/ADR-015-REVISIT-NOTES.md` ✓
- `docs/TODOS.md` (T-002 entry) ✓

**Observation (sub-blocker):** ADR-006 is cited in the body (§ Cross-ADR Relationship) but does not appear in the References list. Pure-formatting omission. Add ADR-006 to References in Phase 6.

---

## Summary

| Item | Result |
|---|---|
| 1. Decision ↔ Implementation coverage | PASS |
| 2. Code Smell ↔ Decision coverage | PASS (MINOR-1: §5 could explicitly forbid consumer lambdas constructing `Value`) |
| 3. Cross-ADR clauses consistent | PASS |
| 4. Alternatives still rejected | PASS |
| 5. `literalValidators` signature consistency | PASS (MINOR-2: rejection blurb could be sharper) |
| 6. `BindError.UnparseableConstant` discipline | PASS |
| 7. Status Note ↔ plan sequencing | PASS |
| 8. References list and file existence | PASS (add ADR-006 to References) |

**8/8 pass.** Three minor editorial improvements identified, all suitable for inclusion in the Phase 6 ADR-acceptance pass; none blocks Phase 1.

---

## Phase-1 Readiness Statement

ADR-015 (2026-05-01) is internally consistent. **Phase 1** (`LiteralParser[A]`, `Extract[A]`, library givens, `Value.extract[A]` extension; TDD-first; cross-platform `sbt folEngineJVM/test` + `sbt folEngineJS/test` green) may proceed when the user gives the go-ahead.

---

## Mandatory HARD STOP

🛑 Per `docs/WORKING-INSTRUCTIONS.md` § Mandatory Review Halt and per `docs/PLAN-symmetric-value-boundaries.md` Phase 0 footer, the agent halts here and awaits explicit user continuation. Recommended agent for Phase 1: **Opus 4.7**.
