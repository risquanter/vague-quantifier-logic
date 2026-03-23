package fol.semantics

import fol.datastore.{KnowledgeBase, KnowledgeSource, RelationValue, RelationTuple}

/** Shared utilities for domain extraction from knowledge sources.
  * 
  * Provides common operations used by both:
  * - RangeExtractor: FOL-based extraction with pattern matching
  * - Query DSL: Direct relation/position lookup
  * 
  * These utilities centralize the basic domain extraction operations,
  * making it easier to add caching, optimization, or alternative
  * implementations in the future.
  * 
  * Design principle: Non-invasive additions that complement existing code,
  * not replacements. Both RangeExtractor and Query can continue using
  * their specialized logic while sharing these common utilities.
  * 
  * Updated to work with KnowledgeSource abstraction, supporting
  * in-memory, SQL, and other backend implementations.
  */
object DomainExtraction:
  
  /** Extract domain from a specific position in a relation.
    * 
    * Returns all unique values that appear at the given position
    * across all tuples in the relation.
    * 
    * Used by:
    * - Query DSL: Direct domain specification (e.g., "all employees")
    * - RangeExtractor: Can use this for simple single-relation ranges
    * 
    * Example:
    * {{{
    * // Source has: person("alice"), person("bob"), person("charlie")
    * extractFromRelation(source, "person", 0)
    * // Returns: Set(Const("alice"), Const("bob"), Const("charlie"))
    * 
    * // Source has: capital("paris", "france"), capital("berlin", "germany")
    * extractFromRelation(source, "capital", 0)  // Cities
    * // Returns: Set(Const("paris"), Const("berlin"))
    * extractFromRelation(source, "capital", 1)  // Countries
    * // Returns: Set(Const("france"), Const("germany"))
    * }}}
    * 
    * @param source Knowledge source to query
    * @param relationName Name of the relation
    * @param position Zero-based position index
    * @return Set of values at that position
    */
  def extractFromRelation(
    source: KnowledgeSource,
    relationName: String,
    position: Int
  ): Set[RelationValue] =
    source.getDomain(relationName, position)
  
  /** Extract the active domain (all constants used in source).
    * 
    * Returns the union of all values appearing in any position
    * of any relation in the knowledge source.
    * 
    * Used by:
    * - Query DSL: When domain is DomainSpec.ActiveDomain
    * - General queries: When you want to quantify over "everything in source"
    * 
    * Example:
    * {{{
    * // Source has: person("alice"), knows("alice", "bob"), age("bob", 30)
    * extractActiveDomain(source)
    * // Returns: Set(Const("alice"), Const("bob"), Num(30))
    * }}}
    * 
    * @param source Knowledge source
    * @return Set of all values in source
    */
  def extractActiveDomain(source: KnowledgeSource): Set[RelationValue] =
    source.activeDomain
  
  /** Extract domain using pattern matching (for FOL range predicates).
    * 
    * Query source with a pattern where Some(value) means "must match this value"
    * and None means "wildcard - extract these values".
    * 
    * Used by:
    * - RangeExtractor: Converts FOL range predicates to source queries
    * 
    * Example:
    * {{{
    * // Source has: capital("paris", "france"), capital("berlin", "germany")
    * // Pattern: [None, Some(Const("france"))]
    * // Meaning: "Find all x where capital(x, france)"
    * extractWithPattern(source, "capital", List(None, Some(Const("france"))))
    * // Returns: Set(Const("paris"))
    * }}}
    * 
    * This is more complex than extractFromRelation because it supports:
    * - Multiple wildcard positions
    * - Filtering by specific values at other positions
    * - Full relational query capabilities
    * 
    * @param source Knowledge source to query
    * @param relationName Name of the relation
    * @param pattern Query pattern (None = wildcard, Some(v) = must equal v)
    * @return Set of matching tuples (full tuples, not just wildcard positions)
    */
  def extractWithPattern(
    source: KnowledgeSource,
    relationName: String,
    pattern: List[Option[RelationValue]]
  ): Set[RelationTuple] =
    source.query(relationName, pattern)
  
  /** Extract values from specific positions in pattern query results.
    * 
    * Combines extractWithPattern with position projection.
    * This is the full operation that RangeExtractor needs: query with pattern,
    * then extract values at specific positions.
    * 
    * Example:
    * {{{
    * // Source has: capital("paris", "france"), capital("berlin", "germany")
    * // Pattern: [None, Some(Const("france"))]
    * // Positions: List(0) - extract first column
    * extractFromPatternAtPositions(source, "capital", pattern, List(0))
    * // Returns: Set(Const("paris"))
    * 
    * // Can extract multiple positions:
    * // Pattern: [None, None] (all tuples)
    * // Positions: List(0, 1) - extract both columns
    * extractFromPatternAtPositions(source, "capital", pattern, List(0, 1))
    * // Returns: Set(Const("paris"), Const("berlin"), Const("france"), Const("germany"))
    * }}}
    * 
    * @param source Knowledge source to query
    * @param relationName Name of the relation
    * @param pattern Query pattern
    * @param positions Positions to extract from matching tuples
    * @return Set of values at specified positions
    */
  def extractFromPatternAtPositions(
    source: KnowledgeSource,
    relationName: String,
    pattern: List[Option[RelationValue]],
    positions: List[Int]
  ): Set[RelationValue] =
    val matchingTuples = extractWithPattern(source, relationName, pattern)
    matchingTuples.flatMap { tuple =>
      positions.map(pos => tuple.values(pos))
    }
  
  /** Extract domain from pattern at single position (most common case).
    * 
    * Convenience method for the typical RangeExtractor use case:
    * query with pattern, extract values at one position.
    * 
    * @param source Knowledge source to query
    * @param relationName Name of the relation
    * @param pattern Query pattern
    * @param position Single position to extract
    * @return Set of values at that position
    */
  def extractFromPatternAtPosition(
    source: KnowledgeSource,
    relationName: String,
    pattern: List[Option[RelationValue]],
    position: Int
  ): Set[RelationValue] =
    extractFromPatternAtPositions(source, relationName, pattern, List(position))
  
  /** Count how many distinct values exist at a position in a relation.
    * 
    * Utility for understanding domain sizes without materializing full sets.
    * Useful for query planning and optimization.
    * 
    * @param source Knowledge source
    * @param relationName Relation to check
    * @param position Position to count
    * @return Number of distinct values
    */
  def domainSize(
    source: KnowledgeSource,
    relationName: String,
    position: Int
  ): Int =
    extractFromRelation(source, relationName, position).size
  
  /** Count size of active domain.
    * 
    * @param source Knowledge source
    * @return Number of distinct values in entire source
    */
  def activeDomainSize(source: KnowledgeSource): Int =
    extractActiveDomain(source).size
