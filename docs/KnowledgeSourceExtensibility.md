# Implementing Custom KnowledgeSource Backends

Guide for integrating data backends (SQL, RDF, graph databases, etc.)
with the vague quantifier evaluation pipeline.

---

## Overview

The `KnowledgeSource` trait decouples evaluation from storage. Implement
it to connect any data source — SQL databases, SPARQL endpoints, graph
databases, or custom in-memory structures.

## Architecture

The evaluation pipeline uses `KnowledgeSource` in three ways:

```
┌─────────────────┐
│ VagueSemantics  │
└────────┬────────┘
         │
         ├─→ RangeExtractor.extractRange(source, query)
         │   └─→ source.queryRelation(name, pattern)
         │
         ├─→ KnowledgeSourceModel.toModel(source)
         │   └─→ source.relationNames / getRelation / queryRelation
         │
         └─→ FOLBridge.scopeToPredicate(scope, var, source)
             (Model constructed from source, then FOLSemantics.holds())
```

---

## The KnowledgeSource Interface

```scala
trait KnowledgeSource:
  def getRelation(name: String): Option[Relation]
  def queryRelation(name: String, pattern: List[PatternElement]): Set[RelationTuple]
  def relationNames: Set[String]
```

### `getRelation(name: String): Option[Relation]`

Returns schema — name, arity, position types.

### `queryRelation(name: String, pattern: List[PatternElement]): Set[RelationTuple]`

Queries with pattern matching:
- `PatternElement.Wildcard` — match any value
- `PatternElement.Specific(value)` — match exact value

```scala
// Find France's capital: capital(_, "France")
source.queryRelation("capital",
  List(PatternElement.Wildcard, PatternElement.Specific(RConst("France"))))
// → Set(RelationTuple(List(RConst("Paris"), RConst("France"))))
```

### `relationNames: Set[String]`

All available relation names. `KnowledgeSourceModel` needs this to
discover predicates when constructing the FOL `Model[Any]`.

---

## Example: SQL Backend

```scala
import fol.datastore.*

class SQLKnowledgeSource(jdbcUrl: String, user: String, pass: String)
    extends KnowledgeSource:

  private lazy val connection = DriverManager.getConnection(jdbcUrl, user, pass)
  private lazy val schemaCache: Map[String, Relation] = loadSchemas()

  override def relationNames: Set[String] = schemaCache.keySet

  override def getRelation(name: String): Option[Relation] = schemaCache.get(name)

  override def queryRelation(
      name: String, pattern: List[PatternElement]): Set[RelationTuple] =
    getRelation(name) match
      case None => Set.empty
      case Some(relation) =>
        val (sql, params) = buildQuery(name, pattern, relation)
        executeQuery(sql, params)

  private def buildQuery(table: String, pattern: List[PatternElement],
      rel: Relation): (String, List[RelationValue]) =
    val cols = (1 to rel.arity).map(i => s"col$i")
    val conditions = pattern.zipWithIndex.collect {
      case (PatternElement.Specific(v), i) => (s"${cols(i)} = ?", v)
    }
    val where = if conditions.isEmpty then ""
                else s" WHERE ${conditions.map(_._1).mkString(" AND ")}"
    (s"SELECT ${cols.mkString(", ")} FROM $table$where", conditions.map(_._2))

  // executeQuery, loadSchemas: standard JDBC — omitted for brevity
```

### Usage

```scala
import fol.parser.VagueQueryParser
import fol.semantics.VagueSemantics
import fol.sampling.{SamplingParams, HDRConfig}

val source = SQLKnowledgeSource("jdbc:postgresql://localhost/mydb", "user", "pw")

val query = VagueQueryParser.parse(
  "Q[>=]^{3/4} x (country(x), large(x))"
)

query.flatMap { q =>
  VagueSemantics.holds(q, source, Map.empty, SamplingParams.exact, HDRConfig())
} match
  case Right(result) => println(s"Satisfied: ${result.satisfied}, p=${result.proportion}")
  case Left(error)   => println(s"Error: ${error.message}")
```

---

## Example: RDF/SPARQL Backend

