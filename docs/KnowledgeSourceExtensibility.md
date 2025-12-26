# Implementing Custom KnowledgeSource Backends

This guide explains how to implement custom `KnowledgeSource` implementations to integrate different data backends (SQL databases, RDF stores, graph databases, etc.) with the vague quantifier semantics engine.

## Overview

The `KnowledgeSource` trait provides an abstraction layer that decouples the vague semantics evaluation from the underlying data storage mechanism. This allows you to:

- Connect to SQL databases (PostgreSQL, MySQL, etc.)
- Query RDF/SPARQL endpoints
- Integrate with graph databases (Neo4j, etc.)
- Use custom in-memory structures
- Implement lazy/streaming data sources

## Architecture

The vague quantifier evaluation pipeline uses `KnowledgeSource` in three main ways:

1. **Domain Extraction** (`DomainExtraction`) - Gets sets of values from relations
2. **Range Extraction** (`RangeExtractor`) - Queries relations with patterns to extract D_R
3. **Model Construction** (`KnowledgeSourceModel`) - Converts source to FOL Model for scope evaluation

```
┌─────────────────┐
│ VagueSemantics  │
└────────┬────────┘
         │
         ├─→ RangeExtractor.extractRange(source, query)
         │   └─→ source.queryRelation(name, pattern)
         │
         ├─→ KnowledgeSourceModel.toModel(source)
         │   └─→ source.relationNames
         │       source.getRelation(name)
         │       source.queryRelation(name, pattern)
         │
         └─→ ScopeEvaluator.calculateProportion(sample, scope, model)
             (Model is already constructed, no source access)
```

## The KnowledgeSource Interface

```scala
trait KnowledgeSource:
  
  /** Get relation schema by name */
  def getRelation(name: String): Option[Relation]
  
  /** Query relation with pattern, returns matching tuples */
  def queryRelation(
    name: String, 
    pattern: List[PatternElement]
  ): Set[RelationTuple]
  
  /** Get all available relation names (for Model construction) */
  def relationNames: Set[String]
```

### Core Methods

#### `getRelation(name: String): Option[Relation]`

Returns the schema information for a relation:
- **name**: Relation identifier
- **arity**: Number of arguments
- **positionTypes**: Type of each position (Constant, Numeric, etc.)

**Example**: For a `person(name, age)` relation:
```scala
Some(Relation(
  name = "person",
  arity = 2,
  positionTypes = List(PositionType.Constant, PositionType.Numeric)
))
```

#### `queryRelation(name: String, pattern: List[PatternElement]): Set[RelationTuple]`

Queries the relation with a pattern:
- **PatternElement.Wildcard**: Match any value at this position
- **PatternElement.Specific(value)**: Match specific value

**Example**: Query `capital(_, "France")` to find France's capital:
```scala
queryRelation(
  "capital", 
  List(PatternElement.Wildcard, PatternElement.Specific(RConst("France")))
)
// Returns: Set(RelationTuple(List(RConst("Paris"), RConst("France"))))
```

#### `relationNames: Set[String]`

Returns all available relation names. Used by `KnowledgeSourceModel` to discover predicates when constructing the FOL Model.

**Example**:
```scala
relationNames // Set("country", "capital", "large", "coastal")
```

## Implementation Examples

### Example 1: SQL Database Backend

