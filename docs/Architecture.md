# Architecture Overview

Entry point for newcomers. Complements the ADRs with a structural map
of the codebase and the integration flow between layers.

---

## Package Map

```
src/main/scala/
├── logic/           Formula, Term, FOL, Model, Valuation
├── parser/          Combinators, FormulaParser, FOLAtomParser, TermParser
├── lexer/           Lexer (String → List[String])
├── semantics/       FOLSemantics, EvaluationContext, ModelAugmenter
├── printer/         FOLPrinter, PrinterUtil
├── util/            StringUtil
│
└── fol/             Vague quantifier extension (imports foundation ↑, never imported by it)
    ├── parser/      VagueQueryParser
    ├── logic/       ParsedQuery, Quantifier
    ├── query/       ResolvedQuery
    ├── semantics/   VagueSemantics, RangeExtractor, ScopeEvaluator, DomainExtraction
    ├── sampling/    HDRSampler, ProportionEstimator, SampleSizeCalculator, NormalApprox, SamplingParams
    ├── quantifier/  VagueQuantifier
    ├── result/      VagueQueryResult, EvaluationOutput
    ├── bridge/      FOLBridge, KnowledgeBaseModel, KnowledgeSourceModel,
    │                NumericAugmenter, ArithmeticAugmenter,
    │                ComparisonAugmenter, LiteralResolver
    ├── datastore/   KnowledgeBase[D], KnowledgeSource[D], Relation,
    │                RelationName, RelationTuple[D], DomainElement,
    │                DomainCodec, RelationValueValidation
    ├── error/       QueryError, QueryException
    └── examples/    CyberSecurityDomain, CyberSecurityExamples, VagueQuantifierDemo
```

---

## Layer Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│  PUBLIC API                                                     │
│  VagueQueryParser.parse(s)     → Either[QueryError, ParsedQuery]│
│  VagueSemantics.holds(q, src)  → Either[QueryError, Result]     │
│  VagueSemantics.evaluate(q, …) → Either[QueryError, Output]    │
│  ResolvedQuery.fromRelation(…)  → Either[QueryError, RQ]       │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────┼────────────────────────────────────┐
│  SHARED IL: ResolvedQuery  │                                    │
│  ┌─────────────────────────┴──────────────────────────────────┐ │
│  │ quantifier: VagueQuantifier                                │ │
│  │ elements:   Set[D]                                                │ │
│  │ predicate:  D => Boolean                                     │ │
│  │ params:     SamplingParams                                 │ │
│  │ hdrConfig:  HDRConfig                                      │ │
│  │                                                            │ │
│  │ .evaluate()           → VagueQueryResult                   │ │
│  │ .evaluateWithOutput() → EvaluationOutput                   │ │
│  └────────────────────────────────────────────────────────────┘ │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────┼────────────────────────────────────┐
│  EVALUATION PIPELINE       │                                    │
│                            ▼                                    │
│  ProportionEstimator.estimateWithSampling(…)                    │
│    ├── SampleSizeCalculator   N, params → n                     │
│    ├── HDRSampler.sample      population, n → sample            │
│    ├── filter by predicate    sample → k successes              │
│    └── Wilson CI + FPC        p, n, k, N → ProportionEstimate   │
│                                                                 │
│  VagueQueryResult.fromEstimate(vq, estimate, N)                 │
│    └── threshold check → satisfied: Boolean                     │
└─────────────────────────────────────────────────────────────────┘
                             │
┌────────────────────────────┼────────────────────────────────────┐
│  FOL FOUNDATION            │                                    │
│                            ▼                                    │
│  FOLSemantics.holds(formula, model, valuation) → Boolean        │
│  FormulaParser.parse(atomParser)(tokens) → (Formula, remaining) │
│  FOLPrinter.printFormula(formula) → String                      │
│  Model[D](domain, interpretation)                               │
│  Valuation[D](bindings: Map[String, D])                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## Two Entry Points, One Evaluator

Both the string parser and the programmatic API converge on `ResolvedQuery`:

| Entry Point | Phase 1 | Phase 2 | Phase 3 |
|---|---|---|---|
| String | `VagueQueryParser.parse(s)` → `ParsedQuery` | `VagueSemantics.toResolved()` → `ResolvedQuery` | `.evaluate()` |
| Programmatic | `ResolvedQuery.fromRelation(source, …)` | → `ResolvedQuery` | `.evaluate()` |

