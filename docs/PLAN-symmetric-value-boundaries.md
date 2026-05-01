# Implementation Plan: Symmetric `Value` Boundaries (ADR-015 refactor)

**Status:** Draft — awaiting user review (per `docs/WORKING-INSTRUCTIONS.md`
§ "Mandatory Review Halt").
**Date:** 2026-05-01
**Parent ADRs:** [ADR-015](ADR-015.md) (Proposed; under refactor),
[ADR-001](ADR-001.md), [ADR-008](ADR-008.md),
[ADR-013](ADR-013.md) (to be superseded), [ADR-014](ADR-014.md)
**Related TODOs:** `docs/TODOS.md` T-002 (named-constants gap),
T-003 (typed function-return pipeline)
**Depends on:** ADR-015 §1–§5 design (already merged in commit
`25228ed`); decision sheet in `docs/ADR-015-REVISIT-NOTES.md`.
**Downstream consumer:** `register/docs/PLAN-QUERY-NODE-NAME-LITERALS.md`
Phases 5b–7 are blocked on Phases 0–6 of this plan (B1
`BinderIntegrationSpec` is `@@ TestAspect.ignore` until Phase 6 completes
and ADR-015 is promoted to `Accepted`).

---

## 0. Executive Summary

ADR-015 (2026-05-01 revision) redesigns the `Value` boundary into a pair of
symmetric typeclasses — `LiteralParser[A]` for the injection side,
`Extract[A]` for the extraction side — both routed through a single
`literalValidators: Map[TypeId, String => Option[Any]]` registry. This
plan executes that ADR in seven phases:

| Phase | Output | Agent | Halt |
|---|---|---|---|
| 0 | ADR-015 internal-consistency review (pass/fail) | **Opus 4.7** | HARD STOP |
| 1 | `LiteralParser[A]`, `Extract[A]`, library givens, `Value.extract[A]` | **Opus 4.7** | HARD STOP |
| 2 | `TypeCatalog.literalValidators: Map[TypeId, String => Option[Any]]` | **Opus 4.7** | HARD STOP |
| 3 | `QueryBinder` named-constant branch routes through validator (closes T-002) | **Opus 4.7** | HARD STOP |
| 4 | Threading `Any` through `TypedSemantics` / `MapDispatcher` / `TypedFunctionImpl` | **Opus 4.7** | HARD STOP |
| 5 | Removal of `LiteralValue` enum and `TypeRepr`; full test green | **Opus 4.7** | HARD STOP |
| 6 | Artifact rename (`fol-engine` → `vql-engine`), publish, code-consistency review, ADR-015 → `Accepted` | **Opus 4.7** | HARD STOP |

> ⚠️ **Per WORKING-INSTRUCTIONS § Mandatory Review Halt**, after every
> phase the agent halts and waits for explicit user continuation. The
> "Recommended agent for next step" annotation is advisory: the user
> remains the gate, and the agent must not auto-advance even when the
> recommendation is the same as the agent currently executing.

> ⚠️ **Per ADR-015 § Status Note (Pre-Acceptance)**, ADR-015 does not
> govern until both Phase 0 *and* Phase 6 reviews pass. During Phases
> 1–5 the ADR is the design target, not a binding constraint;
> deviations discovered during implementation must be reflected back
> into ADR-015 in Phase 6.

---

## 1. Scope and Constraints

**In scope:**
- All changes to `core/src/main/scala/fol/typed/**` required by ADR-015.
- New `LiteralParser` / `Extract` typeclass surface and library givens.
- `TypeCatalog` builder signature change.
- `QueryBinder` named-constant branch rewrite.
- `TypedSemantics` / `MapDispatcher` / `TypedFunctionImpl` `Any`-threading.
- Removal of `LiteralValue` and `TypeRepr` (Phase 5).
- Artifact rename and publish (Phase 6).
- Cross-platform: every phase must pass `sbt folEngineJVM/test` *and*
  `sbt folEngineJS/test`.

