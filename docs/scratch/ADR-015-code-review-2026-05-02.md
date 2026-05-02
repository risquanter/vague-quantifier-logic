# ADR-015 Code Consistency Review — 2026-05-02

**Reviewer:** Internal (Phase 6 automated review)  
**VQL commit at review time:** `4d76d5b` (Phase 5b GREEN — `TypeRepr`/`LiteralValue` deleted)  
**Plan:** `PLAN-symmetric-value-boundaries.md`, Phase 6 Step 6.3  
**Outcome:** ADR-015 promoted to Accepted (2026-05-02) after one ⚠️ drift adopted back into §4.

---

## Methodology

Each row of the ADR-015 Implementation table was compared against the corresponding source file.  
✅ = ADR matches code.  
⚠️ = Divergence found; see notes column.

---

## Results

| ADR-015 Implementation Row | Source File | Finding | Notes |
|---|---|---|---|
| `fol/typed/TypeDefs.scala` — `opaque type TypeId`, `opaque type SymbolName`, `enum TypeDecl` | `core/src/main/scala/fol/typed/TypeDefs.scala` | ✅ | `trait TypeRepr[A]` and `enum LiteralValue` hard-deleted (Phase 5b). |
| `fol/typed/BoundQuery.scala` — `ConstRef(name, sort)` / `LiteralRef(sourceText, sort, value: Any)` | `core/src/main/scala/fol/typed/BoundQuery.scala` | ⚠️ → ✅ | **Drift**: ADR-015 §4 originally showed single-node `ConstRef(name, sort, raw: Any)` (3-field). Code (Phase 3/ADR-016) has `ConstRef(name, sort)` + `LiteralRef(sourceText, sort, value)`. Code is correct; §4 amended before acceptance. |
| `fol/typed/RuntimeModel.scala` — `RuntimeDispatcher.evalFunction: Either[String, Any]`, `Extract[A]` typeclass | `core/src/main/scala/fol/typed/RuntimeModel.scala` | ✅ | `Value.as[A]` extension deleted (Phase 5b); `evalFunction` returns `Either[String, Any]`; scaladoc references `Extract[A]`. |
| `fol/typed/QueryBinder.scala` — `literalValidators: Map[TypeId, String => Option[Any]]` | `core/src/main/scala/fol/typed/QueryBinder.scala` | ✅ | Validator map keyed on `TypeId`; returns raw `Any`. |
| `fol/typed/TypeCatalog.scala` / `KnowledgeBase` — `constants: Map[String, TypeId]` | `core/src/main/scala/fol/typed/TypeCatalog.scala` | ✅ | Named constant registry separate from literal validators. |
| `fol/runtime/Extract.scala` — `trait Extract[A]` with `def extract(v: Any): Option[A]` | `core/src/main/scala/fol/runtime/Extract.scala` | ✅ | Symmetric extraction boundary; library provides `given Extract[Long]`, `given Extract[Double]`. |

---

## Summary

- **6 rows reviewed**: 5 ✅ immediately; 1 ⚠️ resolved by ADR-015 §4 amendment in this commit.
- The ⚠️ drift (`ConstRef` 3-field → 2-node split per ADR-016) is **not a code defect**: the code accurately implements the ADR-016 design decision. The ADR-015 §4 sketch was stale. ADR-015 §4 updated to reflect `ConstRef(name, sort)` + `LiteralRef(sourceText, sort, value)`.
- No test failures or compilation errors at time of review.

---

## Cross-ADR check

| Cross-reference in ADR-015 | Status |
|---|---|
| ADR-001 (many-sorted binding) | Referenced, not superseded. ✅ |
| ADR-008 (`KnowledgeBase[D]`) | Referenced, not superseded. ✅ |
| ADR-016 (`ConstRef`/`LiteralRef` split) | Cited in amended §4. ✅ |

---

*Review archived per plan §8 requirement.*
