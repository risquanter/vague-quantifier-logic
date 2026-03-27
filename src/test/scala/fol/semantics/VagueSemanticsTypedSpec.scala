package fol.semantics

import fol.error.QueryError
import fol.logic.{ParsedQuery, Quantifier}
import fol.sampling.SamplingParams
import fol.typed.{PredicateSig, RuntimeDispatcher, RuntimeModel, TypeCatalog, TypeId, TypeRepr, SymbolName, Value}
import logic.{FOL, Formula, Term}
import munit.FunSuite

class VagueSemanticsTypedSpec extends FunSuite:

  private val asset = TypeId("Asset")

  private val catalog = TypeCatalog.unsafe(
    types = Set(asset),
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

    val result = VagueSemantics.evaluateTyped(
      query = query,
      catalog = catalog,
      model = model,
      samplingParams = SamplingParams.exact
    )

    assert(result.isRight)
    val output = result.toOption.get
    assertEquals(output.rangeElements.size, 2)
    assertEquals(output.satisfyingElements.size, 1)
    assertEquals(output.result.proportion, 0.5)

  test("evaluateTyped returns ValidationError when runtime model is missing declared predicate"):
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

    val result = VagueSemantics.evaluateTyped(
      query = query,
      catalog = catalog,
      model = invalidModel,
      samplingParams = SamplingParams.exact
    )

    result match
      case Left(_: QueryError.ValidationError) => assert(true)
      case Left(other) => fail(s"Expected ValidationError, got $other")
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

    val output = VagueSemantics.evaluateTyped(
      query = query,
      catalog = catalog,
      model = model,
      samplingParams = SamplingParams.exact
    ).toOption.get

    // Project Value witness set to consumer String — no asInstanceOf at call site
    val satisfying: Set[String] = output.satisfyingElements.flatMap(_.as[String])
    assertEquals(satisfying, Set("A"))

    // A Value with a mismatched sort returns None, not a cast exception
    val wrongSortValue = Value(TypeId("Risk"), "something")
    assertEquals(wrongSortValue.as[String], None)
