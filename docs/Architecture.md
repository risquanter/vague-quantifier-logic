# Architecture Documentation

## Overview

This project implements a first-order logic (FOL) system with vague quantifiers, following the mathematical framework from Fermüller et al. (2016) "Querying with Vague Quantifiers Using Probabilistic Semantics". The implementation uses a **tagless initial** style with OCaml-inspired patterns from Harrison's "Handbook of Practical Logic and Automated Reasoning" (2009).

## Architectural Approach

### Tagless Initial Style

The codebase separates data structures (ADTs) from operations:

- **Data**: Enums and case classes define the structure (`Formula`, `Term`, `RelationValue`)
- **Operations**: Module-level functions operate on these structures (`FOLSemantics.holds()`, `FOLPrinter.printFormula()`)

This differs from:
- **Tagless Final**: Operations as type class methods, data represented by constraints
- **OO Style**: Operations as methods on the data structures themselves

Benefits:
- Clear separation of concerns
- Easy to add new operations without modifying data types
- Pattern matching for structural analysis
- Natural fit for mathematical/logical operations

### Continuation-Passing Style Parser

The parser uses CPS combinators for composability:

```scala
def parse[A](f: (A, String) => Unit, error: String => Unit): String => Unit
```

This enables:
- Explicit control flow
- Natural error handling
- Easy composition of parsers

## Layer Architecture

### 1. FOL Foundation (`logic/`, `semantics/`)

**Core ADTs**:
- `Formula`: Recursive structure for logical formulas
- `Term`: Variables, constants, function applications
- `Model[D]`: Tarski semantics (domain, interpretation)
- `Valuation`: Variable → domain value binding

**Operations**:
- `FOLSemantics.holds(formula, model, valuation)`: Truth evaluation
- `FOLParser.parseFormula(input)`: String → Formula
- `FOLPrinter.printFormula(formula)`: Formula → String

### 2. Vague Extension (`vague/`)

Extends FOL with probabilistic quantifiers while preserving FOL foundation.

**Bridge Layer** (`vague/bridge/`):
- `KnowledgeBaseModel`: Adapts KB to FOL Model[Any]
- `RelationValue`: KB type (Const | Num) ↔ FOL type (Any)

**Semantics Layer** (`vague/semantics/`):
- `ScopeEvaluator`: Evaluates vague quantifiers by calling FOL semantics
- `RangeExtractor`: Extracts quantifier domains from KB
- `DomainExtraction`: Utilities for KB domain queries

**Data Layer** (`vague/datastore/`):
- `KnowledgeBase`: Stores facts, provides query operations
- `RelationValue`: Type-safe representation of KB constants

**Query Layer** (`vague/query/`):
- `Query`: User-facing DSL for vague quantification
- `VagueQuantifier`: Quantifier definitions (most, few, some, etc.)

Integration flow:
```
Query DSL → ScopeEvaluator → EvaluationContext → FOLSemantics.holds()
```

## Refactoring: Utility Extraction (2024)

### Motivation

The codebase originally contained significant duplication:
- Precedence logic duplicated in printer
- RelationValue ↔ Any conversions scattered across files
- FOL evaluation required manual valuation management
- Domain extraction logic repeated in multiple files

A comprehensive refactoring created reusable utilities while preserving the tagless initial architecture.

### Utilities Created

#### 1. `printer/PrinterUtil` (Phase 1)

**Purpose**: Generic precedence and infix operator handling for printers.

**Functions**:
```scala
def parenthesize(childPrec: Int, parentPrec: Int, expr: String): String
def printBinaryInfix(opPrec: Int, op: String, left, right, contextPrec: Int, rightAssoc: Boolean): String
def isInfixOp(name: String, infixOps: Set[String]): Boolean
```

**Consumers**: `FOLPrinter` (formula and term printing)

**Benefits**:
- Eliminated ~40 lines of duplicated precedence logic
- Centralized associativity handling
- Reusable for future printer extensions (TPTP format, etc.)

#### 2. `vague/datastore/RelationValueUtil` (Phase 1)

**Purpose**: Type-safe conversion between KB and FOL type systems.

