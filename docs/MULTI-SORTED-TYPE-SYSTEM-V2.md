# Multi-Sorted Type System for fol-engine — V2 (Constraint-Aligned)

**Status:** Implemented (0.8.0-SNAPSHOT) — all D1-D6 decisions locked and in production  
**Date:** 2026-03-26  
**Scope:** fol-engine design for register query-pane integration  

---

## 1. Why this V2 exists

This document keeps `docs/MULTI-SORTED-TYPE-SYSTEM.md` unchanged and adds a tightened variant based on:

- fol-engine ADR foundations (especially ADR-001, ADR-004, ADR-005, ADR-007, ADR-008, ADR-010, ADR-012)
- register ADR-028 + technical appendix + implementation plan
- current register code reality (query endpoint scaffold exists; core query service/controller/KB adapter files are still missing)

This V2 does **not** make final decisions. It narrows design to explicit options for user decision.

---

## 2. Hard constraints extracted from ADRs and current code

### 2.1 fol-engine constraints

1. **Single shared evaluation path after boundary** (ADR-001):
   - both parse-string and programmatic entry should converge to one shared IL + one evaluator
2. **Data as ADTs, operations in objects/layers** (ADR-004)
3. **Model augmentation via composition** (ADR-005)
4. **Preserve OCaml-ported parser core style and boundaries** (ADR-007)
5. **End-to-end typed domain parameterization is intentional** (ADR-008)
6. **Typed identifiers over stringly names** (ADR-010)
7. **No redundant DSL layer if IL constructor can do the job**
8. **Operational failures return `Either`, construction invariants may use `require`** (ADR-012)

### 2.2 register constraints

1. Query-pane architecture is server-side evaluation (ADR-028)
2. Query text must stay self-describing (thresholds in query text)
3. Query errors must map to typed HTTP failures (`FolQueryFailure` branches)
4. Frontend follows Laminar "signals down, callbacks up" composition (ADR-019)
5. Parse-at-boundary principle is mandatory (ADR-001 in register)
6. Query endpoint exists in shared endpoints, but implementation is incomplete:
   - missing `QueryService.scala`
   - missing `QueryController.scala`
   - missing `RiskTreeKnowledgeBase.scala`

### 2.3 user constraints (explicit)

1. Keep original proposal doc unchanged; produce V2 separately
2. No unilateral architectural decisions by agent
3. No `Any` as public typing strategy
4. No predicate overloading
5. Extensible beyond binary `Entity/Numeric`
6. Preserve fidelity to paper foundations and ADR line

---

## 3. Critical review of V1 (delta only)

### 3.1 What V1 got right

- Correct root-cause diagnosis: single-sorted `Interpretation[D]` + untyped AST
- Correct macro-shape: parser → typecheck/bind → typed IL → evaluator
- Correct direction: consumer-defined sorts, library-defined mechanism
- Correct rejection: overloading, `Model[Any]` public API

### 3.2 What V1 should tighten

1. **`SortedModelBuilder.addFunction[A, B](List[A] => B)` is unsound for mixed-arity mixed-sort signatures**
   - `lec(Asset, Loss) -> Probability` cannot be represented by homogeneous `List[A]`
2. **Too many optional features in v1 baseline**
   - hierarchy, advanced literal ambiguity behavior, migration adapters, dual-path surface
3. **Public/internal typing boundary not sharp enough**
   - internal erasure strategy can exist, but public API must remain sort-safe and predictable

---

## 4. V2 design goal

Deliver a **minimal many-sorted core** that:

- solves the query-pane typing deadlock now
- preserves one evaluator pipeline
- keeps parser architecture stable
- avoids speculative complexity
- leaves clear extension seams for later phases

---

## 5. V2 architecture (tight core)

## 5.1 Keep parser and untyped AST as-is

No change to OCaml-ported parser core (ADR-007 alignment).

Current boundary remains:

```scala
def parse(input: String): Either[QueryError, ParsedQuery]
```

### 5.2 Add a mandatory bind/typecheck phase

Introduce a binder/typechecker between parse and evaluation:

```scala
object QueryBinder:
  def bind(
    parsed: ParsedQuery,
    catalog: TypeCatalog
  ): Either[List[TypeCheckError], BoundQuery]
```

`BoundQuery` is the **new shared IL** for sorted execution.

### 5.3 BoundQuery as shared sorted IL

