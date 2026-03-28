package fol.typed

import munit.FunSuite
import fol.logic.{ParsedQuery, Quantifier}
import logic.{FOL, Formula, Term}

class QueryBinderSpec extends FunSuite:

  private val asset = TypeId("Asset")
  private val loss = TypeId("Loss")
  private val prob = TypeId("Probability")
  private val bool = TypeId("Bool")

  private val catalog = TypeCatalog.unsafe(
    types = Set(asset, loss, prob, bool),
    functions = Map(
      SymbolName("p95") -> FunctionSig(List(asset), loss),
      SymbolName("lec") -> FunctionSig(List(asset, loss), prob)
    ),
    predicates = Map(
      SymbolName("leaf") -> PredicateSig(List(asset)),
      SymbolName("gt_loss") -> PredicateSig(List(loss, loss)),
      SymbolName("gt_prob") -> PredicateSig(List(prob, prob))
    ),
    literalValidators = Map(
      loss -> (_.forall(_.isDigit)),
      prob -> (s => s.matches("[0-9]+(\\.[0-9]+)?"))
    )
  )

  test("binds loss comparison query with unambiguous literal typing"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(2, 3),
      variable = "x",
      range = FOL("leaf", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("gt_loss", List(Term.Fn("p95", List(Term.Var("x"))), Term.Const("5000000")))),
      answerVars = Nil
    )

    val result = QueryBinder.bind(query, catalog)
    assert(result.isRight)

  test("binds probability comparison query with explicit sort-specific predicate"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = FOL("leaf", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("gt_prob", List(Term.Fn("lec", List(Term.Var("x"), Term.Const("10000000"))), Term.Const("0.05")))),
      answerVars = Nil
    )

    val result = QueryBinder.bind(query, catalog)
    assert(result.isRight)

  test("fails when literal does not validate against expected type"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = FOL("leaf", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("gt_loss", List(Term.Fn("p95", List(Term.Var("x"))), Term.Const("0.05")))),
      answerVars = Nil
    )

    val result = QueryBinder.bind(query, catalog)
    assert(result.isLeft)

  test("fails with unknown symbol"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = FOL("unknown_range", List(Term.Var("x"))),
      scope = Formula.True,
      answerVars = Nil
    )

    val result = QueryBinder.bind(query, catalog)
    assert(result.isLeft)

  // ==================== TypeNotQuantifiable tests (ADR-014 §2) ====================

  // Asset is a domain type; Loss is a value type (scalar) — not quantifiable
  private val catalogWithValueType = TypeCatalog.unsafe(
    types = Set(asset, loss),
    functions = Map(
      SymbolName("p95") -> FunctionSig(List(asset), loss)
    ),
    predicates = Map(
      SymbolName("leaf")    -> PredicateSig(List(asset)),
      SymbolName("gt_loss") -> PredicateSig(List(loss, loss))
    ),
    domainTypes = Some(Set(asset))   // Loss is a value type — cannot be quantified over
  )

  test("bind fails with TypeNotQuantifiable when root variable resolves to a value type"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "l",
      range = FOL("gt_loss", List(Term.Var("l"), Term.Var("l"))),
      scope = Formula.True,
      answerVars = Nil
    )
    QueryBinder.bind(query, catalogWithValueType) match
      case Left(List(TypeCheckError.TypeNotQuantifiable(name))) =>
        assertEquals(name, "Loss")
      case Left(other) => fail(s"Expected TypeNotQuantifiable, got $other")
      case Right(_)    => fail("Expected Left for value-type root variable")

  test("bind fails with TypeNotQuantifiable for nested Forall over value type"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = FOL("leaf", List(Term.Var("x"))),
      scope = Formula.Forall("l", Formula.Atom(FOL("gt_loss", List(Term.Var("l"), Term.Var("l"))))),
      answerVars = Nil
    )
    QueryBinder.bind(query, catalogWithValueType) match
      case Left(List(TypeCheckError.TypeNotQuantifiable(name))) =>
        assertEquals(name, "Loss")
      case Left(other) => fail(s"Expected TypeNotQuantifiable, got $other")
      case Right(_)    => fail("Expected Left for value-type nested Forall")

  test("bind fails with TypeNotQuantifiable for nested Exists over value type"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = FOL("leaf", List(Term.Var("x"))),
      scope = Formula.Exists("l", Formula.Atom(FOL("gt_loss", List(Term.Var("l"), Term.Var("l"))))),
      answerVars = Nil
    )
    QueryBinder.bind(query, catalogWithValueType) match
      case Left(List(TypeCheckError.TypeNotQuantifiable(name))) =>
        assertEquals(name, "Loss")
      case Left(other) => fail(s"Expected TypeNotQuantifiable, got $other")
      case Right(_)    => fail("Expected Left for value-type nested Exists")

  test("bind succeeds when root variable resolves to a domain type"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = FOL("leaf", List(Term.Var("x"))),
      scope = Formula.True,
      answerVars = Nil
    )
    assert(QueryBinder.bind(query, catalogWithValueType).isRight)
