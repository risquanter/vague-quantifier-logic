package vague.logic

import munit.FunSuite

class QuantifierSpec extends FunSuite:
  import Quantifier.*
  
  // ==================== Constructor Tests ====================
  
  test("mkAbout creates valid quantifier") {
    val q = mkAbout(3, 4)
    assertEquals(targetProportion(q), 0.75)
    assertEquals(tolerance(q), 0.1)
  }
  
  test("mkAtLeast creates valid quantifier") {
    val q = mkAtLeast(2, 3, 0.15)
    assertEquals(targetProportion(q), 2.0/3.0, 0.001)
    assertEquals(tolerance(q), 0.15)
  }
  
  test("mkAtMost creates valid quantifier") {
    val q = mkAtMost(1, 4)
    assertEquals(targetProportion(q), 0.25)
  }
  
  // ==================== Validation Tests ====================
  
  test("reject negative denominator") {
    intercept[IllegalArgumentException] {
      mkAbout(1, -1)
    }
  }
  
  test("reject zero denominator") {
    intercept[IllegalArgumentException] {
      mkAbout(1, 0)
    }
  }
  
  test("reject k > n") {
    intercept[IllegalArgumentException] {
      mkAbout(5, 4)
    }
  }
  
  test("reject negative k") {
    intercept[IllegalArgumentException] {
      mkAbout(-1, 4)
    }
  }
  
  test("reject tolerance > 1") {
    intercept[IllegalArgumentException] {
      mkAbout(1, 2, 1.5)
    }
  }
  
  test("reject negative tolerance") {
    intercept[IllegalArgumentException] {
      mkAbout(1, 2, -0.1)
    }
  }
  
  test("accept k = n") {
    val q = mkAbout(4, 4)
    assertEquals(targetProportion(q), 1.0)
  }
  
  test("accept k = 0") {
    val q = mkAbout(0, 4)
    assertEquals(targetProportion(q), 0.0)
  }
  
  // ==================== Acceptance Tests: About ====================
  
  test("About: accept exactly target proportion") {
    val q = About(1, 2, 0.1)  // "about half"
    assert(accepts(q, 0.5, 0.05))
  }
  
  test("About: accept within epsilon below target") {
    val q = About(1, 2, 0.1)
    assert(accepts(q, 0.45, 0.05))  // 0.5 - 0.05 = 0.45
  }
  
  test("About: accept within epsilon above target") {
    val q = About(1, 2, 0.1)
    assert(accepts(q, 0.54, 0.05))  // |0.5 - 0.54| = 0.04 < 0.05
  }
  
  test("About: reject too far below target") {
    val q = About(1, 2, 0.1)
    assert(!accepts(q, 0.3, 0.05))  // |0.5 - 0.3| = 0.2 > 0.05
  }
  
  test("About: reject too far above target") {
    val q = About(1, 2, 0.1)
    assert(!accepts(q, 0.7, 0.05))  // |0.5 - 0.7| = 0.2 > 0.05
  }
  
  test("About: boundary case at exactly epsilon") {
    val q = About(3, 4, 0.1)  // target = 0.75
    assert(accepts(q, 0.65, 0.1))   // 0.75 - 0.1 = 0.65 (boundary)
    assert(accepts(q, 0.85, 0.1))   // 0.75 + 0.1 = 0.85 (boundary)
  }
  
  // ==================== Acceptance Tests: AtLeast ====================
  
  test("AtLeast: accept exactly threshold") {
    val q = AtLeast(3, 4, 0.1)  // "at least about 3/4"
    assert(accepts(q, 0.75, 0.05))
  }
  
  test("AtLeast: accept above threshold") {
    val q = AtLeast(3, 4, 0.1)
    assert(accepts(q, 0.8, 0.05))
    assert(accepts(q, 0.9, 0.05))
    assert(accepts(q, 1.0, 0.05))
  }
  
  test("AtLeast: accept within epsilon below threshold") {
    val q = AtLeast(3, 4, 0.1)  // threshold = 0.75
    assert(accepts(q, 0.7, 0.05))  // 0.75 - 0.05 = 0.7 (acceptable)
  }
  
  test("AtLeast: reject too far below threshold") {
    val q = AtLeast(3, 4, 0.1)
    assert(!accepts(q, 0.6, 0.05))  // 0.6 < 0.75 - 0.05
  }
  
  test("AtLeast: boundary case") {
    val q = AtLeast(2, 3, 0.1)  // threshold = 0.6667
    assert(accepts(q, 0.5667, 0.1))  // exactly at threshold - ε
  }
  
  // ==================== Acceptance Tests: AtMost ====================
  
  test("AtMost: accept exactly threshold") {
    val q = AtMost(1, 4, 0.1)  // "at most about 1/4"
    assert(accepts(q, 0.25, 0.05))
  }
  
  test("AtMost: accept below threshold") {
    val q = AtMost(1, 4, 0.1)
    assert(accepts(q, 0.2, 0.05))
    assert(accepts(q, 0.1, 0.05))
    assert(accepts(q, 0.0, 0.05))
  }
  
  test("AtMost: accept within epsilon above threshold") {
    val q = AtMost(1, 4, 0.1)  // threshold = 0.25
    assert(accepts(q, 0.3, 0.05))  // 0.25 + 0.05 = 0.3 (acceptable)
  }
  
  test("AtMost: reject too far above threshold") {
    val q = AtMost(1, 4, 0.1)
    assert(!accepts(q, 0.4, 0.05))  // 0.4 > 0.25 + 0.05
  }
  
  test("AtMost: boundary case") {
    val q = AtMost(1, 3, 0.1)  // threshold = 0.3333
    assert(accepts(q, 0.4333, 0.1))  // exactly at threshold + ε
  }
  
  // ==================== Common Quantifiers Tests ====================
  
  test("almostAll has target 1.0") {
    assertEquals(targetProportion(almostAll), 1.0)
    assert(accepts(almostAll, 0.96, 0.05))  // Accept close to 1.0: |1.0 - 0.96| = 0.04 < 0.05
    assert(!accepts(almostAll, 0.8, 0.05))  // Reject far from 1.0
  }
  
  test("aboutHalf has target 0.5") {
    assertEquals(targetProportion(aboutHalf), 0.5)
    assert(accepts(aboutHalf, 0.5, 0.05))
    assert(accepts(aboutHalf, 0.48, 0.05))
  }
  
  test("aboutThreeQuarters has target 0.75 with AtLeast") {
    assertEquals(targetProportion(aboutThreeQuarters), 0.75)
    assert(accepts(aboutThreeQuarters, 0.75, 0.05))
    assert(accepts(aboutThreeQuarters, 0.8, 0.05))  // Above is OK
    assert(!accepts(aboutThreeQuarters, 0.6, 0.05)) // Too far below
  }
  
  test("aboutOneThird has target ~0.333") {
    val target = targetProportion(aboutOneThird)
    assertEquals(target, 1.0/3.0, 0.001)
    assert(accepts(aboutOneThird, 0.33, 0.05))
  }
  
  test("aboutTwoThirds has target ~0.667 with AtLeast") {
    val target = targetProportion(aboutTwoThirds)
    assertEquals(target, 2.0/3.0, 0.001)
    assert(accepts(aboutTwoThirds, 0.7, 0.05))
  }
  
  // ==================== Edge Cases ====================
  
  test("accepts validates proportion bounds") {
    val q = mkAbout(1, 2)
    intercept[IllegalArgumentException] {
      accepts(q, 1.5, 0.1)
    }
  }
  
  test("accepts validates epsilon bounds") {
    val q = mkAbout(1, 2)
    intercept[IllegalArgumentException] {
      accepts(q, 0.5, -0.1)
    }
  }
  
  test("targetProportion works for all types") {
    assertEquals(targetProportion(About(1, 2, 0.1)), 0.5)
    assertEquals(targetProportion(AtLeast(3, 4, 0.1)), 0.75)
    assertEquals(targetProportion(AtMost(1, 3, 0.1)), 1.0/3.0, 0.001)
  }
  
  // ==================== Pretty Print Tests ====================
  
  test("prettyPrint About") {
    val q = mkAbout(1, 2, 0.15)
    assertEquals(prettyPrint(q), "Q[~#]^{1/2}[tol=0.15]")
  }
  
  test("prettyPrint AtLeast") {
    val q = mkAtLeast(3, 4)
    assertEquals(prettyPrint(q), "Q[≥]^{3/4}[tol=0.1]")
  }
  
  test("prettyPrint AtMost") {
    val q = mkAtMost(2, 5, 0.2)
    assertEquals(prettyPrint(q), "Q[≤]^{2/5}[tol=0.2]")
  }
  
  // ==================== Paper Examples Tests ====================
  
  test("paper example: Q[≥]^{3/4} with ε=0.1") {
    // From paper Example 3, query q₁
    val q = mkAtLeast(3, 4)
    
    // If 80% of countries satisfy, should accept
    assert(accepts(q, 0.8, 0.1))
    
    // If 74% satisfy (just within tolerance), should accept
    assert(accepts(q, 0.74, 0.1))  // 0.75 - 0.1 = 0.65, so 0.74 OK
    
    // If only 60% satisfy, should reject
    assert(!accepts(q, 0.6, 0.1))
  }
  
  test("paper example: Q[~#]^{1/2}") {
    // "About half"
    val q = mkAbout(1, 2)
    
    // Exactly half
    assert(accepts(q, 0.5, 0.1))
    
    // Close to half (within ε)
    assert(accepts(q, 0.45, 0.1))
    assert(accepts(q, 0.55, 0.1))
    
    // Too far from half
    assert(!accepts(q, 0.3, 0.1))
    assert(!accepts(q, 0.7, 0.1))
  }