```scala
case class BoundQuery(
  quantifier: Quantifier,
  variable: BoundVar,
  range: BoundAtom,
  scope: BoundFormula,
  answerVars: List[BoundVar]
)

case class BoundVar(name: String, sort: TypeId)

case class BoundAtom(name: SymbolName, args: List[BoundTerm])

enum BoundTerm:
  case VarRef(v: BoundVar)
  case ConstRef(name: String, sort: TypeId)
  case FnApp(name: SymbolName, args: List[BoundTerm], resultSort: TypeId)

enum BoundFormula:
  case True
  case False
  case Atom(a: BoundAtom)
  case Not(p: BoundFormula)
  case And(p: BoundFormula, q: BoundFormula)
  case Or(p: BoundFormula, q: BoundFormula)
  case Imp(p: BoundFormula, q: BoundFormula)
  case Iff(p: BoundFormula, q: BoundFormula)
  case Forall(v: BoundVar, body: BoundFormula)
  case Exists(v: BoundVar, body: BoundFormula)
```

### 5.4 Explicit sort catalog (no overloading)

```scala
opaque type TypeId = String
opaque type SymbolName = String

case class FunctionSig(params: List[TypeId], returns: TypeId)
case class PredicateSig(params: List[TypeId])

case class TypeCatalog(
  types: Set[TypeId],
  constants: Map[String, TypeId],
  functions: Map[SymbolName, FunctionSig],
  predicates: Map[SymbolName, PredicateSig]
)
```

Rule: each symbol has exactly one signature.

### 5.5 Runtime value model + static dispatcher (canonical)

Use heterogeneous, sort-tagged runtime values.

```scala
case class Value(sort: TypeId, raw: Any)
```

Function/predicate evaluation is delegated through a register-owned static dispatcher:

```scala
case class RuntimeModel(
  domains: Map[TypeId, Set[Value]],
  dispatcher: RuntimeDispatcher
)

trait RuntimeDispatcher:
  def evalFunction(name: SymbolName, args: List[Value]): Either[String, Value]
  def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean]
```

This removes the unsound `List[A]` assumption while keeping symbol-to-method binding explicit and register-owned.

### 5.6 Safety contract

- Binder guarantees argument/result sort consistency in `BoundQuery`
- Runtime startup validates dispatcher coverage against `TypeCatalog` (arity + declared type checks)
- Evaluation-time sort errors become infrastructure/configuration errors, not user query typing errors

### 5.7 Canonical runtime path

`parse -> bind -> evaluateTyped -> static dispatcher (register-owned symbol-to-method mapping)`

### 5.8 Single evaluator API

```scala
object TypedSemantics:
  def evaluate(
    query: BoundQuery,
    model: RuntimeModel,
    params: SamplingParams,
    hdr: HDRConfig
  ): Either[QueryError, EvaluationOutput[Value]]
```

This mirrors ADR-001 shape: one resolved/compiled IL, one evaluator.

---

## 6. Register-focused mapping

### 6.1 Recommended sort set (example)

- `Asset`
- `Risk`
- `Mitigation`
- `Portfolio`
- `Loss`
- `Probability`

### 6.2 Recommended symbol naming without overloading

- Functions:
  - `p95 : Asset -> Loss`
  - `p99 : Asset -> Loss`
  - `lec : Asset × Loss -> Probability`
- Predicates:
  - `leaf : Asset`
  - `has_risk : Asset × Risk`
  - `mitigates : Mitigation × Risk`
  - `gt_loss : Loss × Loss`
  - `gt_prob : Probability × Probability`

### 6.3 Query examples

```text
Q[>=]^{2/3} x (leaf(x), gt_loss(p95(x), 5000000))
Q[<=]^{1/4} x (leaf(x), gt_prob(lec(x, 10000000), 0.05))
```

---

## 7. Phased delivery options (no decision made)

## Option A — Minimal unblock (recommended for fast convergence)

Phase A1:
- `TypeCatalog`
- `QueryBinder` + `BoundQuery`
- type-check errors integrated into existing `QueryError` mapping path
- no hierarchy

Phase A2:
- `RuntimeModel` + `TypedSemantics`
- wire into register query endpoint

Pros:
- smallest conceptual surface
- directly unblocks query pane implementation
- ADR-aligned with minimal risk

Cons:
- subtype hierarchies deferred

## Option B — Add optional subtype hierarchy now

Add:
- `subtypeOf: Map[TypeId, Set[TypeId]]`
- assignability checks in binder

Pros:
- more expressive type lattice

Cons:
- more complexity in binder and diagnostics
- not required by current register examples

## Option C — Parser-level sort annotations now

