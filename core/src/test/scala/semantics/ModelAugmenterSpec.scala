package semantics

import munit.FunSuite

/** Tests for ModelAugmenter — endomorphism monoid (ADR-005 §2).
  *
  * Verifies monoid laws (identity, associativity) and convenience
  * factory methods.
  */
class ModelAugmenterSpec extends FunSuite:

  // ==================== Test Fixtures ====================

  val domain = Domain(Set[Any]("a", "b"))
  val baseFns = Map[String, List[Any] => Any](
    "a" -> ((_: List[Any]) => "a"),
    "b" -> ((_: List[Any]) => "b")
  )
  val basePreds = Map[String, List[Any] => Boolean](
    "p" -> ((args: List[Any]) => args.headOption.contains("a"))
  )
  val baseInterp = Interpretation[Any](domain, baseFns, basePreds)
  val baseModel = Model(baseInterp)

  // Augmenters that add tracked markers
  val addF1 = ModelAugmenter.fromFunctions[Any](Map(
    "f1" -> ((_: List[Any]) => "v1")
  ))
  val addF2 = ModelAugmenter.fromFunctions[Any](Map(
    "f2" -> ((_: List[Any]) => "v2")
  ))
  val addF3 = ModelAugmenter.fromFunctions[Any](Map(
    "f3" -> ((_: List[Any]) => "v3")
  ))

  // ==================== Identity ====================

  test("identity returns model unchanged") {
    val id = ModelAugmenter.identity[Any]
    val result = id(baseModel)
    assertEquals(result.domain.elements, baseModel.domain.elements)
    assertEquals(result.interpretation.getFunction("a")(Nil), "a")
    assert(result.interpretation.getPredicate("p")(List("a")))
  }

  test("left identity: identity andThen a == a") {
    val id = ModelAugmenter.identity[Any]
    val composed = id andThen addF1
    val result = composed(baseModel)
    assertEquals(result.interpretation.getFunction("f1")(Nil), "v1")
    // Original still intact
    assertEquals(result.interpretation.getFunction("a")(Nil), "a")
  }

  test("right identity: a andThen identity == a") {
    val id = ModelAugmenter.identity[Any]
    val composed = addF1 andThen id
    val result = composed(baseModel)
    assertEquals(result.interpretation.getFunction("f1")(Nil), "v1")
    assertEquals(result.interpretation.getFunction("a")(Nil), "a")
  }

  // ==================== Associativity ====================

  test("associativity: (a andThen b) andThen c == a andThen (b andThen c)") {
    val ab_c = (addF1 andThen addF2) andThen addF3
    val a_bc = addF1 andThen (addF2 andThen addF3)
    val r1 = ab_c(baseModel)
    val r2 = a_bc(baseModel)
    // Both should have all three functions
    assertEquals(r1.interpretation.getFunction("f1")(Nil), "v1")
    assertEquals(r1.interpretation.getFunction("f2")(Nil), "v2")
    assertEquals(r1.interpretation.getFunction("f3")(Nil), "v3")
    assertEquals(r2.interpretation.getFunction("f1")(Nil), "v1")
    assertEquals(r2.interpretation.getFunction("f2")(Nil), "v2")
    assertEquals(r2.interpretation.getFunction("f3")(Nil), "v3")
  }

  // ==================== compose ====================

  test("compose applies right-to-left") {
    // compose(b) means: apply b first, then this
    val overrideF1 = ModelAugmenter.fromFunctions[Any](Map(
      "f1" -> ((_: List[Any]) => "overridden")
    ))
    // addF1 compose overrideF1 → overrideF1 first, then addF1 overwrites
    val composed = addF1 compose overrideF1
    val result = composed(baseModel)
    assertEquals(result.interpretation.getFunction("f1")(Nil), "v1")
  }

  // ==================== combine ====================

  test("combine is equivalent to andThen") {
    val viaCombine = ModelAugmenter.combine(addF1, addF2)
    val viaAndThen = addF1 andThen addF2
    val r1 = viaCombine(baseModel)
    val r2 = viaAndThen(baseModel)
    assertEquals(
      r1.interpretation.getFunction("f1")(Nil),
      r2.interpretation.getFunction("f1")(Nil)
    )
    assertEquals(
      r1.interpretation.getFunction("f2")(Nil),
      r2.interpretation.getFunction("f2")(Nil)
    )
  }

  // ==================== fromFunctions / fromPredicates ====================

  test("fromFunctions merges into existing model") {
    val aug = ModelAugmenter.fromFunctions[Any](Map(
      "score" -> ((_: List[Any]) => 42.0)
    ))
    val result = aug(baseModel)
    assertEquals(result.interpretation.getFunction("score")(Nil), 42.0)
    // Original functions still present
    assertEquals(result.interpretation.getFunction("a")(Nil), "a")
  }

  test("fromPredicates merges into existing model") {
    val aug = ModelAugmenter.fromPredicates[Any](Map(
      "gt" -> { case List(a: Int, b: Int) => a > b; case _ => false }
    ))
    val result = aug(baseModel)
    assert(result.interpretation.getPredicate("gt")(List(5, 3)))
    assert(!result.interpretation.getPredicate("gt")(List(1, 9)))
    // Original predicates still present
    assert(result.interpretation.getPredicate("p")(List("a")))
  }

  // ==================== apply ====================

  test("apply delegates to run") {
    val aug = ModelAugmenter.fromFunctions[Any](Map("x" -> ((_: List[Any]) => "x")))
    val r1 = aug.apply(baseModel)
    val r2 = aug.run(baseModel)
    assertEquals(
      r1.interpretation.getFunction("x")(Nil),
      r2.interpretation.getFunction("x")(Nil)
    )
  }