```scala
import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}
import scala.collection.mutable

class SQLKnowledgeSource(
  jdbcUrl: String,
  username: String, 
  password: String
) extends KnowledgeSource:
  
  private lazy val connection: Connection = 
    DriverManager.getConnection(jdbcUrl, username, password)
  
  // Cache relation schemas from database metadata
  private lazy val schemaCache: Map[String, Relation] = 
    loadSchemas()
  
  override def relationNames: Set[String] = 
    schemaCache.keySet
  
  override def getRelation(name: String): Option[Relation] = 
    schemaCache.get(name)
  
  override def queryRelation(
    name: String, 
    pattern: List[PatternElement]
  ): Set[RelationTuple] =
    
    getRelation(name) match
      case None => Set.empty
      case Some(relation) =>
        // Build SQL query with WHERE clause for specific values
        val (sql, params) = buildQuery(name, pattern, relation)
        executeQuery(sql, params)
  
  private def buildQuery(
    tableName: String,
    pattern: List[PatternElement],
    relation: Relation
  ): (String, List[RelationValue]) =
    
    val columnNames = (1 to relation.arity).map(i => s"col$i")
    val baseSql = s"SELECT ${columnNames.mkString(", ")} FROM $tableName"
    
    // Build WHERE clause for specific values
    val conditions = mutable.ArrayBuffer[String]()
    val params = mutable.ArrayBuffer[RelationValue]()
    
    pattern.zipWithIndex.foreach {
      case (PatternElement.Specific(value), idx) =>
        conditions += s"${columnNames(idx)} = ?"
        params += value
      case (PatternElement.Wildcard, _) =>
        // No condition for wildcards
    }
    
    val whereCl = if conditions.isEmpty then ""
                 else s" WHERE ${conditions.mkString(" AND ")}"
    
    (baseSql + whereCl, params.toList)
  
  private def executeQuery(
    sql: String, 
    params: List[RelationValue]
  ): Set[RelationTuple] =
    
    val stmt = connection.prepareStatement(sql)
    
    // Bind parameters
    params.zipWithIndex.foreach { case (value, idx) =>
      value match
        case RelationValue.Const(s) => stmt.setString(idx + 1, s)
        case RelationValue.Num(n) => stmt.setDouble(idx + 1, n)
    }
    
    val rs = stmt.executeQuery()
    val results = mutable.Set[RelationTuple]()
    
    while rs.next() do
      val values = (1 to rs.getMetaData.getColumnCount).map { i =>
        val colType = rs.getMetaData.getColumnType(i)
        if colType == java.sql.Types.NUMERIC || colType == java.sql.Types.DOUBLE then
          RelationValue.Num(rs.getDouble(i))
        else
          RelationValue.Const(rs.getString(i))
      }.toList
      
      results += RelationTuple(values)
    
    rs.close()
    stmt.close()
    results.toSet
  
  private def loadSchemas(): Map[String, Relation] =
    val metadata = connection.getMetaData
    val tables = metadata.getTables(null, null, "%", Array("TABLE"))
    val schemas = mutable.Map[String, Relation]()
    
    while tables.next() do
      val tableName = tables.getString("TABLE_NAME")
      val columns = metadata.getColumns(null, null, tableName, "%")
      val columnTypes = mutable.ArrayBuffer[PositionType]()
      
      while columns.next() do
        val colType = columns.getInt("DATA_TYPE")
        columnTypes += (
          if colType == java.sql.Types.NUMERIC || colType == java.sql.Types.DOUBLE
          then PositionType.Numeric
          else PositionType.Constant
        )
      
      schemas(tableName) = Relation(
        tableName, 
        columnTypes.length, 
        columnTypes.toList
      )
    
    schemas.toMap
  
  def close(): Unit = 
    if !connection.isClosed then connection.close()

// Usage
val source = SQLKnowledgeSource(
  "jdbc:postgresql://localhost:5432/mydb",
  "user",
  "password"
)

val query = VagueQuery(
  Quantifier.mostStrict,
  "x",
  FOL("country", List(Var("x"))),
  FOL("large", List(Var("x")))
)

val result = VagueSemantics.holds(query, source, Map.empty)
println(s"Satisfied: ${result.satisfied}")

source.close()
```

### Example 2: RDF/SPARQL Backend

```scala
import org.apache.jena.query.{QueryFactory, QueryExecutionFactory}
import org.apache.jena.rdf.model.{ModelFactory, Resource}

class RDFKnowledgeSource(sparqlEndpoint: String) extends KnowledgeSource:
  
  // Map predicate names to SPARQL property URIs
  private val predicateMapping: Map[String, String] = Map(
    "country" -> "http://example.org/Country",
    "capital" -> "http://example.org/hasCapital",
    "large" -> "http://example.org/isLarge"
  )
  
  override def relationNames: Set[String] = 
    predicateMapping.keySet
  
  override def getRelation(name: String): Option[Relation] =
    predicateMapping.get(name).map { uri =>
      // Query SPARQL endpoint to determine arity
      val arity = inferArity(uri)
      Relation(name, arity, PositionType.allConstants(arity))
    }
  
  override def queryRelation(
    name: String,
    pattern: List[PatternElement]
  ): Set[RelationTuple] =
    
    predicateMapping.get(name) match
      case None => Set.empty
      case Some(uri) =>
        val sparql = buildSPARQL(uri, pattern)
        executeSPARQL(sparql, pattern.length)
  
  private def buildSPARQL(
    predicateUri: String,
    pattern: List[PatternElement]
  ): String =
    
    val vars = pattern.indices.map(i => s"?x$i")
    val triples = pattern match
      case List(p) =>
        // Unary: ?x rdf:type <URI>
        s"?x0 rdf:type <$predicateUri>"
      case List(p1, p2) =>
        // Binary: ?x0 <URI> ?x1
        s"?x0 <$predicateUri> ?x1"
      case _ =>
        // N-ary (custom encoding)
        s"?x0 <$predicateUri> ?tuple . " +
        pattern.indices.tail.map(i => 
          s"?tuple <http://example.org/arg$i> ?x$i"
        ).mkString(" . ")
    
    // Add filters for specific values
    val filters = pattern.zipWithIndex.collect {
      case (PatternElement.Specific(RelationValue.Const(v)), idx) =>
        s"FILTER(?x$idx = <${toURI(v)}>)"
    }.mkString(" ")
    
    s"""
       |SELECT ${vars.mkString(" ")}
       |WHERE {
       |  $triples
       |  $filters
       |}
       |""".stripMargin
  
  private def executeSPARQL(
    sparql: String,
    arity: Int
  ): Set[RelationTuple] =
    
    val query = QueryFactory.create(sparql)
    val qexec = QueryExecutionFactory.sparqlService(sparqlEndpoint, query)
    
    try
      val results = qexec.execSelect()
      val tuples = mutable.Set[RelationTuple]()
      
      while results.hasNext do
        val soln = results.next()
        val values = (0 until arity).map { i =>
          val node = soln.get(s"x$i")
          if node.isLiteral then
            RelationValue.Const(node.asLiteral().getString)
          else
            RelationValue.Const(node.asResource().getURI)
        }.toList
        
        tuples += RelationTuple(values)
      
      tuples.toSet
    finally
      qexec.close()
  
  private def inferArity(uri: String): Int = 
    // Query to determine predicate arity
    // Implementation depends on your RDF schema
    1 // Default to unary
  
  private def toURI(value: String): String =
    if value.startsWith("http://") then value
    else s"http://example.org/$value"

// Usage
val rdfSource = RDFKnowledgeSource("http://dbpedia.org/sparql")

val query = VagueQuery(
  Quantifier.about(1, 2),
  "x",
  FOL("Country", List(Var("x"))),
  FOL("isLarge", List(Var("x")))
)

val result = VagueSemantics.holds(query, rdfSource, Map.empty)
```

