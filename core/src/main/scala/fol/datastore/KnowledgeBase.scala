package fol.datastore

import fol.error.QueryError

/** Knowledge Base
  *
  * A lightweight, in-memory datastore for relational facts.
  * Inspired by relational databases and RDF triple stores,
  * but simplified for FOL querying experiments.
  *
  * Based on concepts from Section 5.1 of Fermüller et al.,
  * "Querying with Vague Quantifiers Using Probabilistic Semantics"
  *
  * @tparam D the domain element type (e.g. `RelationValue`, `String`)
  */

/** Knowledge base containing relations and facts
  *
  * A knowledge base consists of:
  * - A schema (set of relation definitions)
  * - A set of ground facts (relation instances)
  *
  * @param schema Map from relation names to their schemas
  * @param facts Map from relation names to sets of tuples
  * @tparam D the domain element type
  */
case class KnowledgeBase[D](
  schema: Map[RelationName, Relation],
  facts: Map[RelationName, Set[RelationTuple[D]]]
):
  /** Get relation by name */
  def getRelation(name: RelationName): Option[Relation] =
    schema.get(name)

  /** Check if relation exists in schema */
  def hasRelation(name: RelationName): Boolean =
    schema.contains(name)

  /** Get all facts for a relation */
  def getFacts(relationName: RelationName): Set[RelationTuple[D]] =
    facts.getOrElse(relationName, Set.empty)

  /** Check if a fact exists.
    *
    * Returns `Left` if the relation does not exist in the schema.
    */
  def contains(relationName: RelationName, tuple: RelationTuple[D]): Either[QueryError, Boolean] =
    if !schema.contains(relationName) then
      Left(QueryError.RelationNotFoundError(relationName, schema.keySet))
    else
      Right(getFacts(relationName).contains(tuple))

  /** Add a relation to the schema.
    *
    * Returns `Left` if a relation with the same name already exists.
    */
  def addRelation(relation: Relation): Either[QueryError, KnowledgeBase[D]] =
    if schema.contains(relation.name) then
      Left(QueryError.DataStoreError(
        s"Relation ${relation.name.value} already exists",
        operation = "addRelation",
        relation = Some(relation.name.value)
      ))
    else
      Right(copy(schema = schema + (relation.name -> relation)))

  /** Add a fact (validates arity against schema).
    *
    * For symmetric binary relations (see [[RelationProperty.Symmetric]]),
    * the reverse tuple `(B, A)` is automatically materialised alongside
    * `(A, B)`.  This happens at insert time so that `contains`, `query`,
    * and all downstream layers see both directions with zero query overhead.
    *
    * @see docs/ADR-009.md
    */
  def addFact(relationName: RelationName, tuple: RelationTuple[D]): Either[QueryError, KnowledgeBase[D]] =
    schema.get(relationName) match
      case None =>
        Left(QueryError.RelationNotFoundError(relationName, schema.keySet))
      case Some(relation) =>
        if tuple.arity != relation.arity then
          Left(QueryError.SchemaError(
            s"Tuple $tuple has arity ${tuple.arity}, expected ${relation.arity} for relation ${relation}",
            relationName = relationName,
            expectedArity = relation.arity,
            actualArity = tuple.arity
          ))
        else
          val currentFacts = facts.getOrElse(relationName, Set.empty)
          val withForward = currentFacts + tuple
          val updated =
            if relation.isSymmetric then
              val reversed = RelationTuple(tuple.values.reverse)
              withForward + reversed
            else
              withForward
          Right(copy(facts = facts + (relationName -> updated)))

  /** Add multiple facts at once.
    *
    * Stops at the first error and returns it.
    */
  def addFacts(relationName: RelationName, tuples: Set[RelationTuple[D]]): Either[QueryError, KnowledgeBase[D]] =
    tuples.foldLeft(Right(this): Either[QueryError, KnowledgeBase[D]]) { (acc, tuple) =>
      acc.flatMap(_.addFact(relationName, tuple))
    }

  /** Query facts matching a pattern
    *
    * Pattern uses Option[D]:
    * - Some(value): must match exactly
    * - None: wildcard (matches anything)
    *
    * Example: query("has_risk", List(Some(Const("C1")), None))
    *   matches all risks for component C1
    */
  def query(relationName: RelationName, pattern: List[Option[D]]): Either[QueryError, Set[RelationTuple[D]]] =
    schema.get(relationName) match
      case None =>
        Left(QueryError.RelationNotFoundError(relationName, schema.keySet))
      case Some(relation) =>
        if pattern.length != relation.arity then
          Left(QueryError.SchemaError(
            s"Pattern has length ${pattern.length}, expected ${relation.arity} for relation ${relation}",
            relationName = relationName,
            expectedArity = relation.arity,
            actualArity = pattern.length
          ))
        else
          Right(getFacts(relationName).filter(_.matches(pattern)))

  /** Get all unique values at a specific position of a relation.
    *
    * Returns `Left` if the relation does not exist or position is out of bounds.
    */
  def getDomain(relationName: RelationName, position: Int = 0): Either[QueryError, Set[D]] =
    schema.get(relationName) match
      case None =>
        Left(QueryError.RelationNotFoundError(relationName, schema.keySet))
      case Some(rel) =>
        if position < 0 || position >= rel.arity then
          Left(QueryError.PositionOutOfBoundsError(
            s"Position $position out of bounds for relation ${relationName.value} (arity ${rel.arity})",
            relationName = relationName,
            arity = rel.arity,
            position = position
          ))
        else
          Right(getFacts(relationName).map(_.values(position)))

  /** Get active domain: all values used in the KB */
  def activeDomain: Set[D] =
    facts.values.flatten.flatMap(_.values).toSet

  /** Count facts in a relation.
    *
    * Returns `Left` if the relation does not exist in the schema.
    */
  def count(relationName: RelationName): Either[QueryError, Int] =
    if !schema.contains(relationName) then
      Left(QueryError.RelationNotFoundError(relationName, schema.keySet))
    else
      Right(getFacts(relationName).size)

  /** Total number of facts across all relations */
  def totalFacts: Int =
    facts.values.map(_.size).sum

  /** Pretty print KB statistics */
  def stats: String =
    val sb = new StringBuilder
    sb.append(s"Knowledge Base Statistics:\n")
    sb.append(s"  Relations: ${schema.size}\n")
    sb.append(s"  Total facts: $totalFacts\n")
    sb.append(s"  Active domain size: ${activeDomain.size}\n")
    sb.append(s"\nRelations:\n")
    schema.values.toSeq.sortBy(_.name).foreach { rel =>
      sb.append(s"  ${rel.name.value}/${rel.arity}: ${getFacts(rel.name).size} facts\n")
    }
    sb.toString

