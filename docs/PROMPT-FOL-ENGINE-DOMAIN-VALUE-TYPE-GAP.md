# Problem: Domain Value Type Gap Between fol-engine and register

## Context

Two projects are involved:

1. **fol-engine** (`~/projects/vague-quantifier-logic`) — a first-order logic evaluation library, parameterized over a domain type `D`. It ships a concrete domain enum:

```scala
enum RelationValue:
  case Const(name: String)   // symbolic constants (node names, relation values)
  case Num(value: Int)       // numeric values — Int only
```

The library's built-in `NumericAugmenter` provides comparison predicates (`>`, `<`, `>=`, `<=`, `=`), arithmetic (`+`, `-`, `*`, `/`), and numeric literal parsing — all operating on `Num(Int)`. The source comment explicitly notes: *"Arithmetic is integer-based (`RelationValue.Num(Int)`). Division truncates toward zero. Consumers needing precise decimal arithmetic should use `ArithmeticAugmenter[Double]` with a richer domain type."*

Comparisons use `Ordering[RelationValue]`, which compares `Num` by int value and `Const` lexicographically. Mixed `Num`/`Const` comparison throws.

2. **register** (`~/projects/register`) — a risk management application that needs to evaluate vague-quantifier queries over risk trees. It uses fol-engine as a library dependency.

Register's simulation domain types:
- `type Loss = Long` — monetary amounts in base currency units (cents). Losses can exceed `Int.MaxValue` (~$2.1B) for large aggregate portfolios.
- `probOfExceedance(threshold: Loss): Double` — probability values in range [0.0, 1.0]
- Percentile functions (`p50`, `p90`, `p95`, `p99`) — return `Loss` (Long) amounts
- LEC function (`lec(node, threshold)`) — returns `Double` probability

## The Queries That Must Work

From ADR-028 (the architectural decision record defining register's query feature):

### Percentile comparison (most common pattern)
```
Q[>=]^{2/3} x (leaf(x), >(p95(x), 5000000))
```
"At least ⅔ of leaves have P95 above 5,000,000."

Here `p95(x)` must return a numeric value, `5000000` must parse as a numeric literal, and `>` must compare them.

### Loss exceedance screening
```
Q[<=]^{1/4} x (leaf(x), >(lec(x, 10000000), 0.05))
```
"At most ¼ of leaves have >5% probability of exceeding 10,000,000."

Here `lec(x, 10000000)` must return a probability (a Double between 0.0 and 1.0), `0.05` must parse as a numeric literal, and `>` must compare them.

### Structural-only (works fine today)
```
Q[~]^{1/2}[0.05] x (leaf(x), portfolio(z) /\ child_of(x, z))
```
No numeric values involved — only `Const` names and KB relations.

## The Type Gaps

| Function | Returns | Needs to be a `RelationValue` | Problem |
|---|---|---|---|
| `p50`, `p90`, `p95`, `p99` | `Loss` (Long) | `Num(Int)` | Long → Int narrowing. Overflows silently for losses > ~$2.1B |
| `lec(node, threshold)` | `Double` (0.0–1.0) | No `Double` variant exists | Cannot represent at all. `Num(Int)` cannot hold 0.05 |
| Literal `5000000` | — | Parsed as `Num(5000000)` | Fits in Int. But `5000000000` (5B) would not |
| Literal `0.05` | — | No decimal literal support | `LiteralResolver` only resolves integer strings |
| Comparison `>(a, b)` | Boolean | Uses `Ordering[RelationValue]` | Only compares `Num` vs `Num` by int value. No Double support |

## Existing Extension Points

The library is parameterized over `D` and provides composition mechanisms:

- `ModelAugmenter[D]` — endomorphism monoid over `Model[D]`. Can inject custom functions and predicates via `fromFunctions` / `fromPredicates`. Composed with `andThen`.
- `ComparisonAugmenter` works with any `D: Ordering`.
- `ArithmeticAugmenter[D: Fractional]` exists for types with `Fractional` instances.
- `LiteralResolver[D: DomainCodec]` resolves string-encoded constants to domain values.
- `KnowledgeBase[D]` and `KnowledgeSource[D]` are generic.
- `VagueSemantics.evaluate[D]()` accepts `modelAugmenter: ModelAugmenter[D]`.

The `RelationValue` enum itself lives in `fol.datastore.Relation.scala` and is used throughout the library as the default concrete domain type for all existing tests (854 tests).

## Constraint

Both projects are actively maintained by the same team. The fol-engine library can be modified, but changes affect 854 existing tests and should maintain backward compatibility or be carefully migrated.

## Question

How should these type gaps be addressed to support the queries defined in ADR-028?