**Out of scope:**
- Scala-package rename (`fol.*` → `vql.*`). Tracked separately in
  `docs/TODOS.md` (added by this plan) as a follow-up after Phase 6.
- Lexer / parser changes (those land via the register-side
  PLAN-QUERY-NODE-NAME-LITERALS Phases 1–2, which are independent).
- Any change to `Term`, `Formula`, or quantifier surface.
- Any change to the published `0.10.0-SNAPSHOT` version string. The
  artifact name changes in Phase 6; the version does not.

**Hard constraints:**
- Every phase ends with `sbt test` green on both axes.
- No `asInstanceOf` outside `Extract[A]` given instances and the
  internal `LiteralParser[A]` adapter (ADR-015 § Code Smells).
- Every public API change is accompanied by a TDD-first test commit.
- Every signed commit (`git commit -S`) at phase boundaries.

---

## 2. Phase 0 — ADR-015 Internal-Consistency Review

**Goal:** Verify ADR-015 (2026-05-01) is internally consistent before
any code changes. If gaps or contradictions are found, the ADR is
revised first; this plan is rebased afterwards.

**Inputs:**
- `docs/ADR-015.md` (current `Proposed` revision)
- `docs/ADR-015-REVISIT-NOTES.md` (design rationale)
- `docs/TODOS.md` T-002, T-003

**Pass-fail checklist:**

1. Every Decision §1–§5 has a corresponding row in § Implementation.
2. Every § Code Smell maps to a positive Decision that prevents it.
3. Every § Cross-ADR Relationship clause is consistent with the
   referenced ADRs in their current state (ADR-001, ADR-006, ADR-008,
   ADR-013).
4. Every § Alternatives Rejected entry remains rejected for the reason
   stated (no new evidence in REVISIT-NOTES contradicts it).
5. The `literalValidators: Map[TypeId, String => Option[Any]]`
   signature is consistent across § Decision §4, § Implementation, and
   § Alternatives Rejected (in particular: the rejected
   `String => Option[Any] + asInstanceOf` alternative differs from §4
   *only* in where the cast happens, and the rejection reason names
   that difference correctly).
6. `BindError.UnparseableConstant` appears in § Decision §4 and §
   Implementation; no other new error variants are introduced silently.
7. § Status Note (Pre-Acceptance) sequencing matches the phase
   structure in this plan (Phase 0 = internal review, Phase 6 = code
   review + Accepted promotion).
8. References list is complete and every referenced file exists.

**Deliverable:** A short `docs/scratch/ADR-015-consistency-review-2026-05-XX.md`
recording each checklist item as pass/fail with a one-line justification.

**Pass criterion:** all 8 items pass. Any fail blocks Phase 1 until ADR-015
is amended (separate signed commit) and this plan is updated.

**HARD STOP** — Recommended agent for next step: **Opus 4.7**
(Phase 1 introduces typeclass machinery and library givens; the design
nuances around `Extract[A].apply: Value => Either[String, A]` shape and
the numeric-widening givens require careful trade-off reasoning, not
mechanical edits.)

---

## 3. Phase 1 — `LiteralParser[A]` + `Extract[A]` + Library Givens

**Goal:** Introduce the two typeclasses and the library-provided given
instances. No changes to `TypeCatalog`, `QueryBinder`, or
`TypedSemantics` yet; this phase is purely additive.

**TDD first.** New test files:

- `core/src/test/scala/fol/typed/LiteralParserSpec.scala`
  - `LiteralParser[Long]` parses `"42"` → `Right(42L)`; rejects `"abc"`.
  - `LiteralParser[Long]` rejects `"42.0"` (no implicit float→long).
  - `LiteralParser[Double]` parses `"3.14"` → `Right(3.14)`;
    accepts `"42"` (integer literal widens to `Double`).
  - `LiteralParser[Double]` rejects `"abc"`.

