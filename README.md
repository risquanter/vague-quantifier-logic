# First-Order Logic Parser

A Scala 3 implementation of a first-order logic parser, translated from John Harrison's OCaml code in "Handbook of Practical Logic and Automated Reasoning".

## Features

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

## Quick Start

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
└── semantics/
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
    ├── SimpleExpr.scala    # Example: arithmetic expression parser
    ├── FormulaParser.scala # Generic formula parser
    ├── TermParser.scala    # FOL term parser
    ├── FOLAtomParser.scala # FOL atom parser
    └── FOLParser.scala     # Public API

src/test/scala/             # Comprehensive test suite (195+ tests)
```

## Implementation Phases

- ✅ Phase 1-4: Core types, utilities, lexer, combinators
- ✅ Phase 5: Simple expression parser (learning exercise)
- ✅ Phase 6: Generic formula parser
- ✅ Phase 7: Term parser (6 operator levels)
- ✅ Phase 8: FOL atom parser
- ✅ Phase 9: Public API
- ✅ Phase 10: Utility functions (free vars, substitution, renaming)
- ✅ Phase 11: Pretty printer
- ✅ Phase 12: Formal semantics (model theory)types (Term, Formula, FOL)
## Running Tests

```bash
sbt test
```

All 358 tests should pass, covering:
- Parsing (terms, formulas, quantifiers)
- Utilities (free variables, substitution)
- Pretty printing (round-trip property)
- Semantics (term evaluation, formula satisfaction)