```scala
class RDFKnowledgeSource(sparqlEndpoint: String) extends KnowledgeSource:

  private val predicateMapping: Map[String, String] = Map(
    "country" -> "http://example.org/Country",
    "large"   -> "http://example.org/isLarge"
  )

  override def relationNames: Set[String] = predicateMapping.keySet

  override def getRelation(name: String): Option[Relation] =
    predicateMapping.get(name).map(_ => Relation(name, 1, List(PositionType.Constant)))

  override def queryRelation(name: String, pattern: List[PatternElement]): Set[RelationTuple] =
    predicateMapping.get(name) match
      case None      => Set.empty
      case Some(uri) => executeSPARQL(buildSPARQL(uri, pattern), pattern.length)

  // SPARQL query building, execution: standard Jena — omitted for brevity
```

---

## Example: Lazy/Streaming Source

```scala
class LazyKnowledgeSource(dataLoader: String => Iterator[RelationTuple])
    extends KnowledgeSource:

  private val schemas: Map[String, Relation] = loadSchemas()

  override def relationNames: Set[String] = schemas.keySet
  override def getRelation(name: String): Option[Relation] = schemas.get(name)

  override def queryRelation(name: String, pattern: List[PatternElement]): Set[RelationTuple] =
    dataLoader(name)
      .filter(tuple => matchesPattern(tuple, pattern))
      .toSet

  private def matchesPattern(tuple: RelationTuple, pattern: List[PatternElement]): Boolean =
    tuple.values.zip(pattern).forall {
      case (_, PatternElement.Wildcard)           => true
      case (value, PatternElement.Specific(target)) => value == target
    }
```

---

## Testing Your Implementation

```scala
class MyKnowledgeSourceSpec extends munit.FunSuite:

  val source = MyKnowledgeSource(...)

  test("relationNames returns all relations"):
    assert(source.relationNames.contains("myrelation"))

  test("getRelation returns correct schema"):
    assertEquals(source.getRelation("myrelation").map(_.arity), Some(2))

  test("queryRelation with wildcards returns all tuples"):
    val results = source.queryRelation("myrelation",
      List(PatternElement.Wildcard, PatternElement.Wildcard))
    assert(results.nonEmpty)

  test("queryRelation with specific value filters"):
    val results = source.queryRelation("myrelation",
      List(PatternElement.Specific(RelationValue.Const("test")), PatternElement.Wildcard))
    assert(results.forall(_.values.head == RelationValue.Const("test")))
```

### Integration test with evaluation pipeline

```scala
  test("VagueSemantics.holds works with custom source"):
    val parsed = VagueQueryParser.parse("Q[>=]^{1/2} x (entity(x), property(x))")
    val result = parsed.flatMap { q =>
      VagueSemantics.holds(q, source, Map.empty, SamplingParams.exact, HDRConfig())
    }
    assert(result.isRight)
    result.foreach { r =>
      assert(r.domainSize > 0)
      assert(r.proportion >= 0.0 && r.proportion <= 1.0)
    }
```

---

## Design Patterns

### Caching

```scala
class CachedKnowledgeSource(underlying: KnowledgeSource) extends KnowledgeSource:
  private val queryCache = mutable.Map[(String, List[PatternElement]), Set[RelationTuple]]()

  override def queryRelation(name: String, pattern: List[PatternElement]): Set[RelationTuple] =
    queryCache.getOrElseUpdate((name, pattern), underlying.queryRelation(name, pattern))
  // ... delegate relationNames, getRelation
```

### Composite

```scala
class CompositeKnowledgeSource(sources: List[KnowledgeSource]) extends KnowledgeSource:
  override def relationNames: Set[String] = sources.flatMap(_.relationNames).toSet
  override def getRelation(name: String): Option[Relation] =
    sources.view.flatMap(_.getRelation(name)).headOption
  override def queryRelation(name: String, pattern: List[PatternElement]): Set[RelationTuple] =
    sources.flatMap(_.queryRelation(name, pattern)).toSet
```

---

## Common Pitfalls

1. **Missing `relationNames`** — `KnowledgeSourceModel` needs this to discover predicates
2. **Incorrect arity** — `getRelation().arity` must match actual tuple sizes
3. **Pattern matching bugs** — test both `Wildcard` and `Specific` patterns
4. **Type mismatches** — respect `PositionType` (Constant vs Numeric)
5. **Resource leaks** — close connections/streams in custom backends

---

## Additional Resources

- `InMemoryKnowledgeSource` in `fol/datastore/KnowledgeSource.scala` — reference implementation
- `KnowledgeSourceModel` in `fol/bridge/` — how sources become FOL `Model[Any]`
- [Architecture.md](Architecture.md) — system overview and layer diagram
- [ADR-004](ADR-004.md) — tagless initial architecture and bridge pattern
