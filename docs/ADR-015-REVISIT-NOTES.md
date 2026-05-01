# ADR-015 Revisit Notes — Injection Asymmetry Surfaced by Register T-002

**Status:** Open analysis (input to a future ADR-015 revisit / successor ADR)
**Date:** 2026-05-01
**Tags:** adr-revisit, value-boundaries, literal-injection, t-002, named-constants
**Origin:** Hard-stop analysis during register Phase 5a (BinderIntegrationSpec B1 failure) of plan `PLAN-QUERY-NODE-NAME-LITERALS.md`.

> This document records an assessment performed mid-implementation. It is **not** a decision. It is the context a future maintainer needs to (a) execute the immediate fix and (b) decide whether ADR-015 §1 itself should be revised.

---

## 1. Triggering symptom

Register's `BinderIntegrationSpec.B1` (parse + bind + evaluate of a query containing a named-constant Asset literal, e.g. `leaf_descendant_of(x, "IT Risk")`) fails with:

```
EvaluationError("leaf_descendant_of: expected String at index 1, got TextLiteral")
```

Root cause: register's dispatcher receives `Value(Asset, raw)` where `raw` is `String` for tree-supplied values but `LiteralValue.TextLiteral("IT Risk")` for literal-injected named constants. Two distinct JVM shapes for the same logical sort, in the same dispatcher arm.

The shape mismatch originates at `core/src/main/scala/fol/typed/QueryBinder.scala:131-134`:

```scala
catalog.constants.get(name) match
  case Some(actual) if actual == expected =>
    Right((BoundTerm.ConstRef(name, expected, TextLiteral(name)), env))
  …
```

i.e. the named-constant branch synthesises a `TextLiteral` from the source token rather than producing the carrier value the consumer would have produced via a validator. This is **VQL T-002**, deferred 2026-04-03.

## 2. What ADR-015 actually codifies (plain-language summary)

The FOL engine cannot know in advance what JVM type a consumer will use to represent any given sort — Asset might be `String` here, `NodeId` (or `SafeName.SafeName`) elsewhere, `case class Asset(...)` in a third project. So `Value.raw: Any` is unavoidable as the internal carrier.

ADR-015 splits the `Any` boundary into two:

| Direction | Carrier | Library's knowledge | Mechanism |
|---|---|---|---|
| **Injection** (parser → dispatcher) | inline literals: `"10000000"`, `"0.95"`, `"IT Risk"` | **Closed** — sealed `LiteralValue` enum (Int/Float/Text) | exhaustive match in dispatcher |
| **Extraction** (evaluator → consumer) | satisfying values pulled out into consumer types | **Open** — consumer chooses JVM type | `TypeRepr[A]` + `Value.as[A]` |

The asymmetry — sealed on the way in, open on the way out — is the design choice ADR-015 codifies and that this document interrogates.

## 3. Honest re-assessment of ADR-015

### 3.1 What ADR-015 gets right (keep)

1. `asInstanceOf` should not be scattered at call sites. (Liskov-boundary hygiene.)
2. **§2 — `TypeRepr[A]` for extraction.** Single declaration point per sort, runtime sort check, no per-call casts. Register uses this cleanly in `QueryResponseBuilder` (one `given TypeRepr[String]`, used everywhere). No friction observed in practice.
3. Validators must retain the parsed value, not return `Boolean`. The "discard parsed value then re-parse in dispatcher" pattern is a real smell.
4. Function-return wrapping by the framework via `LiteralValue` — reasonable mechanically, but inherits the same asymmetry critique below.

### 3.2 Where ADR-015 looks weaker in hindsight

Re-reading ADR-015 against how register *actually* uses it (after Phase 4 made register the first consumer to populate `catalog.constants` end-to-end) surfaces a real tension:

**Observation 1: the "finite library-controlled" injection set only fits sorts whose canonical carrier IS `Long`/`Double`/`String`.**

