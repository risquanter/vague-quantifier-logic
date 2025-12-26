# Refactoring Analysis: FOL and Vague Domain Simplification

**Date**: December 26, 2025  
**Scope**: Identify simplification opportunities within tagless initial + continuation-passing style  
**Constraint**: Retain current architectural style (no fundamental paradigm shift)

## Executive Summary

Analysis of the FOL and vague domain classes reveals several opportunities for simplification through:
1. **Shared printer abstractions** (precedence, infix operators, parenthesization logic)
2. **Common evaluation patterns** (holds(), domain traversal, valuation management)
3. **Data conversion utilities** (RelationValue ↔ domain value transformations)
4. **Unified semantics interfaces** (standardizing model theory operations)

**Key Finding**: The codebase has good separation of concerns but lacks intermediate abstractions that would reduce duplication without changing the fundamental architectural style.

---

## 1. Current Architecture Assessment

### Strengths
✅ **Clean tagless initial encoding**: ADTs (enums/case classes) separate from operations  
✅ **Continuation-passing style**: Parser combinators with CPS threading  
✅ **OCaml heritage**: Consistent functional patterns from Harrison's "Handbook"  
✅ **Type safety**: Proper use of generics (Formula[A], Model[D])  
✅ **Separation of concerns**: Logic, parsing, printing, semantics in distinct modules

### Identified Duplication Patterns

#### Pattern 1: Precedence-based Printing (FOLPrinter)
```scala
// FOLPrinter.scala - Lines 71-91
private def infixPrec(op: String): Int = ...
private def printInfixTerm(op: String, left: Term, right: Term, prec: Int): String = ...
private def printInfix(opPrec: Int, op: String, left: Formula[FOL], right: Formula[FOL], prec: Int): String = ...
```

**Observation**: Precedence handling, parenthesization, and infix printing are generic patterns that could apply to:
- Term printing (arithmetic operators: +, -, *, /, ^)
- Formula printing (logical connectives: ∧, ∨, ⟹, ⟺)
- **Future**: Vague query printing (if/when needed)

**Issue**: Logic duplicated between term and formula printing.

#### Pattern 2: Model Evaluation (FOLSemantics & VagueSemantics)
```scala
// FOLSemantics.scala - Line 162
def holds[D](formula: Formula[FOL], model: Model[D], valuation: Valuation[D]): Boolean = ...

// ScopeEvaluator.scala - Line 42
def evaluateForElement(formula: Formula[FOL], element: RelationValue, variable: String, model: Model[Any], ...): Boolean =
  FOLSemantics.holds(formula, model, valuation)

// VagueSemantics.scala - Line 58
def holds(query: VagueQuery, kb: KnowledgeBase, answerTuple: Map[String, RelationValue], ...): VagueResult = ...
```

**Observation**: Three different `holds` signatures with overlapping concerns:
- FOLSemantics: Pure FOL evaluation
- ScopeEvaluator: FOL evaluation with RelationValue conversion
- VagueSemantics: Vague quantifier evaluation orchestration

**Issue**: RelationValue ↔ domain value conversion scattered across files.

#### Pattern 3: Data Conversions (RelationValue transformations)
```scala
// KnowledgeBaseModel.scala - Lines 64-70
val domainElements: Set[Any] = activeDomain.map {
  case RelationValue.Const(name) => name
  case RelationValue.Num(value) => value
}

// ScopeEvaluator.scala - Lines 47-50
val elementValue: Any = element match
  case RelationValue.Const(name) => name
  case RelationValue.Num(value) => value

// Query.scala - Lines 71-74, 91-94
val population: Set[A] = domainValues.map {
  case RelationValue.Const(name) => name.asInstanceOf[A]
  case RelationValue.Num(value) => value.asInstanceOf[A]
}
```

**Observation**: Identical pattern match for RelationValue → domain value conversion appears in 4+ places.

**Issue**: No shared utility for this common operation.

