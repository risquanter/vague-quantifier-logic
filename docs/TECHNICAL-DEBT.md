# Technical Debt Register

Known limitations, design tensions, and deferred improvements.
Items here are acknowledged — not bugs, not forgotten.

---

## TD-001: KnowledgeSource Trait Returns Set/Throws Instead of Either

### Description

`KnowledgeSource` methods (`getDomain`, `query`, `contains`, etc.) return
bare `Set[D]` or throw exceptions for invalid inputs.  Callers at the
Either boundary (`ResolvedQuery.fromRelation`, `RangeExtractor.extractRange`)
must duplicate schema-existence checks (`hasRelation`) before calling into
the trait, because the trait itself does not surface errors in a typed way.

### Observation

Both entry points perform the same logical validation — "does this relation
exist?" — independently.  With DSL removal (ADR-011), the two check sites
are now `ResolvedQuery.fromRelation` and `RangeExtractor.extractRange` —
both co-located in the `fol` layer.  This is proportionate today (two call
sites), but would compound if additional entry points or `KnowledgeSource`
implementations are added.

### Preference

Close the gap.  The ideal direction is for `KnowledgeSource` methods that
can fail to return `Either[QueryError, A]` instead of throwing or returning
`Set.empty` for invalid inputs.  This would eliminate the need for
boundary-level pre-checks and make the error contract explicit in the type.

### Currently Known Options

1. **Change trait to return `Either`.**  Pros: typed errors everywhere,
   eliminates pre-checks.  Cons: large API change, breaks all implementors
   (`InMemoryKnowledgeSource`, future SQL/RDF sources), changes every call
   site.

2. **Add `Either`-returning variants alongside existing methods** (e.g.
   `getDomainSafe`, `querySafe`).  Pros: backward compatible.  Cons:
   doubled API surface, naming pollution, callers must choose which to use.

3. **Keep current approach** with boundary pre-checks.  Pros: minimal
   change, proportionate to current scale.  Cons: validation duplication,
   not self-documenting in the type.

This list may be incomplete.

---

## TD-002: ActiveDomain Has No Formal Counterpart — CLOSED

**Closed by:** ADR-011 (DSL Removal)

`DomainSpec.ActiveDomain` was deleted along with the typed DSL.
The `KnowledgeSource.activeDomain` method remains for FOL evaluation
(quantifier scope over full universe), but no programmatic entry point
exposes it as a quantification domain.  See ADR-011 §3.
