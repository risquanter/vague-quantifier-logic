package vague.datastore

import scala.reflect.ClassTag

/** Knowledge source abstraction for querying relational data.
  * 
  * This trait provides a uniform interface for accessing relational data,
  * whether stored in-memory (KnowledgeBase) or in external databases (SQL).
  * 
  * The key design principle is to support **sampling-first** operations:
  * - Don't materialize full domains (can be millions of rows)
  * - Support random sampling at the source (e.g., SQL's ORDER BY RANDOM())
  * - Enable lazy evaluation and streaming where possible
  * 
  * This abstraction makes vague quantifier queries scalable:
  * - "Most employees are satisfied" only needs a sample, not all employees
  * - Sampling can happen in the database, not in Scala memory
  * - Predicate evaluation can be pushed to the database (future work)
  * 
  * Based on Section 5.1 (Data Model) and Section 5.2 (Query Semantics)
  * from Fermüller, Hofer, and Ortiz (2017).
  */
trait KnowledgeSource:
  
  /** Check if a relation exists in this knowledge source.
    * 
    * @param relationName Name of the relation
    * @return true if relation is defined
    */
  def hasRelation(relationName: String): Boolean
  
  /** Get the relation schema.
    * 
    * @param relationName Name of the relation
    * @return Some(relation) if exists, None otherwise
    */
  def getRelation(relationName: String): Option[Relation]
  
  /** Check if a specific fact holds.
    * 
    * @param relationName Name of the relation
    * @param tuple Values to check
    * @return true if the tuple is in the relation
    */
  def contains(relationName: String, tuple: RelationTuple): Boolean
  
  /** Get all unique values at a specific position of a relation.
    * 
    * This returns the **full domain** for that position.
    * WARNING: For large datasets, use sampleDomain instead!
    * 
    * @param relationName Name of the relation
    * @param position Position index (0-based)
    * @return Set of all values at that position
    */
  def getDomain(relationName: String, position: Int): Set[RelationValue]
  
  /** Sample random elements from a relation's domain position.
    * 
    * This is the **key method for scalable querying**.
    * For SQL backends, this should use "ORDER BY RANDOM() LIMIT n".
    * 
    * @param relationName Name of the relation
    * @param position Position index (0-based)
    * @param sampleSize Number of elements to sample
    * @param seed Optional random seed for reproducibility
    * @return Sampled set (size ≤ sampleSize, may be smaller if domain is small)
    */
  def sampleDomain(
    relationName: String,
    position: Int,
    sampleSize: Int,
    seed: Option[Long] = None
  ): Set[RelationValue]
  
  /** Query facts matching a pattern.
    * 
    * Pattern uses Option[RelationValue]:
    * - Some(value): must match exactly
    * - None: wildcard (matches anything)
    * 
    * Example: query("has_risk", List(Some(Const("C1")), None))
    *   returns all risks for component C1
    * 
    * @param relationName Name of the relation
    * @param pattern Pattern to match (None = wildcard)
    * @return Set of matching tuples
    */
  def query(relationName: String, pattern: List[Option[RelationValue]]): Set[RelationTuple]
  
  /** Get the total number of facts in a relation.
    * 
    * @param relationName Name of the relation
    * @return Number of tuples in the relation
    */
  def count(relationName: String): Int
  
  /** Get all constants and numbers used in the knowledge source.
    * 
    * This is the "active domain" in database theory - all values
    * that actually appear in the data.
    * 
    * WARNING: For large datasets, use sampleActiveDomain instead!
    * 
    * @return Set of all relation values used
    */
  def activeDomain: Set[RelationValue]
  
  /** Sample random elements from the active domain.
    * 
    * For SQL backends, this should sample across all tables.
    * 
    * @param sampleSize Number of elements to sample
    * @param seed Optional random seed for reproducibility
    * @return Sampled set from active domain
    */
  def sampleActiveDomain(sampleSize: Int, seed: Option[Long] = None): Set[RelationValue]

