package fol.datastore

import fol.error.QueryError
import scala.reflect.ClassTag

/** Knowledge source abstraction for querying relational data.
  *
  * This trait provides a uniform interface for accessing relational data,
  * whether stored in-memory (KnowledgeBase) or in external databases.
  *
  * State-dependent methods return `Either[QueryError, A]` so that
  * callers handle missing relations, schema mismatches, and (for future
  * implementations) I/O errors through a typed channel.
  * See ADR-012 (Error Channel Policy).
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

  /** Check if a specific fact holds.
    *
    * Returns `Left` if the relation does not exist in the schema.
    */
  def contains(relationName: RelationName, tuple: RelationTuple[D]): Either[QueryError, Boolean]

  /** Get all unique values at a specific position of a relation.
    *
    * Returns `Left` if the relation does not exist or position is out of bounds.
    */
  def getDomain(relationName: RelationName, position: Int): Either[QueryError, Set[D]]

  /** Query facts matching a pattern.
    *
    * Pattern uses Option[D]:
    * - Some(value): must match exactly
    * - None: wildcard (matches anything)
    *
    * Returns `Left` if the relation does not exist or pattern length
    * does not match the relation's arity.
    */
  def query(relationName: RelationName, pattern: List[Option[D]]): Either[QueryError, Set[RelationTuple[D]]]

  /** Get the total number of facts in a relation.
    *
    * Returns `Left` if the relation does not exist in the schema.
    */
  def count(relationName: RelationName): Either[QueryError, Int]

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

  def contains(relationName: RelationName, tuple: RelationTuple[D]): Either[QueryError, Boolean] =
    kb.contains(relationName, tuple)

  def getDomain(relationName: RelationName, position: Int): Either[QueryError, Set[D]] =
    kb.getDomain(relationName, position)

  def query(relationName: RelationName, pattern: List[Option[D]]): Either[QueryError, Set[RelationTuple[D]]] =
    kb.query(relationName, pattern)

  def count(relationName: RelationName): Either[QueryError, Int] =
    kb.count(relationName)

  def activeDomain: Set[D] =
    kb.activeDomain

  def relationNames: Set[RelationName] =
    kb.schema.keySet
