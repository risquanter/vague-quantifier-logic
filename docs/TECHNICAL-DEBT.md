# Technical Debt Register

Known limitations, design tensions, and deferred improvements.
Items here are acknowledged — not bugs, not forgotten.

---

## TD-001: KnowledgeSource Trait Returns Set/Throws Instead of Either

### Description

`KnowledgeSource` methods (`getDomain`, `query`, `contains`, etc.) return
bare `Set[D]` or throw exceptions for invalid inputs.  Callers at the
Either boundary (`UnresolvedQuery.resolve`, `RangeExtractor.extractRange`)
must duplicate schema-existence checks (`hasRelation`) before calling into
the trait, because the trait itself does not surface errors in a typed way.

### Observation

Both entry points perform the same logical validation — "does this relation
exist?" — independently.  This is proportionate today (two call sites), but
would compound if additional entry points or `KnowledgeSource` implementations
are added.

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

## TD-002: ActiveDomain Has No Formal Counterpart

### Description

`DomainSpec.ActiveDomain` in the typed DSL specifies the query domain as
"all values in the entire knowledge base, regardless of relation."  This has
no counterpart in the FOL string parser grammar — every FOL query requires
an explicit range predicate `R(x, ...)` that names a relation.

### Semantic Smell

The typed DSL was designed as a typed equivalent of the FOL string path, not
a superset.  `ActiveDomain` makes the DSL strictly richer than the formal
language, creating a semantic surface that:

- Cannot be expressed in the paper's formalism (Definition 2 requires a
  named range predicate R)
- Cannot be reached through the FOL string entry point
- Has no precedent in the evaluation-path-unification architecture (ADR-001)

The empty-domain semantics (vacuously-false result, `domainSize = 0`) are
correctly unified — `ResolvedQuery.evaluate()` handles empty D_R identically
regardless of source.  The concern is not behavioral correctness but
**semantic alignment** with the formal model.

### Status

Flagged for design review.  No prescriptive action recorded.
