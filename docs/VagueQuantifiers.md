# Vague Quantifiers: Theory and Paper Mapping

Reference document mapping the mathematical framework from
Fermüller et al. (2016) to the fol-engine implementation.

For architecture and code organization, see [Architecture.md](Architecture.md).  
For evaluation pipeline details, see [ADR-001](ADR-001.md).

---

## Theoretical Background

### Definition 1: Vague Quantifier (Paper §2.1)

A vague quantifier `Q[op]^{k/n}` consists of:

- **Operator** `op ∈ {~, ≥, ≤}`:
  - `~` (about): Proportion ≈ k/n
  - `≥` (at least): Proportion ≥ k/n
  - `≤` (at most): Proportion ≤ k/n
- **Threshold** `k/n`: Target proportion (e.g., 3/4 = 75%)
- **Tolerance** `ε`: Allowed deviation (default 0.1 = 10%)

### Definition 2: Vague Query (Paper §2.2)

A vague query has the form:

```
Q[op]^{k/n} x (R(x, y'), φ(x, y))(y)
```

Where:
- `Q[op]^{k/n}`: Vague quantifier
- `x`: Quantified variable (bound)
- `R(x, y')`: **Range predicate** — defines domain D_R
- `φ(x, y)`: **Scope formula** — condition to check
- `(y)`: **Answer variables** (optional)

### Semantics (Paper §2.3)

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
  - `Q[≥]^{3/4} x (country(x), large(x))`
- **Unary query**: One answer variable → returns satisfying tuples
  - `Q[~]^{1/2} x (capital(x, y), large(x))(y)`

---

## Query Syntax

### Structure

```
Q[operator]^{k/n}[tolerance] variable (range, scope)(answer_vars)
```

| Component | Form | Example |
|---|---|---|
| Quantifier | `Q[op]^{k/n}[tol]` | `Q[>=]^{3/4}`, `Q[~]^{1/2}[0.05]` |
| Variable | identifier | `x` |
| Range | FOL atom | `country(x)`, `risk_in_project(x, "Alpha")` |
| Scope | FOL formula | `large(x)`, `exists r . (has_risk(x, r) /\ critical(r))` |
| Answer vars | `(y)` or `(y1, y2)` | Optional |

### Operators

| Category | Symbols |
|---|---|
| Quantifier | `~`, `~#`, `>=`, `≥`, `<=`, `≤` |
| Logical | `/\` (and), `\/` (or), `~` (not), `==>` (implies), `<=>` (iff) |
| Quantifiers | `forall x . P(x)`, `exists x . P(x)` |
| Comparison | `=`, `<`, `<=`, `>`, `>=` |
| Arithmetic | `+`, `-`, `*`, `^` (in terms) |

### Examples

```
Q[>=]^{3/4} x (asset(x), critical(x))

Q[~]^{1/2}[0.05] x (country(x), large(x))

Q[~]^{1/2} x (capital(x, y), large(x))(y)

Q[>=]^{3/4} x (asset(x), exists r . (has_risk(x, r) /\ critical_risk(r)))

Q[<=]^{1/3} x (critical_asset(x),
                exists r . (has_risk(x, r) /\ ~(exists m . has_mitigation(r, m))))