#### Pattern 4: Domain Extraction (RangeExtractor & Query)
```scala
// RangeExtractor.scala - Lines 53-76
def extractRange(kb: KnowledgeBase, query: VagueQuery, substitution: Map[String, RelationValue]): Set[RelationValue] = ...
private def buildPattern(range: FOL, quantifiedVar: String, substitution: Map[String, RelationValue]): List[Option[RelationValue]] = ...

// Query.scala - Lines 63-70
val domainValues = source.getDomain(relationName, position)
val population: Set[A] = domainValues.map { ... }
```

**Observation**: Both extract domains from knowledge sources but use different approaches:
- RangeExtractor: Works with FOL range predicates
- Query: Works with relation names and positions

**Issue**: Opportunity to unify domain extraction interface.

---

## 2. Refactoring Opportunities

### Opportunity 1: Shared Printer Utilities

**Create**: `printer/PrinterUtil.scala` (object with pure functions)

**Extract Common Patterns**:
```scala
object PrinterUtil:
  /** Generic precedence-based parenthesization
    * @param childPrec Precedence of child expression
    * @param parentPrec Precedence of parent context
    * @param expr String representation of child
    * @return expr with parens if needed
    */
  def parenthesize(childPrec: Int, parentPrec: Int, expr: String): String =
    if parentPrec > childPrec then s"($expr)" else expr
  
  /** Print binary infix operator with precedence
    * @param opPrec Operator precedence
    * @param op Operator symbol
    * @param left Left operand printer
    * @param right Right operand printer
    * @param contextPrec Context precedence
    * @param rightAssoc Is operator right-associative?
    * @return String representation
    */
  def printBinaryInfix(
    opPrec: Int,
    op: String,
    left: Int => String,  // Function: precedence -> string
    right: Int => String,
    contextPrec: Int,
    rightAssoc: Boolean = true
  ): String =
    val leftPrec = if rightAssoc then opPrec + 1 else opPrec
    val rightPrec = opPrec
    val leftStr = left(leftPrec)
    val rightStr = right(rightPrec)
    val result = s"$leftStr $op $rightStr"
    parenthesize(opPrec, contextPrec, result)
  
  /** Check if string is infix operator */
  def isInfixOp(name: String, operators: Set[String]): Boolean =
    operators.contains(name)
```

**Benefits**:
- Eliminates duplication between term and formula printing
- Provides tested utilities for future printers (vague queries)
- Clear separation of generic logic from domain-specific logic

**Impact**:
- Files: `FOLPrinter.scala`
- Tests: `FOLPrinterSpec.scala` (no changes, existing tests validate behavior)
- Risk: Low (pure functions, no state)

### Opportunity 2: RelationValue Conversion Utilities

**Create**: `vague/datastore/RelationValueUtil.scala`

**Extract Conversions**:
```scala
object RelationValueUtil:
  /** Convert RelationValue to domain value (Any)
    * Used when integrating KB data with FOL semantics.
    */
  def toDomainValue(rv: RelationValue): Any = rv match
    case RelationValue.Const(name) => name
    case RelationValue.Num(value) => value
  
  /** Convert domain value to RelationValue
    * Used when translating from FOL model to KB representation.
    */
  def fromDomainValue(value: Any): RelationValue = value match
    case s: String => RelationValue.Const(s)
    case i: Int => RelationValue.Num(i)
    case _ => throw IllegalArgumentException(s"Unsupported type: ${value.getClass}")
  
  /** Convert set of RelationValues to domain values */
  def toDomainSet(rvs: Set[RelationValue]): Set[Any] =
    rvs.map(toDomainValue)
  
  /** Convert set with type cast (for Query DSL) */
  def toDomainSetTyped[A](rvs: Set[RelationValue]): Set[A] =
    rvs.map(rv => toDomainValue(rv).asInstanceOf[A])
```

**Benefits**:
- Single source of truth for conversions
- Easy to extend if new RelationValue types added
- Clear documentation of conversion semantics

**Impact**:
- Files: `KnowledgeBaseModel.scala`, `ScopeEvaluator.scala`, `Query.scala`
- Tests: New `RelationValueUtilSpec.scala`, update existing tests to use utilities
- Risk: Low (replaces identical patterns)

### Opportunity 3: Unified Evaluation Interface