- `core/src/test/scala/fol/typed/ExtractSpec.scala`
  - `Extract[Long]` accepts `Value(sort, 42L: Long)` →
    `Right(42L)`; rejects `Value(sort, "42": String)` with a
    descriptive `Left`.
  - `Extract[Double]` accepts `Value(sort, 3.14: Double)`;
    accepts `Value(sort, 42L: Long)` (numeric widening); rejects
    `Value(sort, "abc": String)`.
  - `Extract[String]` accepts `Value(sort, "x": String)`;
    rejects `Value(sort, 42L: Long)`.
  - `Value(sort, raw).extract[Long]` extension delegates to the given
    `Extract[Long]`.
  - **Compile-error test** (using the standard `compileErrors` /
    `assertNoDiff` pattern already in use in the suite, or a dedicated
    `-Wconf` / `assertCompiles` helper): `Value(sort, raw).extract[MyType]`
    where `MyType` has no `Extract` given fails to compile with a
    descriptive message ("missing given instance of Extract[MyType]").

**Implementation:**

| File (NEW) | Content |
|---|---|
| `core/src/main/scala/fol/typed/LiteralParser.scala` | `trait LiteralParser[A] { def parse(s: String): Either[String, A] }`; `given LiteralParser[Long]`, `given LiteralParser[Double]` |
| `core/src/main/scala/fol/typed/Extract.scala` | `trait Extract[A] { def apply(v: Value): Either[String, A] }`; givens for `Long`, `Double` (incl. widening from `Long`), `String`; `extension (v: Value) def extract[A](using e: Extract[A]): Either[String, A]` |

**Pass criterion:**
- `sbt folEngineJVM/test folEngineJS/test` green.
- Compile-error test produces the expected diagnostic (manually verified;
  paste output into the commit message).

**Out of scope for Phase 1:**
- `TypeCatalog` does not yet store `LiteralParser` instances; validators
  are still the existing `String => Boolean` signature.
- Consumers (register) cannot yet *use* `Extract[A]` — the wiring lands
  in Phase 4.

**HARD STOP** — Recommended agent for next step: **Opus 4.7**
(Phase 2 changes the `TypeCatalog` builder signature — a public API
break — and updates the `nameCollisions` invariant. The signature
choice has downstream consequences for register consumers and must be
reasoned about against ADR-015 §3 "Single declaration site per (sort,
carrier)".)

---

## 4. Phase 2 — `TypeCatalog.literalValidators` Signature Change

**Goal:** Change `TypeCatalog`'s validator map from
`Map[TypeId, String => Boolean]` (or whatever the current shape is —
verify in Phase 0 review) to
`Map[TypeId, String => Option[Any]]`, per ADR-015 § Implementation.

**TDD first.** Adapt / extend `TypeCatalogSpec` (or whichever test
file currently covers the validator map):

- Catalog construction with a validator that returns `Some(parsedValue)`
  succeeds.
- Catalog construction with a validator that returns `None` for valid
  input is detected as a builder error if the catalog also asserts a
  required validator (verify whether such an assertion exists; if not,
  defer to Phase 3).
- `nameCollisions` check still passes when validators are present
  (regression test — the builder's collision detection must not depend
  on the validator signature).

**Implementation:**

- `core/src/main/scala/fol/typed/TypeCatalog.scala`:
  - Change `literalValidators` field type to
    `Map[TypeId, String => Option[Any]]`.
  - Update `TypeCatalog.unsafe` / `TypeCatalog.apply` builder
    signatures.
  - Update any `derivedValidator` / library-provided validator helper
    to wrap a `LiteralParser[A]` into the `String => Option[Any]`
    shape (`s => LiteralParser[A].parse(s).toOption`). This is the
    **only** sanctioned location for the cast from `A` to `Any`.

**Callers updated in this phase (compile-only adapter; semantics
preserved):**
- All existing `String => Boolean` validators in tests are rewritten as
  `s => if oldPredicate(s) then Some(s) else None`. This deliberately
  keeps the `raw: Any` carrier as the source string for now; the
  semantic upgrade lands in Phase 3 (`QueryBinder` rewrite).

**Pass criterion:**
- `sbt folEngineJVM/test folEngineJS/test` green.
- No regressions in existing `QueryBinderSpec`, `TypedSemanticsSpec`.

