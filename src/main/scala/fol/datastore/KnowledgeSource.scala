package fol.datastore

import scala.reflect.ClassTag

/** Knowledge source abstraction for querying relational data.
  *
  * This trait provides a uniform interface for accessing relational data,
  * whether stored in-memory (KnowledgeBase) or in external databases.
  *
  * Sampling is handled externally by `HDRSampler` after domain
  * materialisation — the source's job is to provide complete domain
  * sets and pattern-match queries.
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

  /** Get all unique values at a specific position of a relation. */
  def getDomain(relationName: RelationName, position: Int): Set[D]

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

  def query(relationName: RelationName, pattern: List[Option[D]]): Set[RelationTuple[D]] =
    kb.query(relationName, pattern)

  def count(relationName: RelationName): Int =
    kb.count(relationName)

  def activeDomain: Set[D] =
    kb.activeDomain

  def relationNames: Set[RelationName] =
    kb.schema.keySet
