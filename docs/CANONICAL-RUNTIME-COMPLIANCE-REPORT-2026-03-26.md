# Canonical Runtime Compliance Report (2026-03-26)

## Scope

Verification and remediation for:
- Canonical runtime path: parse -> bind -> evaluateTyped -> static dispatcher
- Canonical extension mode: D6-A only
- Compatibility-only paths (map-based runtime wiring, dynamic installer) excluded from primary docs/tests

## Baseline references

- Initial design goals: IMPLEMENTATION_PLAN.md (paper-compliant vague quantifier semantics)
- Paper semantics mapping: docs/VagueQuantifiers.md
- ADR line (fol-engine): ADR-001, ADR-004, ADR-005, ADR-007, ADR-008, ADR-010, ADR-012
- Active V2 planning docs:
  - docs/MULTI-SORTED-TYPE-SYSTEM-V2.md
  - docs/MULTI-SORTED-TYPE-SYSTEM-V2-DECISION-SHEET.md

## Findings and remediation

### MUST-FIX-01
Issue: Primary V2 plan described map-based runtime function/predicate maps as the main execution binding model.

Risk: Drift from approved canonical dispatcher path and reintroduction of dynamic-binding design pressure.

Remediation: Fixed.
- Reframed runtime architecture to static dispatcher as canonical in V2 section 5.5.
- Added explicit canonical runtime path statement in section 5.7.

### MUST-FIX-02
Issue: Decision text still exposed dynamic registration language in primary path framing.

Risk: Non-canonical path remains normalized in core planning documents.

Remediation: Fixed.
- D5 and D6 records updated to hardcoded dispatcher-first rules.
- Dynamic/registry alternatives removed from primary D6 planning surface.
- Compatibility-only language made explicit and exclusion from primary docs/tests added.

### MUST-FIX-03
Issue: Dynamic installer artifacts remained in primary test surface.

Risk: Test suite continues to validate non-canonical architecture.

Remediation: Fixed.
- Removed src/main/scala/fol/sorting/RuntimeModelBuilder.scala.
- Removed src/test/scala/fol/sorting/RuntimeModelBuilderSpec.scala.

### SHOULD-FIX-01
Issue: Decision sequencing section previously recommended transitional options inconsistent with selected decisions.

Risk: Planning ambiguity and accidental re-expansion of scope.

Remediation: Fixed.
- Decision sheet sequencing is now locked to D1-B, D2-A, D3-A, D4-A, D5-B, D6-A.

### SHOULD-FIX-02
Issue: Decision-sheet usage still implied open selection workflow despite locked decisions.

Risk: Process confusion and avoidable iteration churn.

Remediation: Fixed.
- Updated decision sheet purpose/how-to-use for locked-decision validation and drift reporting.

## Verification against initial design goals

Status: PASS.

- Paper-compliant query semantics remain intact (quantifier semantics and range/scope interpretation unchanged).
- The canonical path now explicitly preserves parse-boundary semantics and inserts bind/typecheck before typed evaluation.
- Static symbol-to-method binding is now documented as register-owned and deterministic.

## Verification against ADRs

Status: PASS with one implementation follow-up note.

- ADR-001 (many-sorted query binding): `BoundQuery` is the canonical IL; `evaluateTyped` is the canonical entry point.
- ADR-004 (ADTs + operation layers): preserved; dispatcher is operational layer, not data-model overloading.
- ADR-007 (parser core preservation): preserved; no parser-core mutation introduced.
- ADR-012 (Either boundaries): unchanged and still aligned at public boundaries.

Follow-up note:
- ADR-005 legacy composition remains relevant for legacy path, but sorted canonical path is now unambiguously dispatcher-led in planning docs.

## Verification against original paper semantics

Status: PASS.

The paper-aligned semantics in docs/VagueQuantifiers.md remain preserved:
1. Parse symbolic query form.
2. Resolve/bind query components.
3. Evaluate range/scope under model interpretation.
4. Compute proportion and quantifier satisfaction.

The dispatcher decision affects execution binding mechanics only; it does not alter paper-level truth conditions or vague quantifier semantics.

## Final state

- Canonical plan path is now explicit and consistent across V2 documents.
- Dynamic installer path has been removed from primary code/tests.
- Compatibility-only paths are explicitly marked non-canonical and excluded from primary docs/tests.

## Validation run

Focused JVM tests run after remediation:
- fol.typed.TypeCatalogSpec
- fol.typed.QueryBinderSpec
- fol.semantics.VagueSemanticsTypedSpec
- fol.semantics.VagueSemanticsSpec

Result: PASS (32 passed, 0 failed).
