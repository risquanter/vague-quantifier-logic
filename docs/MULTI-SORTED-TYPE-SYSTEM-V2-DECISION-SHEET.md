# Multi-Sorted Type System V2 — Decision Sheet

**Purpose:** record locked V2 decisions with minimal context, examples, and trade-offs.  
**Scope:** architectural decisions only; selected options are canonical unless explicitly marked compatibility-only.

---

## How to use

For each decision:
1. Read context and selected option
2. Validate consistency with ADRs and paper semantics
3. Record any drift as MUST-FIX or SHOULD-FIX

Template:
- Decision: Dn
- Selected option: A/B
- Rationale:

---

## D1 — Pipeline stance

Detailed rationale: see section `11b. Detailed Decision Record — D1 Pipeline Stance (ADR-ready)` in [MULTI-SORTED-TYPE-SYSTEM-V2.md](MULTI-SORTED-TYPE-SYSTEM-V2.md).

### Context
Two pipeline strategies are possible after introducing many-sorted binding:
- keep existing generic evaluator path and add a sorted path in parallel
- promote sorted path as primary and phase out old path

### Example
- Current path: parsed query -> existing generic flow -> evaluate
- New path: parsed query -> binder/typechecker -> bound query -> sorted evaluate

### Option A — Parallel paths (additive)
**Pros**
- Lower migration risk
- Easier incremental rollout
- Existing integrations keep working during transition

**Cons**
- Ongoing maintenance cost for two paths
- Risk of semantic drift over time
- Larger test matrix

### Option B — Sorted-primary path (migration)
**Pros**
- Single canonical semantics path
- Less long-term complexity
- Stronger architecture coherence with typed binding

**Cons**
- Higher near-term migration effort
- Requires deliberate cutover plan
- More up-front coordination across docs/tests

---

## D2 — Subtype hierarchy in first implementation

Detailed rationale: see section `11c. Detailed Decision Record — D2 Subtyping Scope (ADR-ready)` in [MULTI-SORTED-TYPE-SYSTEM-V2.md](MULTI-SORTED-TYPE-SYSTEM-V2.md).

### Context
Many-sorted core can be flat or include subtyping (for example Asset <: Node).

### Example
- Flat: predicate expects Node only if Node is explicitly used
- Hierarchical: predicate expecting Node can accept Asset and Risk if declared as subtypes

### Option A — No hierarchy initially
**Pros**
- Simpler binder and error model
- Faster implementation
- Fewer edge cases in assignability checks

**Cons**
- Less expressive type lattice
- Some models may duplicate predicate signatures

### Option B — Include hierarchy now
**Pros**
- More expressive and reusable signatures
- Better fit for domains with general/specific sort families

**Cons**
- More complexity in type checking and diagnostics
- More decisions required on transitivity/conflict rules

---

## D3 — Literal policy

Detailed rationale: see section `11a. Detailed Decision Record — D3 Literal Policy (ADR-ready)` in [MULTI-SORTED-TYPE-SYSTEM-V2.md](MULTI-SORTED-TYPE-SYSTEM-V2.md).

### Context
Literal values (for example 100, 0.05) must be assigned a concrete sort by the binder. All literals appear as arguments to predicates or functions. Since each symbol has exactly one signature (`TypeCatalog` enforces this at construction), every argument position has a unique expected sort — derived structurally, not by runtime inference.

### Example
- Query: `gt_loss(p95(x), 100)` — `gt_loss : Loss × Loss`, literal `100` is in a `Loss`-expected position, resolves via `literalValidators`.

### Option A — Argument-position sort derivation (implemented)
**Pros**
- Keeps query syntax compact
- Ambiguity cannot arise structurally — no runtime detection needed
- No `AmbiguousLiteral` error type needed or present

**Cons**
- Requires `literalValidators` to be declared per sort
- Catalog must use non-overloaded, sort-specific symbols

### Option B — Require explicit annotation when ambiguous
**Pros**
- Deterministic and explicit typing

**Cons**
- Unnecessary given structural guarantee of Option A
- Requires annotation design and parser support

---

## D4 — Symbol naming for comparisons

### Context
No overloading is allowed; comparison semantics must stay sort-specific and explicit.

