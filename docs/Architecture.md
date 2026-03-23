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
├── semantics/       FOLSemantics, EvaluationContext
├── printer/         FOLPrinter, PrinterUtil
├── util/            StringUtil
│
└── fol/             Vague quantifier extension (imports foundation ↑, never imported by it)
    ├── parser/      VagueQueryParser
    ├── logic/       ParsedQuery, Quantifier
    ├── query/       UnresolvedQuery, ResolvedQuery
    ├── semantics/   VagueSemantics, RangeExtractor, ScopeEvaluator, DomainExtraction
    ├── sampling/    HDRSampler, ProportionEstimator, SampleSizeCalculator, NormalApprox, SamplingParams
    ├── quantifier/  VagueQuantifier
    ├── result/      VagueQueryResult, EvaluationOutput
    ├── bridge/      FOLBridge, KnowledgeBaseModel, KnowledgeSourceModel
    ├── datastore/   KnowledgeBase, KnowledgeSource, RelationValue, RelationValueUtil
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
│  UnresolvedQuery.resolve(src)  → ResolvedQuery                 │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────┼────────────────────────────────────┐
│  SHARED IL: ResolvedQuery  │                                    │
│  ┌─────────────────────────┴──────────────────────────────────┐ │
│  │ quantifier: VagueQuantifier                                │ │
│  │ elements:   Set[RelationValue]                             │ │
│  │ predicate:  RelationValue => Boolean                       │ │
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

Both the string parser and the typed DSL converge on `ResolvedQuery`:

| Entry Point | Phase 1 | Phase 2 | Phase 3 |
|---|---|---|---|
| String | `VagueQueryParser.parse(s)` → `ParsedQuery` | `VagueSemantics.toResolved()` → `ResolvedQuery` | `.evaluate()` |
| Typed DSL | Build `UnresolvedQuery` | `.resolve(source)` → `ResolvedQuery` | `.evaluate()` |

See [ADR-001](ADR-001.md) for the full trace diagram.

---

## Key Integration Points

**KB → FOL Model:**
`KnowledgeSourceModel.toModel(source)` converts a `KnowledgeSource`
into `Model[Any]`. `RelationValueUtil` handles `RelationValue ↔ Any`
conversion. See [ADR-004](ADR-004.md).

**FOL Scope → Predicate Closure:**
`FOLBridge.scopeToPredicate(scope, variable, source)` compiles an FOL
formula into `RelationValue => Boolean` by closing over the model and
substitution. The closure calls `FOLSemantics.holds()` per element.

**Range Extraction:**
`RangeExtractor.extractRange(source, query, substitution)` queries the
`KnowledgeSource` with a pattern derived from the range predicate.
Returns `Set[RelationValue]` — the domain D_R.

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
| [ADR-001](ADR-001.md) | Evaluation Path Unification — shared IL, end-to-end trace |
| [ADR-002](ADR-002.md) | Parser-Combinator Style — CPS, single Either boundary |
| [ADR-003](ADR-003.md) | HDR Deterministic Sampling — Fisher-Yates, reproducibility |
| [ADR-004](ADR-004.md) | Tagless Initial Architecture — ADTs + operations, layering |

---

## Build

Scala 3.7.4, sbt 1.12.0-RC1, GraalVM Java 25.

No external runtime dependencies beyond `com.risquanter::hdr-rng`.
Test framework: munit 1.0.0.

```
sbt test          # 792 tests
sbt publishLocal  # com.risquanter::fol-engine:0.1.0-SNAPSHOT
```

---

## References

- Fermüller, C. G. et al. (2016). "Querying with Vague Quantifiers Using Probabilistic Semantics". *Int. J. Intelligent Systems*, 31(12).
- Harrison, J. (2009). *Handbook of Practical Logic and Automated Reasoning*. Cambridge University Press.
