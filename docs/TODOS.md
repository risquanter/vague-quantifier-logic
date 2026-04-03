# TODOs

## ~~T-001 — Tagged type constructors for domain vs value type in the programmatic `TypeCatalog` API~~ ✅ Implemented in `0.7.0-SNAPSHOT`

**Status:** DONE. `DomainType(id)` / `ValueType(id)` ADT implemented in `fol/typed/TypeDefs.scala`.
`TypeCatalog.unsafe(types = Set(DomainType(asset), ValueType(loss)), ...)` — no `domainTypes` parameter.
`catalog.domainTypes` is a derived method. See ADR-014 §1.
---

## T-002 — Named constants: design review and correctness gap

**Status:** DEFERRED (documented as-is, 2026-04-03).

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