```

---

## Paper-to-Code Mapping

### Types

| Paper Notation | Code | Package |
|---|---|---|
| Q[~]^{k/n} | `Quantifier.About(k, n, ε)` | `fol.logic` |
| Q[≥]^{k/n} | `Quantifier.AtLeast(k, n, ε)` | `fol.logic` |
| Q[≤]^{k/n} | `Quantifier.AtMost(k, n, ε)` | `fol.logic` |
| R(x, y') | `range: FOL` (in `ParsedQuery`) | `fol.logic` |
| φ(x, y) | `scope: Formula[FOL]` (in `ParsedQuery`) | `fol.logic` |
| (y) | `answerVars: List[String]` | `fol.logic` |
| D_R | `RangeExtractor.extractRange()` result | `fol.semantics` |
| Prop_D(S, φ) | `ProportionEstimator.estimateWithSampling()` | `fol.sampling` |
| VagueQuantifier threshold | `VagueQuantifier.AtLeast(threshold)` etc. | `fol.quantifier` |

### Algorithm Mapping

| Paper Step | Code Location | Method |
|---|---|---|
| Parse query | `fol.parser.VagueQueryParser` | `parse(s): Either[QueryError, ParsedQuery]` |
| Extract D_R | `fol.semantics.RangeExtractor` | `extractRange(source, query, subst)` |
| Build model | `fol.bridge.KnowledgeSourceModel` | `toModel(source): Model[Any]` |
| Compile scope | `fol.bridge.FOLBridge` | `scopeToPredicate(scope, var, source)` |
| Sample S ⊆ D_R | `fol.sampling.HDRSampler` | `sample(population, n): Set[A]` |
| Calculate Prop_D | `fol.sampling.ProportionEstimator` | `estimateWithSampling(…)` |
| Check quantifier | `fol.result.VagueQueryResult` | `fromEstimate(vq, estimate, N)` |
| Full pipeline | `fol.semantics.VagueSemantics` | `holds(q, src, …)` / `evaluate(q, src, …)` |

---

## Evaluation Modes

Both modes use the same code path. `SamplingParams` controls precision:

| Mode | Params | Behavior |
|---|---|---|
| Exact | `SamplingParams.exact` (ε = 1e-6) | n = N, deterministic, full domain |
| Sampled | `SamplingParams.default` (ε = 0.1) | n < N, HDR Fisher-Yates, Wilson CI |

See [ADR-003](ADR-003.md) for sampling design and [ADR-001](ADR-001.md) for mode unification rationale.

---

## Example Walkthrough

**Query**: `Q[>=]^{3/4} x (asset(x), exists r . (has_risk(x, r) /\ critical_risk(r)))`  
**English**: "Do at least 75% of assets have a critical risk?"

**Knowledge Base**:
```
asset: [server1, server2, workstation1, laptop1]
has_risk(server1, sql_injection), has_risk(server2, ransomware)
has_risk(workstation1, weak_password), has_risk(laptop1, outdated_library)
critical_risk: [sql_injection, ransomware]
```

**Evaluation**:

1. **Range**: D_R = {server1, server2, workstation1, laptop1} (4 elements)
2. **Scope check per element**:
   - server1 → ∃r. has_risk(server1, r) ∧ critical_risk(r) → sql_injection → **TRUE**
   - server2 → ransomware → **TRUE**
   - workstation1 → weak_password (not critical) → **FALSE**
   - laptop1 → outdated_library (not critical) → **FALSE**
3. **Proportion**: 2/4 = 0.5
4. **Quantifier**: AtLeast(3/4) with ε=0.1 → check 0.5 ≥ 0.65 → **FALSE**

Result: `VagueQueryResult(satisfied=false, proportion=0.5, domainSize=4, sampleSize=4, satisfyingCount=2)`

---

## Variable Scoping Rules

- Quantified variable `x` must appear in range predicate
- Answer variables must be bound in range predicate
- `forall`/`exists` in scope formula bind their own variables

```
✓ Q[~]^{1/2} x (country(x), large(x))         — x in range
✗ Q[~]^{1/2} x (country(y), large(x))         — x not in range
✓ Q[~]^{1/2} x (capital(x, y), large(x))(y)   — y bound in range
✗ Q[~]^{1/2} x (capital(x, y), large(x))(z)   — z not in range
```

---

## References

1. Fermüller, C. G., Hofer, M., and Ortiz, M. (2016). "Querying with Vague Quantifiers Using Probabilistic Semantics". TU Vienna.
2. Harrison, J. (2009). *Handbook of Practical Logic and Automated Reasoning*. Cambridge University Press.
