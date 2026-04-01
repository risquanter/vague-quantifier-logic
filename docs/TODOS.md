# TODOs

## ~~T-001 — Tagged type constructors for domain vs value type in the programmatic `TypeCatalog` API~~ ✅ Implemented in `0.7.0-SNAPSHOT`

**Status:** DONE. `DomainType(id)` / `ValueType(id)` ADT implemented in `fol/typed/TypeDefs.scala`.
`TypeCatalog.unsafe(types = Set(DomainType(asset), ValueType(loss)), ...)` — no `domainTypes` parameter.
`catalog.domainTypes` is a derived method. See ADR-014 §1.