### Example 3: Lazy/Streaming Source

For large datasets, implement lazy evaluation:

```scala
class LazyKnowledgeSource(
  dataLoader: String => Iterator[RelationTuple]
) extends KnowledgeSource:
  
  private val relationSchemas: Map[String, Relation] = loadSchemas()
  
  override def relationNames: Set[String] = relationSchemas.keySet
  
  override def getRelation(name: String): Option[Relation] = 
    relationSchemas.get(name)
  
  override def queryRelation(
    name: String,
    pattern: List[PatternElement]
  ): Set[RelationTuple] =
    
    // Stream data and filter in memory
    // (For truly large data, consider returning Iterator instead)
    dataLoader(name)
      .filter(tuple => matchesPattern(tuple, pattern))
      .toSet
  
  private def matchesPattern(
    tuple: RelationTuple,
    pattern: List[PatternElement]
  ): Boolean =
    tuple.values.zip(pattern).forall {
      case (value, PatternElement.Wildcard) => true
      case (value, PatternElement.Specific(target)) => value == target
    }
  
  private def loadSchemas(): Map[String, Relation] = ???
```

## Testing Your Implementation

### Unit Tests

```scala
class MyKnowledgeSourceSpec extends munit.FunSuite:
  
  val source = MyKnowledgeSource(...)
  
  test("relationNames returns all relations"):
    val names = source.relationNames
    assert(names.contains("myrelation"))
  
  test("getRelation returns correct schema"):
    val rel = source.getRelation("myrelation")
    assertEquals(rel.map(_.arity), Some(2))
  
  test("queryRelation with wildcards returns all tuples"):
    val pattern = List(PatternElement.Wildcard, PatternElement.Wildcard)
    val results = source.queryRelation("myrelation", pattern)
    assert(results.nonEmpty)
  
  test("queryRelation with specific value filters correctly"):
    val pattern = List(
      PatternElement.Specific(RelationValue.Const("test")),
      PatternElement.Wildcard
    )
    val results = source.queryRelation("myrelation", pattern)
    assert(results.forall(_.values.head == RelationValue.Const("test")))
  
  test("empty relation returns empty set"):
    val results = source.queryRelation("nonexistent", List())
    assertEquals(results, Set.empty)
```

### Integration Tests

```scala
class MyKnowledgeSourceIntegrationSpec extends munit.FunSuite:
  
  val source = MyKnowledgeSource(...)
  
  test("VagueSemantics.holds works with custom source"):
    val query = VagueQuery(
      Quantifier.mostStrict,
      "x",
      FOL("entity", List(Var("x"))),
      FOL("property", List(Var("x")))
    )
    
    val result = VagueSemantics.holds(query, source, Map.empty)
    assert(result.rangeSize > 0)
    assert(result.actualProportion >= 0.0 && result.actualProportion <= 1.0)
  
  test("RangeExtractor works with custom source"):
    val query = VagueQuery(...)
    val range = RangeExtractor.extractRange(source, query, Map.empty)
    assert(range.nonEmpty)
  
  test("DomainExtraction works with custom source"):
    val domain = DomainExtraction.extractActiveDomain(source)
    assert(domain.nonEmpty)
```

