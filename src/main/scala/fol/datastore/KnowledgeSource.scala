package fol.datastore

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
  *
  * @tparam D the domain element type
  */
trait KnowledgeSource[D]:

  /** Check if a relation exists in this knowledge source. */
  def hasRelation(relationName: RelationName): Boolean

  /** Get the relation schema. */
  def getRelation(relationName: RelationName): Option[Relation]

  /** Check if a specific fact holds. */
  def contains(relationName: RelationName, tuple: RelationTuple[D]): Boolean

  /** Get all unique values at a specific position of a relation.
    *
    * This returns the **full domain** for that position.
    * WARNING: For large datasets, use sampleDomain instead!
    */
  def getDomain(relationName: RelationName, position: Int): Set[D]

  /** Sample random elements from a relation's domain position.
    *
    * This is the **key method for scalable querying**.
    * For SQL backends, this should use "ORDER BY RANDOM() LIMIT n".
    */
  def sampleDomain(
    relationName: RelationName,
    position: Int,
    sampleSize: Int,
    seed: Option[Long] = None
  ): Set[D]

  /** Query facts matching a pattern.
    *
    * Pattern uses Option[D]:
    * - Some(value): must match exactly
    * - None: wildcard (matches anything)
    */
  def query(relationName: RelationName, pattern: List[Option[D]]): Set[RelationTuple[D]]

  /** Get the total number of facts in a relation. */
  def count(relationName: RelationName): Int

  /** Get all values used in the knowledge source (active domain). */
  def activeDomain: Set[D]

  /** Sample random elements from the active domain. */
  def sampleActiveDomain(sampleSize: Int, seed: Option[Long] = None): Set[D]

  /** Get all relation names defined in this source. */
  def relationNames: Set[RelationName]

object KnowledgeSource:

  /** Create a knowledge source from an existing KnowledgeBase. */
  def fromKnowledgeBase[D](kb: KnowledgeBase[D]): KnowledgeSource[D] =
    new InMemoryKnowledgeSource(kb)

/** In-memory knowledge source backed by KnowledgeBase.
  *
  * @tparam D the domain element type
  */
class InMemoryKnowledgeSource[D](kb: KnowledgeBase[D]) extends KnowledgeSource[D]:

  def hasRelation(relationName: RelationName): Boolean =
    kb.hasRelation(relationName)

  def getRelation(relationName: RelationName): Option[Relation] =
    kb.getRelation(relationName)

  def contains(relationName: RelationName, tuple: RelationTuple[D]): Boolean =
    kb.contains(relationName, tuple)

  def getDomain(relationName: RelationName, position: Int): Set[D] =
    kb.getDomain(relationName, position)

  // TODO: Replace scala.util.Random with HDRSampler when this method
  //       is needed on the evaluation path (currently unused — main
  //       evaluation uses getDomain + HDRSampler via ProportionEstimator).
  //       See gate G1b: "no scala.util.Random anywhere".
  def sampleDomain(
    relationName: RelationName,
    position: Int,
    sampleSize: Int,
    seed: Option[Long] = None
  ): Set[D] =
    val fullDomain = getDomain(relationName, position)

    // If domain is smaller than sample size, return full domain
    if fullDomain.size <= sampleSize then
      fullDomain
    else
      // TODO: Replace with HDRSampler (scala.util.Random — see above)
      val rng = seed match
        case Some(s) => new scala.util.Random(s)
        case None => new scala.util.Random()

      fullDomain.toVector
        .sortBy(_ => rng.nextDouble())
        .take(sampleSize)
        .toSet

  def query(relationName: RelationName, pattern: List[Option[D]]): Set[RelationTuple[D]] =
    kb.query(relationName, pattern)

  def count(relationName: RelationName): Int =
    kb.count(relationName)

  def activeDomain: Set[D] =
    kb.activeDomain

  // TODO: Replace scala.util.Random with HDRSampler (same as sampleDomain above)
  def sampleActiveDomain(sampleSize: Int, seed: Option[Long] = None): Set[D] =
    val fullDomain = activeDomain

    if fullDomain.size <= sampleSize then
      fullDomain
    else
      // TODO: Replace with HDRSampler (scala.util.Random — see sampleDomain)
      val rng = seed match
        case Some(s) => new scala.util.Random(s)
        case None => new scala.util.Random()

      fullDomain.toVector
        .sortBy(_ => rng.nextDouble())
        .take(sampleSize)
        .toSet

  def relationNames: Set[RelationName] =
    kb.schema.keySet

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