See [ADR-001](ADR-001.md) for the full trace diagram.

---

## Key Integration Points

**KB → FOL Model:**
`KnowledgeSourceModel.toModel(source)` converts a `KnowledgeSource[D]`
into `Model[D]`.  Since ADR-008, the model is fully generic — no
erasure to `Any`.  See [ADR-004](ADR-004.md) and [ADR-008](ADR-008.md).

The resulting model contains only relation-membership predicates and
identity constants. Numeric comparisons (`>`, `<`, `>=`, `<=`),
arithmetic, numeric literal resolution, and consumer-specific functions
require a `ModelAugmenter` — see below.

**Model Augmentation:**
`ModelAugmenter[D]` wraps `Model[D] => Model[D]` as a case class —
an endomorphism monoid under composition. Augmenters compose via
`andThen`, with `identity` as the unit. `NumericAugmenter` provides
built-in comparisons and numeric literal resolution. Consumers chain
domain-specific augmenters (e.g., simulation-backed functions) using
`andThen` or `ModelAugmenter.combine`. The case class wrapper allows
consumers to provide type class instances (e.g., ZIO Prelude `Identity`)
without fol-engine depending on any type class library.
See [ADR-005](ADR-005.md).

```
KnowledgeSourceModel.toModel(source)   →   KB-backed Model[D]
  │                                           (relations + constants only)
  └── modelAugmenter ──────────────────→   Augmented Model[D]
        │                                    (+ comparisons, numerics,
        ├── NumericAugmenter                   custom functions)
        └── consumer augmenter
```

**FOL Scope → Predicate Closure:**
`FOLBridge.scopeToPredicate[D](scope, variable, source, augmenter)` compiles
an FOL formula into `D => Boolean` by applying the augmenter
to the KB-backed model, then closing over the result. The closure calls
`FOLSemantics.holds()` per element.

**Range Extraction:**
`RangeExtractor.extractRange[D](source, query, substitution)` queries the
`KnowledgeSource[D]` with a pattern derived from the range predicate.
Returns `Set[D]` — the domain D_R.

**Sampling:**
`HDRSampler` (Fisher-Yates + counter-based PRNG) draws samples.
`SamplingParams.exact` forces n = N for deterministic full-domain
evaluation. See [ADR-003](ADR-003.md).

**Error Handling:**
Public methods return `Either[QueryError, A]`. Internal code throws
`QueryException`. Two `try/catch` boundaries in the public surface.
See [ADR-002](ADR-002.md).

---

## Architecture Decision Records

| ADR | Topic |
|---|---|
| [ADR-001](ADR-001.md) | Many-Sorted Query Binding — `BoundQuery` typed IL, catalog, binder, typed evaluator |
| [ADR-002](ADR-002.md) | Parser-Combinator Style — CPS, single Either boundary |
| [ADR-003](ADR-003.md) | HDR Deterministic Sampling — Fisher-Yates, reproducibility |
| [ADR-004](ADR-004.md) | Tagless Initial Architecture — ADTs + operations, layering |
| [ADR-005](ADR-005.md) | Model Augmentation — endomorphism monoid, numeric infra, extensibility |
| [ADR-007](ADR-007.md) | Preserve OCaml-Ported Parser Combinator Core |
| [ADR-008](ADR-008.md) | Domain Type Safety — generic `KnowledgeBase[D]` |
| [ADR-009](ADR-009.md) | Symmetric Relation Support via Schema Metadata |
| [ADR-010](ADR-010.md) | Typed Relation Names — `RelationName` Opaque Type |
| [ADR-012](ADR-012.md) | Error Channel Policy — `require` vs `Either` |
| [ADR-013](ADR-013.md) | Typed Result Projection — `TypeRepr[A]` for consumer domain extraction |

---

## Build

Scala 3.7.4, sbt 1.12.0-RC1, GraalVM Java 25.

No external runtime dependencies beyond `com.risquanter::hdr-rng`.
Test framework: munit 1.0.0.

```
sbt test          # 854 tests
sbt publishLocal  # com.risquanter::fol-engine:0.2.0-SNAPSHOT
```

---

## References

- Fermüller, C. G. et al. (2016). "Querying with Vague Quantifiers Using Probabilistic Semantics". *Int. J. Intelligent Systems*, 31(12).
- Harrison, J. (2009). *Handbook of Practical Logic and Automated Reasoning*. Cambridge University Press.