Extend grammar with `x:Asset` style binders.

Pros:
- explicit typing in query text

Cons:
- touches OCaml-ported parser core semantics/surface
- larger user-facing syntax change
- likely unnecessary for first cut

---

## 8. Alignment with paper foundations

The original paper semantics already separates:

- symbolic syntax (`R(x,y)`, `φ(x,y)`) from
- interpretation/model evaluation (`M, v ⊨ φ`)

V2 preserves this by adding a formal bind/typecheck step before model evaluation. This is standard many-sorted FOL treatment and does not conflict with the foundational logic model.

---

## 9. Alignment with best practices in typed query languages

V2 aligns with established patterns (SQL planners, typed Datalog variants, static query analyzers):

1. parse into syntax tree
2. bind symbols against catalog
3. infer/check argument and variable sorts
4. compile to bound/typed plan
5. evaluate bound plan

Key best-practice properties satisfied:

- undeclared symbol rejection before evaluation
- deterministic type errors with source locations
- no hidden runtime coercions
- no overloaded symbol ambiguity in baseline
- one-way flow from untyped syntax to typed execution plan

---

## 10. ADR impact assessment (needs user decision)

### ADRs likely impacted in fol-engine

- ADR-001: updated — covers many-sorted query binding with `BoundQuery` as canonical IL ✓
- ADR-005: clarify how `ModelAugmenter` maps (or does not map) to `RuntimeModel`
- ADR-008: document coexistence of generic `D` path vs sorted `Value` path (or replacement)

### ADRs likely impacted in register

- ADR-028 and appendix: replace historical `Model[Any]` examples with bound sorted flow
- Query-pane implementation plan: remove stale `Set[Any]` conversion notes in favor of typed binding outputs

No ADR update should be done automatically; changes require user decision.

---

## 11. Locked decision record (canonical)

Companion review sheet: [MULTI-SORTED-TYPE-SYSTEM-V2-DECISION-SHEET.md](MULTI-SORTED-TYPE-SYSTEM-V2-DECISION-SHEET.md)

Decision status (locked):
- D1 selected: **B** — sorted path is primary; no parallel transition path.
- D2 selected: **A** — flat many-sorted model only; no subtyping planning unless requirements change.
- D3 selected: **A** — every literal is bound by argument-position sort derivation; ambiguity cannot arise structurally because `TypeCatalog` enforces one signature per symbol.
- D4 selected: **A** — explicit sort-specific comparison symbols; purge symbolic comparators from canonical tests/examples/templates.
- D5 selected: **B** — redesign augmentation around sorted runtime binding layer as canonical extension model.
- D6 selected: **A** — hardcoded dispatcher mode for runtime symbol binding (`lec` maps to one concrete register Scala method).

---

## 11a. Detailed Decision Record — D3 Literal Policy (ADR-ready)

This section is intentionally structured so it can be promoted into a standalone ADR later.

### Status

Accepted for V2 scope: **Option A**

### Context

- The parser emits untyped constants (`Term.Const("5000000")`, `Term.Const("0.05")`).
- The many-sorted binder must assign each literal a concrete sort before evaluation.
- The architecture explicitly rejects overloading: each symbol name has exactly one signature, enforced at `TypeCatalog` construction (`TypeCatalogError.NameCollision`).
- Every literal in a query appears as an argument to a predicate or function call.

### Decision

Bind every literal by **argument-position sort derivation**.

The binder processes all terms through `bindTermExpected(term, expected: TypeId, ...)`. The `expected` sort is always derived from the calling symbol's declared signature. Since `TypeCatalog` guarantees one signature per symbol, every argument position has exactly one possible sort — structurally, not as a runtime check.

Resolution rules for a literal string `s` at argument position expecting sort `S`:

1. If `s` is a declared constant in `catalog.constants` with sort `S` → `ConstRef(s, S)`. ✓
2. If `catalog.literalValidators` declares a validator for `S` and it accepts `s` → `ConstRef(s, S)`. ✓
3. Otherwise → `TypeCheckError.UnknownConstantOrLiteral(s)`. ✗

### Why There Is No `AmbiguousLiteral` Error

Ambiguity requires a literal to appear in a position where multiple sorts are possible. This cannot arise because:

- Every literal is an argument to a predicate or function.
- Every predicate/function has exactly one signature (TypeCatalog construction guarantee).
- Therefore the expected sort at every argument position is unique and statically determined.

No `AmbiguousLiteral` error type exists in `TypeCheckError`. The no-overloading invariant provides the guarantee at schema level rather than requiring a runtime detection path.

