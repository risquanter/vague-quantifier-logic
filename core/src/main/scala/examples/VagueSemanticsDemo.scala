package examples

import logic.{FOL, Formula, Term}
import semantics.{Model, Valuation}
import fol.logic.{ParsedQuery, Quantifier}
import fol.semantics.{RangeExtractor, ScopeEvaluator}
import fol.datastore.{KnowledgeBase, KnowledgeSource, RelationTuple, RelationValue, RelationName}
import fol.bridge.KnowledgeBaseModel

/** Demonstration of Vague Quantifier Semantics
  * 
  * This demo illustrates:
  * 1. How Prop_D (proportion calculation) works step-by-step
  * 2. The difference between exact evaluation vs sampling
  * 3. How vague quantifiers integrate with FOL semantics
  * 4. The role of range extraction (D_R) and scope evaluation
  * 
  * Key concepts demonstrated:
  * - Prop_D(S, φ(x,c)) = proportion of sample satisfying formula
  * - FOLSemantics.holds() for evaluating scope formulas
  * - Exact evaluation: use entire D_R as "sample"
  * - Sampling: random subset of D_R
  * - How sample size affects results
  */
@main def VagueSemanticsDemo(): Unit =
  println("=" * 80)
  println("Vague Quantifier Semantics Demonstration")
  println("=" * 80)
  println()
  
  // Run all demos
  proportionCalculationDemo()
  exactEvaluationDemo()
  samplingVsExactDemo()
  quantifierTypesDemo()
  complexFormulaDemo()
  
  println("=" * 80)
  println("Demo Complete!")
  println("=" * 80)

/** ============================================================================
  * SECTION 1: Understanding Prop_D - Proportion Calculation
  * ============================================================================
  * 
  * Prop_D(S, φ(x,c)) = |{x ∈ S | D ⊨ φ(x,c)}| / |S|
  * 
  * In plain English:
  * "What fraction of elements in sample S satisfy the condition φ?"
  */
def proportionCalculationDemo(): Unit =
  println("SECTION 1: Understanding Prop_D - Proportion Calculation")
  println("-" * 80)
  println()
  
  // Create a simple knowledge base about European countries
  val kb = KnowledgeBase.builder[RelationValue]
    .withUnaryRelation("country")
    .withUnaryRelation("large")
    .withUnaryRelation("has_coast")
    .withFacts("country", Set(
      RelationTuple(List(RelationValue.Const("France"))),
      RelationTuple(List(RelationValue.Const("Germany"))),
      RelationTuple(List(RelationValue.Const("Italy"))),
      RelationTuple(List(RelationValue.Const("Spain"))),
      RelationTuple(List(RelationValue.Const("Luxembourg"))),
      RelationTuple(List(RelationValue.Const("Switzerland")))
    ))
    .withFacts("large", Set(
      RelationTuple(List(RelationValue.Const("France"))),
      RelationTuple(List(RelationValue.Const("Germany"))),
      RelationTuple(List(RelationValue.Const("Italy"))),
      RelationTuple(List(RelationValue.Const("Spain")))
    ))
    .withFacts("has_coast", Set(
      RelationTuple(List(RelationValue.Const("France"))),
      RelationTuple(List(RelationValue.Const("Italy"))),
      RelationTuple(List(RelationValue.Const("Spain")))
    ))
    .build()
  
  val model = KnowledgeBaseModel.toModel(kb)
  
  println("Knowledge Base Contents:")
  println("  Countries: France, Germany, Italy, Spain, Luxembourg, Switzerland")
  println("  Large countries: France, Germany, Italy, Spain")
  println("  Coastal countries: France, Italy, Spain")
  println()
  
  // Example 1: Simple proportion calculation
  println("Example 1: What proportion of a sample are large countries?")
  println("-" * 40)
  
  val sample1 = Set(
    RelationValue.Const("France"),
    RelationValue.Const("Germany"),
    RelationValue.Const("Italy"),
    RelationValue.Const("Luxembourg")
  )
  
  val formula1 = Formula.Atom(FOL("large", List(Term.Var("x"))))
  
  println(s"Sample S = ${sample1.map(_.toString).mkString("{", ", ", "}")}")
  println(s"Formula φ(x) = large(x)")
  println()
  println("Step-by-step evaluation:")
  
  sample1.foreach { elem =>
    val satisfies = ScopeEvaluator.evaluateForElement(
      formula1, elem, "x", model
    )
    val symbol = if satisfies then "✓" else "✗"
    println(f"  $symbol ${elem.toString}%-12s : large(${elem.toString}) = $satisfies")
  }
  
  val prop1 = ScopeEvaluator.calculateProportion(sample1, formula1, "x", model)
  val count1 = ScopeEvaluator.countSatisfying(sample1, formula1, "x", model)
  
  println()
  println(f"Satisfying elements: $count1 out of ${sample1.size}")
  println(f"Prop_D = $count1 / ${sample1.size} = $prop1%.2f (${prop1 * 100}%.0f%%)")
  println()
  
  // Example 2: Different proportion with same formula
  println("Example 2: Different sample, same formula")
  println("-" * 40)
  
  val sample2 = Set(
    RelationValue.Const("Luxembourg"),
    RelationValue.Const("Switzerland")
  )
  
  println(s"Sample S = ${sample2.map(_.toString).mkString("{", ", ", "}")}")
  println(s"Formula φ(x) = large(x)")
  println()
  
  sample2.foreach { elem =>
    val satisfies = ScopeEvaluator.evaluateForElement(
      formula1, elem, "x", model
    )
    val symbol = if satisfies then "✓" else "✗"
    println(f"  $symbol ${elem.toString}%-12s : large(${elem.toString}) = $satisfies")
  }
  
  val prop2 = ScopeEvaluator.calculateProportion(sample2, formula1, "x", model)
  
  println()
  println(f"Prop_D = 0 / ${sample2.size} = $prop2%.2f (${prop2 * 100}%.0f%%)")
  println()
  println("Key insight: Same formula, different samples → different proportions!")
  println()