**Functions**:
```scala
def toDomainValue(rv: RelationValue): Any          // Const→String, Num→Int
def fromDomainValue(value: Any): RelationValue     // Reverse with validation
def toDomainSet(rvs: Set[RelationValue]): Set[Any] // Bulk conversion
def toDomainSetTyped[A](rvs): Set[A]               // With type cast
def toDomainList(rvs): List[Any]                   // Order-preserving
```

**Consumers**: `KnowledgeBaseModel`, `ScopeEvaluator`, `Query`

**Design Rationale**:
- KB uses `RelationValue` for type safety (Const | Num)
- FOL semantics uses `Any` for generality
- Utilities provide single source of truth for conversion

**Before**:
```scala
activeDomain.map {
  case RelationValue.Const(name) => name
  case RelationValue.Num(value) => value
}
```

**After**:
```scala
RelationValueUtil.toDomainSet(activeDomain)
```

#### 3. `semantics/EvaluationContext` (Phase 2)

**Purpose**: Wrapper for FOL evaluation that simplifies common patterns.

**Core API**:
```scala
case class EvaluationContext[D](model: Model[D], valuation: Valuation[D]):
  def holds(formula: Formula): Boolean
  def withBinding(variable: String, value: D): EvaluationContext[D]
  def evalTerm(term: Term): D
  def domain: Domain[D]
  def isBound(variable: String): Boolean
```

**Extension Methods** (KB Integration):
```scala
extension (ctx: EvaluationContext[Any])
  def holdsWithRelationValue(formula, variable, value: RelationValue): Boolean
  def holdsWithRelationValues(formula, bindings: Map[String, RelationValue]): Boolean
```

**Design Rationale**:
- **Generic `D`**: Works with any domain type (Int, String, Any)
- **Extension method constraint**: `holdsWithRelationValue` only for `EvaluationContext[Any]` to avoid type parameter conflicts with KB operations
- **Immutable context chaining**: `withBinding` returns new context

**Consumers**: `ScopeEvaluator` (primary), any code evaluating FOL formulas

**Impact on ScopeEvaluator**:

Before (15 lines):
```scala
val elementValue: Any = element match {
  case RelationValue.Const(name) => name
  case RelationValue.Num(value) => value
}
val valuation = Valuation(Map(variable -> elementValue) ++ substitution)
FOLSemantics.holds(formula, model, valuation)
```

After (3 lines):
```scala
val ctx = EvaluationContext(model, substitution)
ctx.holdsWithRelationValue(formula, variable, element)
```

#### 4. `vague/semantics/DomainExtraction` (Phase 3)

**Purpose**: Reusable operations for extracting domains from knowledge bases.

**Functions**:
```scala
def extractFromRelation(kb, relationName, position): Set[RelationValue]
def extractActiveDomain(kb): Set[RelationValue]
def extractWithPattern(kb, relationName, pattern): Set[RelationValue]
def extractFromPatternAtPosition(kb, relationName, pattern, position): Set[RelationValue]
def extractFromPatternAtPositions(kb, relationName, pattern, positions): Set[Set[RelationValue]]
def domainSize(kb, relationName, position): Int
def activeDomainSize(kb): Int
```

**Consumers**: `RangeExtractor`, any code querying KB domains

**Design Pattern**: Combines KB query + projection in single operation

**Before**:
```scala
val matchingTuples = kb.query(predicate, pattern)
matchingTuples.map(_.values(position))
```

**After**:
```scala
DomainExtraction.extractFromPatternAtPosition(kb, predicate, pattern, position)
```

### Migration Strategy

**Incremental Phases**:
1. **Phase 1**: Low-risk utilities (PrinterUtil, RelationValueUtil)
2. **Phase 2**: Context wrapper (EvaluationContext)
3. **Phase 3**: Domain extraction (DomainExtraction)
4. **Phase 4**: Documentation + comprehensive testing

**Validation**: After each phase, run full test suite (628 tests) to ensure zero regressions.

**Results**:
- ✅ ~100 lines of duplicate code eliminated
- ✅ 529 lines of reusable utilities created
- ✅ All 628 tests passing throughout
- ✅ Cleaner APIs (e.g., ScopeEvaluator simplified)