object KnowledgeBase:
  /** Convenience alias for backward compatibility during migration. */
  type Classic = KnowledgeBase[RelationValue]

  /** Create an empty knowledge base */
  def empty[D]: KnowledgeBase[D] =
    KnowledgeBase(Map.empty, Map.empty)

  /** Builder for constructing knowledge bases fluently
    *
    * @tparam D the domain element type
    */
  class Builder[D]:
    private var kb = KnowledgeBase.empty[D]

    /** Add a relation to the schema */
    def withRelation(relation: Relation): Builder[D] =
      kb = kb.addRelation(relation) match
        case Right(updated) => updated
        case Left(err) => throw err.toThrowable
      this

    /** Add a unary relation */
    def withUnaryRelation(name: String): Builder[D] =
      withRelation(Relation.unary(name))

    /** Add a binary relation */
    def withBinaryRelation(name: String): Builder[D] =
      withRelation(Relation.binary(name))

    /** Add a fact using domain values.
      *
      * Accepts raw `String` — wraps to `RelationName` (ADR-010 §3).
      */
    def withFact(relationName: String, values: D*): Builder[D] =
      val tuple = RelationTuple(values.toList)
      kb = kb.addFact(RelationName(relationName), tuple) match
        case Right(updated) => updated
        case Left(err) => throw err.toThrowable
      this

    /** Add a fact using a pre-built tuple.
      *
      * Accepts raw `String` — wraps to `RelationName` (ADR-010 §3).
      */
    def withFactTuple(relationName: String, tuple: RelationTuple[D]): Builder[D] =
      kb = kb.addFact(RelationName(relationName), tuple) match
        case Right(updated) => updated
        case Left(err) => throw err.toThrowable
      this

    /** Add multiple facts from tuples.
      *
      * Accepts raw `String` — wraps to `RelationName` (ADR-010 §3).
      */
    def withFacts(relationName: String, tuples: Set[RelationTuple[D]]): Builder[D] =
      kb = kb.addFacts(RelationName(relationName), tuples) match
        case Right(updated) => updated
        case Left(err) => throw err.toThrowable
      this

    /** Build the final knowledge base */
    def build(): KnowledgeBase[D] = kb

  /** Start building a knowledge base */
  def builder[D]: Builder[D] = new Builder[D]

  // ==================== RelationValue Convenience ====================

  /** Builder convenience: add fact from constant strings.
    *
    * Only available when D = RelationValue.  Wraps each string
    * in `RelationValue.Const`.
    */
  extension (b: Builder[RelationValue])
    def withConstFact(relationName: String, values: String*): Builder[RelationValue] =
      val tuple = RelationTuple.fromConstants(values*)
      b.withFactTuple(relationName, tuple)