/** ============================================================================
  * SECTION 2: Exact Evaluation - Using Entire Range
  * ============================================================================
  * 
  * Exact evaluation means using ALL elements from D_R (range domain) as the
  * sample, rather than a random subset. This gives us the TRUE proportion
  * without sampling error.
  */
def exactEvaluationDemo(): Unit =
  println("SECTION 2: Exact Evaluation - Using Entire Range")
  println("-" * 80)
  println()
  
  val kb = KnowledgeBase.builder[RelationValue]
    .withUnaryRelation("country")
    .withUnaryRelation("large")
    .withFacts("country", Set(
      RelationTuple(List(RelationValue.Const("France"))),
      RelationTuple(List(RelationValue.Const("Germany"))),
      RelationTuple(List(RelationValue.Const("Italy"))),
      RelationTuple(List(RelationValue.Const("Spain"))),
      RelationTuple(List(RelationValue.Const("Luxembourg"))),
      RelationTuple(List(RelationValue.Const("Switzerland"))),
      RelationTuple(List(RelationValue.Const("Austria"))),
      RelationTuple(List(RelationValue.Const("Belgium")))
    ))
    .withFacts("large", Set(
      RelationTuple(List(RelationValue.Const("France"))),
      RelationTuple(List(RelationValue.Const("Germany"))),
      RelationTuple(List(RelationValue.Const("Italy"))),
      RelationTuple(List(RelationValue.Const("Spain")))
    ))
    .build()
  
  val model = KnowledgeBaseModel.toModel(kb)
  
  // Create a vague query: "About 1/2 of countries are large"
  val query = ParsedQuery(
    quantifier = Quantifier.aboutHalf,
    variable = "x",
    range = FOL("country", List(Term.Var("x"))),
    scope = Formula.Atom(FOL("large", List(Term.Var("x"))))
  )
  
  println("Query: Q[~#]^{1/2} x (country(x), large(x))")
  println("In English: \"About half of countries are large\"")
  println()
  
  // Step 1: Extract full range D_R
  val source = KnowledgeSource.fromKnowledgeBase(kb)
  val rangeSet = RangeExtractor.extractRangeBoolean(source, query) match
    case Right(r) => r
    case Left(e) => println(s"Range extraction error: ${e.formatted}"); return
  
  println(s"Step 1: Extract range D_R (all countries)")
  println(s"  D_R = ${rangeSet.map(_.toString).mkString("{", ", ", "}")}")
  println(s"  |D_R| = ${rangeSet.size}")
  println()
  
  // Step 2: Use ENTIRE range as sample (exact evaluation)
  val sample = rangeSet  // No sampling - use everything!
  
  println("Step 2: Exact evaluation - use ALL elements as sample")
  println(s"  S = D_R (entire range, no sampling)")
  println(s"  |S| = ${sample.size}")
  println()
  
  // Step 3: Calculate exact proportion
  println("Step 3: Evaluate each element")
  
  val (satisfying, nonSatisfying) = ScopeEvaluator.evaluateSample(
    sample, query.scope, query.variable, model
  )
  
  println("  Satisfying (large):")
  satisfying.foreach { elem =>
    println(f"    ✓ ${elem.toString}")
  }
  println("  Not satisfying (not large):")
  nonSatisfying.foreach { elem =>
    println(f"    ✗ ${elem.toString}")
  }
  
  val exactProp = ScopeEvaluator.calculateProportion(
    sample, query.scope, query.variable, model
  )
  
  println()
  println(f"Step 4: Calculate exact proportion")
  println(f"  Prop_D = ${satisfying.size} / ${sample.size} = $exactProp%.3f")
  println(f"  Exact answer: ${exactProp * 100}%.1f%% of countries are large")
  println()
  
  // Step 5: Check quantifier
  val targetProp = Quantifier.targetProportion(query.quantifier)
  val tolerance = 0.1  // From paper
  val accepts = Quantifier.accepts(query.quantifier, exactProp, tolerance)
  
  println(s"Step 5: Check quantifier Q[~#]^{1/2}")
  println(f"  Target proportion: $targetProp%.2f (50%%)")
  println(f"  Tolerance: ±$tolerance%.2f")
  println(f"  Acceptable range: [${targetProp - tolerance}%.2f, ${targetProp + tolerance}%.2f]")
  println(f"  Actual proportion: $exactProp%.3f")
  println(s"  Query satisfied? $accepts")
  println()
  
  if accepts then
    println("✓ CONCLUSION: The query is SATISFIED")
    println("  About half of countries ARE large (exact evaluation)")
  else
    println("✗ CONCLUSION: The query is NOT satisfied")
    println(f"  $exactProp%.3f is not close enough to 0.5 (±0.1)")
  println()

