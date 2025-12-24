package vague.datastore

/** Knowledge Base
  * 
  * A lightweight, in-memory datastore for relational facts.
  * Inspired by relational databases and RDF triple stores,
  * but simplified for FOL querying experiments.
  * 
  * Based on concepts from Section 5.1 of Fermüller et al.,
  * "Querying with Vague Quantifiers Using Probabilistic Semantics"
  */

/** Knowledge base containing relations and facts
  * 
  * A knowledge base consists of:
  * - A schema (set of relation definitions)
  * - A set of ground facts (relation instances)
  * 
  * @param schema Map from relation names to their schemas
  * @param facts Map from relation names to sets of tuples
  */
case class KnowledgeBase(
  schema: Map[String, Relation],
  facts: Map[String, Set[RelationTuple]]
):
  /** Get relation by name */
  def getRelation(name: String): Option[Relation] =
    schema.get(name)
  
  /** Check if relation exists in schema */
  def hasRelation(name: String): Boolean =
    schema.contains(name)
  
  /** Get all facts for a relation */
  def getFacts(relationName: String): Set[RelationTuple] =
    facts.getOrElse(relationName, Set.empty)
  
  /** Check if a fact exists */
  def contains(relationName: String, tuple: RelationTuple): Boolean =
    getFacts(relationName).contains(tuple)
  
  /** Add a relation to the schema */
  def addRelation(relation: Relation): KnowledgeBase =
    if schema.contains(relation.name) then
      throw new IllegalArgumentException(s"Relation ${relation.name} already exists")
    else
      copy(schema = schema + (relation.name -> relation))
  
  /** Add a fact (validates against schema) */
  def addFact(relationName: String, tuple: RelationTuple): KnowledgeBase =
    schema.get(relationName) match
      case None =>
        throw new IllegalArgumentException(s"Unknown relation: $relationName")
      case Some(relation) =>
        if !relation.validates(tuple) then
          throw new IllegalArgumentException(
            s"Tuple $tuple does not conform to relation ${relation}"
          )
        val currentFacts = facts.getOrElse(relationName, Set.empty)
        copy(facts = facts + (relationName -> (currentFacts + tuple)))
  
  /** Add multiple facts at once */
  def addFacts(relationName: String, tuples: Set[RelationTuple]): KnowledgeBase =
    tuples.foldLeft(this)((kb, tuple) => kb.addFact(relationName, tuple))
  
  /** Query facts matching a pattern
    * 
    * Pattern uses Option[RelationValue]:
    * - Some(value): must match exactly
    * - None: wildcard (matches anything)
    * 
    * Example: query("has_risk", List(Some(Const("C1")), None))
    *   matches all risks for component C1
    */
  def query(relationName: String, pattern: List[Option[RelationValue]]): Set[RelationTuple] =
    getFacts(relationName).filter(_.matches(pattern))
  
  /** Get all unique values at a specific position of a relation
    * 
    * Useful for getting all domain elements (e.g., all component IDs)
    */
  def getDomain(relationName: String, position: Int = 0): Set[RelationValue] =
    val rel = schema.get(relationName).getOrElse(
      throw new IllegalArgumentException(s"Unknown relation: $relationName")
    )
    if position < 0 || position >= rel.arity then
      throw new IllegalArgumentException(
        s"Position $position out of bounds for relation $relationName (arity ${rel.arity})"
      )
    getFacts(relationName).map(_.values(position))
  
  /** Get active domain: all constants and numbers used in the KB */
  def activeDomain: Set[RelationValue] =
    facts.values.flatten.flatMap(_.values).toSet
  
  /** Count facts in a relation */
  def count(relationName: String): Int =
    getFacts(relationName).size
  
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
      sb.append(s"  ${rel.name}/${rel.arity}: ${count(rel.name)} facts\n")
    }
    sb.toString

object KnowledgeBase:
  /** Create an empty knowledge base */
  def empty: KnowledgeBase =
    KnowledgeBase(Map.empty, Map.empty)
  
  /** Builder for constructing knowledge bases fluently */
  class Builder:
    private var kb = KnowledgeBase.empty
    
    /** Add a relation to the schema */
    def withRelation(relation: Relation): Builder =
      kb = kb.addRelation(relation)
      this
    
    /** Add a unary relation */
    def withUnaryRelation(name: String): Builder =
      withRelation(Relation.unary(name))
    
    /** Add a binary relation */
    def withBinaryRelation(name: String): Builder =
      withRelation(Relation.binary(name))
    
    /** Add a fact using constant names */
    def withFact(relationName: String, values: String*): Builder =
      val tuple = RelationTuple.fromConstants(values: _*)
      kb = kb.addFact(relationName, tuple)
      this
    
    /** Add a fact using RelationValues */
    def withFactValues(relationName: String, values: RelationValue*): Builder =
      val tuple = RelationTuple.of(values: _*)
      kb = kb.addFact(relationName, tuple)
      this
    
    /** Add multiple facts from tuples */
    def withFacts(relationName: String, tuples: Set[RelationTuple]): Builder =
      kb = kb.addFacts(relationName, tuples)
      this
    
    /** Build the final knowledge base */
    def build(): KnowledgeBase = kb
  
  /** Start building a knowledge base */
  def builder: Builder = new Builder