## Performance Considerations

### Caching

For remote sources (SQL, SPARQL), cache relation schemas:

```scala
class CachedKnowledgeSource(underlying: KnowledgeSource) 
  extends KnowledgeSource:
  
  private val schemaCache = mutable.Map[String, Option[Relation]]()
  private val queryCache = mutable.Map[(String, List[PatternElement]), Set[RelationTuple]]()
  
  override def relationNames: Set[String] = 
    underlying.relationNames // Cache this too if expensive
  
  override def getRelation(name: String): Option[Relation] =
    schemaCache.getOrElseUpdate(name, underlying.getRelation(name))
  
  override def queryRelation(
    name: String,
    pattern: List[PatternElement]
  ): Set[RelationTuple] =
    queryCache.getOrElseUpdate(
      (name, pattern),
      underlying.queryRelation(name, pattern)
    )
```

### Batch Queries

If your backend supports batch queries, override additional methods:

```scala
trait BatchKnowledgeSource extends KnowledgeSource:
  
  def queryRelations(
    queries: List[(String, List[PatternElement])]
  ): Map[(String, List[PatternElement]), Set[RelationTuple]]
```

### Connection Pooling

For SQL sources, use connection pooling:

```scala
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

class PooledSQLKnowledgeSource(jdbcUrl: String) extends KnowledgeSource:
  
  private val config = new HikariConfig()
  config.setJdbcUrl(jdbcUrl)
  config.setMaximumPoolSize(10)
  
  private val dataSource = new HikariDataSource(config)
  
  override def queryRelation(...): Set[RelationTuple] =
    val conn = dataSource.getConnection
    try
      // Execute query
      ???
    finally
      conn.close() // Returns to pool
```

## Design Patterns

### Factory Pattern

```scala
object KnowledgeSource:
  
  def fromKnowledgeBase(kb: KnowledgeBase): KnowledgeSource =
    InMemoryKnowledgeSource(kb)
  
  def fromSQL(jdbcUrl: String, user: String, pass: String): KnowledgeSource =
    SQLKnowledgeSource(jdbcUrl, user, pass)
  
  def fromRDF(endpoint: String): KnowledgeSource =
    RDFKnowledgeSource(endpoint)
  
  def fromConfig(config: Config): KnowledgeSource =
    config.getString("type") match
      case "sql" => fromSQL(...)
      case "rdf" => fromRDF(...)
      case "memory" => fromKnowledgeBase(...)
```

### Composite Pattern

Combine multiple sources:

```scala
class CompositeKnowledgeSource(sources: List[KnowledgeSource]) 
  extends KnowledgeSource:
  
  override def relationNames: Set[String] =
    sources.flatMap(_.relationNames).toSet
  
  override def getRelation(name: String): Option[Relation] =
    sources.view.flatMap(_.getRelation(name)).headOption
  
  override def queryRelation(
    name: String,
    pattern: List[PatternElement]
  ): Set[RelationTuple] =
    sources.flatMap(_.queryRelation(name, pattern)).toSet
```

## Common Pitfalls

1. **Forgetting relationNames**: `KnowledgeSourceModel` needs this to discover predicates
2. **Incorrect arity**: Ensure `getRelation().arity` matches actual data
3. **Pattern matching bugs**: Test both Wildcard and Specific patterns thoroughly
4. **Type mismatches**: Respect PositionType (Constant vs Numeric)
5. **Resource leaks**: Always close connections/streams
6. **Empty results**: Return empty Set, not throw exceptions

## Migration from KnowledgeBase

Existing code using `KnowledgeBase` directly:

```scala
// OLD
val kb = KnowledgeBase(...)
val result = VagueSemantics.holds(query, kb, Map.empty)

// NEW (Option 1: Wrap)
val source = KnowledgeSource.fromKnowledgeBase(kb)
val result = VagueSemantics.holds(query, source, Map.empty)

// NEW (Option 2: Use backward-compatible method)
val result = VagueSemantics.holdsOnKB(query, kb, Map.empty)
```

## Additional Resources

- See `InMemoryKnowledgeSource` for reference implementation
- See `KnowledgeSourceModel` for how sources are used in Model construction
- See test files for usage examples: `RangeExtractorSpec`, `VagueSemanticsSpec`
- Architecture decision: [docs/ArchitectureDecisions.md](ArchitectureDecisions.md)

## Questions?

For implementation help or architectural questions, please open an issue on GitHub.