/** ============================================================================
  * SECTION 3: Sampling vs Exact Evaluation
  * ============================================================================
  * 
  * This demonstrates the key difference:
  * - Exact: Use all of D_R, get TRUE proportion
  * - Sampling: Use random subset, get ESTIMATE with potential error
  */
def samplingVsExactDemo(): Unit =
  println("SECTION 3: Sampling vs Exact Evaluation")
  println("-" * 80)
  println()
  
  // Create larger KB to make sampling effects visible
  // Add 20 cities, 15 are large
  val cities = (1 to 20).map(i => s"City$i")
  val largeCities = cities.take(15)  // First 15 are large
  
  val kbBuilder = KnowledgeBase.builder[RelationValue]
    .withUnaryRelation("city")
    .withUnaryRelation("large")
  cities.foreach(city => kbBuilder.withFact("city", RelationValue.Const(city)))
  largeCities.foreach(city => kbBuilder.withFact("large", RelationValue.Const(city)))
  val kbFinal = kbBuilder.build()
  
  val model = KnowledgeBaseModel.toModel(kbFinal)
  
  println("Knowledge Base: 20 cities, 15 are large")
  println(s"  Large cities: ${largeCities.mkString(", ")}")
  println(s"  Small cities: ${cities.drop(15).mkString(", ")}")
  println()
  
  val query = ParsedQuery(
    quantifier = Quantifier.aboutThreeQuarters,
    variable = "x",
    range = FOL("city", List(Term.Var("x"))),
    scope = Formula.Atom(FOL("large", List(Term.Var("x"))))
  )
  
  println("Query: Q[~#]^{3/4} x (city(x), large(x))")
  println("In English: \"About three-quarters of cities are large\"")
  println()
  
  // EXACT EVALUATION
  println("=" * 40)
  println("EXACT EVALUATION (use all 20 cities)")
  println("=" * 40)
  
  val sourceFinal = KnowledgeSource.fromKnowledgeBase(kbFinal)
  val fullRange = RangeExtractor.extractRangeBoolean(sourceFinal, query) match
    case Right(r) => r
    case Left(e) => println(s"Range extraction error: ${e.formatted}"); return
  val exactProp = ScopeEvaluator.calculateProportion(
    fullRange, query.scope, query.variable, model
  )
  
  println(s"Sample size: ${fullRange.size} (entire D_R)")
  println(f"Proportion: $exactProp%.3f (${exactProp * 100}%.1f%%)")
  
  val exactAccepts = Quantifier.accepts(query.quantifier, exactProp, 0.1)
  println(s"Query satisfied? $exactAccepts")
  println()
  

