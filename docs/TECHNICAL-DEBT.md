# Technical Debt Register

Known limitations, design tensions, and deferred improvements.
Items here are acknowledged — not bugs, not forgotten.

---

## TD-001: KnowledgeSource Trait Returns Set/Throws Instead of Either

### Description

`KnowledgeSource[D]` methods (`getDomain`, `query`, `contains`, `count`)
return bare values or throw exceptions for invalid inputs.  Callers at
the Either boundary (`ResolvedQuery.fromRelation`,
`RangeExtractor.extractRange`) duplicate schema-existence checks
(`hasRelation`) before calling into the trait, because the trait itself
does not surface errors in a typed way.

### Behavioural Analysis

SQL analogy: three cases arise when accessing relational data.

| Case | SQL Analogy | `getDomain` | `query` | `contains` | `count` |
|---|---|---|---|---|---|
| **Relation exists, has rows** | `SELECT FROM users` (populated) | `Set(a,b,c)` ✓ | `Set(…)` ✓ | `true`/`false` ✓ | `N` ✓ |
| **Relation exists, zero rows** | `SELECT FROM users` (empty table) | `Set.empty` ✓ | `Set.empty` ✓ | `false` ✓ | `0` ✓ |
| **Relation does not exist** | `SELECT FROM nonexistent` | **Throws** ⚠ | `Set.empty` ✗ | `false` ✗ | `0` ✗ |

Case 1 and 2 are handled correctly by all methods.

Case 3 — the error case — is **inconsistent**:

- `getDomain` validates against the schema and **throws**
  `IllegalArgumentException`.  This is loud but untyped.
- `query`, `contains`, `count` delegate to `getFacts`, which returns
  `Set.empty` for unknown relations.  This is **silent corruption** —
  the caller gets a plausible-looking answer for a nonsensical question.

In practice, the boundary pre-checks (`hasRelation`) fire before any
of these methods are reached, so Case 3 is currently unreachable
through public entry points.  The risk is a future caller bypassing
the guard.

### Current Safeguards

There are 3 `hasRelation` guard checks across 2 files:

- `ResolvedQuery.fromRelation` — 1 check before `getDomain`
- `RangeExtractor.extractRange` — 2 checks (extractRangeBoolean,
  extractRangeSubstitution) before `getDomain`/`query`

These guards are tested and correct.  No production path reaches
Case 3.

### Alternatives

#### Option A: Change trait methods to return `Either[QueryError, A]`

```scala
trait KnowledgeSource[D]:
  def getDomain(name: RelationName, pos: Int): Either[QueryError, Set[D]]
  def query(name: RelationName, pattern: List[Option[D]]): Either[QueryError, Set[RelationTuple[D]]]
  def contains(name: RelationName, tuple: RelationTuple[D]): Either[QueryError, Boolean]
  def count(name: RelationName): Either[QueryError, Int]
  // hasRelation, activeDomain, relationNames — unchanged (cannot fail)
```

**Pros:**
- Case 3 becomes impossible to ignore — the type forces handling
- Eliminates all boundary pre-check duplication
- Fixes the inconsistency: all four methods report missing relations
  the same way
- Callers compose naturally with `flatMap`/for-comprehensions
- Future `KnowledgeSource` impls (SQL, RDF) can surface connection
  errors, timeouts, etc. through the same channel

**Cons:**
- Breaking change to trait + all implementors
- `InMemoryKnowledgeSource` wraps every return in `Right(…)` —
  more verbose for the in-memory case where failure is rare
- Ripple through ~15 files: `FOLBridge`, `ScopeEvaluator`,
  `DomainExtraction`, etc. must handle Either at every call
- Test fixtures become noisier (`.getOrElse(fail(...))` everywhere)
- Solves a problem that is currently unreachable through public APIs

**Estimated scope:** ~15 files, moderate migration.

#### Option B: Add safe variants alongside existing methods

