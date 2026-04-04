package fol.typed

import TypeDecl.*
import LiteralValue.*
import fol.error.QueryError
import fol.logic.{ParsedQuery, Quantifier}
import fol.sampling.SamplingParams
import fol.semantics.VagueSemantics
import fol.typed.FolModel
import logic.{FOL, Formula, Term}
import munit.FunSuite

/** Tests for [[MapDispatcher]].
  *
  * Coverage:
  *   - Symbol sets derived from map keys (the core intra-dispatcher coherence guarantee)
  *   - evalPredicate and evalFunction dispatch to correct lambdas
  *   - Unknown symbol returns Left (not exception)
  *   - Lambda returning Left propagates as Either.Left through the pipeline
  *   - Default empty functions map
  *   - validateAgainst integration: MapDispatcher symbol sets are used correctly
  *   - Full evaluateTyped pipeline with MapDispatcher and non-empty domains
  *     (this test class is the library-side proof that raw type handling works
  *      end-to-end; register-side tests must mirror this pattern — see handover doc)
  */
class MapDispatcherSpec extends FunSuite:

  // ─── shared fixtures ────────────────────────────────────────────────────────

  private val tAsset = TypeId("Asset")
  private val tLoss  = TypeId("Loss")
  private val tProb  = TypeId("Probability")

  private val symLeaf   = SymbolName("leaf")
  private val symGtProb = SymbolName("gt_prob")
  private val symGtLoss = SymbolName("gt_loss")
  private val symLec    = SymbolName("lec")
  private val symP95    = SymbolName("p95")

  // ─── unit: symbol sets ───────────────────────────────────────────────────────

  test("functionSymbols derived from functions map keys"):
    val d = MapDispatcher(
      predicates = Map(symLeaf -> (_ => Right(true))),
      functions  = Map(symLec -> (_ => Right(FloatLiteral(0.1))),
                       symP95 -> (_ => Right(IntLiteral(42L))))
    )
    assertEquals(d.functionSymbols, Set(symLec, symP95))

  test("predicateSymbols derived from predicates map keys"):
    val d = MapDispatcher(
      predicates = Map(symLeaf -> (_ => Right(true)), symGtProb -> (_ => Right(false)))
    )
    assertEquals(d.predicateSymbols, Set(symLeaf, symGtProb))

  test("functionSymbols is empty when functions map omitted (default)"):
    val d = MapDispatcher(predicates = Map(symLeaf -> (_ => Right(true))))
    assertEquals(d.functionSymbols, Set.empty[SymbolName])

  test("adding symbol to map immediately reflected in symbol set — no separate declaration"):
    val base  = MapDispatcher(predicates = Map(symLeaf -> (_ => Right(true))))
    val extended = base.copy(predicates = base.predicates + (symGtProb -> (_ => Right(false))))
    assert(!base.predicateSymbols.contains(symGtProb))
    assert(extended.predicateSymbols.contains(symGtProb))

  // ─── unit: evalPredicate dispatch ───────────────────────────────────────────

  test("evalPredicate dispatches to correct lambda"):
    var leafCalled  = false
    var otherCalled = false
    val d = MapDispatcher(predicates = Map(
      symLeaf   -> { _ => leafCalled = true;  Right(true)  },
      symGtProb -> { _ => otherCalled = true; Right(false) }
    ))
    val r = d.evalPredicate(symLeaf, List(Value(tAsset, "A")))
    assertEquals(r, Right(true))
    assert(leafCalled,  "leaf lambda was not called")
    assert(!otherCalled, "gt_prob lambda should not have been called")

  test("evalPredicate returns Left for unknown symbol"):
    val d = MapDispatcher(predicates = Map(symLeaf -> (_ => Right(true))))
    val r = d.evalPredicate(symGtProb, Nil)
    assert(r.isLeft)
    assert(r.left.toOption.get.contains("gt_prob"))

  test("evalPredicate propagates Left returned by lambda"):
    val d = MapDispatcher(predicates = Map(
      symLeaf -> { _ => Left("deliberate failure from lambda") }
    ))
    assertEquals(d.evalPredicate(symLeaf, Nil), Left("deliberate failure from lambda"))

  // ─── unit: evalFunction dispatch ────────────────────────────────────────────

  test("evalFunction dispatches to correct lambda"):
    var lecCalled = false
    val d = MapDispatcher(
      predicates = Map.empty,
      functions  = Map(
        symLec -> { _ => lecCalled = true; Right(FloatLiteral(0.07)) },
        symP95 -> { _ => Right(IntLiteral(100L)) }
      )
    )
    val r = d.evalFunction(symLec, List(Value(tAsset, 1), Value(tLoss, 10000000L)))
    assertEquals(r, Right(FloatLiteral(0.07)))
    assert(lecCalled, "lec lambda was not called")

  test("evalFunction returns Left for unknown symbol"):
    val d = MapDispatcher(
      predicates = Map.empty,
      functions  = Map(symP95 -> (_ => Right(IntLiteral(0L))))
    )
    val r = d.evalFunction(symLec, Nil)
    assert(r.isLeft)
    assert(r.left.toOption.get.contains("lec"))

  test("evalFunction propagates Left returned by lambda"):
    val d = MapDispatcher(
      predicates = Map.empty,
      functions  = Map(symLec -> { _ => Left("computation failed") })
    )
    assertEquals(d.evalFunction(symLec, Nil), Left("computation failed"))

  // ─── unit: lambda receives correct args ─────────────────────────────────────

  test("evalPredicate passes args list to lambda unchanged"):
    val vA = Value(tAsset, "A")
    val vB = Value(tAsset, "B")
    var received: List[Value] = Nil
    val d = MapDispatcher(predicates = Map(symLeaf -> { args => received = args; Right(true) }))
    d.evalPredicate(symLeaf, List(vA, vB))
    assertEquals(received, List(vA, vB))

  test("evalFunction passes args list to lambda unchanged"):
    val vAsset = Value(tAsset, 1)
    val vLoss  = Value(tLoss, 10000000L)
    var received: List[Value] = Nil
    val d = MapDispatcher(
      predicates = Map.empty,
      functions  = Map(symLec -> { args => received = args; Right(FloatLiteral(0.0)) })
    )
    d.evalFunction(symLec, List(vAsset, vLoss))
    assertEquals(received, List(vAsset, vLoss))

  // ─── integration: validateAgainst ───────────────────────────────────────────
  // MapDispatcher symbol sets are used by RuntimeModel.validateAgainst exactly
  // as anonymous-class dispatcher symbol sets are — the guarantee is the same.

  test("validateAgainst passes when MapDispatcher covers all catalog symbols"):
    val catalog = TypeCatalog.unsafe(
      types      = Set(DomainType(tAsset), ValueType(tLoss), ValueType(tProb)),
      predicates = Map(symLeaf -> PredicateSig(List(tAsset)),
                       symGtProb -> PredicateSig(List(tProb, tProb))),
      functions  = Map(symLec -> FunctionSig(List(tAsset, tLoss), tProb))
    )
    val d = MapDispatcher(
      predicates = Map(symLeaf   -> (_ => Right(true)),
                       symGtProb -> (_ => Right(true))),
      functions  = Map(symLec    -> (_ => Right(FloatLiteral(0.0))))
    )
    val model = RuntimeModel(domains = Map(tAsset -> Set(Value(tAsset, "A"))), dispatcher = d)
    assert(model.validateAgainst(catalog).isRight)

  test("validateAgainst returns MissingPredicateImplementation when predicate absent from MapDispatcher"):
    val catalog = TypeCatalog.unsafe(
      types      = Set(DomainType(tAsset), ValueType(tLoss)),  // tLoss must be declared for gt_loss signature
      predicates = Map(symLeaf   -> PredicateSig(List(tAsset)),
                       symGtLoss -> PredicateSig(List(tLoss, tLoss)))
    )
    // gt_loss missing from dispatcher
    val d = MapDispatcher(predicates = Map(symLeaf -> (_ => Right(true))))
    val model = RuntimeModel(domains = Map(tAsset -> Set(Value(tAsset, "A"))), dispatcher = d)
    val result = model.validateAgainst(catalog)
    assert(result.isLeft)
    val errors = result.left.toOption.get
    assert(errors.exists {
      case RuntimeModelError.MissingPredicateImplementation(n) => n == symGtLoss
      case _ => false
    })

  test("validateAgainst returns MissingFunctionImplementation when function absent from MapDispatcher"):
    val catalog = TypeCatalog.unsafe(
      types      = Set(DomainType(tAsset), ValueType(tLoss), ValueType(tProb)),
      predicates = Map(symLeaf -> PredicateSig(List(tAsset))),
      functions  = Map(symLec  -> FunctionSig(List(tAsset, tLoss), tProb))
    )
    // lec present in catalog but functions map is empty
    val d = MapDispatcher(predicates = Map(symLeaf -> (_ => Right(true))))
    val model = RuntimeModel(domains = Map(tAsset -> Set(Value(tAsset, "A"))), dispatcher = d)
    val result = model.validateAgainst(catalog)
    assert(result.isLeft)
    val errors = result.left.toOption.get
    assert(errors.exists {
      case RuntimeModelError.MissingFunctionImplementation(n) => n == symLec
      case _ => false
    })

  // ─── integration: full evaluateTyped pipeline ───────────────────────────────
  // These tests exercise MapDispatcher through the complete evaluation path
  // with a NON-EMPTY domain.  Any raw-type mismatch inside a lambda will surface
  // as a ClassCastException here — not as an Either.Left.
  // Register-side tests MUST replicate this pattern with their actual domain types.

  // [ILLUSTRATIVE] Catalog matching the canonical doc example sorts:
  //   lec  : Asset × Loss → Probability
  //   p95  : Asset → Loss
  //   leaf : Asset → Boolean  (range predicate)
  //   gt_prob : Probability × Probability → Boolean
  //
  // Asset raw type: String (asset identifier)
  // Loss  raw type: LiteralValue (IntLiteral for inline integer literals like "10000000")
  // Probability raw type: LiteralValue (FloatLiteral for inline decimals like "0.05")

  private val catalog = TypeCatalog.unsafe(
    types = Set(
      DomainType(tAsset),
      ValueType(tLoss),
      ValueType(tProb)
    ),
    predicates = Map(
      symLeaf   -> PredicateSig(List(tAsset)),
      symGtProb -> PredicateSig(List(tProb, tProb))
    ),
    functions = Map(
      symLec -> FunctionSig(List(tAsset, tLoss), tProb),
      symP95 -> FunctionSig(List(tAsset), tLoss)
    ),
    // Numeric literals appearing as Loss or Probability tokens in queries are
    // validated by these validators at bind time.  The validator produces the
    // parsed LiteralValue that flows through ConstRef.raw → Value.raw into
    // dispatcher lambdas.  See ADR-015.
    literalValidators = Map(
      tLoss -> (s => s.toLongOption.map(IntLiteral(_))),
      tProb -> (s => s.toDoubleOption.map(FloatLiteral(_)))
    )
  )

  // Domain: two assets, A and B. A has lec > 0.05 at threshold 10000000, B does not.
  // Raw type for Asset domain elements is String — this is a deliberate illustrative
  // choice. Register will use its own domain type (e.g. an AssetId value class).
  private val domainA = Value(tAsset, "A")
  private val domainB = Value(tAsset, "B")

  // [NOTE on raw types at the dispatcher boundary]:
  // There are three sources of Value.raw and each has a different type:
  //
  //   1. Domain elements (built by consumer) — raw is whatever type the consumer
  //      put into Value(sort, raw) when constructing RuntimeModel.domains.
  //      In this test, Asset domain elements are Value(tAsset, "A"), so raw is String.
  //
  //   2. Literals from query text — ConstRef.raw is a LiteralValue produced by the
  //      sort's literalValidator.  In this test:
  //        - "10000000" (Loss)  → IntLiteral(10000000L)
  //        - "0.05" (Prob)     → FloatLiteral(0.05)
  //      Use a sealed match — no asInstanceOf in the good path (ADR-015).
  //
  //   3. Function return values — the framework wraps the lambda's LiteralValue
  //      return as Value(resultSort, literalResult).  In this test lec returns
  //      FloatLiteral(0.07), so downstream consumers of lec's result also see a
  //      LiteralValue — the same shape as an inline literal.  No rawToDouble
  //      helper is needed; a single FloatLiteral match suffices.  See ADR-015 §1.
  //
  // Consequence: lambdas receiving ValueType arguments always see LiteralValue
  // as raw.  Only DomainType arguments (domain elements) carry consumer-owned
  // raw types.  The consumer owns the DomainType raw contract on both sides.

  private val dispatcher = MapDispatcher(
    predicates = Map(
      symLeaf -> { _ =>
        // Every asset is in range (all are "leaves" in this illustrative model)
        Right(true)
      },
      symGtProb -> { args =>
        // args(0): result of lec(x, ...) — raw is FloatLiteral (wrapped by framework)
        // args(1): literal "0.05"        — raw is FloatLiteral(0.05) (from literalValidator)
        // Both are LiteralValue; a single sealed match covers both.
        (args(0).raw, args(1).raw) match
          case (FloatLiteral(a), FloatLiteral(b)) => Right(a > b)
          case other => Left(s"gt_prob: unexpected arg types: $other")
      }
    ),
    functions = Map(
      symLec -> { args =>
        // args(0): domain element Value(tAsset, "A") — raw is String
        // args(1): literal "10000000" — raw is IntLiteral(10000000L) (from literalValidator)
        val assetId   = args(0).raw.asInstanceOf[String]
        val threshold = args(1).raw match
          case IntLiteral(n) => n
          case other => throw IllegalArgumentException(s"expected IntLiteral for lec threshold, got $other")
        // [ILLUSTRATIVE computation]: A has lec=0.07, B has lec=0.02
        val lec = assetId match
          case "A" => 0.07
          case _   => 0.02
        Right(FloatLiteral(lec))  // framework wraps as Value(tProb, FloatLiteral(lec))
      },
      symP95 -> { args =>
        // args(0): domain element — raw is String
        val assetId = args(0).raw.asInstanceOf[String]
        val p95 = assetId match
          case "A" => 5000000L
          case _   => 1000000L
        Right(IntLiteral(p95))  // framework wraps as Value(tLoss, IntLiteral(p95))
      }
    )
  )

  private val model = RuntimeModel(
    domains    = Map(tAsset -> Set(domainA, domainB)),
    dispatcher = dispatcher
  )

  // Query: Q[<=]^{1/4} x (leaf(x), gt_prob(lec(x, 10000000), 0.05))
  // Expected: only A satisfies gt_prob(lec(A, 10000000), 0.05) since lec(A)=0.07 > 0.05
  private val lecQuery = ParsedQuery(
    quantifier = Quantifier.About(1, 2, 0.01),
    variable   = "x",
    range      = FOL(symLeaf.value, List(Term.Var("x"))),
    scope      = Formula.Atom(FOL(symGtProb.value, List(
      Term.Fn(symLec.value, List(Term.Var("x"), Term.Const("10000000"))),
      Term.Const("0.05")
    ))),
    answerVars = Nil
  )

  test("evaluateTyped with MapDispatcher: full pipeline over non-empty domain"):
    val fm = FolModel(catalog, model).fold(e => fail(s"FolModel construction failed: $e"), identity)
    val result = VagueSemantics.evaluateTyped(query = lecQuery, folModel = fm, samplingParams = SamplingParams.exact)
    assert(result.isRight, s"Expected Right, got $result")
    val output = result.toOption.get
    assertEquals(output.rangeElements.size, 2, "both assets should be in range")
    assertEquals(output.satisfyingElements.size, 1, "only A satisfies gt_prob(lec(x,10M), 0.05)")
    assert(output.satisfyingElements.contains(domainA))
    assert(!output.satisfyingElements.contains(domainB))

  test("evaluateTyped with MapDispatcher: proportion reflects satisfying count"):
    val fm = FolModel(catalog, model).fold(e => fail(s"FolModel construction failed: $e"), identity)
    val result = VagueSemantics.evaluateTyped(query = lecQuery, folModel = fm, samplingParams = SamplingParams.exact)
    val output = result.toOption.get
    assertEquals(output.result.proportion, 0.5)

  test("evaluateTyped with MapDispatcher: lambda Left propagates as EvaluationError"):
    val failingDispatcher = MapDispatcher(
      predicates = Map(
        symLeaf   -> (_ => Right(true)),
        symGtProb -> (_ => Left("deliberate predicate failure"))
      ),
      functions = Map(
        symLec -> (_ => Right(FloatLiteral(0.07))),
        symP95 -> (_ => Right(IntLiteral(5000000L)))
      )
    )
    val failingModel = RuntimeModel(
      domains    = Map(tAsset -> Set(domainA, domainB)),
      dispatcher = failingDispatcher
    )
    val fm = FolModel(catalog, failingModel).fold(e => fail(s"FolModel construction failed: $e"), identity)
    val result = VagueSemantics.evaluateTyped(query = lecQuery, folModel = fm, samplingParams = SamplingParams.exact)
    result match
      case Left(e: QueryError.EvaluationError) =>
        assert(e.message.contains("deliberate predicate failure"))
      case Left(other) => fail(s"Expected EvaluationError, got $other")
      case Right(_)    => fail("Expected Left for failing dispatcher")

  test("evaluateTyped with MapDispatcher: validateAgainst catches missing symbol in catalog"):
    // A dispatcher that is missing symGtProb from its predicates map
    val incompleteDispatcher = MapDispatcher(
      predicates = Map(symLeaf -> (_ => Right(true))), // gt_prob absent
      functions  = Map(
        symLec -> (_ => Right(FloatLiteral(0.07))),
        symP95 -> (_ => Right(IntLiteral(5000000L)))
      )
    )
    val incompleteModel = RuntimeModel(
      domains    = Map(tAsset -> Set(domainA, domainB)),
      dispatcher = incompleteDispatcher
    )
    val result = FolModel(catalog, incompleteModel)
    result match
      case Left(e: QueryError.ModelValidationError) =>
        assert(e.errors.exists(_.contains("gt_prob")))
      case Left(other) => fail(s"Expected ModelValidationError, got $other")
      case Right(_)    => fail("Expected Left for incomplete dispatcher")

  // ─── raw type mismatch behaviour ────────────────────────────────────────────
  // IMPORTANT: The library does NOT wrap dispatcher lambdas in try/catch.
  // A ClassCastException inside a lambda propagates UNCAUGHT through evaluateTyped,
  // even though evaluateTyped is declared to return Either.
  //
  // This test documents and verifies that behaviour explicitly.
  // Register-side tests MUST exercise their actual raw types through a non-empty
  // domain to catch mismatches early — see handover doc.

  test("raw type mismatch in lambda propagates as ClassCastException (not Either.Left)"):
    val badDispatcher = MapDispatcher(
      predicates = Map(
        symLeaf   -> (_ => Right(true)),
        symGtProb -> { args =>
          // Deliberate wrong cast: lec returns FloatLiteral, but this casts to Int.
          // The evaluateTyped return type is Either but no try/catch wraps lambdas,
          // so ClassCastException propagates uncaught through the Either boundary.
          val _ = args(0).raw.asInstanceOf[Int]  // will throw ClassCastException
          Right(true)
        }
      ),
      functions = Map(
        symLec -> (_ => Right(FloatLiteral(0.07))),
        symP95 -> (_ => Right(IntLiteral(5000000L)))
      )
    )
    val badModel = RuntimeModel(
      domains    = Map(tAsset -> Set(domainA, domainB)),
      dispatcher = badDispatcher
    )
    // On the JVM this is ClassCastException.
    // On ScalaJS the same cast failure surfaces as UndefinedBehaviorError (extends Error).
    // The key property being tested — that the exception escapes the Either boundary
    // rather than being caught as Left — holds on both platforms.
    val fm = FolModel(catalog, badModel).fold(e => fail(s"FolModel construction failed: $e"), identity)
    var threw = false
    try
      VagueSemantics.evaluateTyped(query = lecQuery, folModel = fm, samplingParams = SamplingParams.exact)
      ()
    catch
      case _: Throwable => threw = true
    assert(threw, "Expected raw-type mismatch to propagate as uncaught exception, not Either.Left")
