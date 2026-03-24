package fol.semantics

import munit.FunSuite
import logic.{FOL, Formula, Term}
import fol.datastore.{KnowledgeBase, KnowledgeSource, Relation, RelationValue, RelationTuple}
import fol.logic.{Quantifier, ParsedQuery}
import fol.bridge.{FOLBridge, NumericAugmenter}
import fol.error.QueryError
import semantics.{Domain, Interpretation, Model, ModelAugmenter}

/** Integration tests: ModelAugmenter through the full evaluation pipeline.
  *
  * Verifies ADR-005 §5 — augmenter threading through VagueSemantics
  * and FOLBridge with default identity preserving backward compatibility.
  */
class ModelAugmentationIntegrationSpec extends FunSuite:

  // ==================== Helpers ====================

  extension (kb: KnowledgeBase[RelationValue])
    def asSource: KnowledgeSource[RelationValue] = KnowledgeSource.fromKnowledgeBase(kb)

  def ok[A](result: Either[QueryError, A]): A =
    result.fold(e => fail(s"Expected Right, got Left(${e.message})"), identity)

  def unary(value: String): RelationTuple[RelationValue] =
    RelationTuple(List(RelationValue.Const(value)))

  /** KB with items and a "score" concept that needs a function augmenter. */
  def createItemKB(): KnowledgeBase[RelationValue] =
    KnowledgeBase[RelationValue](Map.empty, Map.empty)
      .addRelation(Relation("item", 1))
      .addRelation(Relation("category", 1))
      .addFacts("item", Set(
        unary("A"), unary("B"), unary("C"), unary("D")
      ))
      .addFacts("category", Set(
        unary("A"), unary("B"), unary("C"), unary("D")
      ))

  /** Custom augmenter that maps items to numeric scores. */
  val scoreAugmenter: ModelAugmenter[Any] = ModelAugmenter.fromFunctions[Any](Map(
    "score" -> {
      case List("A") => 80.0: Any
      case List("B") => 60.0: Any
      case List("C") => 40.0: Any
      case List("D") => 20.0: Any
      case List(x)   => 0.0: Any
      case args       => throw new Exception(s"score expects 1 arg, got ${args.length}")
    }
  ))

  // ==================== Identity augmenter: backward compatibility ====================

  test("identity augmenter: KB-only queries work unchanged") {
    val kb = createItemKB()
    // "all items are in category" — trivially true since we added same facts
    val query = ParsedQuery(
      quantifier = Quantifier.AtLeast(1, 1, 0.1),
      variable = "x",
      range = FOL("item", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("category", List(Term.Var("x"))))
    )
    val result = ok(VagueSemantics.holds(query, kb.asSource))
    assertEquals(result.proportion, 1.0)
    assertEquals(result.satisfied, true)
  }

  test("identity augmenter: explicit identity produces same result") {
    val kb = createItemKB()
    val query = ParsedQuery(
      quantifier = Quantifier.AtLeast(1, 1, 0.1),
      variable = "x",
      range = FOL("item", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("category", List(Term.Var("x"))))
    )
    val r1 = ok(VagueSemantics.holds(query, kb.asSource))
    val r2 = ok(VagueSemantics.holds(query, kb.asSource,
      modelAugmenter = ModelAugmenter.identity))
    assertEquals(r1.proportion, r2.proportion)
    assertEquals(r1.satisfied, r2.satisfied)
  }

  // ==================== Custom function via augmenter ====================

  test("custom score function + numeric comparison via composed augmenter") {
    val kb = createItemKB()
    val augmenter = NumericAugmenter.augmenter andThen scoreAugmenter

    // >(score(x), 50)  — items with score > 50 are A (80) and B (60), so 2/4
    val query = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.1),
      variable = "x",
      range = FOL("item", List(Term.Var("x"))),
      scope = Formula.Atom(FOL(">", List(
        Term.Fn("score", List(Term.Var("x"))),
        Term.Const("50")
      )))
    )

    val result = ok(VagueSemantics.holds(query, kb.asSource,
      modelAugmenter = augmenter))
    assertEquals(result.domainSize, 4)
    assertEquals(result.satisfyingCount, 2)
    assertEquals(result.proportion, 0.5)
    assertEquals(result.satisfied, true)  // 0.5 ≈ 1/2 within 0.1
  }

  // ==================== NumericAugmenter enables comparisons in scope ====================

  test("NumericAugmenter enables numeric comparisons on KB model") {
    val kb = createItemKB()
    val augmenter = NumericAugmenter.augmenter andThen scoreAugmenter

    // >=(score(x), 40) — items A(80), B(60), C(40) satisfy → 3/4
    val query = ParsedQuery(
      quantifier = Quantifier.AtLeast(3, 4, 0.05),
      variable = "x",
      range = FOL("item", List(Term.Var("x"))),
      scope = Formula.Atom(FOL(">=", List(
        Term.Fn("score", List(Term.Var("x"))),
        Term.Const("40")
      )))
    )

    val result = ok(VagueSemantics.holds(query, kb.asSource,
      modelAugmenter = augmenter))
    assertEquals(result.satisfyingCount, 3)
    assertEquals(result.proportion, 0.75)
  }

  // ==================== Composed augmenters ====================

  test("composed augmenter: numeric + custom domain functions") {
    val kb = createItemKB()
    val bonusAugmenter = ModelAugmenter.fromFunctions[Any](Map(
      "bonus" -> {
        case List("A") => 10.0: Any
        case _         => 0.0: Any
      }
    ))
    // Chain: numeric infra → scores → bonus
    val augmenter = NumericAugmenter.augmenter andThen scoreAugmenter andThen bonusAugmenter

    // >(bonus(x), 5) — only A has bonus 10 > 5 → 1/4
    val query = ParsedQuery(
      quantifier = Quantifier.AtMost(1, 4, 0.05),
      variable = "x",
      range = FOL("item", List(Term.Var("x"))),
      scope = Formula.Atom(FOL(">", List(
        Term.Fn("bonus", List(Term.Var("x"))),
        Term.Const("5")
      )))
    )

    val result = ok(VagueSemantics.holds(query, kb.asSource,
      modelAugmenter = augmenter))
    assertEquals(result.satisfyingCount, 1)
    assertEquals(result.proportion, 0.25)
    assertEquals(result.satisfied, true)
  }

  // ==================== Error case ====================

  test("missing function without augmenter: meaningful error") {
    val kb = createItemKB()
    // score(x) is not in the KB model, and no augmenter is provided
    val query = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.1),
      variable = "x",
      range = FOL("item", List(Term.Var("x"))),
      scope = Formula.Atom(FOL(">", List(
        Term.Fn("score", List(Term.Var("x"))),
        Term.Const("50")
      )))
    )

    val result = VagueSemantics.holds(query, kb.asSource)
    assert(result.isLeft, s"Expected Left, got $result")
  }

  // ==================== FOLBridge direct usage ====================

  test("FOLBridge.scopeToPredicate with augmenter") {
    val kb = createItemKB()
    val augmenter = NumericAugmenter.augmenter andThen scoreAugmenter

    val formula = Formula.Atom(FOL(">", List(
      Term.Fn("score", List(Term.Var("x"))),
      Term.Const("50")
    )))

    val pred = FOLBridge.scopeToPredicate(
      formula, "x", kb.asSource, Map.empty, augmenter
    )

    assert(pred(RelationValue.Const("A")))   // score 80 > 50
    assert(pred(RelationValue.Const("B")))   // score 60 > 50
    assert(!pred(RelationValue.Const("C")))  // score 40 ≤ 50
    assert(!pred(RelationValue.Const("D")))  // score 20 ≤ 50
  }

  // ==================== evaluate (with output) ====================

  test("evaluate with augmenter returns element sets") {
    val kb = createItemKB()
    val augmenter = NumericAugmenter.augmenter andThen scoreAugmenter

    val query = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.1),
      variable = "x",
      range = FOL("item", List(Term.Var("x"))),
      scope = Formula.Atom(FOL(">", List(
        Term.Fn("score", List(Term.Var("x"))),
        Term.Const("50")
      )))
    )

    val output = ok(VagueSemantics.evaluate(query, kb.asSource,
      modelAugmenter = augmenter))
    assertEquals(output.result.satisfyingCount, 2)
    assertEquals(output.result.proportion, 0.5)
  }
