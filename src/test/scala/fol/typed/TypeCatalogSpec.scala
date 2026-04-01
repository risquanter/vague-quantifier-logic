package fol.typed

import munit.FunSuite

class TypeCatalogSpec extends FunSuite:

  test("validate succeeds for coherent catalog"):
    val asset = TypeId("Asset")
    val loss = TypeId("Loss")
    val bool = TypeId("Bool")

    val result = TypeCatalog(
      types = Set(DomainType(asset), DomainType(loss), DomainType(bool)),
      constants = Map("rootAsset" -> asset),
      functions = Map(
        SymbolName("p95") -> FunctionSig(List(asset), loss)
      ),
      predicates = Map(
        SymbolName("gt_loss") -> PredicateSig(List(loss, loss))
      ),
      literalValidators = Map(loss -> (_.forall(_.isDigit)))
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