```scala
trait KnowledgeSource[D]:
  def getDomain(name: RelationName, pos: Int): Set[D]                       // existing
  def getDomainE(name: RelationName, pos: Int): Either[QueryError, Set[D]]  // new
```

**Pros:**
- Backward compatible — no existing code breaks
- New callers can opt into typed errors
- Incremental migration possible

**Cons:**
- Doubled API surface (8 methods → 16)
- Two ways to do the same thing — cognitive overhead for implementors
- Old callers have no incentive to migrate
- Doesn't fix the silent-corruption inconsistency in the original
  methods unless they are individually migrated
- Future implementors must implement both variants

**Estimated scope:** ~4 files (trait + impl + 2 boundary call sites).

#### Option C: Keep current approach (boundary pre-checks)

No code changes.  `hasRelation` guards remain at `ResolvedQuery.fromRelation`
and `RangeExtractor.extractRange`.

**Pros:**
- Zero cost, zero risk
- Proportionate to current scale (2 files, 3 checks)
- Guards are tested and correct — Case 3 is unreachable today
- No API churn for a problem that has never manifested

**Cons:**
- Convention-enforced, not type-enforced — a future caller could
  bypass the guard
- `query`/`contains`/`count` still silently accept nonexistent
  relations (inconsistent with `getDomain`)
- Duplication grows linearly with new entry points
- Future `KnowledgeSource` impls would inherit the inconsistency

#### Option D: Make `getFacts` throw (consistency fix only)

Change `KnowledgeBase.getFacts` to throw on unknown relations, matching
`getDomain`'s behavior.  No trait signature change.

```scala
def getFacts(relationName: RelationName): Set[RelationTuple[D]] =
  facts.getOrElse(relationName,
    throw new IllegalArgumentException(s"Unknown relation: ${relationName.value}"))
```

**Pros:**
- Fixes the Case 3 inconsistency — all four methods now throw
- Smallest possible change (1 line)
- No API signature change, no ripple
- Boundary guards already prevent this path, so behavior is unchanged
  for all current callers
- A future caller who forgets the guard gets a loud exception instead
  of silent corruption

**Cons:**
- Still uses exceptions, not typed errors — `getDomain`'s existing
  problem extends to all methods rather than being fixed
- Does not help future SQL/RDF implementations surface typed errors
- Doesn't eliminate guard duplication

**Estimated scope:** 1 line in `KnowledgeBase.scala`.

### Decision

**Option A** — migrate `KnowledgeBase` and `KnowledgeSource` to
`Either[QueryError, A]`.  See ADR-012 (Error Channel Policy) for
the guiding principle.

### Scope

**In scope:**

| # | Change | Files |
|---|--------|-------|
| P1 | `KnowledgeBase` — 3 throwing methods (5 throw sites) + 3 silent methods → Either | `KnowledgeBase.scala` |
| P2 | `KnowledgeBase.query` — prevalidate pattern length before `matches` | `KnowledgeBase.scala` |
| P3 | `KnowledgeSource` trait + `InMemoryKnowledgeSource` — signatures → Either | `KnowledgeSource.scala` |
| P4 | `DomainExtraction` — propagate Either from source calls | `DomainExtraction.scala` |
| P5 | `ResolvedQuery.fromRelation` — remove `hasRelation` guard, use Either directly | `ResolvedQuery.scala` |
| P6 | `RangeExtractor` — remove 2 `hasRelation` guards, use source Either directly | `RangeExtractor.scala` |
| P7 | `KnowledgeBaseModel` / `KnowledgeSourceModel` — handle Either from `contains` | `KnowledgeBaseModel.scala`, `KnowledgeSourceModel.scala` |
| P8 | `VagueSemantics.toResolved` — eliminate throw-to-catch trampoline, compose Either | `VagueSemantics.scala` |
| P9 | Tests — update ~50+ test sites | `*Spec.scala` |
| P10 | Demo/example sites | `examples/` |