**HARD STOP** — Recommended agent for next step: **Opus 4.7**
(Phase 3 is the T-002 fix — the named-constant branch rewrite is the
critical correctness change; introduces `BindError.UnparseableConstant`;
must thread the validator's `Any` through `ConstRef.raw`. Requires
careful reasoning about error precedence and existing `BindError`
hierarchy.)

---

## 5. Phase 3 — `QueryBinder` Named-Constant Branch Rewrite (closes T-002)

**Goal:** In `QueryBinder.bindTermExpected` (line ~131–141 today),
replace the existing `Term.Const(name)` → `ConstRef(name, expected,
TextLiteral(name))` path with one that consults
`catalog.literalValidators(expected)` and produces
`ConstRef(name, expected, parsedValue: Any)`.

**TDD first.** New / extended tests in `QueryBinderSpec`:

- Named constant whose validator parses the source text returns a
  `BoundTerm.ConstRef(name, expected, raw)` where `raw` matches the
  validator's parsed `Any`.
- Named constant whose validator returns `None` produces
  `Left(BindError.UnparseableConstant(name, expected, sourceText))`.
- Named constant for a sort with **no** validator registered produces
  the existing `BindError.UnknownConstantOrLiteral` (regression — must
  not silently change behaviour for sorts that opt out of validation).
- `BoundTerm.ConstRef.raw: Any` carries the parsed value, *not* the
  source text, for sorts with a validator.

**Implementation:**

- `core/src/main/scala/fol/typed/BoundQuery.scala`:
  - `ConstRef(sourceText: String, sort: TypeId, raw: Any)` (signature
    likely already matches; verify in Phase 0).
- `core/src/main/scala/fol/typed/TypeCheckError.scala` (or wherever
  `BindError` lives):
  - Add `final case class UnparseableConstant(name: String, sort: TypeId, sourceText: String) extends BindError`.
- `core/src/main/scala/fol/typed/QueryBinder.scala`:
  - Rewrite the `Term.Const(name)` branch in `bindTermExpected` per
    ADR-015 §4. Validator absence falls through to existing
    `UnknownConstantOrLiteral`; validator presence + `None` returns
    `UnparseableConstant`; validator presence + `Some(raw)` produces
    the `ConstRef` with the parsed `Any`.

**Pass criterion:**
- `sbt folEngineJVM/test folEngineJS/test` green.
- `QueryBinderSpec` named-constant cases all green.
- `docs/TODOS.md` T-002 is updated in this commit to add a "Resolved by
  ADR-015 §4 + Phase 3 of `PLAN-symmetric-value-boundaries.md`" line
  (do NOT remove T-002; mark it ✅).

**HARD STOP** — Recommended agent for next step: **Opus 4.7**
(Phase 4 is the deepest change — threads `Any` through evaluation,
dispatcher, and function-impl machinery. Numerous call sites; risk of
silent behaviour change if any `LiteralValue.match` is missed.
Mechanical in shape but not in judgement; needs careful code-smell
discipline.)

---

## 6. Phase 4 — Thread `Any` Through Semantics / Dispatcher / Function Impl

**Goal:** Update `TypedSemantics.evalTerm`, `MapDispatcher`, and
`TypedFunctionImpl` so the runtime carrier is `raw: Any` end-to-end and
extraction at consumer-call boundaries goes through `Extract[A]`.

**TDD first.** Extended tests in `MapDispatcherSpec`,
`TypedSemanticsSpec`, `TypedFunctionImplSpec`:

- A function registered via `TypedFunctionImpl.of[Args, R]` (or
  whichever combinator is canonical post-T-003 sketch) receives raw
  arguments via `Extract[A]` and returns `Either[String, R]`; the
  framework lifts the result into `Value(returnSort, r: Any)`.
- A dispatcher lambda whose argument is consumed via `value.extract[Long]`
  succeeds for a `Value(sort, 42L)` and fails with a descriptive
  message for `Value(sort, "42": String)`.
- The "two raw shapes" code smell (T-003 context) is *not* present:
  no test relies on a `rawToDouble` helper that handles both
  `LiteralValue` and primitive shapes.

