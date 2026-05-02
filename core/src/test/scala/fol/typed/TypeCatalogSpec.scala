package fol.typed

import TypeDecl.*
import LiteralValue.*
import munit.FunSuite

class TypeCatalogSpec extends FunSuite:

  test("validate succeeds for coherent catalog"):
    val asset = TypeId("Asset")
    val loss = TypeId("Loss")
    val bool = TypeId("Bool")

    val result = TypeCatalog(
      types = Set(DomainType(asset), ValueType(loss), DomainType(bool)),
      constants = Map("rootAsset" -> asset),
      functions = Map(
        SymbolName("p95") -> FunctionSig(List(asset), loss)
      ),
      predicates = Map(
        SymbolName("gt_loss") -> PredicateSig(List(loss, loss))
      ),
      literalValidators = Map(loss -> (s => s.toLongOption.map(IntLiteral(_))))
    )

    assert(result.isRight)

  test("validate fails when symbol name is reused across functions and predicates"):
    val asset = TypeId("Asset")
    val loss = TypeId("Loss")

    val result = TypeCatalog(
      types = Set(DomainType(asset), DomainType(loss)),
      constants = Map.empty,
      functions = Map(SymbolName("foo") -> FunctionSig(List(asset), loss)),
      predicates = Map(SymbolName("foo") -> PredicateSig(List(loss)))
    )

    assert(result.isLeft)

  test("validate succeeds when loss is declared as a ValueType (not quantifiable)"):
    val asset = TypeId("Asset")
    val loss  = TypeId("Loss")

    val result = TypeCatalog(
      types = Set(DomainType(asset), ValueType(loss)),  // Loss present but not quantifiable
      predicates = Map(
        SymbolName("leaf")    -> PredicateSig(List(asset)),
        SymbolName("hasloss") -> PredicateSig(List(loss))
      )
    )

    assert(result.isRight)

  test("domainTypes returns only DomainType ids"):
    val asset = TypeId("Asset")
    val loss  = TypeId("Loss")

    val catalog = TypeCatalog(
      types = Set(DomainType(asset), ValueType(loss)),
      predicates = Map(
        SymbolName("leaf")    -> PredicateSig(List(asset)),
        SymbolName("hasloss") -> PredicateSig(List(loss))
      )
    ).getOrElse(fail("catalog must be valid"))

    assertEquals(catalog.domainTypes, Set(asset))
    assertEquals(catalog.typeIds, Set(asset, loss))

  // PLAN-symmetric-value-boundaries §4 (Phase 2) regression: name-collision
  // detection must not be sensitive to the validator-map signature change.
  test("nameCollisions detected even when validators are present"):
    val asset = TypeId("Asset")
    val loss  = TypeId("Loss")

    val result = TypeCatalog(
      types = Set(DomainType(asset), ValueType(loss)),
      functions = Map(SymbolName("foo") -> FunctionSig(List(asset), loss)),
      predicates = Map(SymbolName("foo") -> PredicateSig(List(loss))),
      literalValidators = Map(loss -> (s => s.toLongOption.map(_ => s)))
    )

    assert(result.isLeft)
    assert(result.left.exists(_.exists {
      case TypeCatalogError.NameCollision("foo") => true
      case _                                     => false
    }))

  // PLAN-symmetric-value-boundaries §4 (Phase 2): validators may now produce
  // any consumer carrier; e.g. parse Long literals into Long carriers.
  test("validator returning Some(parsedValue: Any) is accepted"):
    val asset = TypeId("Asset")
    val loss  = TypeId("Loss")

    val result = TypeCatalog(
      types = Set(DomainType(asset), ValueType(loss)),
      literalValidators = Map(loss -> LiteralParser.asValidator[Long])
    )

    assert(result.isRight)

  // ==================== S-3: UnknownType.location ====================

  test("UnknownType carries location string for function return type"):
    val asset = TypeId("Asset")
    val ghost = TypeId("Ghost")

    TypeCatalog(
      types = Set(DomainType(asset)),
      functions = Map(SymbolName("p95") -> FunctionSig(List(asset), ghost))
    ) match
      case Left(List(TypeCatalogError.UnknownType(name, location))) =>
        assertEquals(name, "Ghost")
        assert(location.contains("p95"),       s"location '$location' should mention 'p95'")
        assert(location.contains("return type"), s"location '$location' should mention 'return type'")
      case other => fail(s"expected exactly one UnknownType, got $other")

  test("UnknownType emitted separately per site for same unknown type"):
    val asset = TypeId("Asset")
    val ghost = TypeId("Ghost")

    TypeCatalog(
      types      = Set(DomainType(asset)),
      functions  = Map(SymbolName("f") -> FunctionSig(List(ghost), asset)),
      predicates = Map(SymbolName("p") -> PredicateSig(List(ghost)))
    ) match
      case Left(errors) =>
        val unknowns = errors.collect { case e: TypeCatalogError.UnknownType => e }
        assertEquals(unknowns.length, 2, s"expected one error per site, not deduplicated: $unknowns")
        assert(unknowns.exists(_.location.contains("function")), s"no function-site error in $unknowns")
        assert(unknowns.exists(_.location.contains("predicate")), s"no predicate-site error in $unknowns")
      case Right(_) => fail("expected Left")

  test("FunctionReturnIsDomainType emitted when function return sort is a DomainType"):
    val asset = TypeId("Asset")
    val loss  = TypeId("Loss")

    TypeCatalog(
      types     = Set(DomainType(asset), DomainType(loss)),
      functions = Map(SymbolName("p95") -> FunctionSig(List(asset), loss))
    ) match
      case Left(errors) =>
        val domainReturns = errors.collect { case e: TypeCatalogError.FunctionReturnIsDomainType => e }
        assertEquals(domainReturns.length, 1, s"expected one FunctionReturnIsDomainType error: $errors")
        assertEquals(domainReturns.head.functionName, "p95")
        assertEquals(domainReturns.head.returnType, "Loss")
      case Right(_) => fail("expected Left for function returning DomainType")

  test("FunctionReturnIsDomainType not emitted when function return sort is a ValueType"):
    val asset = TypeId("Asset")
    val loss  = TypeId("Loss")

    val result = TypeCatalog(
      types     = Set(DomainType(asset), ValueType(loss)),
      functions = Map(SymbolName("p95") -> FunctionSig(List(asset), loss))
    )
    assert(result.isRight, s"ValueType return should be accepted, got $result")
