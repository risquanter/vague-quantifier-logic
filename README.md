# First-Order Logic Parser with Vague Quantifiers

A Scala 3 implementation of first-order logic with support for **vague quantifiers** ("about half", "most", "at least 3/4"), translated from John Harrison's OCaml code and extended with the probabilistic semantics from Fermüller et al. (2016).

## Features

### Core FOL Parser

✅ **Complete FOL parser** with:
- Terms: variables, constants, functions, 6 levels of infix operators
- Formulas: quantifiers (∀, ∃), logical connectives (∧, ∨, ⟹, ⟺), negation
- Predicates and infix relations (=, <, ≤, >, ≥)

✅ **Parser combinator infrastructure**:
- Generic `parseGinfix` for iterated infix operators
- Left and right associativity support
- Precedence handling through subparser composition

✅ **Parametric formula parser**:
- Generic over atom types
- Works with propositional logic, FOL, or custom logics

✅ **FOL utilities**:
- Free variable analysis (fvt, fvFOL)
- Substitution with capture avoidance
- Variable renaming and universal closure

✅ **Pretty printer**:
- Converts parsed formulas back to readable strings
- Handles operator precedence and parenthesization
- Round-trip property (parse → print → parse preserves meaning)

✅ **Formal semantics (Model Theory)**:
- Tarski semantics for FOL
- Term evaluation in models
- Formula satisfaction (M,v ⊨ φ)
- Semantic entailment
- Example models: integer arithmetic, boolean algebra

### Vague Quantifiers

✅ **Parser for vague quantifier queries**:
- Paper syntax: `Q[op]^{k/n} x (R, φ)(y)`
- Operators: About (~), AtLeast (≥), AtMost (≤)
- Custom tolerance: `Q[~]^{1/2}[0.05]`
- Complex scope formulas with nested quantifiers

✅ **Vague query semantics**:
- Proportion-based evaluation: Prop_D(S, φ)
- Quantifier satisfaction with tolerance
- Exact and sampling evaluation modes
- Statistical confidence intervals

✅ **Knowledge base integration**:
- Relational datastore for facts
- Range extraction (D_R) from relations
- Scope evaluation using FOL semantics
- Answer variable substitution

✅ **Practical examples**:
- Cybersecurity risk management domain
- 4 working demonstration queries
- Boolean and unary query types
- Real-world data and results

## Quick Start

### Basic FOL Parsing

```scala
import parser.FOLParser

// Parse simple predicates
val p1 = FOLParser.parse("P(x)")

// Parse quantified formulas
val p2 = FOLParser.parse("forall x . exists y . x < y")

// Parse complex formulas with arithmetic
val p3 = FOLParser.parse("forall x y z . x < y /\\ y < z ==> x < z")

// Parse arithmetic relations
val p4 = FOLParser.parse("2 * x + 3 = y")

// Pretty print formulas
import printer.FOLPrinter
val formula = FOLParser.parse("forall x y. x < y ==> exists z. x < z /\\ z < y")
println(FOLPrinter.print(formula))
```

### Vague Quantifiers

```scala
import vague.parser.VagueQueryParser
import vague.semantics.VagueSemantics
import vague.examples.CyberSecurityDomain

// Parse a vague quantifier query
val query = VagueQueryParser.parse(
  "Q[>=]^{3/4} x (asset(x), exists r . (has_risk(x, r) /\\ critical_risk(r)))"
)

// Exact evaluation (use entire range - deterministic)
val domain = CyberSecurityDomain.kb
val result = VagueSemantics.holdsExact(query, domain)

println(s"Satisfied: ${result.satisfied}")
println(s"Proportion: ${result.actualProportion}")
println(s"Range size: ${result.rangeSize}")

// Sampling evaluation (for large datasets - probabilistic)
val sampledResult = VagueSemantics.holdsWithSampling(
  query, domain,
  sampleSize = 1000,
  seed = Some(42L)  // Optional: for reproducibility
)

// Run complete demo
// sbt "runMain vague.examples.demo"
```

### Example Query: Cybersecurity Risk Assessment

**Query**: "At least 3/4 of assets have critical risks"

```scala
Q[>=]^{3/4} x (asset(x), exists r . (has_risk(x, r) /\ critical_risk(r)))
```

**Result**: ✅ SATISFIED (86% of assets have critical risks)

**Note**: All examples use exact evaluation (`holdsExact`). Sampling evaluation (`holdsWithSampling`) is available for large datasets but not currently demonstrated.

See `src/main/scala/vague/examples/` for more examples.

## Project Structure

