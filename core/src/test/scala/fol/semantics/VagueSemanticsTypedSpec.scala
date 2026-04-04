package fol.semantics

import fol.error.QueryError
import fol.logic.{ParsedQuery, Quantifier}
import fol.sampling.SamplingParams
import fol.typed.{BoundAtom, BoundFormula, BoundQuery, BoundTerm, BoundVar, FolModel, PredicateSig, RuntimeDispatcher, RuntimeModel, TypeCatalog, TypedSemantics, TypeId, TypeRepr, SymbolName, Value}
import fol.typed.TypeDecl.{DomainType, ValueType}
import logic.{FOL, Formula, Term}
import munit.FunSuite

class VagueSemanticsTypedSpec extends FunSuite:

  private val asset = TypeId("Asset")

  private val catalog = TypeCatalog.unsafe(
    types = Set(DomainType(asset)),
    predicates = Map(
      SymbolName("leaf") -> PredicateSig(List(asset)),
      SymbolName("coastal") -> PredicateSig(List(asset))
    )
  )

  private val vA = Value(asset, "A")
  private val vB = Value(asset, "B")

  private val query = ParsedQuery(
    quantifier = Quantifier.About(1, 2, 0.01),
    variable = "x",
    range = FOL("leaf", List(Term.Var("x"))),
    scope = Formula.Atom(FOL("coastal", List(Term.Var("x")))),
    answerVars = Nil
  )

  test("evaluateTyped returns expected proportion for simple unary predicates"):
    val dispatcher = new RuntimeDispatcher:
      override def evalFunction(name: SymbolName, args: List[Value]): Either[String, Value] =
        Left(s"No function implementation for '${name.value}'")

      override def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean] =
        name.value match
          case "leaf"    => Right(args.nonEmpty)
          case "coastal" => Right(args.headOption.exists(_.raw == "A"))
          case other     => Left(s"No predicate implementation for '$other'")

      override def functionSymbols: Set[SymbolName] = Set.empty
      override def predicateSymbols: Set[SymbolName] = Set(SymbolName("leaf"), SymbolName("coastal"))

    val model = RuntimeModel(
      domains = Map(asset -> Set(vA, vB)),
      dispatcher = dispatcher
    )

    val fm = FolModel(catalog, model).fold(e => fail(s"FolModel construction failed: $e"), identity)
    val result = VagueSemantics.evaluateTyped(
      query = query,
      folModel = fm,
      samplingParams = SamplingParams.exact
    )

    assert(result.isRight)
    val output = result.toOption.get
    assertEquals(output.rangeElements.size, 2)
    assertEquals(output.satisfyingElements.size, 1)
    assertEquals(output.result.proportion, 0.5)

  test("evaluateTyped returns ModelValidationError when runtime model is missing declared predicate"):
    val dispatcher = new RuntimeDispatcher:
      override def evalFunction(name: SymbolName, args: List[Value]): Either[String, Value] =
        Left(s"No function implementation for '${name.value}'")

      override def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean] =
        name.value match
          case "leaf"  => Right(args.nonEmpty)
          case other   => Left(s"No predicate implementation for '$other'")

      override def functionSymbols: Set[SymbolName] = Set.empty
      override def predicateSymbols: Set[SymbolName] = Set(SymbolName("leaf"))

    val invalidModel = RuntimeModel(
      domains = Map(asset -> Set(vA, vB)),
      dispatcher = dispatcher
    )

    val result = FolModel(catalog, invalidModel)

    result match
      case Left(e: QueryError.ModelValidationError) =>
        assert(e.errors.nonEmpty)
        assert(e.errors.exists(_.contains("coastal")))
      case Left(other) => fail(s"Expected ModelValidationError, got $other")
      case Right(_)    => fail("Expected Left for invalid runtime model")

  test("Value.as[A] projects satisfyingElements to consumer domain type via TypeRepr"):
    // Represents the register use case: after evaluation, project Value witness
    // set back to a consumer domain type. Here String is the JVM carrier for
    // the Asset sort — in register this would be LeafId backed by String.

    given TypeRepr[String] with
      val typeId = asset

    val dispatcher = new RuntimeDispatcher:
      override def evalFunction(name: SymbolName, args: List[Value]): Either[String, Value] =
        Left(s"No function implementation for '${name.value}'")
      override def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean] =
        name.value match
          case "leaf"    => Right(args.nonEmpty)
          case "coastal" => Right(args.headOption.exists(_.raw == "A"))
          case other     => Left(s"No predicate implementation for '$other'")
      override def functionSymbols: Set[SymbolName] = Set.empty
      override def predicateSymbols: Set[SymbolName] = Set(SymbolName("leaf"), SymbolName("coastal"))

    val model = RuntimeModel(
      domains = Map(asset -> Set(vA, vB)),
      dispatcher = dispatcher
    )

    val fm = FolModel(catalog, model).fold(e => fail(s"FolModel construction failed: $e"), identity)
    val output = VagueSemantics.evaluateTyped(
      query = query,
      folModel = fm,
      samplingParams = SamplingParams.exact
    ).toOption.get

    // Project Value witness set to consumer String — no asInstanceOf at call site
    val satisfying: Set[String] = output.satisfyingElements.flatMap(_.as[String])
    assertEquals(satisfying, Set("A"))

    // A Value with a mismatched sort returns None, not a cast exception
    val wrongSortValue = Value(TypeId("Risk"), "something")
    assertEquals(wrongSortValue.as[String], None)
  // ==================== BindError tests ====================

  test("bindTyped returns BindError (not ValidationError) on type-check failure"):
    val badQuery = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.01),
      variable = "x",
      range = FOL("nonexistent_predicate", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("leaf", List(Term.Var("x")))),
      answerVars = Nil
    )
    val result = VagueSemantics.bindTyped(badQuery, catalog)
    result match
      case Left(e: QueryError.BindError) =>
        assert(e.errors.nonEmpty)
        assert(e.errors.exists(_.contains("nonexistent_predicate")))
      case Left(other) => fail(s"Expected BindError, got $other")
      case Right(_)    => fail("Expected Left for unknown predicate")

  test("evaluateTyped returns BindError (not ValidationError) for malformed query"):
    val badQuery = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.01),
      variable = "x",
      range = FOL("nonexistent_predicate", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("leaf", List(Term.Var("x")))),
      answerVars = Nil
    )
    val dispatcher = new RuntimeDispatcher:
      override def evalFunction(name: SymbolName, args: List[Value]): Either[String, Value] =
        Left("no function")
      override def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean] =
        name.value match
          case "leaf"    => Right(true)
          case "coastal" => Right(false)
          case other     => Left(s"no predicate: $other")
      override def functionSymbols: Set[SymbolName] = Set.empty
      override def predicateSymbols: Set[SymbolName] = Set(SymbolName("leaf"), SymbolName("coastal"))
    val model = RuntimeModel(domains = Map(asset -> Set(vA, vB)), dispatcher = dispatcher)
    val fm = FolModel(catalog, model).fold(e => fail(s"FolModel construction failed: $e"), identity)
    val result = VagueSemantics.evaluateTyped(badQuery, fm, samplingParams = SamplingParams.exact)
    result match
      case Left(_: QueryError.BindError) => assert(true)
      case Left(other) => fail(s"Expected BindError, got $other")
      case Right(_)    => fail("Expected Left")

  // ==================== ModelValidationError (missing domain) tests ====================

  private val loss = TypeId("Loss")

  private val catalogWithLoss = TypeCatalog.unsafe(
    types = Set(DomainType(asset), DomainType(loss)),  // both are domain types — Loss can be quantified over
    predicates = Map(
      SymbolName("leaf")    -> PredicateSig(List(asset)),
      SymbolName("coastal") -> PredicateSig(List(asset)),
      SymbolName("hasloss") -> PredicateSig(List(loss))
    )
  )

  private val losslessDispatcher = new RuntimeDispatcher:
    override def evalFunction(name: SymbolName, args: List[Value]): Either[String, Value] =
      Left("no function")
    override def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean] =
      name.value match
        case "leaf"    => Right(true)
        case "coastal" => Right(true)
        case "hasloss" => Right(true)
        case other     => Left(s"no predicate: $other")
    override def functionSymbols: Set[SymbolName] = Set.empty
    override def predicateSymbols: Set[SymbolName] =
      Set(SymbolName("leaf"), SymbolName("coastal"), SymbolName("hasloss"))

  test("ModelValidationError raised when enumerable type has no registered domain (root variable)"):
    val queryOverLoss = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.01),
      variable = "l",
      range = FOL("hasloss", List(Term.Var("l"))),
      scope = Formula.True,
      answerVars = Nil
    )
    // Loss is enumerable in catalogWithLoss; model omits Loss domain → FolModel construction fails
    val model = RuntimeModel(domains = Map(asset -> Set(vA, vB)), dispatcher = losslessDispatcher)
    val result = FolModel(catalogWithLoss, model)
    result match
      case Left(e: QueryError.ModelValidationError) =>
        assert(e.errors.exists(_.contains("Loss")))
      case Left(other) => fail(s"Expected ModelValidationError, got $other")
      case Right(_)    => fail("Expected Left")

  test("ModelValidationError raised when enumerable type has no registered domain (nested Forall)"):
    val queryWithInnerForall = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.01),
      variable = "x",
      range = FOL("leaf", List(Term.Var("x"))),
      scope = Formula.Forall("l", Formula.Atom(FOL("hasloss", List(Term.Var("l"))))),
      answerVars = Nil
    )
    val model = RuntimeModel(domains = Map(asset -> Set(vA, vB)), dispatcher = losslessDispatcher)
    val result = FolModel(catalogWithLoss, model)
    result match
      case Left(e: QueryError.ModelValidationError) =>
        assert(e.errors.exists(_.contains("Loss")))
      case Left(other) => fail(s"Expected ModelValidationError, got $other")
      case Right(_)    => fail("Expected Left")

  test("ModelValidationError raised when enumerable type has no registered domain (nested Exists)"):
    val queryWithInnerExists = ParsedQuery(
      quantifier = Quantifier.About(1, 2, 0.01),
      variable = "x",
      range = FOL("leaf", List(Term.Var("x"))),
      scope = Formula.Exists("l", Formula.Atom(FOL("hasloss", List(Term.Var("l"))))),
      answerVars = Nil
    )
    val model = RuntimeModel(domains = Map(asset -> Set(vA, vB)), dispatcher = losslessDispatcher)
    val result = FolModel(catalogWithLoss, model)
    result match
      case Left(e: QueryError.ModelValidationError) =>
        assert(e.errors.exists(_.contains("Loss")))
      case Left(other) => fail(s"Expected ModelValidationError, got $other")
      case Right(_)    => fail("Expected Left")

  // ==================== DomainNotFoundError defensive fallback ====================
  // Reachable only by bypassing the normal pipeline (direct TypedSemantics.evaluate
  // with a manually constructed BoundQuery). In a correctly wired system this path
  // should never be taken.

  test("DomainNotFoundError defensive fallback via direct TypedSemantics.evaluate"):
    // A catalog where Loss is NOT enumerable (asset only) — validateAgainst only
    // checks Asset domain coverage, so it passes.
    val catalogAssetOnly = TypeCatalog.unsafe(
      types = Set(DomainType(asset), ValueType(loss)),  // Loss is a value type — validateAgainst will not check Loss domain
      predicates = Map(
        SymbolName("hasloss") -> PredicateSig(List(loss))
      )
    )
    // Manually construct a BoundQuery over Loss, bypassing QueryBinder.bind
    val boundQuery = BoundQuery(
      quantifier = Quantifier.About(1, 2, 0.01),
      variable = BoundVar("l", loss),
      range = BoundAtom(SymbolName("hasloss"), List(BoundTerm.VarRef(BoundVar("l", loss)))),
      scope = BoundFormula.True,
      answerVars = Nil
    )
    val model = RuntimeModel(domains = Map(asset -> Set(vA, vB)), dispatcher = losslessDispatcher)
    // validateAgainst passes (only checks enumerable types); TypedSemantics.evaluate hits the fallback
    val result = TypedSemantics.evaluate(boundQuery, model, samplingParams = SamplingParams.exact)
    result match
      case Left(e: QueryError.DomainNotFoundError) =>
        assertEquals(e.typeName, "Loss")
        assert(e.availableTypes.contains("Asset"))
      case Left(other) => fail(s"Expected DomainNotFoundError, got $other")
      case Right(_)    => fail("Expected Left")

  // ==================== S-1: And/Or/Imp short-circuit ====================
  // Verifies that the right branch of connectives is never dispatched when
  // the left branch already determines the truth value.

  private def sentinelDispatcher(sentinel: SymbolName, onSentinelCalled: () => Unit): RuntimeDispatcher =
    new RuntimeDispatcher:
      override def evalPredicate(name: SymbolName, args: List[Value]): Either[String, Boolean] =
        name.value match
          case "leaf" => Right(true)
          case s if s == sentinel.value =>
            onSentinelCalled()
            Left(s"SHOULD NOT BE DISPATCHED: $s")
          case other => Left(s"unknown predicate: $other")
      override def evalFunction(name: SymbolName, args: List[Value]): Either[String, Value] =
        Left("no functions")
      override def functionSymbols: Set[SymbolName] = Set.empty
      override def predicateSymbols: Set[SymbolName] = Set(SymbolName("leaf"), sentinel)

  test("And short-circuits: right branch not dispatched when left is False"):
    val sentinel = SymbolName("and-sentinel")
    var dispatched = false
    val x = BoundVar("x", asset)
    val scope = BoundFormula.And(
      BoundFormula.False,
      BoundFormula.Atom(BoundAtom(sentinel, List(BoundTerm.VarRef(x))))
    )
    val boundQuery = BoundQuery(
      quantifier = Quantifier.About(1, 2, 0.01),
      variable   = x,
      range      = BoundAtom(SymbolName("leaf"), List(BoundTerm.VarRef(x))),
      scope      = scope
    )
    val model = RuntimeModel(
      domains    = Map(asset -> Set(vA, vB)),
      dispatcher = sentinelDispatcher(sentinel, () => dispatched = true)
    )
    val result = TypedSemantics.evaluate(boundQuery, model, samplingParams = SamplingParams.exact)
    assert(result.isRight, s"Expected Right, got $result")
    assert(!dispatched, "And right branch was dispatched despite left being False")
    assertEquals(result.toOption.get.satisfyingElements.size, 0)

  test("Or short-circuits: right branch not dispatched when left is True"):
    val sentinel = SymbolName("or-sentinel")
    var dispatched = false
    val x = BoundVar("x", asset)
    val scope = BoundFormula.Or(
      BoundFormula.True,
      BoundFormula.Atom(BoundAtom(sentinel, List(BoundTerm.VarRef(x))))
    )
    val boundQuery = BoundQuery(
      quantifier = Quantifier.About(1, 2, 0.01),
      variable   = x,
      range      = BoundAtom(SymbolName("leaf"), List(BoundTerm.VarRef(x))),
      scope      = scope
    )
    val model = RuntimeModel(
      domains    = Map(asset -> Set(vA, vB)),
      dispatcher = sentinelDispatcher(sentinel, () => dispatched = true)
    )
    val result = TypedSemantics.evaluate(boundQuery, model, samplingParams = SamplingParams.exact)
    assert(result.isRight, s"Expected Right, got $result")
    assert(!dispatched, "Or right branch was dispatched despite left being True")
    assertEquals(result.toOption.get.satisfyingElements.size, 2)  // Or(True, _) always satisfied

  test("Imp short-circuits: consequent not dispatched when antecedent is False"):
    val sentinel = SymbolName("imp-sentinel")
    var dispatched = false
    val x = BoundVar("x", asset)
    val scope = BoundFormula.Imp(
      BoundFormula.False,
      BoundFormula.Atom(BoundAtom(sentinel, List(BoundTerm.VarRef(x))))
    )
    val boundQuery = BoundQuery(
      quantifier = Quantifier.About(1, 2, 0.01),
      variable   = x,
      range      = BoundAtom(SymbolName("leaf"), List(BoundTerm.VarRef(x))),
      scope      = scope
    )
    val model = RuntimeModel(
      domains    = Map(asset -> Set(vA, vB)),
      dispatcher = sentinelDispatcher(sentinel, () => dispatched = true)
    )
    val result = TypedSemantics.evaluate(boundQuery, model, samplingParams = SamplingParams.exact)
    assert(result.isRight, s"Expected Right, got $result")
    assert(!dispatched, "Imp consequent was dispatched despite antecedent being False")
    assertEquals(result.toOption.get.satisfyingElements.size, 2)  // Imp(False, _) vacuously true
