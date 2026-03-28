package fol.typed

import munit.FunSuite

class TypeCatalogSpec extends FunSuite:

  test("validate succeeds for coherent catalog"):
    val asset = TypeId("Asset")
    val loss = TypeId("Loss")
    val bool = TypeId("Bool")

    val result = TypeCatalog(
      types = Set(asset, loss, bool),
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
      types = Set(asset, loss),
      constants = Map.empty,
      functions = Map(SymbolName("foo") -> FunctionSig(List(asset), loss)),
      predicates = Map(SymbolName("foo") -> PredicateSig(List(loss)))
    )

    assert(result.isLeft)

  test("validate fails when domainTypes references a type not in declared types"):
    val asset = TypeId("Asset")
    val ghost  = TypeId("Ghost")  // not in types

    val result = TypeCatalog(
      types = Set(asset),
      predicates = Map(SymbolName("leaf") -> PredicateSig(List(asset))),
      domainTypes = Some(Set(asset, ghost))
    )

    assert(result.isLeft)

  test("validate succeeds when domainTypes is a proper subset of declared types"):
    val asset = TypeId("Asset")
    val loss  = TypeId("Loss")

    val result = TypeCatalog(
      types = Set(asset, loss),
      predicates = Map(
        SymbolName("leaf")    -> PredicateSig(List(asset)),
        SymbolName("hasloss") -> PredicateSig(List(loss))
      ),
      domainTypes = Some(Set(asset))   // Loss present in types but not a domain type
    )

    assert(result.isRight)
