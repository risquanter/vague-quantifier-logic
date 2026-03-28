# TODOs

## T-001 — Tagged type constructors for domain vs value type in the programmatic `TypeCatalog` API

**Area:** `fol/typed/TypeCatalog.scala`

**Problem:** Declaring a type as a domain type currently requires two separate, independent parameters that can drift out of sync:

```scala
// today: two declarations, one implicit in `types`, one explicit in `domainTypes`
TypeCatalog.unsafe(
  types       = Set(asset, risk, loss, probability),
  domainTypes = Some(Set(asset, risk))   // if you forget risk here, no compile error
)
```

**Goal:** A single declaration site where the role (domain vs value) is expressed inline, making mismatches impossible and intent self-documenting:

```scala
// desired: one declaration, role encoded at construction
TypeCatalog.unsafe(
  types = Set(
    DomainType(asset),        // quantifiable, must have element set in RuntimeModel
    DomainType(risk),
    ValueType(loss),          // scalar attribute, quantification rejected at bind time
    ValueType(probability)
  ),
  predicates = ...
  // domainTypes derived automatically — no separate parameter
)
```

**Investigation scope:**
- Represent the tagged variants as an ADT (`sealed trait TypeDecl`, `case class DomainType(id: TypeId)`, `case class ValueType(id: TypeId)`) or as opaque type aliases (`opaque type DomainTypeId <: TypeId`)
- `TypeCatalog.unsafe(types: Set[TypeDecl], ...)` extracts `domainTypes` from the tags internally — the `Option[Set[TypeId]]` parameter is eliminated from the public API
- Predicate/function signature parameters (`PredicateSig`, `FunctionSig`) continue to use plain `TypeId` since role does not affect arity or sort-checking — only construction needs the tag
- Evaluate whether the ADT approach (simpler, no opaque machinery) is sufficient, or whether opaque types buy additional compile-time guarantees at the `BoundVar` / `BoundQuery` level
- Assess impact on `RuntimeModel.domains`: key type stays `TypeId`; the tag is only relevant at catalog construction time

**Reference:** ADR-014 §1–2; `fol/typed/TypeCatalog.scala`; `fol/typed/QueryBinder.scala`