### Examples

- `gt_loss(p95(x), 5000000)` — `gt_loss : Loss × Loss`, literal resolves to `Loss` via validator.
- `gt_prob(lec(x, 10000000), 0.05)` — `gt_prob : Probability × Probability`, literal resolves to `Probability` via validator.

### Consequences

Positive:

- No query syntax overhead — no literal annotations required.
- Deterministic binder behaviour — one resolution path per argument position.
- No ambiguity error type or detection logic needed.
- Strong alignment with no-overloading policy.

Constraints:

- Catalog design must use sort-specific symbols (e.g. `gt_loss`, `gt_prob`) to preserve unique argument-position sorts.
- `literalValidators` must be declared for each sort that accepts inline literals.

### Code Smells (to avoid)

- Defining generic numeric predicates where sort-specific predicates are intended.
- Introducing hidden numeric coercions across sorts.
- Allowing multiple signatures for one symbol name.

### Implementation

- `QueryBinder.bindTermExpected` — literal resolution at argument position.
- `TypeCatalog` — `literalValidators: Map[TypeId, String => Boolean]` for format validation.
- `TypeCheckError.UnknownConstantOrLiteral` — emitted when no resolution path applies.

---

## 11d. Detailed Decision Record — D4 Symbol Naming (ADR-ready)

This section is intentionally structured so it can be promoted into a standalone ADR later.

### Status

Accepted for V2 scope: **Option A**

### Context

- V2 rejects overloading and subtyping in current scope.
- With fixed one-signature symbols, naming becomes the primary carrier of sort semantics.
- Symbolic comparators (`>`, `<`, `>=`, `<=`) are concise but visually imply generic/overloaded behavior.
- The user has chosen explicit sort-specific naming for clarity and consistency.

### Decision

Use explicit comparison symbols by sort domain, for example:

- `gt_loss : Loss × Loss -> Bool`
- `gt_prob : Probability × Probability -> Bool`

Rules:

1. Canonical query surface uses explicit sort-specific comparator names.
2. Do not use symbolic comparator names in canonical tests/examples/query templates.
3. If symbolic aliases exist temporarily, treat them as migration compatibility only, not canonical style.

### Decision Intent

Make sort semantics self-describing in query text and eliminate accidental ambiguity signals from symbolic operators.

### Examples

Canonical:

- `Q[>=]^{2/3} x (leaf(x), gt_loss(p95(x), 5000000))`
- `Q[<=]^{1/4} x (leaf(x), gt_prob(lec(x, 10000000), 0.05))`

Non-canonical (to purge from examples/tests):

- `Q[>=]^{2/3} x (leaf(x), >(p95(x), 5000000))`
- `Q[<=]^{1/4} x (leaf(x), >(lec(x, 10000000), 0.05))`

### Consequences

Positive:

- Query text communicates sort intent directly
- Stronger alignment with no-overloading policy
- Cleaner diagnostics and catalog readability

Trade-offs:

- Slightly longer query strings
- Requires consistent naming convention enforcement

### Alternatives Considered

Alternative B: keep symbolic comparator names with fixed single signatures.

Rejected for this phase because:

- It remains easy to misread as generic operator semantics
- It weakens the explicitness objective chosen for V2

### Code Smells (to avoid)

- Mixing symbolic and explicit comparator styles across tests/examples
- Defining explicit sort-specific comparators but documenting symbolic forms as canonical
- Silent aliasing that obscures the canonical query language style

### Implementation Notes

- Update canonical examples in docs and query templates to explicit comparators.
- Update binder/e2e tests to use explicit comparator symbols as default fixtures.
- If parser still accepts symbolic operators, keep tests that cover them isolated as compatibility tests, not canonical style tests.

---

## 11b. Detailed Decision Record — D1 Pipeline Stance (ADR-ready)

This section is intentionally structured so it can be promoted into a standalone ADR later.

### Status

Accepted for V2 scope: **Option B**

### Context

- V2 introduces a typed bind phase and sorted execution model.
- Running old and new pipelines in parallel would increase semantic and maintenance surface.
- The user has explicitly rejected a parallel transition approach for this effort.
- No external client compatibility constraints currently require dual-path support.

### Decision

Adopt the sorted path as the **primary pipeline now**.

Rules:

1. The binder/typechecker + bound sorted IL is the canonical path for query evaluation.
2. No strategic parallel "new + old" long-lived architecture is planned.
3. Migration work is accepted up front to avoid future dual-path drift.