/** ============================================================================
  * SECTION 4: Different Quantifier Types
  * ============================================================================
  * 
  * Demonstrates how About, AtLeast, and AtMost quantifiers work
  */
def quantifierTypesDemo(): Unit =
  println("SECTION 4: Different Quantifier Types")
  println("-" * 80)
  println()
  
  val kb = KnowledgeBase.builder[RelationValue]
    .withUnaryRelation("student")
    .withUnaryRelation("passed")
    .withFacts("student", (1 to 10).map(i =>
      RelationTuple(List(RelationValue.Const(s"Student$i")))
    ).toSet)
    .withFacts("passed", (1 to 7).map(i =>  // 7 out of 10 passed
      RelationTuple(List(RelationValue.Const(s"Student$i")))
    ).toSet)
    .build()
  
  val model = KnowledgeBaseModel.toModel(kb)
  
  println("Scenario: 10 students, 7 passed the exam (70%)")
  println()
  
  val range = FOL("student", List(Term.Var("x")))
  val scope = Formula.Atom(FOL("passed", List(Term.Var("x"))))
  
  val sourceStudent = KnowledgeSource.fromKnowledgeBase(kb)
  val fullRange = RangeExtractor.extractRangeBoolean(sourceStudent, 
    ParsedQuery(Quantifier.aboutHalf, "x", range, scope)
  ) match
    case Right(r) => r
    case Left(e) => println(s"Range extraction error: ${e.formatted}"); return
  
  val actualProp = ScopeEvaluator.calculateProportion(
    fullRange, scope, "x", model
  )
  
  println(f"Actual proportion who passed: $actualProp%.2f (70%%)")
  println()
  
  // Test different quantifiers
  val quantifiers = List(
    ("About half", Quantifier.mkAbout(1, 2, 0.1), "Q[~#]^{1/2}"),
    ("About 3/4", Quantifier.mkAbout(3, 4, 0.1), "Q[~#]^{3/4}"),
    ("At least 1/2", Quantifier.mkAtLeast(1, 2, 0.1), "Q[≥]^{1/2}"),
    ("At least 3/4", Quantifier.mkAtLeast(3, 4, 0.1), "Q[≥]^{3/4}"),
    ("At most 1/2", Quantifier.mkAtMost(1, 2, 0.1), "Q[≤]^{1/2}"),
    ("At most 3/4", Quantifier.mkAtMost(3, 4, 0.1), "Q[≤]^{3/4}")
  )
  
  println("Testing different quantifiers:")
  println("-" * 40)
  
  quantifiers.foreach { case (name, q, notation) =>
    val target = Quantifier.targetProportion(q)
    val accepts = Quantifier.accepts(q, actualProp, 0.1)
    val symbol = if accepts then "✓" else "✗"
    
    val explanation = q match
      case Quantifier.About(k, n, tol) =>
        f"$actualProp%.2f ≈ $target%.2f ± 0.1"
      case Quantifier.AtLeast(k, n, tol) =>
        f"$actualProp%.2f ≥ $target%.2f - 0.1 = ${target - 0.1}%.2f"
      case Quantifier.AtMost(k, n, tol) =>
        f"$actualProp%.2f ≤ $target%.2f + 0.1 = ${target + 0.1}%.2f"
    
    val symbolPadded = symbol.padTo(15, ' ')
    val namePadded = name.padTo(15, ' ')
    val explanationPadded = explanation.padTo(30, ' ')
    println(s"$symbolPadded$namePadded $notation : $explanationPadded → $accepts")
  }
  
  println()

/** ============================================================================
  * SECTION 5: Complex Formulas with Logical Connectives
  * ============================================================================
  * 
  * Shows how Prop_D works with complex scope formulas
  */