`literalValidators: Map[TypeId, String => Option[LiteralValue]]` — return type is sealed to `LiteralValue`. For any sort whose carrier is *not* one of those three (a newtype, a domain entity, a named constant), the validator can only return `TextLiteral(sourceToken)` and the dispatcher must subsequently re-resolve the token. That re-resolution is exactly the dual-shape problem in B1.

ADR-015's own code-smell list calls out "two raw shapes for the same sort" as a smell — but the validator API *forces* it whenever the consumer's chosen carrier isn't in the `LiteralValue` set. The ADR criticises a smell its own mechanism creates.

**Observation 2: register's three sorts are all primitive carriers — yet T-002 still bit.**

Register currently uses `String`, `Long`, `Double` as the carriers for Asset, Loss, Probability. These are the *easiest* possible case for ADR-015 — and the named-constant pathway still produces a shape mismatch. A future consumer with a non-primitive Asset carrier (e.g. an opaque `SafeName.SafeName` or a domain newtype) would see the same problem at *every* dispatcher arm comparing tree-supplied values against literal-injected ones.

**Observation 3: the rejected `String => Option[Any]` alternative was rejected on a flawed argument.**

ADR-015 says: *"moves the cast from the re-parsing site to the dispatcher lambda, but does not eliminate it."* This holds only of literally-typed `Any`. A symmetric mechanism — `String => Option[A]` paired with a consumer-declared `TypeRepr[A]` for the same sort — would eliminate the cast at the consumer's view boundary in exactly the way `Value.as[A]` does for extraction. The library's internal `raw: Any` storage is unchanged either way; the question is purely the consumer's API surface.

**Observation 4: injection and extraction are the same problem in opposite directions.**

Both cross the `Any`-typed boundary. Both want a single declaration site per sort. Extraction got a parametric mechanism (`TypeRepr[A]`); injection got a closed enum. ADR-015's stated justification ("library cannot enumerate consumer JVM types") applies equally to both — so the asymmetry is a design choice, not a forced one.

**Observation 5: `LiteralValue` does carry one genuine convenience.**

For sorts whose carrier IS `Long`/`Double`, the consumer is spared writing `"10000000".toLongOption` per sort — `IntLiteral`/`FloatLiteral` provide it. Real ergonomic value that a fully symmetric design must preserve via library-provided helpers.

### 3.3 Verdict

- **§2 (extraction):** sound, keep.
- **§1 (injection):** partially sound; its sealed/asymmetric framing is the *cause* of T-002, not a constraint preventing T-002's fix. The B1 failure is direct evidence of the gap for any sort whose canonical consumer carrier isn't a `LiteralValue` case.

ADR-015 should not be treated as a hard veto on changes to the injection mechanism. It is a codified pattern with a known gap (T-002 itself proves the gap). Whether the asymmetry should be retained is a legitimate open question for a successor ADR.

## 4. Decision space (A / B / B+ / D)

Recorded for completeness; B is recommended (§5).

| Option | Where | What | ADR-015 stance | Cost |
|---|---|---|---|---|
| **A** | register only | extend `extractString` to also unwrap `TextLiteral` | perpetuates §1 smell at consumer side; localised workaround | smallest; documents the ADR gap as a register-side hack |
| **B** | VQL `QueryBinder` | implement T-002(b): named-constant branch routes through `literalValidators`, producing the consumer's chosen `LiteralValue` shape | conformant with §1 as written | small; fol-engine bump; resolves B1 |
| **B+** | VQL | implement T-002(c): separate named-constant registry where consumers supply full `Value(sort, raw)` | quietly relaxes §1 asymmetry for the named-constant case only | medium; new API surface |
| **D** | VQL | replace `literalValidators: Map[TypeId, String => Option[LiteralValue]]` with `String => Option[Any]` plus per-sort `TypeRepr[A]`; mirrors §2 | **supersedes ADR-015 §1**; warrants a new ADR | largest; touches every `LiteralValue` usage and every consumer dispatcher |