**Create**: `semantics/EvaluationContext.scala`

**Abstraction**:
```scala
/** Context for FOL formula evaluation
  * Encapsulates model, valuation, and common operations.
  */
case class EvaluationContext[D](
  model: Model[D],
  valuation: Valuation[D]
):
  /** Evaluate formula in this context */
  def holds(formula: Formula[FOL]): Boolean =
    FOLSemantics.holds(formula, model, valuation)
  
  /** Create new context with updated valuation */
  def withBinding(variable: String, value: D): EvaluationContext[D] =
    EvaluationContext(model, valuation.updated(variable, value))
  
  /** Evaluate term in this context */
  def evalTerm(term: Term): D =
    FOLSemantics.evalTerm(term, model.interpretation, valuation)

/** Extension methods for working with RelationValues */
extension (ctx: EvaluationContext[Any])
  /** Evaluate formula with RelationValue binding */
  def holdsWithRelationValue(formula: Formula[FOL], variable: String, value: RelationValue): Boolean =
    val domainValue = RelationValueUtil.toDomainValue(value)
    ctx.withBinding(variable, domainValue).holds(formula)
```

**Benefits**:
- Reduces parameter passing in ScopeEvaluator
- Clearer API for FOL evaluation
- Easier to add memoization/caching later

**Impact**:
- Files: `ScopeEvaluator.scala` (simplify method signatures)
- Tests: `ScopeEvaluatorSpec.scala` (update to use new API)
- Risk: Medium (changes call sites but preserves behavior)

### Opportunity 4: Domain Extraction Interface

**Unify**: Domain extraction patterns

**Current Duplication**:
- RangeExtractor: FOL-based extraction with pattern matching
- Query: Direct relation/position lookup

**Proposal**: Keep both (they serve different purposes) but add shared utilities:

```scala
/** Common domain extraction operations */
object DomainExtraction:
  /** Extract domain from relation at specific position
    * Used by both RangeExtractor and Query DSL.
    */
  def extractFromRelation(
    kb: KnowledgeBase,
    relationName: String,
    position: Int
  ): Set[RelationValue] =
    kb.getDomain(relationName, position)
  
  /** Extract active domain (all constants) */
  def extractActiveDomain(kb: KnowledgeBase): Set[RelationValue] =
    kb.activeDomain
  
  /** Apply pattern matching to extract domain
    * Used by RangeExtractor for FOL range predicates.
    */
  def extractWithPattern(
    kb: KnowledgeBase,
    relationName: String,
    pattern: List[Option[RelationValue]]
  ): Set[RelationValue] =
    val matchingTuples = kb.query(relationName, pattern)
    // Extract values at wildcard positions...
```

**Benefits**:
- Shared logic for domain extraction
- Clear separation of concerns
- Easier to add caching/optimization

**Impact**:
- Files: `RangeExtractor.scala`, `Query.scala`
- Tests: Existing tests should pass (behavior unchanged)
- Risk: Low (extraction, not refactoring)

---

## 3. Non-Opportunities (Things to Keep Separate)

### ❌ Don't Merge: Parser Combinators
**Reason**: Generic infrastructure already shared via `Combinators.scala`  
**Current State**: TermParser, FormulaParser, VagueQueryParser all use shared combinators  
**Verdict**: Already well-factored

### ❌ Don't Unify: FOL vs Vague Semantics
**Reason**: Different abstraction levels serving distinct purposes

**Mathematical Relationship**:
- Vague quantifiers **extend** FOL (not replace it)
- Range predicates `R(x,y')` are FOL formulas
- Scope formulas `φ(x,y)` are FOL formulas evaluated via Tarski semantics
- `ScopeEvaluator` explicitly calls `FOLSemantics.holds()` for scope evaluation

**Architectural Layering**:
- **FOLSemantics**: Foundation layer - pure Tarski semantics, boolean results, full domain evaluation
- **VagueSemantics**: Extension layer - orchestrates range extraction, sampling, scope evaluation (via FOL), proportion checking