### Decision Intent

Prefer architectural coherence and one canonical semantics path over incremental dual-path rollout.

### Consequences

Positive:

- One canonical evaluation architecture
- Lower long-term maintenance and testing complexity
- Stronger alignment with ADR-001 "one shared IL, one evaluator" principle

Trade-offs:

- Higher immediate migration/refactor effort
- Requires deliberate implementation sequencing to avoid temporary breakage

### Alternatives Considered

Alternative A: parallel paths during transition.

Rejected because:

- Creates an avoidable long-lived maintenance burden
- Increases risk of semantic drift between paths
- Conflicts with user direction for this decision

### Code Smells (to avoid)

- Keeping two production-grade pipelines with overlapping semantics
- Allowing old path behavior to diverge from sorted canonical behavior
- Deferring canonical-path migration decisions indefinitely

### Implementation Notes

- Treat sorted path as default/canonical in architecture docs and integration plans.
- If temporary compatibility shims are needed, mark them as migration-only and short-lived.
- Update related ADRs/implementation plans to remove long-lived parallel-path assumptions.

---

## 11c. Detailed Decision Record — D2 Subtyping Scope (ADR-ready)

This section is intentionally structured so it can be promoted into a standalone ADR later.

### Status

Accepted for V2 scope: **Option A**

### Context

- Current requirements are satisfied by flat many-sorted typing.
- Subtyping adds assignability rules and checker complexity not required by present use cases.
- The user has explicitly stated no subtyping planning for now unless requirements change.

### Decision

Use a **flat many-sorted model** with exact sort matching only.

Rules:

1. No subtype lattice is modeled in this phase.
2. Sort compatibility is equality-based (no implicit assignability).
3. Do not add hierarchy-focused design work unless explicit new requirements appear.

### Decision Intent

Maximize clarity and implementation simplicity for the first canonical sorted architecture.

### Consequences

Positive:

- Simpler binder/typechecker logic
- More predictable diagnostics
- Faster implementation and verification

Trade-offs:

- Less expressive abstraction for generalized parent-sort signatures
- Potential signature duplication for cross-sort shared semantics

### Alternatives Considered

Alternative B: include subtype hierarchy now.

Rejected for this phase because:

- Adds complexity without present requirement demand
- Introduces policy decisions (transitivity/conflicts/assignability behavior) prematurely

### Code Smells (to avoid)

- Introducing implicit subtype behavior without explicit requirement
- Designing hierarchy abstractions "just in case"
- Encoding pseudo-hierarchy conventions in symbol names without type-system support

### Implementation Notes

- Catalog model remains flat (`Set[TypeId]` + exact signature matching).
- Tests should assert exact-match behavior and clear mismatch errors.
- If future requirements demand hierarchy, handle as a new explicit ADR decision, not incremental drift.

---

## 11e. Detailed Decision Record — D5 Augmentation Strategy (ADR-ready)

This section is intentionally structured so it can be promoted into a standalone ADR later.

### Status

Accepted for V2 scope: **Option B**

### Context

- D1 selected canonical sorted architecture now (no long-lived parallel strategy).
- Existing augmentation model (`ModelAugmenter[D]`) is tied to the prior model/evaluator surface.
- V2 introduces catalog + binder + sorted runtime execution architecture.
- Maintaining two augmentation abstractions would add avoidable conceptual and maintenance overhead.

### Decision

Redesign augmentation around the sorted runtime model as the canonical extension model.

Rules:

1. Canonical symbol extension occurs through a register-owned static dispatcher validated against `TypeCatalog` signatures.
2. New domain functions/predicates are added by explicit dispatcher branches mapped to concrete Scala methods.
3. Existing augmenter abstractions are not the target architecture for sorted path documentation/tests.
4. Compatibility shims must be labeled non-canonical and excluded from primary docs/tests.

### Decision Intent

Align extension mechanism with the canonical sorted pipeline and avoid long-lived dual abstraction.

### Examples

Canonical style:

- Bind `p95 : Asset -> Loss` symbol to one concrete runtime method.
- Bind `lec : Asset × Loss -> Probability` symbol to one concrete runtime method.
- Bind `gt_loss : Loss × Loss -> Bool` symbol to one concrete runtime predicate method.

Non-canonical for V2 target architecture:

- Modeling new sorted behavior primarily through legacy augmenter composition wrappers.

### Consequences

Positive:

- One extension abstraction for canonical architecture
- Cleaner mental model and documentation
- Better alignment with D1/B and D4 explicit naming strategy