object KnowledgeSource:
  
  /** Create a knowledge source from an existing KnowledgeBase.
    * 
    * This wraps the in-memory KnowledgeBase to provide the
    * KnowledgeSource interface, enabling sampling operations.
    * 
    * @param kb The knowledge base to wrap
    * @return A knowledge source backed by the KB
    */
  def fromKnowledgeBase(kb: KnowledgeBase): KnowledgeSource =
    new InMemoryKnowledgeSource(kb)

/** In-memory knowledge source backed by KnowledgeBase.
  * 
  * This implementation delegates to an existing KnowledgeBase
  * and adds sampling capabilities using Scala's random utilities.
  * 
  * For production systems with large datasets, consider implementing
  * a SQL-backed knowledge source instead.
  */
class InMemoryKnowledgeSource(kb: KnowledgeBase) extends KnowledgeSource:
  
  def hasRelation(relationName: String): Boolean =
    kb.hasRelation(relationName)
  
  def getRelation(relationName: String): Option[Relation] =
    kb.getRelation(relationName)
  
  def contains(relationName: String, tuple: RelationTuple): Boolean =
    kb.contains(relationName, tuple)
  
  def getDomain(relationName: String, position: Int): Set[RelationValue] =
    kb.getDomain(relationName, position)
  
  def sampleDomain(
    relationName: String,
    position: Int,
    sampleSize: Int,
    seed: Option[Long] = None
  ): Set[RelationValue] =
    val fullDomain = getDomain(relationName, position)
    
    // If domain is smaller than sample size, return full domain
    if fullDomain.size <= sampleSize then
      fullDomain
    else
      // Sample using Scala's Random with optional seed
      val rng = seed match
        case Some(s) => new scala.util.Random(s)
        case None => new scala.util.Random()
      
      // Reservoir sampling for unbiased random sample
      fullDomain.toVector
        .sortBy(_ => rng.nextDouble())  // Simple shuffle
        .take(sampleSize)
        .toSet
  
  def query(relationName: String, pattern: List[Option[RelationValue]]): Set[RelationTuple] =
    kb.query(relationName, pattern)
  
  def count(relationName: String): Int =
    kb.count(relationName)
  
  def activeDomain: Set[RelationValue] =
    kb.activeDomain
  
  def sampleActiveDomain(sampleSize: Int, seed: Option[Long] = None): Set[RelationValue] =
    val fullDomain = activeDomain
    
    if fullDomain.size <= sampleSize then
      fullDomain
    else
      val rng = seed match
        case Some(s) => new scala.util.Random(s)
        case None => new scala.util.Random()
      
      fullDomain.toVector
        .sortBy(_ => rng.nextDouble())
        .take(sampleSize)
        .toSet

/** Extension methods for SQL backend implementation (future work).
  * 
  * To implement a SQL-backed knowledge source:
  * 
  * 1. Create SQLKnowledgeSource(connection: Connection) extends KnowledgeSource
  * 
  * 2. Implement sampleDomain using SQL random sampling:
  *    ```sql
  *    SELECT DISTINCT column 
  *    FROM table 
  *    ORDER BY RANDOM() 
  *    LIMIT sampleSize
  *    ```
  * 
  * 3. Implement query using SQL WHERE clauses:
  *    ```sql
  *    SELECT * FROM relation 
  *    WHERE col1 = ? AND col2 = ?  -- for exact matches
  *    ```
  * 
  * 4. For vague quantifier evaluation, consider pushing predicates to SQL:
  *    ```sql
  *    SELECT COUNT(*) FILTER (WHERE predicate) / COUNT(*)::float
  *    FROM (SELECT * FROM table ORDER BY RANDOM() LIMIT n)
  *    ```
  * 
  * Benefits of SQL backend:
  * - Sampling happens in database (much faster for large datasets)
  * - No need to materialize full tables in Scala memory
  * - Can leverage database indexes and query optimization
  * - Scales to millions/billions of rows
  * 
  * Trade-offs:
  * - More complex predicate compilation (Scala function → SQL expression)
  * - Network latency for each query
  * - Database-specific SQL dialects (PostgreSQL, MySQL, etc.)
  */
object SQLKnowledgeSourceGuide
