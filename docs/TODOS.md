# TODOs

## T-000 — Scala-package rename `fol.*` → `vql.*` (post-Phase 6 follow-up)

**Status:** PENDING — sequence as a separate single-purpose commit
*after* `PLAN-symmetric-value-boundaries.md` Phase 6 lands the artifact
rename (`fol-engine` → `vql-engine`, version unchanged at
`0.10.0-SNAPSHOT`).

**Scope:**
- Rename Scala packages across `core/src/**`:
  `fol.typed` → `vql.typed`,
  `fol.parser` → `vql.parser`,
  `fol.semantics` → `vql.semantics`,
  `fol.error` → `vql.error`,
  and any other top-level `fol.*` package surface.
- Update all `import` statements within VQL.
- Update register's `import fol.*` references accordingly
  (`register/modules/server/src/main/scala/com/risquanter/register/foladapter/**`
  imports `fol.typed.{Value, TypeId, TypeRepr, ...}` today — see e.g.
  `QueryResponseBuilder.scala`).

**Why deferred:** Mechanical but breaking — both the artifact rename
(Phase 6) and the package rename in the same commit would conflate
two concerns. Sequencing as a separate commit keeps the diff
reviewable and lets the artifact rename ship cleanly first.

**Why not bumped to a major version:** `0.10.0-SNAPSHOT` is a
pre-1.0 SNAPSHOT; we are explicitly not stabilising the public API
yet (per user direction; see register `PLAN-QUERY-NODE-NAME-LITERALS.md`
§9 F-R4 and ADR-020).

**Reference:** `docs/PLAN-symmetric-value-boundaries.md` §1 (Out of
scope) and §8 Phase 6 (Step 6.1).

---

## ~~T-001 — Tagged type constructors for domain vs value type in the programmatic `TypeCatalog` API~~ ✅ Implemented in `0.7.0-SNAPSHOT`

**Status:** DONE. `DomainType(id)` / `ValueType(id)` ADT implemented in `fol/typed/TypeDefs.scala`.
`TypeCatalog.unsafe(types = Set(DomainType(asset), ValueType(loss)), ...)` — no `domainTypes` parameter.
`catalog.domainTypes` is a derived method. See ADR-014 §1.
---

## T-002 — Named constants: design review and correctness gap

**Status:** ✅ RESOLVED by ADR-015 §4 + Phase 3 of `PLAN-symmetric-value-boundaries.md` (2026-05-02). `BoundTerm.ConstRef.raw` is now `Any` and inline literals carry the parsed primitive (or consumer-chosen wrapper) produced by the registered `literalValidator`, not `TextLiteral` of the source text. A validator returning `None` produces the new `TypeCheckError.UnparseableConstant(name, sort, sourceText)`. Named constants registered via `catalog.constants` (option (a)/(c) of the original design space) are still treated as a separate path; the design space below is preserved for the historical record.

**Original status (2026-04-03):** DEFERRED.

The `catalog.constants: Map[String, TypeId]` feature is an OCaml-heritage artifact. A named constant bound through `QueryBinder` currently falls into the `catalog.constants.get(name)` branch in `bindTermExpected` and produces `ConstRef(name, expected, TextLiteral(name))` — the raw value is a `TextLiteral` of the source text, which is semantically incorrect for typed consumers expecting a `Long` or `Double` value at evaluation time.

**Why this is deferred and not fixed now:**
- No paper requirement: Fermüller et al. do not define named constants as a query language feature.
- No end-to-end tests cover named constants; only catalog-level schema validation tests exist.
- The correct design is non-trivial and intersects with T-003.

**Design space for a future decision (do not prescribe a solution prematurely):**
- (a) Remove named constants entirely as out-of-scope — `catalog.constants` is deleted; any `Term.Const(c)` that is not an inline literal is a bind error.
- (b) Route named constants through the same `LiteralValue`/validator mechanism as inline literals — the consumer registers a validator that recognises the constant name and returns the appropriate `LiteralValue`.
- (c) A separate named-constant registry where consumers supply the full `Value(sort, raw)` directly, bypassing the validator mechanism.

**Context:** ADR-015 §1 (injection boundary), `fol/typed/QueryBinder.scala` `bindTermExpected` `Term.Const` branch, conversation history 2026-04-03.

---

## T-003 — Typed literal pipeline and function return-type normalizer

**Status:** DEFERRED (documented as-is, 2026-04-03).

A `TypedFunctionImpl.of` combinator pattern was sketched where the consumer lambda returns a native `A` and a `wrap: A => LiteralValue` normalizer converts it, so the framework constructs `Value(resultSort, literalValue)`. This eliminates the two-tier raw world for value-type function results: currently a function lambda can return any `raw` (e.g. a plain `Double` 0.07), while inline literals produce `LiteralValue` variants (`FloatLiteral(0.05)`). Downstream dispatcher lambdas that receive arguments from both sources must handle both raw shapes.

**Why this is deferred and not fixed now:**
- Requires `FolModel` API to be stable first (planned next).
- Requires a design decision on whether the normalizer is part of `MapDispatcher` (registration-time wrapping) or a separate wrapper combinator.
- The `LiteralValue` foundation is now in place (ADR-015, T-002 unresolved), so this can proceed once T-002 and `FolModel` are settled.

**Context:** ADR-015 §1 and Code Smells §4, `MapDispatcherSpec` `rawToDouble` helper (shows the two raw shapes), conversation history 2026-04-03.
---

## T-004 — Domain-returning functions and entity identity representation

**Status:** DEFERRED pending use case (2026-04-04).

The function return normalisation plan (T-003 / `PLAN-function-return-normalisation.md`)
scopes `evalFunction` return type to `Either[String, LiteralValue]`. This implicitly
constrains all functions to return `ValueType` sorts — a function declared with a
`DomainType` return (e.g. `ownerOf: Asset → Company`) cannot be expressed as a
`LiteralValue` without re-introducing `Any` or adopting an explicit entity identity
type.

**The core tension:** a `LiteralValue` variant for entity references (`EntityRef(key)`)
would need a key type. Options examined:

- `EntityRef(key: String)` — defeats the purpose; `String` is too permissive and
  reintroduces the untyped-raw smell at a different level
- `EntityRef(key: Int)` — avoids string ambiguity but raises questions about whether
  integer keys are sufficient (security, composites, natural keys from external systems)
- `EntityRef(key: Any)` — directly re-introduces `Any`; rejected

**Why deferred:** no current use case requires a domain-returning function. The
`ownerOf: Asset → Company` pattern does not appear in any planned query model.
The right answer depends on whether integer keys suffice for all anticipated domains,
which requires a concrete use case to evaluate.

**Prerequisite for T-003 implementation:** T-003 must add a catalog-validation guard
(`TypeCatalogError.FunctionReturnIsDomainType`) that rejects domain-returning function
declarations at construction time, making the current scope limitation explicit and
surfacing it as an error rather than silent misbehaviour. This guard is removed when
T-004 is resolved.

**Context:** `PLAN-function-return-normalisation.md` §Q2, `fol/typed/TypeCatalog.scala`
`collectErrors`, conversation history 2026-04-04.