def complexFormulaDemo(): Unit =
  println("SECTION 5: Complex Formulas with Logical Connectives")
  println("-" * 80)
  println()
  
  val kb = KnowledgeBase.builder[RelationValue]
    .withUnaryRelation("country")
    .withUnaryRelation("large")
    .withUnaryRelation("wealthy")
    .withUnaryRelation("coastal")
    .withFacts("country", Set(
      RelationTuple(List(RelationValue.Const("France"))),
      RelationTuple(List(RelationValue.Const("Germany"))),
      RelationTuple(List(RelationValue.Const("Switzerland"))),
      RelationTuple(List(RelationValue.Const("Norway")))
    ))
    .withFacts("large", Set(
      RelationTuple(List(RelationValue.Const("France"))),
      RelationTuple(List(RelationValue.Const("Germany")))
    ))
    .withFacts("wealthy", Set(
      RelationTuple(List(RelationValue.Const("Switzerland"))),
      RelationTuple(List(RelationValue.Const("Norway")))
    ))
    .withFacts("coastal", Set(
      RelationTuple(List(RelationValue.Const("France"))),
      RelationTuple(List(RelationValue.Const("Norway")))
    ))
    .build()
  
  val model = KnowledgeBaseModel.toModel(kb)
  
  println("Countries and their properties:")
  println("  France: large, coastal")
  println("  Germany: large")
  println("  Switzerland: wealthy")
  println("  Norway: wealthy, coastal")
  println()
  
  // Formula 1: Conjunction - "large AND coastal"
  println("Example 1: Conjunction (∧)")
  println("-" * 40)
  
  val formula1 = Formula.And(
    Formula.Atom(FOL("large", List(Term.Var("x")))),
    Formula.Atom(FOL("coastal", List(Term.Var("x"))))
  )
  
  val range = FOL("country", List(Term.Var("x")))
  val fullRange = Set(
    RelationValue.Const("France"),
    RelationValue.Const("Germany"),
    RelationValue.Const("Switzerland"),
    RelationValue.Const("Norway")
  )
  
  println("Formula: large(x) ∧ coastal(x)")
  println("In English: \"Country is large AND coastal\"")
  println()
  
  fullRange.foreach { country =>
    val satisfies = ScopeEvaluator.evaluateForElement(
      formula1, country, "x", model
    )
    val symbol = if satisfies then "✓" else "✗"
    println(f"  $symbol ${country.toString}%-15s : large ∧ coastal = $satisfies")
  }
  
  val prop1 = ScopeEvaluator.calculateProportion(fullRange, formula1, "x", model)
  println(f"\nProp_D = $prop1%.2f (${prop1 * 100}%.0f%%)")
  println()
  
  // Formula 2: Disjunction - "large OR wealthy"
  println("Example 2: Disjunction (∨)")
  println("-" * 40)
  
  val formula2 = Formula.Or(
    Formula.Atom(FOL("large", List(Term.Var("x")))),
    Formula.Atom(FOL("wealthy", List(Term.Var("x"))))
  )
  
  println("Formula: large(x) ∨ wealthy(x)")
  println("In English: \"Country is large OR wealthy\"")
  println()
  
  fullRange.foreach { country =>
    val satisfies = ScopeEvaluator.evaluateForElement(
      formula2, country, "x", model
    )
    val symbol = if satisfies then "✓" else "✗"
    println(f"  $symbol ${country.toString}%-15s : large ∨ wealthy = $satisfies")
  }
  
  val prop2 = ScopeEvaluator.calculateProportion(fullRange, formula2, "x", model)
  println(f"\nProp_D = $prop2%.2f (${prop2 * 100}%.0f%%)")
  println()
  
  // Formula 3: Negation - "NOT wealthy"
  println("Example 3: Negation (¬)")
  println("-" * 40)
  
  val formula3 = Formula.Not(
    Formula.Atom(FOL("wealthy", List(Term.Var("x"))))
  )
  
  println("Formula: ¬wealthy(x)")
  println("In English: \"Country is NOT wealthy\"")
  println()
  
  fullRange.foreach { country =>
    val satisfies = ScopeEvaluator.evaluateForElement(
      formula3, country, "x", model
    )
    val symbol = if satisfies then "✓" else "✗"
    println(f"  $symbol ${country.toString}%-15s : ¬wealthy = $satisfies")
  }
  
  val prop3 = ScopeEvaluator.calculateProportion(fullRange, formula3, "x", model)
  println(f"\nProp_D = $prop3%.2f (${prop3 * 100}%.0f%%)")
  println()
  
  println("Summary:")
  println("  • Conjunction (∧): Both conditions must hold")
  println("  • Disjunction (∨): At least one condition must hold")
  println("  • Negation (¬): Inverts the truth value")
  println("  • FOLSemantics.holds() evaluates these recursively")
  println()