**Why Keep Separate**:
- FOLSemantics is reusable for pure FOL reasoning (beyond vague quantifiers)
- VagueSemantics has additional concerns (sampling, statistics, probabilistic quantifiers)
- Clean interface: `ScopeEvaluator` is the integration boundary
- Separation reflects layered architecture, not mathematical independence

**Verdict**: Keep separate, they represent different layers of abstraction with clear composition boundaries

### ❌ Don't Abstract: Data Types (Term, Formula, VagueQuery)
**Reason**: These are domain-specific ADTs, not generic patterns  
**Current State**: Already tagless initial (data separate from operations)  
**Verdict**: Leave as-is

---

## 4. Incremental Migration Plan

### Phase 1: Low-Risk Utility Extraction (Week 1)
**Goal**: Extract pure utility functions with no API changes

**Steps**:
1. ✅ Create `printer/PrinterUtil.scala` with precedence utilities
2. ✅ Create `vague/datastore/RelationValueUtil.scala` with conversions
3. ✅ Update `FOLPrinter.scala` to use PrinterUtil
4. ✅ Update `KnowledgeBaseModel.scala`, `ScopeEvaluator.scala`, `Query.scala` to use RelationValueUtil
5. ✅ Run full test suite (all tests should pass unchanged)

**Tests to Verify**:
- `printer/FOLPrinterSpec.scala` (round-trip tests)
- `vague/semantics/ScopeEvaluatorSpec.scala`
- `vague/semantics/VagueSemanticsSpec.scala`
- `vague/datastore/KnowledgeBaseSpec.scala`

**Rollback**: Git revert (no API changes)

### Phase 2: Evaluation Context (Week 2)
**Goal**: Introduce EvaluationContext to simplify ScopeEvaluator

**Steps**:
1. ✅ Create `semantics/EvaluationContext.scala`
2. ✅ Update `ScopeEvaluator.scala` to use EvaluationContext internally
3. ✅ Keep existing public API intact (for backward compatibility)
4. ✅ Run ScopeEvaluatorSpec tests
5. ✅ Update VagueSemanticsSpec if needed

**Tests to Verify**:
- `vague/semantics/ScopeEvaluatorSpec.scala` (all existing tests)
- `vague/semantics/VagueSemanticsSpec.scala` (integration tests)

**Rollback**: Keep old methods as deprecated, remove after validation

### Phase 3: Domain Extraction Utilities (Week 3)
**Goal**: Add shared utilities for domain extraction (non-breaking)

**Steps**:
1. ✅ Create `vague/semantics/DomainExtraction.scala`
2. ✅ Update `RangeExtractor.scala` to use shared utilities where applicable
3. ✅ Update `Query.scala` to use shared utilities
4. ✅ Run RangeExtractorSpec and query-related tests

**Tests to Verify**:
- `vague/semantics/RangeExtractorSpec.scala`
- `vague/query/QuerySpec.scala` (if exists)

**Rollback**: Utilities are additions, not replacements

### Phase 4: Documentation & Examples (Week 4)
**Goal**: Document new utilities and update examples

**Steps**:
1. ✅ Add KDoc comments to all new utilities
2. ✅ Update `docs/Architecture.md` with refactoring rationale
3. ✅ Update examples in `examples/` to use new utilities (if beneficial)
4. ✅ Run all examples as smoke tests

---

## 5. Success Criteria

### Quantitative Metrics
- **Code Reduction**: Remove 100-150 lines of duplicate code
- **Test Coverage**: Maintain 100% of existing test coverage
- **No Regressions**: All existing tests pass without modification

### Qualitative Goals
- **Clarity**: New utilities have clear, focused responsibilities
- **Maintainability**: Future changes easier (e.g., adding new RelationValue types)
- **Consistency**: Similar operations use same abstractions

---

## 6. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Breaking existing API | Low | High | Keep public APIs unchanged, add new utilities alongside |
| Test failures | Low | Medium | Run tests after each incremental step |
| Performance regression | Very Low | Low | Utilities are simple wrappers, no algorithmic changes |
| Merge conflicts | Medium | Low | Work in feature branch, frequent rebases |

---

## 7. Out of Scope

