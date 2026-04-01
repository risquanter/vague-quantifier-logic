package semantics

import munit.FunSuite

/** Tests for Interpretation composition primitives (ADR-005 §1).
  *
  * Verifies monoid laws for Map-merge composition and Option.orElse
  * fallback chains.
  */
class InterpretationSpec extends FunSuite:

  // ==================== Test Fixtures ====================

  val domain3 = Domain(Set(1, 2, 3))
  val domain5 = Domain(Set(4, 5))

  val fnA: Map[String, List[Int] => Int] = Map(
    "a" -> ((_: List[Int]) => 1),
    "shared" -> ((_: List[Int]) => 10)
  )
  val fnB: Map[String, List[Int] => Int] = Map(
    "b" -> ((_: List[Int]) => 2),
    "shared" -> ((_: List[Int]) => 20)
  )
  val fnC: Map[String, List[Int] => Int] = Map(
    "c" -> ((_: List[Int]) => 3)
  )

  val predA: Map[String, List[Int] => Boolean] = Map(
    "pa" -> ((args: List[Int]) => args.headOption.contains(1)),
    "common" -> ((_: List[Int]) => true)
  )
  val predB: Map[String, List[Int] => Boolean] = Map(
    "pb" -> ((args: List[Int]) => args.headOption.contains(2)),
    "common" -> ((_: List[Int]) => false)
  )

  val interpA = Interpretation(domain3, fnA, predA)
  val interpB = Interpretation(domain5, fnB, predB)

  // ==================== withFunctions ====================

  test("withFunctions merges new functions") {
    val extra = Map("x" -> ((_: List[Int]) => 42))
    val merged = interpA.withFunctions(extra)
    assertEquals(merged.getFunction("a")(Nil), 1)
    assertEquals(merged.getFunction("x")(Nil), 42)
  }

  test("withFunctions right-biases on collision") {
    val override_ = Map("a" -> ((_: List[Int]) => 99))
    val merged = interpA.withFunctions(override_)
    assertEquals(merged.getFunction("a")(Nil), 99)
  }

  test("withFunctions preserves domain and predicates") {
    val merged = interpA.withFunctions(Map("x" -> ((_: List[Int]) => 0)))
    assertEquals(merged.domain.elements, domain3.elements)
    assert(merged.hasPredicate("pa"))
  }

  // ==================== withPredicates ====================

  test("withPredicates merges new predicates") {
    val extra = Map("px" -> ((_: List[Int]) => true))
    val merged = interpA.withPredicates(extra)
    assert(merged.getPredicate("pa")(List(1)))
    assert(merged.getPredicate("px")(Nil))
  }

  test("withPredicates right-biases on collision") {
    val override_ = Map("common" -> ((_: List[Int]) => false))
    val merged = interpA.withPredicates(override_)
    assertEquals(merged.getPredicate("common")(Nil), false)
  }

  // ==================== withDomain ====================

  test("withDomain unions domain elements") {
    val merged = interpA.withDomain(Set(10, 20))
    assertEquals(merged.domain.elements, Set(1, 2, 3, 10, 20))
  }

  test("withDomain preserves functions and predicates") {
    val merged = interpA.withDomain(Set(99))
    assertEquals(merged.getFunction("a")(Nil), 1)
    assert(merged.hasPredicate("pa"))
  }

  // ==================== combine ====================

  test("combine unions domains") {
    val combined = interpA.combine(interpB)
    assertEquals(combined.domain.elements, Set(1, 2, 3, 4, 5))
  }

  test("combine merges functions (right-biased)") {
    val combined = interpA.combine(interpB)
    assertEquals(combined.getFunction("a")(Nil), 1)     // from A
    assertEquals(combined.getFunction("b")(Nil), 2)     // from B
    assertEquals(combined.getFunction("shared")(Nil), 20) // B wins
  }

  test("combine merges predicates (right-biased)") {
    val combined = interpA.combine(interpB)
    assert(combined.getPredicate("pa")(List(1)))              // from A
    assert(combined.getPredicate("pb")(List(2)))              // from B
    assertEquals(combined.getPredicate("common")(Nil), false) // B wins
  }

  test("combine associativity") {
    val interpC = Interpretation(Domain(Set(6)), fnC, Map.empty)
    val ab_c = interpA.combine(interpB).combine(interpC)
    val a_bc = interpA.combine(interpB.combine(interpC))
    // Same domain
    assertEquals(ab_c.domain.elements, a_bc.domain.elements)
    // Same function results
    assertEquals(ab_c.getFunction("a")(Nil), a_bc.getFunction("a")(Nil))
    assertEquals(ab_c.getFunction("b")(Nil), a_bc.getFunction("b")(Nil))
    assertEquals(ab_c.getFunction("c")(Nil), a_bc.getFunction("c")(Nil))
  }

  test("combine identity: i combine empty == i") {
    val empty = Interpretation[Int](Domain(Set(0)), Map.empty, Map.empty)
    // We need at least one overlapping domain element for a valid "empty"
    // In practice the identity is ModelAugmenter.identity, not Interpretation.combine
    // But we verify the map-merge identity: x ++ Map.empty == x
    val combined = interpA.combine(Interpretation(domain3, Map.empty, Map.empty))
    assertEquals(combined.getFunction("a")(Nil), 1)
    assertEquals(combined.getFunction("shared")(Nil), 10)
    assert(combined.getPredicate("pa")(List(1)))
  }

  // ==================== withFunctionFallback ====================

  test("withFunctionFallback: map entries take priority over fallback") {
    val fallback: String => Option[List[Int] => Int] = {
      case "a" => Some(_ => 999)      // would conflict
      case _   => None
    }
    val withFb = interpA.withFunctionFallback(fallback)
    // Map entry wins
    assertEquals(withFb.getFunction("a")(Nil), 1)
  }

  test("withFunctionFallback: falls through to fallback on missing name") {
    val fallback: String => Option[List[Int] => Int] = {
      case "dynamic" => Some(_ => 42)
      case _         => None
    }
    val withFb = interpA.withFunctionFallback(fallback)
    assertEquals(withFb.getFunction("dynamic")(Nil), 42)
  }

  test("withFunctionFallback: throws when both map and fallback miss") {
    val fallback: String => Option[List[Int] => Int] = _ => None
    val withFb = interpA.withFunctionFallback(fallback)
    val ex = intercept[Exception] {
      withFb.getFunction("nonexistent")(Nil)
    }
    assert(ex.getMessage.contains("Uninterpreted function: nonexistent"))
  }

  test("withFunctionFallback: numeric literal resolution pattern") {
    val numericFallback: String => Option[List[Int] => Int] =
      name => name.toIntOption.map(n => (_: List[Int]) => n)
    val withFb = interpA.withFunctionFallback(numericFallback)
    assertEquals(withFb.getFunction("42")(Nil), 42)
    assertEquals(withFb.getFunction("-7")(Nil), -7)
    // Non-numeric still throws
    intercept[Exception] { withFb.getFunction("xyz")(Nil) }
  }