### Non-Changes (Architectural Constraints)

The refactoring explicitly preserved:
- **Tagless initial style**: No conversion to tagless final
- **CPS parser combinators**: Parser architecture unchanged
- **FOL/vague separation**: Vague layer still calls FOL layer (no merging)
- **Existing test contracts**: All test assertions unchanged

## Integration Patterns

### FOL ↔ KB Type Bridging

```scala
// KB → FOL
val domainSet: Set[Any] = RelationValueUtil.toDomainSet(kb.activeDomain)
val model: Model[Any] = KnowledgeBaseModel.fromKnowledgeBase(kb)

// FOL → KB
val rvSet: Set[RelationValue] = domainSet.map(RelationValueUtil.fromDomainValue)
```

### Vague Quantifier Evaluation

```scala
// High-level Query DSL
val query = Query.select("x").where(kb).vague(VagueQuantifier.Most, "y").in("person")
  .satisfy(Formula.Atom(FOL("knows", List(Term.Var("x"), Term.Var("y")))))

// Internal: ScopeEvaluator
val ctx = EvaluationContext(model, substitution)
val satisfyingElements = rangeElements.count { elem =>
  ctx.holdsWithRelationValue(scopeFormula, variable, elem)
}
val degree = satisfyingElements.toDouble / rangeElements.size

// Bottom: FOL evaluation
FOLSemantics.holds(formula, model, valuation)
```

### Context Chaining for Quantifiers

```scala
// Simulate ∀x. ∃y. P(x, y)
val ctx = EvaluationContext.empty(model)
model.domain.elements.forall { x =>
  val ctxWithX = ctx.withBinding("x", x)
  model.domain.elements.exists { y =>
    ctxWithX.withBinding("y", y).holds(formula)
  }
}
```

## Testing Strategy

### Test Structure

- **Unit tests**: Test individual functions in isolation (`PrinterUtilSpec`, `RelationValueUtilSpec`, `EvaluationContextSpec`)
- **Integration tests**: Test interactions between components (`MySuite`, `FOLSemanticsSpec`, `ScopeEvaluatorSpec`)
- **Framework**: munit 1.0.0

### Coverage

- **628 integration tests**: Full system behavior
- **~70 utility unit tests**: Direct utility testing
- **Total**: ~698 tests

### Validation Pattern

After each refactoring phase:
1. Run `sbt test`
2. Verify all existing tests pass (zero regressions)
3. Add utility-specific unit tests
4. Verify new tests pass

## Build Configuration

**sbt 1.11.7** with Scala 3.7.4

**Dependencies**:
- `org.apache.commons:commons-math3:3.6.1` (probability distributions)
- `org.scalatest:munit:1.0.0` (testing)
- `pt.unl.fct.di:simulation.util:0.8.0` (utilities)

**JVM Compatibility** (Java 25):
```scala
javaOptions ++= Seq(
  "--add-opens=java.base/java.lang=ALL-UNNAMED",
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
  // ... suppress sun.misc.Unsafe warnings
)
Test / fork := true
```

## Future Extensions

Potential areas for growth:
- **TPTP format**: Export FOL formulas in TPTP syntax (can reuse PrinterUtil)
- **More quantifiers**: Additional vague quantifiers (approximately, nearly all, etc.)
- **Optimization**: Cache domain extractions, memoize evaluations
- **Visualization**: Formula AST rendering, evaluation traces

The utility extraction provides a foundation for these extensions without requiring further refactoring of core logic.

## References

- Fermüller, C. G., Kosheleva, O., Kreinovich, V., & Wang, J. (2016). "Querying with Vague Quantifiers Using Probabilistic Semantics". *International Journal of Intelligent Systems*, 31(12), 1164-1188.
- Harrison, J. (2009). *Handbook of Practical Logic and Automated Reasoning*. Cambridge University Press.
- Tarski, A. (1944). "The Semantic Conception of Truth and the Foundations of Semantics". *Philosophy and Phenomenological Research*, 4(3), 341-376.