**Implementation:**

- `core/src/main/scala/fol/typed/RuntimeModel.scala`:
  - `Value(sort, raw: Any)` (already so per ADR-015 § Implementation;
    verify in Phase 0).
- `core/src/main/scala/fol/typed/MapDispatcher.scala`:
  - `functions: Map[SymbolName, List[Value] => Either[String, Any]]`.
- `core/src/main/scala/fol/typed/TypedFunctionImpl.scala`:
  - Combinator surface uses `Extract[A]` for argument extraction; result
    type stays `Either[String, Any]` (or normalises through a
    `LiteralParser`-style wrapper if T-003 lands jointly — defer that
    decision to a separate plan).
- `core/src/main/scala/fol/typed/TypedSemantics.scala` (`evalTerm`):
  - Carries `raw: Any` through; no `LiteralValue.match`.
- `core/src/main/scala/fol/typed/FolModel.scala`:
  - Update consumer-facing types if `evalFunction` signature changes
    (line ~17, 27 are call sites today — re-verify in Phase 0).

**Pass criterion:**
- `sbt folEngineJVM/test folEngineJS/test` green.
- No `LiteralValue.*` references remain in the production code (grep
  check; `LiteralValue` itself may still exist as an `@deprecated`
  re-export — that lands or doesn't in Phase 5).

**HARD STOP** — Recommended agent for next step: **Opus 4.7**
(Phase 5 is the cleanup pass — `LiteralValue` enum and `TypeRepr`
removal — and must decide between hard-delete vs `@deprecated` for one
release cycle. The decision interacts with downstream consumer
migration timing in `register` and should be reasoned about against
ADR-015 § Status Note (Pre-Acceptance) sequencing.)

---

## 7. Phase 5 — Remove `LiteralValue` and `TypeRepr`

**Goal:** Complete the design by removing the two abstractions ADR-015
declares dead.

**Decision point:** Hard-delete vs `@deprecated` for one cycle.

- **Hard-delete** (recommended if no external consumer besides
  `register` depends on the symbols, and `register` migrates in the
  same release window): cleanest, smaller surface.
- **`@deprecated` for one cycle**: re-export `LiteralValue` and
  `TypeRepr` with `@deprecated("Use Value(sort, raw: Any) and Extract[A]
  per ADR-015", "0.10.0-SNAPSHOT")` annotations; remove in the next
  minor release.

The agent **must not** pick a default. The user decides at the Phase 4
HARD STOP.

**Implementation (either branch):**
- `core/src/main/scala/fol/typed/TypeDefs.scala` (line ~70–71): remove
  or `@deprecated`-mark `LiteralValue` enum.
- `core/src/main/scala/fol/typed/TypeRepr.scala`: remove file or convert
  to `@deprecated` re-export of `Extract`.
- All test files: remove `LiteralValue.*` constructors; replace with
  raw value injection (`Value(sort, 42L)`).

**Pass criterion:**
- `sbt folEngineJVM/test folEngineJS/test` green.
- Grep: no production references to `LiteralValue` or `TypeRepr`
  (deprecated re-exports excepted).

**HARD STOP** — Recommended agent for next step: **Opus 4.7**
(Phase 6 combines the artifact rename, code-consistency review against
ADR-015, and the ADR-015 `Proposed → Accepted` promotion. The review
step requires architectural judgement about whether implementation
matches design or whether the ADR must be amended.)

---

## 8. Phase 6 — Artifact Rename, Publish, ADR-015 Acceptance

**Goal:** Finish the refactor with a publishable artifact under the
correct name and a binding ADR-015.

**Step 6.1 — Artifact rename (`fol-engine` → `vql-engine`):**

- `vague-quantifier-logic/build.sbt`: change the `name := "fol-engine"`
  setting on the cross-project to `name := "vql-engine"`. The sbt
  project IDs `folEngineJVM` / `folEngineJS` and the Scala packages
  `fol.*` are **NOT** changed in this phase (see follow-up TODO).
- **Version stays `0.10.0-SNAPSHOT`** — explicitly do not bump.
- `vague-quantifier-logic/README.md`, `CHANGELOG-48H.md` (if it covers
  the version), and any `docs/**` mention of `fol-engine` as an
  artifact identifier: update to `vql-engine`. Do NOT rewrite mentions
  of the Scala package `fol.typed` etc. — those stay until the
  follow-up.
- `sbt clean publishLocal` from `vague-quantifier-logic`.

**Step 6.2 — Register-side dependency bump:**

- `register/build.sbt`: change the `"com.risquanter" %% "fol-engine" %
  "0.10.0-SNAPSHOT"` (or `%%%` cross-build) coordinate to
  `"com.risquanter" %% "vql-engine" % "0.10.0-SNAPSHOT"`. Version
  string is unchanged.
- `register/docs/**`: update artifact mentions only (no Scala-import
  changes). Note in particular the line in
  `docs/PLAN-QUERY-NODE-NAME-LITERALS.md` §1 referencing
  `com.risquanter::fol-engine:0.9.0-SNAPSHOT` — update artifact name
  *and* version to current `vql-engine:0.10.0-SNAPSHOT`.
- `sbt compile` from register; do NOT run the full register test suite
  in this phase (that's the responsibility of register-side Phase 5b).

**Step 6.3 — Code-consistency review:**

Walk every row in ADR-015 § Implementation against the actual code on
disk. Each row is one of:
- ✅ Match: code matches ADR exactly.
- ⚠️ Drift: code differs in a way the ADR should adopt — open a
  separate signed commit amending ADR-015 before promoting.
- ❌ Bug: code differs in a way that must be fixed — open a separate
  signed commit fixing the code before promoting.

Record findings in `docs/scratch/ADR-015-code-review-2026-05-XX.md`.

**Step 6.4 — ADR-015 promotion:**

If Step 6.3 records only ✅ rows (or all ⚠️/❌ items have been resolved
in their own commits), update `docs/ADR-015.md`:
- `Status: Accepted (2026-05-XX)`
- Strike the § Status Note (Pre-Acceptance) section, replacing it with
  a brief acceptance note pointing to the consistency-review files.
- Update `docs/ADR-013.md` Status banner to reflect formal supersession
  (the existing "Superseded by ADR-015 (2026-05-01 revision)" banner
  may need a date refresh).

**Pass criterion:**
- `sbt publishLocal` succeeds in `vague-quantifier-logic`.
- `sbt compile` succeeds in `register`.
- ADR-015 Status is `Accepted`.
- Review scratch files exist and record only ✅ rows (or document the
  amendment commits).

**Signed commits at Phase 6 boundary:**
- VQL: artifact rename + ADR-015 promotion (separate commits if Step
  6.3 produces amendments).
- Register: dependency bump + doc updates.

**HARD STOP** — Recommended agent for next step: **Sonnet 4.6**
(The first action after this plan completes is the rewritten
`register` Phase 5b — re-enabling B1 in `BinderIntegrationSpec` and
adapting `RiskTreeKnowledgeBase` to the new VQL surface. That work is
mechanical adaptation against a now-binding ADR-015 surface and is
well-suited to Sonnet 4.6. The register-side Phase 7 ADR review
returns to Opus 4.7.)

---

## 9. Cross-Repo Sequencing Reminder

Phases 0–6 are **VQL-only**. The downstream `register` work
(`PLAN-QUERY-NODE-NAME-LITERALS.md` Phases 5b–7) does **not**
interleave with these phases. The B1 test in
`register/modules/server/src/test/scala/com/risquanter/register/foladapter/BinderIntegrationSpec.scala`
remains `@@ TestAspect.ignore` for the entire duration of this plan
and is re-enabled as the *first* action of register Phase 5b, against
the published `vql-engine:0.10.0-SNAPSHOT`.

---

## 10. Halt Marker

> 🛑 **Per WORKING-INSTRUCTIONS § Mandatory Review Halt, the agent now stops.**
> Awaiting explicit user signal to begin Phase 0.
