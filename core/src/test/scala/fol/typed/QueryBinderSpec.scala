package fol.typed

import TypeDecl.*
import LiteralValue.*
import munit.FunSuite
import fol.logic.ParsedQuery
import fol.quantifier.Quantifier
import logic.{FOL, Formula, Term}

class QueryBinderSpec extends FunSuite:

  private val asset = TypeId("Asset")
  private val loss = TypeId("Loss")
  private val prob = TypeId("Probability")
  private val bool = TypeId("Bool")

  private val catalog = TypeCatalog.unsafe(
    types = Set(DomainType(asset), ValueType(loss), ValueType(prob), ValueType(bool)),
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
      loss -> (s => s.toLongOption.map(IntLiteral(_))),
      prob -> (s => s.toDoubleOption.map(FloatLiteral(_)))
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
    types = Set(DomainType(asset), ValueType(loss)),  // Loss is a value type — cannot be quantified over
    functions = Map(
      SymbolName("p95") -> FunctionSig(List(asset), loss)
    ),
    predicates = Map(
      SymbolName("leaf")    -> PredicateSig(List(asset)),
      SymbolName("gt_loss") -> PredicateSig(List(loss, loss))
    )
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

  // ==================== Phase 3: named-constant rewrite (closes T-002) ====================
  // PLAN-symmetric-value-boundaries.md §5; ADR-015 §4.
  //
  // Validators now carry a parsed `Any` carrier into `ConstRef.raw` (was a
  // `LiteralValue` stopgap). A validator returning `None` produces
  // `TypeCheckError.UnparseableConstant`. Sorts with no validator continue
  // to fall through to `UnknownConstantOrLiteral`.

  // Catalog wired with primitive-carrier validators via LiteralParser.asValidator.
  private val catalogPrim = TypeCatalog.unsafe(
    types = Set(DomainType(asset), ValueType(loss), ValueType(prob), ValueType(bool)),
    functions = Map(
      SymbolName("p95") -> FunctionSig(List(asset), loss),
      SymbolName("lec") -> FunctionSig(List(asset, loss), prob)
    ),
    predicates = Map(
      SymbolName("leaf")    -> PredicateSig(List(asset)),
      SymbolName("gt_loss") -> PredicateSig(List(loss, loss)),
      SymbolName("gt_prob") -> PredicateSig(List(prob, prob))
    ),
    literalValidators = Map(
      loss -> LiteralParser.asValidator[Long],
      prob -> LiteralParser.asValidator[Double]
    )
  )

  /** Walk a bound query and find the first ConstRef in any atom argument. */
  private def firstConstRef(bq: BoundQuery): BoundTerm.ConstRef =
    def fromTerm(t: BoundTerm): Option[BoundTerm.ConstRef] = t match
      case c: BoundTerm.ConstRef => Some(c)
      case BoundTerm.FnApp(_, args, _) => args.iterator.flatMap(fromTerm).nextOption()
      case _: BoundTerm.VarRef => None
    def fromFormula(f: BoundFormula): Option[BoundTerm.ConstRef] = f match
      case BoundFormula.Atom(a) => a.args.iterator.flatMap(fromTerm).nextOption()
      case BoundFormula.Not(p) => fromFormula(p)
      case BoundFormula.And(p, q) => fromFormula(p).orElse(fromFormula(q))
      case BoundFormula.Or(p, q) => fromFormula(p).orElse(fromFormula(q))
      case BoundFormula.Imp(p, q) => fromFormula(p).orElse(fromFormula(q))
      case BoundFormula.Iff(p, q) => fromFormula(p).orElse(fromFormula(q))
      case BoundFormula.Forall(_, b) => fromFormula(b)
      case BoundFormula.Exists(_, b) => fromFormula(b)
      case BoundFormula.True | BoundFormula.False => None
    fromFormula(bq.scope)
      .orElse(bq.range.args.iterator.flatMap(fromTerm).nextOption())
      .getOrElse(fail("no ConstRef found in bound query"))

  test("Phase 3: validator parses inline literal, ConstRef.raw is parsed Any (Long)"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = FOL("leaf", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("gt_loss",
        List(Term.Fn("p95", List(Term.Var("x"))), Term.Const("5000000")))),
      answerVars = Nil
    )
    QueryBinder.bind(query, catalogPrim) match
      case Right(bq) =>
        val cr = firstConstRef(bq)
        assertEquals(cr.sourceText, "5000000")
        assertEquals(cr.typeId, loss)
        assertEquals(cr.raw, 5000000L: Any)
        // Carrier must be the parsed primitive, NOT the source text:
        assert(cr.raw.isInstanceOf[Long],
          s"expected Long carrier, got ${cr.raw.getClass.getName}: ${cr.raw}")
      case Left(errs) => fail(s"expected Right, got Left($errs)")

  test("Phase 3: validator parses Double literal, ConstRef.raw is Double"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = FOL("leaf", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("gt_prob",
        List(Term.Fn("lec", List(Term.Var("x"), Term.Const("10000000"))), Term.Const("0.05")))),
      answerVars = Nil
    )
    QueryBinder.bind(query, catalogPrim) match
      case Right(bq) =>
        // The first ConstRef encountered in the scope is the Long literal "10000000"
        // inside lec(...); the Double literal "0.05" is the second argument of gt_prob.
        // Walk the scope explicitly to grab the Double one.
        val scope = bq.scope match
          case BoundFormula.Atom(a) => a
          case other => fail(s"expected Atom scope, got $other")
        val probArg = scope.args(1) match
          case c: BoundTerm.ConstRef => c
          case other => fail(s"expected ConstRef as 2nd arg, got $other")
        assertEquals(probArg.sourceText, "0.05")
        assertEquals(probArg.typeId, prob)
        assertEquals(probArg.raw, 0.05: Any)
        assert(probArg.raw.isInstanceOf[Double])
      case Left(errs) => fail(s"expected Right, got Left($errs)")

  test("Phase 3: validator returns None -> UnparseableConstant(name, sort, sourceText)"):
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = FOL("leaf", List(Term.Var("x"))),
      // "0.05" cannot be parsed as Long for the loss sort.
      scope = Formula.Atom(FOL("gt_loss",
        List(Term.Fn("p95", List(Term.Var("x"))), Term.Const("0.05")))),
      answerVars = Nil
    )
    QueryBinder.bind(query, catalogPrim) match
      case Left(errs) =>
        val matched = errs.collect {
          case e @ TypeCheckError.UnparseableConstant(name, sort, src) => (name, sort, src)
        }
        assertEquals(matched, List(("0.05", loss, "0.05")))
      case Right(_) => fail("expected Left(UnparseableConstant)")

  test("Phase 3: sort with no validator falls through to UnknownConstantOrLiteral"):
    // Catalog with NO validator for `loss`.
    val noValidator = TypeCatalog.unsafe(
      types = Set(DomainType(asset), ValueType(loss), ValueType(prob), ValueType(bool)),
      functions = Map(
        SymbolName("p95") -> FunctionSig(List(asset), loss)
      ),
      predicates = Map(
        SymbolName("leaf")    -> PredicateSig(List(asset)),
        SymbolName("gt_loss") -> PredicateSig(List(loss, loss))
      )
    )
    val query = ParsedQuery(
      quantifier = Quantifier.mkAtLeast(1, 2),
      variable = "x",
      range = FOL("leaf", List(Term.Var("x"))),
      scope = Formula.Atom(FOL("gt_loss",
        List(Term.Fn("p95", List(Term.Var("x"))), Term.Const("5000000")))),
      answerVars = Nil
    )
    QueryBinder.bind(query, noValidator) match
      case Left(errs) =>
        assert(errs.exists {
          case TypeCheckError.UnknownConstantOrLiteral("5000000") => true
          case _ => false
        }, s"expected UnknownConstantOrLiteral, got $errs")
        assert(!errs.exists(_.isInstanceOf[TypeCheckError.UnparseableConstant]),
          s"must not produce UnparseableConstant when no validator registered: $errs")
      case Right(_) => fail("expected Left for sort with no validator")

