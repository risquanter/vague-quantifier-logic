package vague.semantics

import vague.datastore.{KnowledgeBase, RelationValue, RelationTuple}

/** Shared utilities for domain extraction from knowledge bases.
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
    * // KB has: person("alice"), person("bob"), person("charlie")
    * extractFromRelation(kb, "person", 0)
    * // Returns: Set(Const("alice"), Const("bob"), Const("charlie"))
    * 
    * // KB has: capital("paris", "france"), capital("berlin", "germany")
    * extractFromRelation(kb, "capital", 0)  // Cities
    * // Returns: Set(Const("paris"), Const("berlin"))
    * extractFromRelation(kb, "capital", 1)  // Countries
    * // Returns: Set(Const("france"), Const("germany"))
    * }}}
    * 
    * @param kb Knowledge base to query
    * @param relationName Name of the relation
    * @param position Zero-based position index
    * @return Set of values at that position
    */
  def extractFromRelation(
    kb: KnowledgeBase,
    relationName: String,
    position: Int
  ): Set[RelationValue] =
    kb.getDomain(relationName, position)
  
  /** Extract the active domain (all constants used in KB).
    * 
    * Returns the union of all values appearing in any position
    * of any relation in the knowledge base.
    * 
    * Used by:
    * - Query DSL: When domain is DomainSpec.ActiveDomain
    * - General queries: When you want to quantify over "everything in KB"
    * 
    * Example:
    * {{{
    * // KB has: person("alice"), knows("alice", "bob"), age("bob", 30)
    * extractActiveDomain(kb)
    * // Returns: Set(Const("alice"), Const("bob"), Num(30))
    * }}}
    * 
    * @param kb Knowledge base
    * @return Set of all values in KB
    */
  def extractActiveDomain(kb: KnowledgeBase): Set[RelationValue] =
    kb.activeDomain
  
  /** Extract domain using pattern matching (for FOL range predicates).
    * 
    * Query KB with a pattern where Some(value) means "must match this value"
    * and None means "wildcard - extract these values".
    * 
    * Used by:
    * - RangeExtractor: Converts FOL range predicates to KB queries
    * 
    * Example:
    * {{{
    * // KB has: capital("paris", "france"), capital("berlin", "germany")
    * // Pattern: [None, Some(Const("france"))]
    * // Meaning: "Find all x where capital(x, france)"
    * extractWithPattern(kb, "capital", List(None, Some(Const("france"))))
    * // Returns: Set(Const("paris"))
    * }}}
    * 
    * This is more complex than extractFromRelation because it supports:
    * - Multiple wildcard positions
    * - Filtering by specific values at other positions
    * - Full relational query capabilities
    * 
    * @param kb Knowledge base to query
    * @param relationName Name of the relation
    * @param pattern Query pattern (None = wildcard, Some(v) = must equal v)
    * @return Set of matching tuples (full tuples, not just wildcard positions)
    */
  def extractWithPattern(
    kb: KnowledgeBase,
    relationName: String,
    pattern: List[Option[RelationValue]]
  ): Set[RelationTuple] =
    kb.query(relationName, pattern)
  
  /** Extract values from specific positions in pattern query results.
    * 
    * Combines extractWithPattern with position projection.
    * This is the full operation that RangeExtractor needs: query with pattern,
    * then extract values at specific positions.
    * 
    * Example:
    * {{{
    * // KB has: capital("paris", "france"), capital("berlin", "germany")
    * // Pattern: [None, Some(Const("france"))]
    * // Positions: List(0) - extract first column
    * extractFromPatternAtPositions(kb, "capital", pattern, List(0))
    * // Returns: Set(Const("paris"))
    * 
    * // Can extract multiple positions:
    * // Pattern: [None, None] (all tuples)
    * // Positions: List(0, 1) - extract both columns
    * extractFromPatternAtPositions(kb, "capital", pattern, List(0, 1))
    * // Returns: Set(Const("paris"), Const("berlin"), Const("france"), Const("germany"))
    * }}}
    * 
    * @param kb Knowledge base to query
    * @param relationName Name of the relation
    * @param pattern Query pattern
    * @param positions Positions to extract from matching tuples
    * @return Set of values at specified positions
    */
  def extractFromPatternAtPositions(
    kb: KnowledgeBase,
    relationName: String,
    pattern: List[Option[RelationValue]],
    positions: List[Int]
  ): Set[RelationValue] =
    val matchingTuples = extractWithPattern(kb, relationName, pattern)
    matchingTuples.flatMap { tuple =>
      positions.map(pos => tuple.values(pos))
    }
  
  /** Extract domain from pattern at single position (most common case).
    * 
    * Convenience method for the typical RangeExtractor use case:
    * query with pattern, extract values at one position.
    * 
    * @param kb Knowledge base to query
    * @param relationName Name of the relation
    * @param pattern Query pattern
    * @param position Single position to extract
    * @return Set of values at that position
    */
  def extractFromPatternAtPosition(
    kb: KnowledgeBase,
    relationName: String,
    pattern: List[Option[RelationValue]],
    position: Int
  ): Set[RelationValue] =
    extractFromPatternAtPositions(kb, relationName, pattern, List(position))
  
  /** Count how many distinct values exist at a position in a relation.
    * 
    * Utility for understanding domain sizes without materializing full sets.
    * Useful for query planning and optimization.
    * 
    * @param kb Knowledge base
    * @param relationName Relation to check
    * @param position Position to count
    * @return Number of distinct values
    */
  def domainSize(
    kb: KnowledgeBase,
    relationName: String,
    position: Int
  ): Int =
    extractFromRelation(kb, relationName, position).size
  
  /** Count size of active domain.
    * 
    * @param kb Knowledge base
    * @return Number of distinct values in entire KB
    */
  def activeDomainSize(kb: KnowledgeBase): Int =
    extractActiveDomain(kb).size