## 5. Recommendation (carry forward)

**Option B is the right immediate move** (fixes B1, smallest bump, ADR-conformant under current ADR-015), **and Phase 7's post-impl ADR review should explicitly examine whether ADR-015 §1's injection asymmetry should be revisited** — with this document as the starting point. This keeps the current plan (`register/docs/PLAN-QUERY-NODE-NAME-LITERALS.md`) moving while acknowledging that the underlying ADR may itself want revision rather than being treated as an immutable constraint.

### 5.1 Decision taken (2026-05-01)

**Path: Option 2 — dedicated VQL plan.** The recommendation in §5 above is superseded as follows:

- The current `PLAN-QUERY-NODE-NAME-LITERALS` is paused at end of Phase 5a.
- A dedicated VQL plan introduces a symmetric typeclass-driven boundary design (`LiteralParser[A]` + `Extract[A]`).
- **ADR-015 is rewritten in place** (not a new ADR-016). The 2026-04-03 version is archived as `ADR-015.backup-2026-05-01.md`. The new ADR-015 is `Proposed` and **not binding** until both internal-consistency and code-consistency reviews pass; during the refactor neither version governs.
- VQL bumps to **0.11.0-SNAPSHOT** (breaking API change).
- Phases 5b/6/7 of the node-literals plan are **rewritten** against the new VQL surface and resumed afterwards.
- **B1 is deferred to the very end** (after the rewritten Phase 5b lands the new VQL surface). Stabilisation: mark `B1` as `ignore("blocked on ADR-015 refactor")` at the end of Phase 5a; B2 + B3 ship green.
- **Opaque-type follow-up is strict "after both plans"** — and may turn out to be unnecessary. Register's actual identity types are `NodeId` (case-class wrapper over `SafeId.SafeId`, used for both leaves and portfolios) and `SafeName.SafeName` (opaque type for names). The Asset sort's carrier is the node *name* (currently `String`); whether to migrate it to `SafeName.SafeName` is a separate, optional question for that follow-up. **No new opaque types should be invented** — the prior pseudocode `LeafId`/`RiskId` were illustrative, not prescriptive.

Confirmed user answers (2026-05-01):
- VQL version target: `0.11.0-SNAPSHOT` ✓
- Opaque-type timing: strict "after both plans" ✓
- Sequencing: Option 2 ✓
- B1 stabilisation: `ignore`-with-comment, address at the very end ✓
- ADR-016 vs rewrite ADR-015: **rewrite ADR-015 in place** (with backup) ✓

Open questions remaining:
1. **ADR-015 acceptance gates** — internal consistency review and code consistency review must both pass before the new ADR-015 is treated as binding. Until then the *scope* of ADR-015 is the subject of the refactor; both the new and archived versions are out of effect.
2. **Opaque-type necessity in register** — to be evaluated in the post-completion follow-up; non-blocking.

## 6. Pickup checklist (if executing this advice)

### 6.1 Immediate — execute Option B in VQL

