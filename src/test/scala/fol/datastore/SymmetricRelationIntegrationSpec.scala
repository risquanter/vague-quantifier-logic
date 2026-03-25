package fol.datastore

import munit.FunSuite
import fol.query.{Query, Predicates, execute, executeWithOutput}
import fol.quantifier.VagueQuantifier
import fol.bridge.{toModel, holds}
import logic.{Term, Formula, FOL}
import semantics.Valuation
import fol.RelationValueFixtures

/** Integration tests for symmetric relations across all layers (ADR-009).
  *
  * Validates that symmetric materialisation propagates correctly through:
  * - DSL `Predicates.relatedTo` / `hasRelation` / `inRelation`
  * - DSL `Query` builder + evaluation
  * - FOL bridge (`toModel`) + formula evaluation
  *
  * This ensures no divergent code paths — the same materialised data
  * is visible at the KB, KnowledgeSource, DSL, and FOL layers.
  */
class SymmetricRelationIntegrationSpec extends FunSuite, RelationValueFixtures:

  /** Countries KB with symmetric `borders` relation. */
  def createSymmetricCountryKB(): KnowledgeBase[RelationValue] =
    KnowledgeBase.empty[RelationValue]
      .addRelation(Relation.unary("country"))
      .addRelation(Relation.symmetricBinary("borders"))
      .addFacts(RelationName("country"), Set(
        "France", "Germany", "Italy", "Spain",
        "Belgium", "Austria", "Switzerland"
      ).map(n => RelationTuple(List(const(n)))))
      .addFacts(RelationName("borders"), Set(
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

  def symmetricSource: KnowledgeSource[RelationValue] =
    KnowledgeSource.fromKnowledgeBase(createSymmetricCountryKB())

  // ══════════════════════════════════════════════════════════════════
  //  Section 1: DSL Predicates with symmetric relations
  // ══════════════════════════════════════════════════════════════════

  test("Predicates.relatedTo works in BOTH directions on symmetric relation"):
    val src = symmetricSource

    // borders(France, Germany) was inserted — but relatedTo checks (entity, target)
    val bordersGermany = Predicates.relatedTo[RelationValue](src, "borders", const("Germany"))

    // France → Germany (forward direction)
    assert(bordersGermany(const("France")), "France borders Germany (forward)")

    // Germany → France would be relatedTo checking (Germany, Germany) — no.
    // But Belgium → Germany: was originally (Germany, Belgium), so reverse = (Belgium, Germany)
    val bordersFrance = Predicates.relatedTo[RelationValue](src, "borders", const("France"))

    // Germany → France (reverse direction — materialised)
    assert(bordersFrance(const("Germany")), "Germany borders France (reverse, symmetric)")
    // Spain → France (reverse of France → Spain)
    assert(bordersFrance(const("Spain")), "Spain borders France (reverse, symmetric)")
    // Austria → France should NOT hold (no fact)
    assert(!bordersFrance(const("Austria")), "Austria does NOT border France")

  test("Predicates.hasRelation works symmetrically"):
    val src = symmetricSource

    // Check borders(entity, Germany) — using argMapper
    val pred = Predicates.hasRelation[RelationValue](
      src, "borders", entity => List(entity, const("Germany"))
    )

    assert(pred(const("France")), "France→Germany (forward)")
    // Belgium→Germany: original was (Germany, Belgium), symmetric → (Belgium, Germany) exists
    assert(pred(const("Belgium")), "Belgium→Germany (materialised reverse)")
    assert(pred(const("Austria")), "Austria→Germany (materialised reverse)")
    assert(!pred(const("Spain")), "Spain does NOT border Germany")

  // ══════════════════════════════════════════════════════════════════
  //  Section 2: DSL Query evaluation with symmetric relations
  // ══════════════════════════════════════════════════════════════════

  test("DSL query: 'Most countries border France' — symmetric makes reverse visible"):
    val src = symmetricSource

    val q = Query
      .quantifier(VagueQuantifier.most)
      .over("country")
      .where[RelationValue](Predicates.relatedTo(src, "borders", const("France")))

    val result = q.evaluate(src)
    assert(result.isRight, s"Expected Right, got $result")

    // France has symmetric borders with: Germany, Belgium, Switzerland, Italy, Spain = 5 neighbours
    // 7 countries total, 5 border France → 5/7 ≈ 0.714
    // "most" = ≥ 70% → should be satisfied
    val r = result.toOption.get
    assertEquals(r.satisfyingCount, 5) // Germany, Belgium, Switzerland, Italy, Spain
    assertEquals(r.domainSize, 7)
    assertEquals(r.satisfied, true)

  test("DSL query: executeWithOutput shows symmetric satisfying sets"):
    val src = symmetricSource

    val q = Query
      .quantifier(VagueQuantifier.several)
      .over("country")
      .where[RelationValue](Predicates.relatedTo(src, "borders", const("Germany")))

    val output = src.executeWithOutput(q)
    assert(output.isRight)

    val o = output.toOption.get
    // Germany's symmetric borders: France, Austria, Switzerland, Belgium = 4
    // (original: Germany→Austria, Germany→Switzerland, Germany→Belgium + reverse from France→Germany)
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
  //  Section 4: End-to-end consistency — DSL and FOL agree
  // ══════════════════════════════════════════════════════════════════

  test("DSL and FOL produce consistent results for symmetric queries"):
    val kb = createSymmetricCountryKB()
    val src = KnowledgeSource.fromKnowledgeBase(kb)
    val model = kb.toModel

    // Check all (country, France) pairs — DSL and FOL must agree
    val countries = Set("France", "Germany", "Italy", "Spain",
                        "Belgium", "Austria", "Switzerland")

    countries.foreach { country =>
      val dslResult = Predicates.relatedTo[RelationValue](
        src, "borders", const("France")
      )(const(country))

      val folFormula = Formula.Atom(FOL("borders", List(
        Term.Const(country), Term.Const("France")
      )))
      val folResult = semantics.FOLSemantics.holds(folFormula, model, Valuation(Map.empty))

      assertEquals(dslResult, folResult,
        s"DSL and FOL disagree on borders($country, France): DSL=$dslResult, FOL=$folResult")
    }
