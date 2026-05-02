# ADR-016: Carrier Witness Layered on Symmetric Value Typeclasses

**Status:** Proposed
**Date:** 2026-05-02
**Tags:** typed-ir, typeclasses, gadt, value-boundary, compiler-design

---

## Context

- Heterogeneous IR storage (one map keyed by runtime sort, one literal node) cannot be parameterised by a single Scala type — the carrier varies per entry.
- A closed sum (`enum LiteralValue`) over carriers fixes the storage problem but bars user-defined domain entities from being first-class literals.
- A bare `Any` payload erases the carrier identity, leaving no static guarantee at consumer sites that walk literal nodes directly.
- Boundary-shaped typeclasses (parse-in, extract-out) and storage-shaped witnesses (carrier tags) address disjoint concerns: contract enforcement vs. payload representation.
- Production compilers (LLVM `Constant*`, Roslyn `BoundLiteral`, GHC `Literal`) tag literal-payload nodes with a carrier discriminator while exposing typed accessors at the API boundary — the two layers compose, neither replaces the other.

---

## Decision

### 1. Two Layers, Composed

Boundary typeclasses define **what counts as a valid value crossing the boundary**; carrier witnesses define **how a heterogeneous payload identifies its own type at runtime**. Both layers are required for a fully-typed extensible literal pipeline.

```
  source text             bound IR                        runtime A
  ───────────             ────────                        ─────────
  String  ─LiteralParser[A]─►  LiteralRef[A](      ─Extract[A]─►  A
                                  carrier: Carrier[A],
                                  value:   A         )
            (boundary,                ▲
             ADR-015)                 │
                                Carrier[A]
                                (storage witness,
                                 this ADR)
```

### 2. `Carrier[A]` as the Storage Witness

```scala
sealed trait Carrier[A]
object Carrier:
  given Carrier[Long]   = new Carrier[Long]   {}
  given Carrier[Double] = new Carrier[Double] {}
  given Carrier[String] = new Carrier[String] {}
  // user-defined: given Carrier[EntityId] = new Carrier[EntityId] {}
```

A `Carrier[A]` registration unlocks a sort for first-class literal storage; consumers recover `A` by matching on the carrier tag without `asInstanceOf`.

### 3. Split `BoundTerm` Literal vs Named-Constant Cases

A literal node carries a parsed payload; a named-constant node carries only a name resolved by the model. They are different IR shapes and must not share a payload field.

```scala
enum BoundTerm:
  case ConstRef(name: String, sort: TypeId)                          // resolved by model
  case LiteralRef[A](sourceText: String, sort: TypeId,
                     carrier: Carrier[A], value: A)                  // typed payload
  case VarRef(...)
  case FnApp(...)
```

### 4. Layered Derivations

`LiteralParser[A]` and `Extract[A]` may be derived from a registered `Carrier[A]` where the parse/extract logic is mechanical, leaving sort-specific work (e.g. `Long.parse`) as the only manual extension point.

```scala
given [A](using Carrier[A]): Extract[A] with
  def apply(v: Value): Either[String, A] = matchCarrier(v)
```

### 5. Optional Adoption Gate

`Carrier[A]` becomes worthwhile in proportion to the number of consumer surfaces that walk literal nodes directly (serializer, codegen, debugger). With `Extract[A]` as the sole consumer, an `Any` payload on `LiteralRef` is acceptable; adoption is a per-phase decision recorded in the implementation plan, not a global mandate of this ADR.

---

## Code Smells

### ❌ Heterogeneous payload typed as `Any` with no witness

```scala
// BAD: consumer must inspect raw Any with no static guard
def render(node: BoundTerm): String = node match
  case LiteralRef(_, _, v) => v.toString  // v: Any — accidental coercion lurks
```

```scala
// GOOD: payload pairs A with a Carrier[A] tag; recovery is exhaustive
def render(node: BoundTerm): String = node match
  case LiteralRef(_, _, c, v) => c match
    case Carrier.LongC   => v.toString
    case Carrier.DoubleC => v.toString
```

### ❌ One IR node carrying both named and parsed-literal payloads

```scala
// BAD: ConstRef means two unrelated things, payload field semantically overloaded
case ConstRef(sourceText: String, sort: TypeId, raw: Any)
```

```scala
// GOOD: one node per IR shape
case ConstRef(name: String, sort: TypeId)
case LiteralRef[A](sourceText: String, sort: TypeId,
                   carrier: Carrier[A], value: A)
```

### ❌ Closed sum of literal carriers

```scala
// BAD: extending requires patching the engine; domain entities excluded
enum LiteralValue:
  case IntLiteral(n: Long)
  case FloatLiteral(d: Double)
  case TextLiteral(s: String)
```

```scala
// GOOD: open via given Carrier[A]; no engine change for new sorts
given Carrier[EntityId] = new Carrier[EntityId] {}
```

### ❌ Carrier match without typeclass at consumer boundary

```scala
// BAD: every consumer rewrites the same dispatch
v match
  case Value(_, x: Long)   => useLong(x)
  case Value(_, x: Double) => useDouble(x)
```

```scala
// GOOD: consumers ask for the type they want; typeclass routes to the carrier
val x: Either[String, Long] = v.extract[Long]
```

---

## Cross-ADR Relationship

[ADR-015](ADR-015.md) governs the boundary contract (`LiteralParser[A]`, `Extract[A]`) and stops short of prescribing the IR-payload representation. ADR-016 fills that gap: it neither contradicts nor supersedes ADR-015 but layers a storage witness beneath the same boundary. ADR-015 remains authoritative for `Value.extract[A]` semantics; ADR-016 is authoritative for `LiteralRef` payload typing and the `BoundTerm` node split.

---

## Alternatives Rejected

### Closed `enum LiteralValue` over carriers
- **What**: a sealed sum of `IntLiteral`/`FloatLiteral`/`TextLiteral` etc. used as both IR payload and dispatcher return.
- **Why rejected**: cannot represent user-defined domain entities as first-class literals; every new sort requires patching the engine, violating the open-extension principle motivating ADR-015 (T-004).

### Bare `Any` payload on a single combined `ConstRef` node
- **What**: keep one IR node serving named constants and parsed literals, payload typed `Any`.
- **Why rejected**: conflates two semantic cases (model-resolved vs. parser-produced); blocks any future static carrier discrimination; duplicates the source-text identifier in the named case.

### Existential wrapper without carrier tag (`Entry[?]`)
- **What**: `case class Entry[A](impl: …); Map[SymbolName, Entry[?]]` — heterogeneous entries hidden behind an existential.
- **Why rejected**: re-introduces erasure at every open-existential site; provides no exhaustivity guarantee over registered carriers; pure ceremony with no static gain over `Any`.

### Type-indexed `BoundTerm[A]` throughout the IR
- **What**: parameterise the entire `BoundTerm` enum by the literal carrier type.
- **Why rejected**: forces `A` into every signature in `QueryBinder`/`TypedSemantics`/`TypeChecker` even though >90% of nodes (variables, function applications, quantifiers) have no payload type to index by; unjustified pervasive complexity.

---

## References

- [ADR-015](ADR-015.md) — Symmetric `Value` boundaries (parent)
- [PLAN-symmetric-value-boundaries.md](PLAN-symmetric-value-boundaries.md) — Phase 5a/5b/5c gating
- LLVM `llvm::Constant` hierarchy; Roslyn `BoundLiteral`; GHC `Literal`
