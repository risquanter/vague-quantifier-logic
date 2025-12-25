# Vague Quantifiers: Theory, Implementation, and Usage

This document provides comprehensive documentation for the vague quantifier implementation in scala-logic, based on the probabilistic semantics from Fermüller et al. (2016).

## Table of Contents

1. [Introduction](#introduction)
2. [Theoretical Background](#theoretical-background)
3. [Architecture](#architecture)
4. [Component Interaction](#component-interaction)
5. [Query Syntax](#query-syntax)
6. [API Reference](#api-reference)
7. [Examples](#examples)
8. [Performance Considerations](#performance-considerations)
9. [Paper Mappings](#paper-mappings)

---

## Introduction

Vague quantifiers allow expressing fuzzy proportional statements in database queries:

- "About half of the countries are large"
- "At least 3/4 of assets have critical risks"
- "At most 1/3 of critical assets have unmitigated vulnerabilities"

Traditional quantifiers (∀, ∃) are crisp: either all elements satisfy a condition or at least one does. Vague quantifiers introduce gradation, checking if a **proportion** of elements satisfies a condition.

### Motivation

Real-world queries often involve proportions:

- **Business Intelligence**: "Most customers prefer product A over B"
- **Cybersecurity**: "Almost all servers have been patched"
- **Healthcare**: "About half of patients show improvement"
- **Science**: "Few experiments yielded significant results"

Traditional SQL can compute proportions but cannot express them as first-class logical constraints. Vague quantifiers bridge this gap.

---

## Theoretical Background

### Definition 1: Vague Quantifier (Paper Section 2.1)

A vague quantifier `Q[op]^{k/n}` consists of:

- **Operator** `op ∈ {~, ≥, ≤}`:
  - `~` (about): Proportion ≈ k/n
  - `≥` (at least): Proportion ≥ k/n
  - `≤` (at most): Proportion ≤ k/n

- **Threshold** `k/n`: Target proportion (e.g., 3/4 = 75%)

- **Tolerance** `ε`: Allowed deviation (default 0.1 = 10%)

### Definition 2: Vague Query (Paper Section 2.2)

A vague query has the form:

```
Q[op]^{k/n} x (R(x, y'), φ(x, y))(y)
```

Where:
- `Q[op]^{k/n}`: Vague quantifier
- `x`: Quantified variable (bound)
- `R(x, y')`: **Range predicate** - defines domain D_R
- `φ(x, y)`: **Scope formula** - condition to check
- `(y)`: **Answer variables** (optional) - for unary/n-ary queries

### Semantics (Paper Section 2.3)

Given a knowledge base KB and answer tuple c:

1. **Extract range**: D_R = {d | KB ⊨ R(d, c)}
2. **Select sample**: S ⊆ D_R (or S = D_R for exact evaluation)
3. **Calculate proportion**: Prop_D(S, φ) = |{s ∈ S | KB ⊨ φ(s, c)}| / |S|
4. **Check quantifier**:
   - About: |Prop_D - k/n| ≤ ε
   - AtLeast: Prop_D ≥ k/n - ε
   - AtMost: Prop_D ≤ k/n + ε

### Query Types

- **Boolean query**: No answer variables → returns satisfied/unsatisfied
  - Example: `Q[≥]^{3/4} x (country(x), large(x))`
  - "At least 3/4 of countries are large"

- **Unary query**: One answer variable → returns list of satisfying tuples
  - Example: `Q[~]^{1/2} x (capital(x, y), large(x))(y)`
  - "About half of capitals belong to large countries - which capitals?"

- **N-ary query**: Multiple answer variables → returns relation
  - Example: `Q[≥]^{2/3} x (student(x, name, gpa), gpa > 3.5)(name, gpa)`

---

## Architecture

The implementation follows a clean separation of concerns:

```
┌─────────────────────────────────────────────────────────┐
│                    VagueQuery                           │
│  Q[op]^{k/n} x (R(x,y'), φ(x,y))(y)                    │
└─────────────────┬───────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────┐
│          VagueSemantics.holdsExact()                    │
│  Main orchestrator - coordinates all components         │
└──┬──────────────────────────────────────────────────┬───┘
   │                                                   │
   ▼                                                   ▼
┌──────────────────────┐                   ┌──────────────────────┐
│  RangeExtractor      │                   │  ScopeEvaluator      │
│  Extract D_R from KB │                   │  Evaluate φ using    │
│  Handle substitution │                   │  FOL semantics       │
└──────────────────────┘                   └──────────────────────┘
           │                                          │
           ▼                                          ▼
    ┌─────────────┐                          ┌──────────────┐
    │ Knowledge   │                          │ FOLSemantics │
    │ Base        │                          │ (Tarski)     │
    └─────────────┘                          └──────────────┘
```

### Component Responsibilities

1. **VagueQuery** (`vague/logic/VagueQuery.scala`):
   - ADT for query representation
   - Validation (quantified var in range, answer vars subset check)
   - Pretty printing

2. **VagueQueryParser** (`vague/parser/VagueQueryParser.scala`):
   - Parse paper syntax to VagueQuery
   - OCaml continuation-passing style
   - Integration with FOL parser

3. **VagueSemantics** (`vague/semantics/VagueSemantics.scala`):
   - Main evaluation orchestrator
   - Sampling vs exact evaluation
   - Quantifier satisfaction checking

4. **RangeExtractor** (`vague/semantics/RangeExtractor.scala`):
   - Extract D_R = {d | KB ⊨ R(d, c)} from knowledge base
   - Handle answer variable substitution
   - Support Boolean and unary queries

5. **ScopeEvaluator** (`vague/semantics/ScopeEvaluator.scala`):
   - Evaluate φ(x, c) using FOL semantics
   - Calculate Prop_D(S, φ)
   - Batch evaluation for efficiency

6. **KnowledgeBase** (`vague/datastore/KnowledgeBase.scala`):
   - In-memory relational datastore
   - Schema with typed relations
   - Pattern-based querying

---

## Component Interaction

Understanding how `RangeExtractor`, `ScopeEvaluator`, and `VagueSemantics.holdsExact()` coordinate is key to understanding the system.

### The Three Components

1. **RangeExtractor**: Extracts the **domain** D_R from which we'll check proportions. Given a range predicate like `asset(x)`, it finds all values that satisfy it in the knowledge base.

2. **ScopeEvaluator**: Evaluates the **scope formula** φ for each element in the range, determining which elements satisfy the condition we're checking.

3. **VagueSemantics.holdsExact()**: The **orchestrator** that coordinates: extracts the range → evaluates the scope for each element → calculates the proportion → checks if it satisfies the quantifier.

### Example Walkthrough

Let's trace a complete query evaluation:

**Query**: `Q[>=]^{3/4} x (asset(x), exists r . (has_risk(x, r) /\ critical_risk(r)))`

**English**: "Do at least 75% of assets have a critical risk?"

**Knowledge Base** (simplified):
```
Assets: [server1, server2, workstation1, laptop1]
Critical risks: [sql_injection, ransomware]
Facts:
  - has_risk(server1, sql_injection)      [critical]
  - has_risk(server2, ransomware)          [critical]
  - has_risk(workstation1, weak_password)  [not critical]
  - has_risk(laptop1, outdated_library)    [not critical]
```

#### Step 1: VagueSemantics.holdsExact() receives the query

```scala
val query = VagueQuery(
  quantifier = AtLeast(k=3, n=4, tolerance=0.1),  // >= 75%
  variable = "x",
  range = asset(x),                                // What to quantify over
  scope = exists r . (has_risk(x,r) /\ critical_risk(r)), // What to check
  answerVars = List()                              // Boolean query
)

val result = VagueSemantics.holdsExact(query, kb)
```

#### Step 2: RangeExtractor.extractRange() - Build D_R

**Task**: Find all assets (the domain we'll check)

```scala
// Inside RangeExtractor.scala
def extractRange(
  rangeAtom: FOL,           // asset(x)
  quantifiedVar: String,    // "x"
  kb: KnowledgeBase,
  answerTuple: Map[String, RelationValue] = Map.empty
): Set[RelationValue] = {
  
  // Query KB: "Give me all values where asset(?) holds"
  val pattern = List(None)  // Wildcard for x
  val results = kb.query("asset", pattern)
  
  // Extract the values
  results.map(_.values(0))  // Get first column
}

// Returns: {server1, server2, workstation1, laptop1}
```

**Result**: D_R = {server1, server2, workstation1, laptop1} (4 elements)

#### Step 3: ScopeEvaluator.calculateProportion() - Check each element

**Task**: For each asset, does it have a critical risk?

```scala
// Inside ScopeEvaluator.scala
def calculateProportion(
  scopeFormula: Formula[FOL],  // exists r . (has_risk(x,r) /\ critical_risk(r))
  quantifiedVar: String,       // "x"
  range: Set[RelationValue],   // {server1, server2, workstation1, laptop1}
  kb: KnowledgeBase,
  answerTuple: Map[String, RelationValue] = Map.empty
): (Int, Int) = {
  
  var satisfyingCount = 0
  
  // Check EACH element in range
  for (element <- range) {
    // Substitute x = element in scope formula
    val substituted = scopeFormula.substitute("x" -> element)
    // Example: exists r . (has_risk(server1, r) /\ critical_risk(r))
    
    // Evaluate using FOL semantics
    val holds = FOLSemantics.holds(kb, List(), substituted)
    
    if (holds) satisfyingCount += 1
  }
  
  (satisfyingCount, range.size)
}
```

**Evaluation trace**:

1. **server1**: `exists r . (has_risk(server1, r) /\ critical_risk(r))`
   - Find r: sql_injection
   - Check: has_risk(server1, sql_injection) ✓ AND critical_risk(sql_injection) ✓
   - **Result: TRUE** ✓

2. **server2**: `exists r . (has_risk(server2, r) /\ critical_risk(r))`
   - Find r: ransomware
   - Check: has_risk(server2, ransomware) ✓ AND critical_risk(ransomware) ✓
   - **Result: TRUE** ✓

3. **workstation1**: `exists r . (has_risk(workstation1, r) /\ critical_risk(r))`
   - Find r: weak_password
   - Check: has_risk(workstation1, weak_password) ✓ BUT critical_risk(weak_password) ✗
   - **Result: FALSE** ✗

4. **laptop1**: `exists r . (has_risk(laptop1, r) /\ critical_risk(r))`
   - Find r: outdated_library
   - Check: has_risk(laptop1, outdated_library) ✓ BUT critical_risk(outdated_library) ✗
   - **Result: FALSE** ✗

**Result**: (satisfyingCount=2, rangeSize=4)

#### Step 4: VagueSemantics.holdsExact() - Check quantifier

```scala
// Back in VagueSemantics.scala

// We have:
val rangeSize = 4
val satisfyingCount = 2
val actualProportion = 2.0 / 4.0 = 0.5  // 50%

// Query requires: >= 75% (with 10% tolerance)
val targetProportion = 3.0 / 4.0 = 0.75
val tolerance = 0.1

// Check: AtLeast quantifier
val satisfied = actualProportion >= (targetProportion - tolerance)
//                0.5            >= (0.75 - 0.1)
//                0.5            >= 0.65
//                FALSE ✗

VagueResult(
  satisfied = false,
  actualProportion = 0.5,
  rangeSize = 4,
  sampleSize = 4,
  satisfyingCount = 2
)
```

### Coordination Flow Diagram

```
VagueSemantics.holdsExact()
     │
     ├─ 1. Call RangeExtractor
     │      Input: asset(x), quantifiedVar="x"
     │      Output: D_R = {server1, server2, workstation1, laptop1}
     │
     ├─ 2. Call ScopeEvaluator
     │      Input: exists r.(has_risk(x,r) /\ critical_risk(r))
     │             Range: {server1, server2, workstation1, laptop1}
     │      Process:
     │         ├─ Check server1 → TRUE ✓
     │         ├─ Check server2 → TRUE ✓
     │         ├─ Check workstation1 → FALSE ✗
     │         └─ Check laptop1 → FALSE ✗
     │      Output: (2, 4)
     │
     └─ 3. Check Quantifier
            actualProportion = 2/4 = 0.5
            required = 0.75
            tolerance = 0.1
            satisfied = 0.5 >= 0.65 ? FALSE ✗
```

### Key Insights

1. **RangeExtractor** answers: "**What** are we checking?" → The domain D_R
   
2. **ScopeEvaluator** answers: "**Which ones** satisfy the condition?" → The count

3. **VagueSemantics** answers: "**Does this meet** the quantifier threshold?" → Boolean + metrics

The coordination is:
- **Sequential dependencies**: Can't evaluate scope without knowing the range
- **Clean separation**: Each component has one clear responsibility
- **Reusability**: RangeExtractor and ScopeEvaluator work for any query structure

This design mirrors the paper's mathematical definition:
- D_R extraction (Definition 2, range predicate)
- Prop_D calculation (Definition 3, scope evaluation)  
- Quantifier satisfaction check (Definition 1, threshold comparison)

---

## Query Syntax

### Basic Structure

```
Q[operator]^{k/n}[tolerance] variable (range, scope)(answer_vars)
```

### Components

**Quantifier**: `Q[operator]^{k/n}[tolerance]`
- Operators: `~`, `~#`, `>=`, `≥`, `<=`, `≤`
- Fraction: `{k/n}` where k ≤ n
- Tolerance (optional): `[0.05]` (default: 0.1)

**Variable**: Single identifier (e.g., `x`)

**Range**: FOL atom (e.g., `country(x)`, `has_risk(x, r)`)

**Scope**: FOL formula (e.g., `large(x)`, `exists r . P(x, r)`)

**Answer variables** (optional): `(y)` or `(y1, y2, ...)`

### Examples

```scala
// Boolean query (no answer variables)
Q[>=]^{3/4} x (asset(x), critical(x))

// Custom tolerance
Q[~]^{1/2}[0.05] x (country(x), large(x))

// With answer variable
Q[~]^{1/2} x (capital(x, y), large(x))(y)

// Complex scope with quantifiers
Q[>=]^{3/4} x (asset(x), exists r . (has_risk(x, r) /\ critical_risk(r)))

// Nested quantifiers and negation
Q[<=]^{1/3} x (critical_asset(x), 
                exists r . (has_risk(x, r) /\ ~(exists m . has_mitigation(r, m))))

// Multiple answer variables
Q[>=]^{2/3} x (student(x, name, gpa), gpa > 3.5)(name, gpa)
```

### Supported Operators

**Logical**: `/\` (and), `\/` (or), `~` (not), `==>` (implies), `<=>` (iff)

**Quantifiers**: `forall x . P(x)`, `exists x . P(x)`

**Relations**: `=`, `<`, `<=`, `>`, `>=`

**Arithmetic**: `+`, `-`, `*`, `^` (in terms)

---

## API Reference

### Parsing

```scala
import vague.parser.VagueQueryParser

// Parse query from string
val query: VagueQuery = VagueQueryParser.parse(
  "Q[>=]^{3/4} x (asset(x), critical(x))"
)

// Convenience parsers
val aboutQuery = VagueQueryParser.parseAbout("Q[~]^{1/2} x (P(x), Q(x))")
val atLeastQuery = VagueQueryParser.parseAtLeast("Q[>=]^{3/4} x (P(x), Q(x))")
val atMostQuery = VagueQueryParser.parseAtMost("Q[<=]^{1/4} x (P(x), Q(x))")
```

### Evaluation

```scala
import vague.semantics.VagueSemantics
import vague.datastore.KnowledgeBase

val kb: KnowledgeBase = // ... your domain
val query: VagueQuery = // ... parsed query

// Exact evaluation (use entire range)
val result: VagueResult = VagueSemantics.holdsExact(query, kb)

println(s"Satisfied: ${result.satisfied}")
println(s"Proportion: ${result.actualProportion}")
println(s"Range size: ${result.rangeSize}")
println(s"Satisfying: ${result.satisfyingCount}")

// With answer variable substitution
val answerTuple: Map[String, RelationValue] = Map("y" -> RelationValue.Const("USA"))
val result2 = VagueSemantics.holdsExact(query, kb, answerTuple)

// Sampling evaluation
val result3 = VagueSemantics.holdsWithSampling(
  query, kb, 
  sampleSize = 100,
  seed = Some(42L)
)
```

### Building Knowledge Bases

```scala
import vague.datastore.*

val kb = KnowledgeBase.builder
  // Define schema
  .withUnaryRelation("country")
  .withUnaryRelation("large")
  .withBinaryRelation("has_risk")
  
  // Add facts
  .withFact("country", "USA")
  .withFact("country", "China")
  .withFact("large", "USA")
  .withFact("large", "China")
  .withFact("has_risk", "server1", "sql_injection")
  
  .build()

// Query the knowledge base
val allCountries = kb.getDomain("country")
val largeCountries = kb.query("large", List(None))
val risks = kb.query("has_risk", List(Some(RelationValue.Const("server1")), None))
```

### Constructing Queries Programmatically

```scala
import vague.logic.*
import logic.*

// Create quantifier
val quantifier = Quantifier.About(k = 1, n = 2, tolerance = 0.1)
// or: Quantifier.AtLeast(3, 4, 0.1)
// or: Quantifier.AtMost(1, 3, 0.1)

// Create query
val query = VagueQuery.mk(
  quantifier = quantifier,
  variable = "x",
  range = FOL.Atom(R("country", 1), List(Var("x"))),
  scope = Formula.Atom(FOL.Atom(R("large", 1), List(Var("x")))),
  answerVars = List()  // Boolean query
)
```

### Result Type

```scala
case class VagueResult(
  satisfied: Boolean,              // Query satisfaction
  actualProportion: Double,        // Computed Prop_D
  rangeSize: Int,                  // |D_R|
  sampleSize: Int,                 // |S| (= rangeSize for exact)
  satisfyingCount: Int             // |{s ∈ S | φ(s)}|
)
```

---

## Examples

### Example 1: Boolean Query

**Domain**: Cybersecurity risk management

**Query**: "Do at least 3/4 of assets have critical risks?"

```scala
import vague.parser.VagueQueryParser
import vague.semantics.VagueSemantics
import vague.examples.CyberSecurityDomain

val query = VagueQueryParser.parse(
  "Q[>=]^{3/4} x (asset(x), exists r . (has_risk(x, r) /\\ critical_risk(r)))"
)

val domain = CyberSecurityDomain.kb
val result = VagueSemantics.holdsExact(query, domain)

// Output:
// Satisfied: true
// Proportion: 0.857 (18/21 assets)
// Interpretation: 86% > 75% threshold → SATISFIED
```

**Breakdown**:
1. Range: D_R = {all assets} = 21 elements
2. Sample: S = D_R (exact evaluation)
3. For each asset, check if ∃r. (has_risk(asset, r) ∧ critical_risk(r))
4. Count: 18 assets satisfy the condition
5. Proportion: 18/21 ≈ 0.857
6. Check: 0.857 ≥ 0.75 - 0.1 = 0.65 ✓

### Example 2: Unary Query with Answer Set

**Query**: "About half of risks have mitigations - which ones?"

```scala
val query = VagueQueryParser.parse(
  "Q[~]^{1/2} x (risk(x), exists m . has_mitigation(x, m))(x)"
)

val result = VagueSemantics.holdsExact(query, domain)

// Output:
// Satisfied: true
// Proportion: 0.5 (10/20 risks)
// Answer set: {sql_injection, ransomware_threat, zero_day_vuln, ...}
```

To extract the answer set:

```scala
if query.isUnary then
  val mitigatedRisks = domain.query("has_mitigation", List(None, None))
    .map(_.values(0).toString)
    .toSet
  
  println(s"Mitigated risks: ${mitigatedRisks.mkString(", ")}")
```

### Example 3: Complex Query with Nested Quantifiers

**Query**: "At most 1/3 of critical assets have unmitigated risks"

```scala
val query = VagueQueryParser.parse("""
  Q[<=]^{1/3} x (critical_asset(x), 
                  exists r . (has_risk(x, r) /\ 
                              ~(exists m . has_mitigation(r, m))))
""")

val result = VagueSemantics.holdsExact(query, domain)

// Output:
// Satisfied: false
// Proportion: 0.625 (5/8 critical assets)
// Interpretation: 63% > 33% threshold → NOT SATISFIED (security issue!)
```

**Breakdown**:
1. Range: D_R = {critical assets} = 8 elements
2. For each critical asset:
   - Check if ∃r. (has_risk(asset, r) ∧ ¬∃m. has_mitigation(r, m))
   - I.e., "has at least one risk with no mitigation"
3. Count: 5 critical assets have unmitigated risks
4. Proportion: 5/8 = 0.625
5. Check: 0.625 ≤ 0.333 + 0.1 = 0.433? No ✗

### Example 4: High-Value Assets with Patched Mitigations

**Query**: "At least 9/10 of high-value assets have patched mitigations"

```scala
val query = VagueQueryParser.parse("""
  Q[>=]^{9/10} x (high_value(x), 
                   exists r m . (has_risk(x, r) /\ 
                                 has_mitigation(r, m) /\ 
                                 patched(m)))
""")

val result = VagueSemantics.holdsExact(query, domain)

// Output:
// Satisfied: false
// Proportion: 0.75 (6/8 high-value assets)
// Interpretation: 75% < 90% threshold → NOT SATISFIED
```

**Interpretation**: Only 6 out of 8 high-value assets have their risks fully patched. This falls short of the 90% target, indicating a gap in vulnerability management for critical infrastructure.

---

## Performance Considerations

### Exact vs Sampling Evaluation

**Exact Evaluation** (`holdsExact`):
- Uses entire range D_R
- Precise proportion calculation
- Suitable for small-medium datasets (< 10,000 elements)
- No statistical uncertainty

**Sampling Evaluation** (`holdsWithSampling`):
- Uses random sample S ⊂ D_R
- Approximate proportion with confidence intervals
- Suitable for large datasets
- Configurable sample size and random seed

```scala
// For large datasets (millions of rows)
val result = VagueSemantics.holdsWithSampling(
  query, kb,
  sampleSize = 1000,    // Sample 1000 elements
  seed = Some(42L)      // Reproducible results
)

// Check statistical significance
if result.satisfied then
  println("Query satisfied (with sampling)")
  // Consider: confidence intervals, margin of error
```

### Optimization Tips

1. **Index common predicates** in knowledge base queries
2. **Use selective range predicates** to reduce D_R size
3. **Batch scope evaluation** when possible (already done internally)
4. **Cache knowledge base queries** if reusing same KB
5. **Use sampling** for exploratory queries on large datasets

### Complexity Analysis

Given:
- |D_R| = n (range size)
- |S| = m (sample size, m ≤ n)
- |KB| = k (number of facts)

**Time Complexity**:
- Range extraction: O(k) - scan facts matching pattern
- Scope evaluation: O(m × |φ|) - evaluate formula for each sample element
- Overall: O(k + m × |φ|)

**Space Complexity**:
- Range storage: O(n)
- Sample storage: O(m)
- Knowledge base: O(k)

---

## Paper Mappings

### Syntax Correspondence

| Paper Notation | Implementation | Notes |
|----------------|----------------|-------|
| Q[~]^{k/n} | `Quantifier.About(k, n, ε)` | About quantifier |
| Q[≥]^{k/n} | `Quantifier.AtLeast(k, n, ε)` | At least quantifier |
| Q[≤]^{k/n} | `Quantifier.AtMost(k, n, ε)` | At most quantifier |
| R(x, y') | `range: FOL` | Range predicate |
| φ(x, y) | `scope: Formula[FOL]` | Scope formula |
| (y) | `answerVars: List[String]` | Answer variables |
| D_R | `RangeExtractor.extractRange()` | Range domain |
| Prop_D(S, φ) | `ScopeEvaluator.calculateProportion()` | Proportion |

### Semantic Correspondence

| Paper Definition | Implementation | Location |
|-----------------|----------------|----------|
| Definition 1 (Quantifier) | `Quantifier` enum | `vague/logic/Quantifier.scala` |
| Definition 2 (Query) | `VagueQuery` case class | `vague/logic/VagueQuery.scala` |
| Algorithm 1 (Evaluation) | `VagueSemantics.holdsExact()` | `vague/semantics/VagueSemantics.scala` |
| Range extraction | `RangeExtractor.extractRange()` | `vague/semantics/RangeExtractor.scala` |
| Scope evaluation | `ScopeEvaluator.calculateProportion()` | `vague/semantics/ScopeEvaluator.scala` |
| Quantifier check | `checkQuantifier()` | `vague/semantics/VagueSemantics.scala:180` |

### Test Coverage

| Paper Example | Test Location | Status |
|---------------|---------------|--------|
| q₁ (Boolean) | `VagueQuerySpec.scala:86` | ✅ Passing |
| q₃ skeleton | `VagueQuerySpec.scala:98` | ✅ Passing |
| Parser q₁ | `VagueQueryParserSpec.scala:146` | ✅ Passing |
| Cybersecurity | `CyberSecurityExamples.scala` | ✅ 4 demos |

---

## Advanced Topics

### Custom Tolerances

Default tolerance is 10% (ε = 0.1). Customize per query:

```scala
// Strict: ±5% tolerance
Q[~]^{1/2}[0.05] x (P(x), Q(x))

// Lenient: ±20% tolerance
Q[~]^{3/4}[0.20] x (P(x), Q(x))
```

### Negation Handling

Negation in scope formulas requires careful interpretation:

```scala
// "Assets WITHOUT critical risks"
Q[>=]^{1/2} x (asset(x), ~(exists r . critical_risk(r) /\ has_risk(x, r)))

// Equivalent: "Assets where NO risk is critical"
Q[>=]^{1/2} x (asset(x), forall r . (has_risk(x, r) ==> ~critical_risk(r)))
```

### Variable Scoping

Quantified variable `x` must appear in range:

```scala
// Valid: x in country(x)
Q[~]^{1/2} x (country(x), large(x))

// Invalid: x not in country(y)
Q[~]^{1/2} x (country(y), large(x))  // Validation error!
```

Answer variables must be bound in range:

```scala
// Valid: y in capital(x, y)
Q[~]^{1/2} x (capital(x, y), large(x))(y)

// Invalid: z not in range
Q[~]^{1/2} x (capital(x, y), large(x))(z)  // Validation error!
```

### Composing Queries

Vague queries can be components of larger logical expressions:

```scala
// Check multiple conditions
val q1Satisfied = VagueSemantics.holdsExact(query1, kb).satisfied
val q2Satisfied = VagueSemantics.holdsExact(query2, kb).satisfied

if q1Satisfied && q2Satisfied then
  println("Both conditions met")

// Compare proportions
val prop1 = VagueSemantics.holdsExact(query1, kb).actualProportion
val prop2 = VagueSemantics.holdsExact(query2, kb).actualProportion

if prop1 > prop2 then
  println("Query 1 has higher satisfaction rate")
```

---

## Troubleshooting

### Common Errors

**"Closing bracket ')' expected"**
- Missing dot in quantifier: `exists y . P(y)` not `exists y P(y)`

**"Quantified variable x must appear in range"**
- Variable binding issue: Ensure `x` appears in range predicate

**"Answer variables must be subset of range variables"**
- Answer variable not in range: Check binary relation positions

**"Expected variable, got: ["**
- Custom tolerance parsing: May need spaces around brackets

### Debugging Tips

1. **Print parsed structure**:
```scala
val query = VagueQueryParser.parse(queryString)
println(s"Range: ${query.range}")
println(s"Scope: ${query.scope}")
println(s"Quantifier: ${query.quantifier}")
```

2. **Check knowledge base**:
```scala
println(s"Range size: ${kb.getDomain(\"predicate\").size}")
println(kb.stats)  // Print summary
```

3. **Trace evaluation**:
```scala
val result = VagueSemantics.holdsExact(query, kb)
println(s"Range: ${result.rangeSize}")
println(s"Satisfying: ${result.satisfyingCount}")
println(s"Proportion: ${result.actualProportion}")
```

---

## Future Extensions

Potential enhancements:

1. **Linguistic quantifiers**: "most", "few", "many", "almost all"
2. **Fuzzy membership**: Gradual satisfaction degrees
3. **Temporal quantifiers**: "Usually", "rarely", "often"
4. **Aggregation**: AVG, SUM, COUNT in scope formulas
5. **Optimization**: Query rewriting, indexing strategies
6. **Distributed evaluation**: Parallel processing for large KBs

---

## References

1. Christian G. Fermüller, Matthias Hofer, and Magdalena Ortiz (2016). "Querying with Vague Quantifiers Using Probabilistic Semantics"
   - TU Vienna, Austria
   - Vague quantifier semantics: Q[op]^{k/n}
   - Sampling-based probabilistic evaluation
   - Semi-fuzzy proportional quantifiers
   - Game-theoretic semantics for fuzzy quantifiers

2. John Harrison (2009). "Handbook of Practical Logic and Automated Reasoning"
   - Cambridge University Press
   - ISBN: 978-0-521-89957-4
   - OCaml parser combinator patterns
   - FOL syntax and semantics implementation

---

**Document Version**: 1.1  
**Last Updated**: December 25, 2025  
**Maintainer**: scala-logic contributors