```
src/main/scala/
├── logic/
│   ├── Term.scala          # Term data type (Var, Fn)
│   ├── Formula.scala       # Polymorphic formula type
│   ├── FOL.scala           # FOL atoms (predicates)
│   └── FOLUtil.scala       # Utilities (free vars, substitution)
├── util/
│   └── StringUtil.scala    # String utilities (explode, implode)
├── lexer/
│   └── Lexer.scala         # Tokenization
├── parser/
│   ├── Combinators.scala   # Parser combinators (parseGinfix, etc.)
│   ├── SimpleExpr.scala    # Example: arithmetic expression parser
│   ├── FormulaParser.scala # Generic formula parser
│   ├── TermParser.scala    # FOL term parser
│   ├── FOLAtomParser.scala # FOL atom parser
│   └── FOLParser.scala     # Public API
├── printer/
│   └── FOLPrinter.scala    # Pretty printer
├── semantics/
│   └── FOLSemantics.scala  # Tarski semantics for FOL
└── vague/                   # Vague quantifier implementation
    ├── logic/
    │   ├── Quantifier.scala      # About/AtLeast/AtMost quantifiers
    │   └── VagueQuery.scala      # Query ADT: Q[op]^{k/n} x (R, φ)(y)
    ├── parser/
    │   └── VagueQueryParser.scala # Parser for vague queries
    ├── datastore/
    │   ├── Relation.scala        # Relational schema
    │   └── KnowledgeBase.scala   # In-memory fact store
    ├── semantics/
    │   ├── RangeExtractor.scala  # Extract D_R from KB
    │   ├── ScopeEvaluator.scala  # Evaluate φ with FOL semantics
    │   └── VagueSemantics.scala  # Main evaluation orchestrator
    ├── sampling/
    │   └── Sampling.scala        # Statistical sampling utilities
    └── examples/
        ├── CyberSecurityDomain.scala   # Realistic domain data
        └── CyberSecurityExamples.scala # Demo queries

src/test/scala/             # Comprehensive test suite (270+ vague tests)
```

## Implementation Phases

### Core FOL (Completed)

- ✅ Core types, utilities, lexer, parser combinators
- ✅ Simple expression parser (learning exercise)
- ✅ Generic formula parser
- ✅ Term parser (6 operator levels)
- ✅ FOL atom parser
- ✅ Public API
- ✅ Utility functions (free vars, substitution, renaming)
- ✅ Pretty printer
- ✅ Formal semantics (model theory)

### Vague Quantifiers (Completed)

- ✅ Core ADTs (Quantifier, VagueQuery) - 69 tests
- ✅ Range extraction from knowledge base - 21 tests
- ✅ Scope evaluation with FOL semantics - 26 tests
- ✅ Vague semantics orchestration - 15 tests
- ✅ Parser for paper syntax - 41 tests
- ✅ Cybersecurity examples - 4 working demos
- ✅ Documentation

**Total: 270 vague quantifier tests passing + 4 working examples**

## Running Tests

```bash
# Run all tests
sbt test

# Run only vague quantifier tests
sbt "testOnly vague.*"

# Run specific test suite
sbt "testOnly vague.parser.VagueQueryParserSpec"
```

All tests should pass:
- **358 FOL tests**: Parsing, utilities, pretty printing, semantics
- **270 vague tests**: Quantifiers, queries, evaluation, parsing

## Running Examples

```bash
# Run cybersecurity risk management demo
sbt "runMain vague.examples.demo"
```

The demo will show:
- Domain summary (assets, risks, mitigations)
- 4 example queries with results
- Boolean and unary query evaluation
- Answer set extraction

## Documentation

- **[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)**: Detailed implementation phases and architecture
- **[docs/VagueQuantifiers.md](docs/VagueQuantifiers.md)**: Theory, semantics, and API reference
- **Source code**: Comprehensive scaladoc on all public APIs

## Educational Value

This project demonstrates:
- **OCaml to Scala translation** patterns
- **Parser combinator** design
- **Precedence and associativity** handling without parser generators
- **Parametric polymorphism** in parser design
- **Functional programming** techniques
- **Variable capture avoidance** in substitution
- **Tarski semantics** for first-order logic
- **Model theory** implementation
- **Vague quantifier semantics** from database theory
- **Proportion-based query evaluation**
- **Statistical sampling** for large datasets
- **Knowledge base** design and querying foundations

## References

1. **Harrison, J.** (2009). *Handbook of Practical Logic and Automated Reasoning*. Cambridge University Press. ISBN: 978-0-521-89957-4.
   - OCaml parser combinator implementation
   - FOL syntax and semantics foundations

2. **Fermüller, C. G., Hofer, M., & Ortiz, M.** (2016). Querying with Vague Quantifiers Using Probabilistic Semantics. *Proceedings of the 25th International Conference on Information and Knowledge Management (CIKM)*, TU Vienna, Austria.
   - Vague quantifier semantics: Q[op]^{k/n}
   - Sampling-based probabilistic evaluation