The following are explicitly **not** part of this refactoring:
- ❌ Converting to tagless final (algebra-based) style
- ❌ Changing parser combinator infrastructure
- ❌ Unifying FOL and vague semantics evaluation
- ❌ Modifying data structures (Term, Formula, VagueQuery)
- ❌ Introducing new dependencies or frameworks
- ❌ Performance optimization (beyond what falls out naturally)

---

## 8. Next Steps

1. **Review & Approval**: Discuss this plan with team
2. **Create Feature Branch**: `feature/refactoring-utilities`
3. **Start Phase 1**: Low-risk utility extraction
4. **Iterate**: Gather feedback after each phase
5. **Document**: Update architecture docs as we go

---

## Appendix A: File Impact Summary

### New Files (4)
- `printer/PrinterUtil.scala` (~60 lines)
- `vague/datastore/RelationValueUtil.scala` (~40 lines)
- `semantics/EvaluationContext.scala` (~50 lines)
- `vague/semantics/DomainExtraction.scala` (~80 lines)

### Modified Files (5)
- `printer/FOLPrinter.scala` (refactor precedence logic)
- `vague/semantics/ScopeEvaluator.scala` (use EvaluationContext)
- `vague/semantics/RangeExtractor.scala` (use DomainExtraction)
- `vague/bridge/KnowledgeBaseModel.scala` (use RelationValueUtil)
- `vague/query/Query.scala` (use RelationValueUtil)

### Test Files to Update (4-6)
- `printer/FOLPrinterSpec.scala` (verify round-trip still works)
- `vague/semantics/ScopeEvaluatorSpec.scala` (verify behavior preserved)
- `vague/semantics/RangeExtractorSpec.scala` (verify extraction still correct)
- New: `printer/PrinterUtilSpec.scala`
- New: `vague/datastore/RelationValueUtilSpec.scala`
- New: `semantics/EvaluationContextSpec.scala`

---

## Appendix B: Code Samples

### Before: FOLPrinter precedence handling
```scala
// FOLPrinter.scala - Current implementation
private def printInfix(
  opPrec: Int, 
  op: String, 
  left: Formula[FOL], 
  right: Formula[FOL], 
  prec: Int
): String =
  val leftStr = printFormula(left, opPrec + 1)
  val rightStr = printFormula(right, opPrec)
  val result = s"$leftStr $op $rightStr"
  if prec > opPrec then s"($result)" else result
```

### After: Using PrinterUtil
```scala
// FOLPrinter.scala - Refactored
import printer.PrinterUtil.*

private def printInfix(
  opPrec: Int, 
  op: String, 
  left: Formula[FOL], 
  right: Formula[FOL], 
  prec: Int
): String =
  printBinaryInfix(
    opPrec,
    op,
    leftPrec => printFormula(left, leftPrec),
    rightPrec => printFormula(right, rightPrec),
    prec,
    rightAssoc = true
  )
```

### Before: RelationValue conversions (scattered)
```scala
// KnowledgeBaseModel.scala
val domainElements: Set[Any] = activeDomain.map {
  case RelationValue.Const(name) => name
  case RelationValue.Num(value) => value
}

// ScopeEvaluator.scala
val elementValue: Any = element match
  case RelationValue.Const(name) => name
  case RelationValue.Num(value) => value

// Query.scala
val population: Set[A] = domainValues.map {
  case RelationValue.Const(name) => name.asInstanceOf[A]
  case RelationValue.Num(value) => value.asInstanceOf[A]
}
```

### After: Unified utility
```scala
// All files use:
import vague.datastore.RelationValueUtil.*

// KnowledgeBaseModel.scala
val domainElements = toDomainSet(activeDomain)

// ScopeEvaluator.scala
val elementValue = toDomainValue(element)

// Query.scala
val population = toDomainSetTyped[A](domainValues)
```

---

## Conclusion

This refactoring focuses on **extracting shared patterns** into well-tested utilities while **preserving the tagless initial architecture**. The incremental approach ensures we can validate each step and rollback if needed. The result will be more maintainable code with clearer separation of concerns, without disrupting the proven architectural patterns.
