package fol.datastore

import munit.FunSuite
import fol.datastore.RelationValue.*
import fol.RelationValueFixtures

/** Test suite for symmetric relation support (ADR-009).
  *
  * Validates:
  * - Schema: RelationProperty.Symmetric on Relation
  * - Materialisation: addFact/addFacts auto-insert reverse tuples
  * - Query: contains, query, getDomain all see both directions
  * - Builder: withSymmetricBinaryRelation convenience
  * - Constraint: Symmetric only valid on binary relations
  * - Backward compat: non-symmetric relations unchanged
  */
class SymmetricRelationSpec extends FunSuite, RelationValueFixtures:

  // ══════════════════════════════════════════════════════════════════
  //  Section 1: Schema definition
  // ══════════════════════════════════════════════════════════════════

  test("Relation.symmetricBinary creates binary relation with Symmetric property"):
    val rel = Relation.symmetricBinary("borders")
    assertEquals(rel.name.value, "borders")
    assertEquals(rel.arity, 2)
    assert(rel.isSymmetric)
    assert(rel.isBinary)
    assertEquals(rel.properties, Set(RelationProperty.Symmetric))

  test("Relation.binary creates relation WITHOUT Symmetric property"):
    val rel = Relation.binary("has_risk")
    assert(!rel.isSymmetric)
    assertEquals(rel.properties, Set.empty)

  test("Relation constructor accepts Symmetric on binary"):
    val rel = Relation(RelationName("knows"), 2, Set(RelationProperty.Symmetric))
    assert(rel.isSymmetric)

  test("Relation rejects Symmetric on unary — schema error"):
    val caught = intercept[IllegalArgumentException] {
      Relation(RelationName("tagged"), 1, Set(RelationProperty.Symmetric))
    }
    assert(caught.getMessage.contains("Symmetric"), s"Message: ${caught.getMessage}")

  test("Relation rejects Symmetric on ternary — schema error"):
    val caught = intercept[IllegalArgumentException] {
      Relation(RelationName("between"), 3, Set(RelationProperty.Symmetric))
    }
    assert(caught.getMessage.contains("Symmetric"), s"Message: ${caught.getMessage}")

  test("Relation.toString shows property annotation"):
    val rel = Relation.symmetricBinary("borders")
    assert(rel.toString.contains("Symmetric"), s"toString: $rel")
    assert(rel.toString.contains("borders"), s"toString: $rel")

  test("Relation.toString for non-symmetric omits properties"):
    val rel = Relation.binary("has_risk")
    assertEquals(rel.toString, "has_risk/2")

  // ══════════════════════════════════════════════════════════════════
  //  Section 2: Auto-materialisation in KnowledgeBase
  // ══════════════════════════════════════════════════════════════════

  test("addFact on symmetric relation materialises reverse tuple"):
    val kb = KnowledgeBase.builder[RelationValue]
      .withRelation(Relation.symmetricBinary("borders"))
      .withFactTuple("borders", binary("France", "Germany"))
      .build()

    assertEquals(kb.contains(RelationName("borders"), binary("France", "Germany")),
      Right(true), "Forward tuple should exist")
    assertEquals(kb.contains(RelationName("borders"), binary("Germany", "France")),
      Right(true), "Reverse tuple should be auto-materialised")

  test("addFact on non-symmetric relation does NOT materialise reverse"):
    val kb = KnowledgeBase.builder[RelationValue]
      .withRelation(Relation.binary("has_risk"))
      .withFactTuple("has_risk", binary("C1", "R1"))
      .build()

    assertEquals(kb.contains(RelationName("has_risk"), binary("C1", "R1")),
      Right(true), "Forward tuple should exist")
    assertEquals(kb.contains(RelationName("has_risk"), binary("R1", "C1")),
      Right(false), "Reverse should NOT exist for non-symmetric")

  test("addFacts materialises reverse for all tuples"):
    val kb = KnowledgeBase.builder[RelationValue]
      .withRelation(Relation.symmetricBinary("borders"))
      .withFacts("borders", Set(
        binary("France", "Germany"),
        binary("Italy", "Austria")
      ))
      .build()

    // All four directions
    assertEquals(kb.contains(RelationName("borders"), binary("France", "Germany")), Right(true))
    assertEquals(kb.contains(RelationName("borders"), binary("Germany", "France")), Right(true))
    assertEquals(kb.contains(RelationName("borders"), binary("Italy", "Austria")), Right(true))
    assertEquals(kb.contains(RelationName("borders"), binary("Austria", "Italy")), Right(true))

  test("symmetric materialisation is idempotent"):
    val kb = KnowledgeBase.builder[RelationValue]
      .withRelation(Relation.symmetricBinary("knows"))
      .withFactTuple("knows", binary("Alice", "Bob"))
      .withFactTuple("knows", binary("Bob", "Alice")) // redundant
      .build()

    // Should have exactly 2 facts (forward + reverse), not 4
    assertEquals(kb.count(RelationName("knows")), Right(2))
    assertEquals(kb.contains(RelationName("knows"), binary("Alice", "Bob")), Right(true))
    assertEquals(kb.contains(RelationName("knows"), binary("Bob", "Alice")), Right(true))

  test("self-referential tuple in symmetric relation — no duplication"):
    val kb = KnowledgeBase.builder[RelationValue]
      .withRelation(Relation.symmetricBinary("linked"))
      .withFactTuple("linked", binary("A", "A"))
      .build()

    // (A, A) reversed is (A, A) — same tuple, set absorbs
    assertEquals(kb.count(RelationName("linked")), Right(1))
    assertEquals(kb.contains(RelationName("linked"), binary("A", "A")), Right(true))

  test("symmetric fact count reflects materialised tuples"):
    val kb = KnowledgeBase.builder[RelationValue]
      .withRelation(Relation.symmetricBinary("borders"))
      .withFacts("borders", Set(
        binary("France", "Germany"),
        binary("France", "Italy"),
        binary("Germany", "Italy")
      ))
      .build()

    // 3 original tuples × 2 directions = 6 facts
    assertEquals(kb.count(RelationName("borders")), Right(6))

  // ══════════════════════════════════════════════════════════════════
  //  Section 3: query and getDomain with symmetric relations
  // ══════════════════════════════════════════════════════════════════

  test("query with wildcard finds both directions"):
    val kb = KnowledgeBase.builder[RelationValue]
      .withRelation(Relation.symmetricBinary("borders"))
      .withFactTuple("borders", binary("France", "Germany"))
      .build()

    // Query: borders(France, ?)
    val fromFrance = kb.query(RelationName("borders"), List(Some(const("France")), None))
    assertEquals(fromFrance.map(_.size), Right(1))
    assert(fromFrance.exists(_.contains(binary("France", "Germany"))))

    // Query: borders(?, France) — should also find the reverse
    val toFrance = kb.query(RelationName("borders"), List(None, Some(const("France"))))
    assertEquals(toFrance.map(_.size), Right(1))
    assert(toFrance.exists(_.contains(binary("Germany", "France"))))

    // Query: borders(Germany, ?)
    val fromGermany = kb.query(RelationName("borders"), List(Some(const("Germany")), None))
    assertEquals(fromGermany.map(_.size), Right(1))
    assert(fromGermany.exists(_.contains(binary("Germany", "France"))))

  test("getDomain includes values from both directions"):
    val kb = KnowledgeBase.builder[RelationValue]
      .withRelation(Relation.symmetricBinary("borders"))
      .withFactTuple("borders", binary("France", "Germany"))
      .build()

    // Position 0 should include both France and Germany
    val pos0 = kb.getDomain(RelationName("borders"), 0)
    assertEquals(pos0, Right(Set(const("France"), const("Germany"))))

    // Position 1 should also include both
    val pos1 = kb.getDomain(RelationName("borders"), 1)
    assertEquals(pos1, Right(Set(const("Germany"), const("France"))))

  // ══════════════════════════════════════════════════════════════════
  //  Section 4: KnowledgeSource integration
  // ══════════════════════════════════════════════════════════════════

  test("KnowledgeSource.fromKnowledgeBase preserves symmetric materialisation"):
    val kb = KnowledgeBase.builder[RelationValue]
      .withRelation(Relation.symmetricBinary("borders"))
      .withFactTuple("borders", binary("France", "Germany"))
      .build()

    val source = KnowledgeSource.fromKnowledgeBase(kb)

    assertEquals(source.contains(RelationName("borders"), binary("France", "Germany")), Right(true))
    assertEquals(source.contains(RelationName("borders"), binary("Germany", "France")), Right(true))

  test("KnowledgeSource.query returns symmetric results"):
    val kb = KnowledgeBase.builder[RelationValue]
      .withRelation(Relation.symmetricBinary("borders"))
      .withFacts("borders", Set(
        binary("France", "Germany"),
        binary("France", "Italy")
      ))
      .build()

    val source = KnowledgeSource.fromKnowledgeBase(kb)

    // All neighbours OF France (position 0 = France)
    val neighboursOfFrance = source.query(RelationName("borders"), List(Some(const("France")), None))
    assertEquals(neighboursOfFrance.map(_.size), Right(2))

    // All countries that border Germany (position 1 = Germany) → should find (France, Germany)
    val borderGermany = source.query(RelationName("borders"), List(None, Some(const("Germany"))))
    assert(borderGermany.exists(_.contains(binary("France", "Germany"))))

    // Germany IN position 0 → should find (Germany, France) and (Germany, Italy) via reverse  
    val fromGermany = source.query(RelationName("borders"), List(Some(const("Germany")), None))
    assert(fromGermany.exists(_.nonEmpty), "Symmetric relation should have Germany in position 0 too")

  // ══════════════════════════════════════════════════════════════════
  //  Section 5: Builder API
  // ══════════════════════════════════════════════════════════════════

  test("Builder.withRelation accepts symmetric relation"):
    val kb = KnowledgeBase.builder[RelationValue]
      .withRelation(Relation.symmetricBinary("borders"))
      .withFact("borders", const("France"), const("Germany"))
      .build()

    assertEquals(kb.contains(RelationName("borders"), binary("France", "Germany")), Right(true))
    assertEquals(kb.contains(RelationName("borders"), binary("Germany", "France")), Right(true))

  // ══════════════════════════════════════════════════════════════════
  //  Section 6: String domain (generic typing)
  // ══════════════════════════════════════════════════════════════════

  test("Symmetric relation works with String domain"):
    val kb = KnowledgeBase.builder[String]
      .withRelation(Relation.symmetricBinary("friends"))
      .withFactTuple("friends", RelationTuple(List("Alice", "Bob")))
      .build()

    assertEquals(kb.contains(RelationName("friends"), RelationTuple(List("Alice", "Bob"))), Right(true))
    assertEquals(kb.contains(RelationName("friends"), RelationTuple(List("Bob", "Alice"))), Right(true))

  test("Symmetric relation works with Int domain"):
    val kb = KnowledgeBase.builder[Int]
      .withRelation(Relation.symmetricBinary("adjacent"))
      .withFactTuple("adjacent", RelationTuple(List(1, 2)))
      .build()

    assertEquals(kb.contains(RelationName("adjacent"), RelationTuple(List(1, 2))), Right(true))
    assertEquals(kb.contains(RelationName("adjacent"), RelationTuple(List(2, 1))), Right(true))

  // ══════════════════════════════════════════════════════════════════
  //  Section 7: Mixed schema — symmetric and non-symmetric coexist
  // ══════════════════════════════════════════════════════════════════

  test("KB with both symmetric and non-symmetric relations"):
    val kb = KnowledgeBase.builder[RelationValue]
      .withRelation(Relation.symmetricBinary("borders"))
      .withRelation(Relation.binary("has_risk"))
      .withRelation(Relation.unary("country"))
      .withFactTuple("borders", binary("France", "Germany"))
      .withFactTuple("has_risk", binary("C1", "R1"))
      .withFactTuple("country", RelationTuple(List(const("France"))))
      .build()

    // borders is symmetric
    assertEquals(kb.contains(RelationName("borders"), binary("France", "Germany")), Right(true))
    assertEquals(kb.contains(RelationName("borders"), binary("Germany", "France")), Right(true))

    // has_risk is NOT symmetric
    assertEquals(kb.contains(RelationName("has_risk"), binary("C1", "R1")), Right(true))
    assertEquals(kb.contains(RelationName("has_risk"), binary("R1", "C1")), Right(false))

    // country is unary — unaffected
    assertEquals(kb.contains(RelationName("country"), RelationTuple(List(const("France")))), Right(true))

  test("activeDomain includes all symmetric materialised values"):
    val kb = KnowledgeBase.builder[RelationValue]
      .withRelation(Relation.symmetricBinary("borders"))
      .withFactTuple("borders", binary("France", "Germany"))
      .build()

    val domain = kb.activeDomain
    assert(domain.contains(const("France")))
    assert(domain.contains(const("Germany")))