**Out of scope:**

| Item | Reason |
|------|--------|
| Bridge augmenters (Arithmetic / Comparison / Numeric) | Closures flow through `Interpretation[D]` into OCaml core (`FOLSemantics`); requires ADR-007-governed signature change. Separate TD item if desired. |
| `require()` sites (RelationName, RelationTuple, Relation, VagueQuantifier, Quantifier, sampling) | Construction-time invariants — ADR-012 §1 |
| `RelationTuple.matches` require | Belt-and-suspenders behind P2 validation — ADR-012 §3 |
| OCaml-ported code (ADR-007 Tiers 1–3: parser, lexer, logic, semantics/FOLSemantics, printer, util/StringUtil) | ADR-007 |

### Method Signatures — Before/After

#### KnowledgeBase[D]

| Method | Before | After |
|---|---|---|
| `getRelation` | `Option[Relation]` | unchanged |
| `hasRelation` | `Boolean` | unchanged |
| `getFacts` | `Set[RelationTuple[D]]` | unchanged (returns `Set.empty` for unknown) |
| `contains` | `Boolean` | `Either[QueryError, Boolean]` |
| `query` | `Set[RelationTuple[D]]` | `Either[QueryError, Set[RelationTuple[D]]]` |
| `getDomain` | `Set[D]` *(throws)* | `Either[QueryError, Set[D]]` |
| `count` | `Int` | `Either[QueryError, Int]` |
| `addRelation` | `KnowledgeBase[D]` *(throws)* | `Either[QueryError, KnowledgeBase[D]]` |
| `addFact` | `KnowledgeBase[D]` *(throws)* | `Either[QueryError, KnowledgeBase[D]]` |
| `addFacts` | `KnowledgeBase[D]` *(throws)* | `Either[QueryError, KnowledgeBase[D]]` |
| `activeDomain` | `Set[D]` | unchanged |
| `totalFacts` | `Int` | unchanged |
| `stats` | `String` | unchanged |

#### KnowledgeSource[D] (trait)

| Method | Before | After |
|---|---|---|
| `hasRelation` | `Boolean` | unchanged |
| `getRelation` | `Option[Relation]` | unchanged |
| `contains` | `Boolean` | `Either[QueryError, Boolean]` |
| `getDomain` | `Set[D]` | `Either[QueryError, Set[D]]` |
| `query` | `Set[RelationTuple[D]]` | `Either[QueryError, Set[RelationTuple[D]]]` |
| `count` | `Int` | `Either[QueryError, Int]` |
| `activeDomain` | `Set[D]` | unchanged |
| `relationNames` | `Set[RelationName]` | unchanged |

#### Error Types Used (all pre-existing in `QueryError`)

- `RelationNotFoundError(name, availableRelations)` — unknown relation
- `SchemaError(message, name, expectedArity, actualArity)` — arity mismatch, position out of bounds
- `DataStoreError(message, operation, relation)` — duplicate relation in `addRelation`

### Implementation Phases

**Phase 1 — Core:** P1 + P2 + P3 (KnowledgeBase + KnowledgeSource)
**Phase 2 — Propagation:** P4 + P5 + P6 + P7 + P8 (callers)
**Phase 3 — Tests & demos:** P9 + P10

### Priority

Medium.  Eliminates the behavioral inconsistency (Case 3), removes
3 `hasRelation` guard duplications, eliminates the throw-to-catch
trampoline in `VagueSemantics`, and prepares the trait for future
implementations (SQL, RDF) where typed errors are essential.

---

## TD-002: ActiveDomain Has No Formal Counterpart — CLOSED

**Closed by:** ADR-011 (DSL Removal)

`DomainSpec.ActiveDomain` was deleted along with the typed DSL.
The `KnowledgeSource.activeDomain` method remains for FOL evaluation
(quantifier scope over full universe), but no programmatic entry point
exposes it as a quantification domain.  See ADR-011 §3.