1. **Choose between T-002(b) and T-002(c).** Both ADR-015-compliant. (b) is smaller and reuses the existing validator path; (c) introduces a separate registry. Recommend (b) unless the named-constant semantics genuinely diverge from "validate a source token". Surface this as a HARD STOP if not obvious — it is the next decision point.
2. **VQL changes (T-002(b) sketch):**
   - In `core/src/main/scala/fol/typed/QueryBinder.scala`, the `catalog.constants.get(name)` branch (lines 131-134) must obtain a `LiteralValue` for `name` via the registered `literalValidators(expected)` rather than synthesising `TextLiteral(name)`.
   - Decide what happens when a sort has named constants but no validator (or the validator rejects the constant's source token). Likely `Left(BindError.…)`. New error case may be needed in `BoundQuery`/binder error ADT.
   - Update `core/src/main/scala/fol/typed/TypeDefs.scala` `TextLiteral` scaladoc — remove the "stopgap for named constants" wording once T-002(b) lands.
   - Update `docs/TODOS.md` T-002 to **DONE** with link to this document.
3. **VQL test:** add a binder unit test asserting that a named constant of a sort with a `Long` validator binds to `ConstRef(name, sort, IntLiteral(parsedLong))`, not `TextLiteral(name)`.
4. **Version bump:** bump VQL `version` (next snapshot or `0.10.0` final), `sbt publishLocal`.
5. **Register changes:**
   - Bump fol-engine dependency in `build.sbt`.
   - Register's `literalValidators` for `assetSort` must be added (currently absent — `RiskTreeKnowledgeBase.scala:172-176` only registers `lossSort` and `probabilitySort`). The validator must produce a `LiteralValue` that *matches the tree-supplied carrier shape*. Since register's Asset carrier is `String`, the validator should return `TextLiteral(s)` — and `extractString` (line 291) must then unwrap `TextLiteral` rather than expecting raw `String`. **Note:** this means register chooses `TextLiteral` as the canonical Asset carrier and pushes a small rewrite into the tree path: tree-supplied Asset values must also be wrapped as `TextLiteral` when constructed into a `Value`. Alternatively, ADR-015's "ALSO GOOD: `TypedFunctionImpl.of[A]` makes the wrap declaration explicit" pattern can hide the wrap.
   - Re-run `BinderIntegrationSpec` — B1 must go green; B2 and B3 must remain green.
6. **Resume `PLAN-QUERY-NODE-NAME-LITERALS.md` Phase 5b** (HTTP IT QueryEndpointSpec H1-H3) and onwards.

### 6.2 Phase 7 — ADR-015 revisit

The plan's Phase 7 ("post-impl ADR review") should add an explicit item:

> **Phase 7.X — Revisit ADR-015 §1 (injection asymmetry).** Use `vague-quantifier-logic/docs/ADR-015-REVISIT-NOTES.md` as input. Decide one of:
> - (i) **Keep ADR-015 §1 as-is**, document the named-constant carrier-shape constraint explicitly, accept that consumers with non-primitive carriers must wrap into `TextLiteral`.
> - (ii) **Amend ADR-015 §1** to permit `String => Option[A]` validators paired with `TypeRepr[A]`, mirroring §2. Write a new ADR (ADR-016 or supersede ADR-015) capturing the symmetric design.
> - (iii) **Defer** with a dated re-review trigger (e.g. next consumer with non-primitive carrier).

Inputs to that decision:
- This document.
- Register's actual usage of `literalValidators` and `TypeRepr` after Option B lands.
- Any second consumer of fol-engine that exists by then.

## 7. Cross-references

- Plan: `register/docs/PLAN-QUERY-NODE-NAME-LITERALS.md` (Phases 5a-7).
- Failing test: `register/modules/server/src/test/scala/com/risquanter/register/foladapter/BinderIntegrationSpec.scala` (B1).
- VQL technical debt: `docs/TODOS.md` T-002.
- VQL ADRs touched: ADR-015 (primary), ADR-013 (subsumed by §2), ADR-006 (enum encoding — applies if §1 is amended).
- VQL files implicated by Option B: `core/src/main/scala/fol/typed/QueryBinder.scala`, `core/src/main/scala/fol/typed/TypeDefs.scala`, `core/src/main/scala/fol/typed/BoundQuery.scala`.
- Register files implicated by Option B: `modules/server/src/main/scala/com/risquanter/register/foladapter/RiskTreeKnowledgeBase.scala` (`literalValidators`, `extractString`), `build.sbt` (fol-engine version).

## 8. Status of HARD STOP at the time of writing

- `BinderIntegrationSpec.scala` is on disk in register, B1 failing, B2 + B3 passing. Not committed.
- No code changes have been made to either repo as a result of this analysis.
- Decision A vs B vs B+ vs D not yet taken.
- If B is chosen, the next HARD STOP is T-002(b) vs T-002(c).