### Example
- Explicit names: gt_loss(Loss, Loss), gt_prob(Probability, Probability)
- Legacy-style shared name with single declared signature would reject non-matching sort usage

### Option A — Explicit names now
**Pros**
- Clear and unambiguous semantics
- Better diagnostics
- No overload resolution complexity

**Cons**
- Slightly longer query text
- Requires naming standard adoption

### Option B — Keep legacy names with one signature each
**Pros**
- Familiar query surface for users used to symbolic operators
- Minimal naming churn at first

**Cons**
- Harder to scale across many numeric/entity sorts
- Higher risk of confusion when one symbol name appears to imply generic behavior

---

## D5 — Augmentation strategy

Detailed rationale: see section `11e. Detailed Decision Record — D5 Augmentation Strategy (ADR-ready)` in [MULTI-SORTED-TYPE-SYSTEM-V2.md](MULTI-SORTED-TYPE-SYSTEM-V2.md).

### Context
Current fol-engine uses compositional model augmentation. Sorted runtime requires a canonical static binding layer.

### Example
- Existing style: compose augmenters into model interpretation
- New sorted style: bind symbols through static dispatcher branches validated against catalog signatures

### Option A — Keep existing augmenter for legacy path; add separate sorted runtime binding layer
**Pros**
- Minimal disruption
- Clear transition path
- Preserves current extension model while sorted path matures

**Cons**
- Two extension abstractions during transition
- More documentation burden

### Option B — Redesign augmentation around sorted runtime model
**Pros**
- Single extension abstraction in target architecture
- Better conceptual alignment with binder/catalog model

**Cons**
- Larger refactor now
- Higher migration risk

---

## D6 — Runtime symbol binding mode

Detailed rationale: see section `11f. Detailed Decision Record — D6 Runtime Symbol Binding Mode (ADR-ready)` in [MULTI-SORTED-TYPE-SYSTEM-V2.md](MULTI-SORTED-TYPE-SYSTEM-V2.md).

### Context
The sorted evaluator must resolve symbols like `lec`, `p95`, and `gt_prob` to executable Scala code in register.

### Example
- Query uses `lec(x, y)`
- Runtime should dispatch `lec` to one concrete register method implementation

### Option A — Hardcoded dispatcher (static mapping)
**Pros**
- Minimal API surface and lower cognitive load
- Deterministic one-symbol-to-one-method mapping
- Best fit for single known consumer (`register`)

**Cons**
- Less reusable for broader multi-consumer extension scenarios
- Symbol additions require source edits in dispatcher implementation

---

## Canonical sequencing (locked)

1. D1-B (sorted-primary pipeline)
2. D2-A (no hierarchy first)
3. D3-A (argument-position sort derivation — ambiguity structurally prevented by TypeCatalog)
4. D4-A (explicit names)
5. D5-B (sorted runtime extension model)
6. D6-A (hardcoded dispatcher)

Rationale: smallest canonical surface with one runtime path and no dynamic dispatch drift.

---

## Decision log

- D1: [ ] A  [x] B  — Rationale: adopt sorted-primary architecture now; reject parallel transition path.
- D2: [x] A  [ ] B  — Rationale: no subtyping in current scope; do not plan/design for hierarchy unless requirements explicitly change.
- D3: [x] A  [ ] B  — Every literal appears as an argument to a predicate or function; `TypeCatalog` enforces one signature per symbol, so the expected sort at every argument position is uniquely determined. Ambiguity cannot arise structurally. Catalog design uses sort-specific symbols (`gt_loss`, `gt_prob`) to preserve this guarantee.
- D4: [x] A  [ ] B  — Rationale: use explicit sort-specific comparison symbols (`gt_loss`, `gt_prob`); purge symbolic operators (`>`, `<`, `>=`, `<=`) from canonical tests/examples/query templates for consistency.
- D5: [ ] A  [x] B  — Rationale: redesign augmentation around the sorted runtime model as the primary and canonical extension abstraction.
- D6: [x] A  [ ] B  — Rationale: bind sorted symbols through a static, hardcoded dispatcher in register (`lec` -> concrete Scala method) to match current single-consumer scope and avoid registration overengineering.