Trade-offs:

- Higher up-front migration/refactor effort
- Requires explicit migration notes where legacy augmentation still appears in code/docs

### Code Smells (to avoid)

- Documenting two extension models as equally canonical
- Introducing new sorted features through legacy-only augmentation APIs
- Hidden adapter layers that obscure where symbol signatures are actually validated

### Implementation Notes

- Runtime binding layer must validate implementation signatures against catalog at startup/build time.
- New examples/tests should show the selected D6 binding mode as default pattern.
- If temporary compatibility adapters are needed, mark them migration-only and non-canonical; keep them out of primary docs/tests.

---

## 11f. Detailed Decision Record — D6 Runtime Symbol Binding Mode (ADR-ready)

This section is intentionally structured so it can be promoted into a standalone ADR later.

### Status

Accepted for V2 scope: **Option A**

### Context

- Runtime evaluation must map symbols like `lec`, `p95`, and `gt_prob` to executable Scala code.
- Current integration scope has one concrete consumer (`register`) with known symbol set.
- User preference is explicit: avoid dynamic registration complexity and keep symbol mapping hardcoded.

### Decision

Use a **hardcoded static dispatcher** for runtime symbol binding in the current scope.

Rules:

1. Each symbol resolves through one explicit dispatcher branch.
2. `lec` maps to one concrete register Scala method implementation.
3. Dispatcher is validated against `TypeCatalog` signatures at startup.
4. Compatibility-only adapters are explicitly non-canonical and excluded from primary docs/tests.

### Decision Intent

Match architecture to present requirements and minimize avoidable abstraction overhead.

### Consequences

Positive:

- Smaller API surface
- Easier operational debugging (direct symbol-to-method traceability)
- Lower maintenance burden for single-consumer scope

Trade-offs:

- Lower extensibility for multi-consumer/plugin scenarios
- Symbol additions require source-level dispatcher changes

### Code Smells (to avoid)

- Generic registration DSL where only one static consumer exists
- Hidden symbol rebinding behavior at runtime
- Divergence between declared catalog symbols and dispatcher coverage

### Implementation Notes

- Keep dispatcher object simple and explicit (`match`/pattern dispatch).
- Add startup consistency checks: catalog symbol coverage + arity/sort conformance guards.
- Treat any non-dispatch compatibility path as migration-only and non-canonical.

---

## 12. Implementation sketch (aligned with selected D1-B)

1. Add new package `fol.typed`:
   - `TypeCatalog.scala`
   - `BoundQuery.scala`
   - `TypeCheckError.scala`
   - `QueryBinder.scala`
   - `RuntimeModel.scala`
   - `TypedSemantics.scala`

2. Add a new facade entry point:

```scala
object VagueSemantics:
  def evaluateTyped(
    query: ParsedQuery,
    catalog: TypeCatalog,
    model: RuntimeModel,
    answerTuple: Map[String, Value] = Map.empty,
    params: SamplingParams = SamplingParams.exact,
    hdrConfig: HDRConfig = HDRConfig.default
  ): Either[QueryError, EvaluationOutput[Value]]
```

3. Treat `evaluateTyped(...)` as canonical and migrate call sites/integration plans accordingly.
4. Use sorted runtime model with static dispatcher as canonical extension mechanism (D5-B + D6-A).
5. Compatibility-only note: map-based runtime wiring and dynamic installer flows are non-canonical and must be excluded from primary docs/tests.

---

## 13. Risk register (technical)

1. **Boundary drift risk**
   - if binder is optional, runtime type exceptions return
   - mitigation: make binder mandatory in sorted API

2. **Migration sequencing risk**
   - if canonical sorted adoption is attempted without ordered integration steps
   - mitigation: staged migration plan with compile/test gates per phase

3. **Catalog/model mismatch risk**
   - wrong runtime function implementation for declared signature
   - mitigation: startup validation pass for runtime binding layer (dispatcher coverage + signature checks)

4. **Error taxonomy inflation**
   - too many ad-hoc error types between repos
   - mitigation: map all binder/eval failures into existing `QueryError` family + register `FolQueryFailure` mapping table

---

## 14. Summary

V2 keeps the original proposal intact while tightening the core:

- mandatory bind/typecheck stage
- bound sorted IL as single execution plan
- runtime model design that fixes `List[A]` unsoundness
- no overloading
- minimal first-cut scope with explicit optional expansions

This provides a cleaner, more direct path to unblocking register query-pane typing while staying aligned with existing ADR foundations and paper-based semantics.
