package fol.datastore

import munit.FunSuite
import fol.query.ResolvedQuery
import fol.quantifier.VagueQuantifier
import fol.bridge.{toModel, holds}
import logic.{Term, Formula, FOL}
import semantics.Valuation
import fol.RelationValueFixtures

/** Integration tests for symmetric relations across all layers (ADR-009).
  *
  * Validates that symmetric materialisation propagates correctly through:
  * - Programmatic path: ResolvedQuery.fromRelation + inline predicates
  * - FOL bridge (toModel) + formula evaluation
  *
  * This ensures no divergent code paths — the same materialised data
  * is visible at the KB, KnowledgeSource, and FOL layers.
  */
class SymmetricRelationIntegrationSpec extends FunSuite, RelationValueFixtures:

  /** Countries KB with symmetric `borders` relation. */
  def createSymmetricCountryKB(): KnowledgeBase[RelationValue] =
    KnowledgeBase.builder[RelationValue]
      .withRelation(Relation.unary("country"))
      .withRelation(Relation.symmetricBinary("borders"))
      .withFacts("country", Set(
        "France", "Germany", "Italy", "Spain",
        "Belgium", "Austria", "Switzerland"
      ).map(n => RelationTuple(List(const(n)))))
      .withFacts("borders", Set(
        binary("France", "Germany"),
        binary("France", "Belgium"),
        binary("France", "Switzerland"),
        binary("France", "Italy"),
        binary("France", "Spain"),
        binary("Germany", "Austria"),
        binary("Germany", "Switzerland"),
        binary("Germany", "Belgium"),
        binary("Italy", "Austria"),
        binary("Italy", "Switzerland")
      ))
      .build()

  def symmetricSource: KnowledgeSource[RelationValue] =
    KnowledgeSource.fromKnowledgeBase(createSymmetricCountryKB())

  // ══════════════════════════════════════════════════════════════════
  //  Section 1: Programmatic path with symmetric relations
  // ══════════════════════════════════════════════════════════════════

  test("Inline relatedTo predicate works in BOTH directions on symmetric relation"):
    val src = symmetricSource

    // borders(x, Germany) — pattern-match query
    val bordersGermany: RelationValue => Boolean =
      d => src.query(RelationName("borders"), List(Some(d), Some(const("Germany")))).exists(_.nonEmpty)

    // France → Germany (forward direction)
    assert(bordersGermany(const("France")), "France borders Germany (forward)")

    // borders(x, France) — reverse direction
    val bordersFrance: RelationValue => Boolean =
      d => src.query(RelationName("borders"), List(Some(d), Some(const("France")))).exists(_.nonEmpty)

    // Germany → France (reverse direction — materialised)
    assert(bordersFrance(const("Germany")), "Germany borders France (reverse, symmetric)")
    // Spain → France (reverse of France → Spain)
    assert(bordersFrance(const("Spain")), "Spain borders France (reverse, symmetric)")
    // Austria → France should NOT hold (no fact)
    assert(!bordersFrance(const("Austria")), "Austria does NOT border France")

  test("Inline hasRelation predicate works symmetrically"):
    val src = symmetricSource

    // Check borders(entity, Germany) via pattern-match
    val pred: RelationValue => Boolean =
      entity => src.query(RelationName("borders"), List(Some(entity), Some(const("Germany")))).exists(_.nonEmpty)

    assert(pred(const("France")), "France→Germany (forward)")
    // Belgium→Germany: original was (Germany, Belgium), symmetric → (Belgium, Germany) exists
    assert(pred(const("Belgium")), "Belgium→Germany (materialised reverse)")
    assert(pred(const("Austria")), "Austria→Germany (materialised reverse)")
    assert(!pred(const("Spain")), "Spain does NOT border Germany")

  // ══════════════════════════════════════════════════════════════════
  //  Section 2: ResolvedQuery.fromRelation with symmetric relations
  // ══════════════════════════════════════════════════════════════════

  test("fromRelation: 'Most countries border France' — symmetric makes reverse visible"):
    val src = symmetricSource

    val bordersFrance: RelationValue => Boolean =
      d => src.query(RelationName("borders"), List(Some(d), Some(const("France")))).exists(_.nonEmpty)

    val result = ResolvedQuery.fromRelation(
      source = src,
      relationName = RelationName("country"),
      quantifier = VagueQuantifier.most,
      predicate = bordersFrance
    )
    assert(result.isRight, s"Expected Right, got $result")

    // France has symmetric borders with: Germany, Belgium, Switzerland, Italy, Spain = 5 neighbours
    // 7 countries total, 5 border France → 5/7 ≈ 0.714
    // "most" = ≥ 70% → should be satisfied
    val r = result.toOption.get.evaluate()
    assertEquals(r.satisfyingCount, 5) // Germany, Belgium, Switzerland, Italy, Spain
    assertEquals(r.domainSize, 7)
    assertEquals(r.satisfied, true)

  test("fromRelation: evaluateWithOutput shows symmetric satisfying sets"):
    val src = symmetricSource

    val bordersGermany: RelationValue => Boolean =
      d => src.query(RelationName("borders"), List(Some(d), Some(const("Germany")))).exists(_.nonEmpty)

    val result = ResolvedQuery.fromRelation(
      source = src,
      relationName = RelationName("country"),
      quantifier = VagueQuantifier.several,
      predicate = bordersGermany
    )
    assert(result.isRight)

    val o = result.toOption.get.evaluateWithOutput()
    // Germany's symmetric borders: France, Austria, Switzerland, Belgium = 4
    assertEquals(o.satisfyingElements.size, 4)
    assert(o.satisfyingElements.contains(const("France")))
    assert(o.satisfyingElements.contains(const("Austria")))

  // ══════════════════════════════════════════════════════════════════
  //  Section 3: FOL bridge — toModel with symmetric relations
  // ══════════════════════════════════════════════════════════════════

  test("toModel: symmetric relation evaluates true in both argument orders"):
    val kb = createSymmetricCountryKB()
    val model = kb.toModel

    // borders(France, Germany) — forward
    val f1 = Formula.Atom(FOL("borders", List(Term.Const("France"), Term.Const("Germany"))))
    assert(
      semantics.FOLSemantics.holds(f1, model, Valuation(Map.empty)),
      "borders(France, Germany) should hold"
    )

    // borders(Germany, France) — reverse (materialised)
    val f2 = Formula.Atom(FOL("borders", List(Term.Const("Germany"), Term.Const("France"))))
    assert(
      semantics.FOLSemantics.holds(f2, model, Valuation(Map.empty)),
      "borders(Germany, France) should hold (symmetric)"
    )

  test("toModel: non-existent symmetric pair evaluates false"):
    val kb = createSymmetricCountryKB()
    val model = kb.toModel

    // Austria→Spain (no fact)
    val f = Formula.Atom(FOL("borders", List(Term.Const("Austria"), Term.Const("Spain"))))
    assert(
      !semantics.FOLSemantics.holds(f, model, Valuation(Map.empty)),
      "borders(Austria, Spain) should NOT hold"
    )

  test("toModel: existential over symmetric relation finds both directions"):
    val kb = createSymmetricCountryKB()
    val model = kb.toModel

    // ∃y. borders(Belgium, y) — Belgium originally only appeared in position 1
    // With symmetry, (Belgium, France) and (Belgium, Germany) are materialised
    val f = Formula.Exists("y", Formula.Atom(FOL("borders", List(
      Term.Const("Belgium"), Term.Var("y")
    ))))

    assert(
      semantics.FOLSemantics.holds(f, model, Valuation(Map.empty)),
      "∃y. borders(Belgium, y) should hold via symmetric materialisation"
    )

  // ══════════════════════════════════════════════════════════════════
  //  Section 4: End-to-end consistency — programmatic path and FOL agree
  // ══════════════════════════════════════════════════════════════════

  test("IL and FOL produce consistent results for symmetric queries"):
    val kb = createSymmetricCountryKB()
    val src = KnowledgeSource.fromKnowledgeBase(kb)
    val model = kb.toModel

    // Check all (country, France) pairs — IL predicate and FOL must agree
    val countries = Set("France", "Germany", "Italy", "Spain",
                        "Belgium", "Austria", "Switzerland")

    countries.foreach { country =>
      val ilResult =
        src.query(RelationName("borders"), List(Some(const(country)), Some(const("France")))).exists(_.nonEmpty)

      val folFormula = Formula.Atom(FOL("borders", List(
        Term.Const(country), Term.Const("France")
      )))
      val folResult = semantics.FOLSemantics.holds(folFormula, model, Valuation(Map.empty))

      assertEquals(ilResult, folResult,
        s"IL and FOL disagree on borders($country, France): IL=$ilResult, FOL=$folResult")
    }
