package fol.datastore

import munit.FunSuite
import fol.datastore.RelationValue.*
import fol.error.QueryError

/** Test suite for Knowledge Base
  * 
  * Based on Fermüller, Hofer, and Ortiz (2017).
  * "Querying with Vague Quantifiers Using Probabilistic Semantics"
  * 
  * Tests:
  * - Relation schema definition
  * - Knowledge base construction and queries
  * - Risk domain data integrity
  */
class KnowledgeBaseSpec extends FunSuite:
  
  // ==================== Relation Tests ====================
  
  test("create unary relation") {
    val rel = Relation.unary("person")
    assertEquals(rel.name.value, "person")
    assertEquals(rel.arity, 1)
    assert(rel.isUnary)
    assert(!rel.isBinary)
  }
  
  test("create binary relation") {
    val rel = Relation.binary("knows")
    assertEquals(rel.name.value, "knows")
    assertEquals(rel.arity, 2)
    assert(!rel.isUnary)
    assert(rel.isBinary)
  }
  
  test("relation arity-only validation accepts matching arity") {
    val rel = Relation.unary("person")
    val tuple = RelationTuple.fromConstants("alice")
    assertEquals(tuple.arity, rel.arity)
  }
  
  test("relation rejects wrong arity via addFact") {
    val kb = KnowledgeBase.builder[RelationValue]
      .withUnaryRelation("person")
      .build()
    assert(kb.addFact(RelationName("person"), RelationTuple.fromConstants("alice", "bob")).isLeft)
  }
  
  // ==================== Tuple Tests ====================
  
  test("create tuple from constants") {
    val tuple = RelationTuple.fromConstants("alice", "bob")
    assertEquals(tuple.arity, 2)
    assertEquals(tuple(0), Const("alice"))
    assertEquals(tuple(1), Const("bob"))
  }
  
  test("create tuple from numbers") {
    val tuple = RelationTuple.fromNums(1, 2, 3)
    assertEquals(tuple.arity, 3)
    assertEquals(tuple(0), Num(1))
    assertEquals(tuple(1), Num(2))
    assertEquals(tuple(2), Num(3))
  }
  
  test("tuple matches pattern with wildcards") {
    val tuple = RelationTuple.fromConstants("alice", "bob")
    assert(tuple.matches(List(Some(Const("alice")), None)))
    assert(tuple.matches(List(None, Some(Const("bob")))))
    assert(tuple.matches(List(None, None)))
    assert(!tuple.matches(List(Some(Const("charlie")), None)))
  }
  
  // ==================== Knowledge Base Tests ====================
  
  test("create empty knowledge base") {
    val kb = KnowledgeBase.empty[RelationValue]
    assertEquals(kb.totalFacts, 0)
    assertEquals(kb.schema.size, 0)
  }
  
  test("add relation to knowledge base") {
    val kb = KnowledgeBase.builder[RelationValue]
      .withUnaryRelation("person")
      .build()
    
    assert(kb.hasRelation(RelationName("person")))
    assertEquals(kb.schema.size, 1)
  }
  
  test("cannot add duplicate relation") {
    val kb = KnowledgeBase.builder[RelationValue]
      .withUnaryRelation("person")
      .build()
    assert(kb.addRelation(Relation.unary("person")).isLeft)
  }
  
  test("add fact to knowledge base") {
    val kb = KnowledgeBase.builder[RelationValue]
      .withUnaryRelation("person")
      .withConstFact("person", "alice")
      .build()
    
    assertEquals(kb.contains(RelationName("person"), RelationTuple.fromConstants("alice")), Right(true))
    assertEquals(kb.count(RelationName("person")), Right(1))
  }
  
  test("cannot add fact without schema") {
    val kb = KnowledgeBase.empty[RelationValue]
    assert(kb.addFact(RelationName("person"), RelationTuple.fromConstants("alice")).isLeft)
  }
  
  test("cannot add fact with wrong arity") {
    val kb = KnowledgeBase.builder[RelationValue]
      .withUnaryRelation("person")
      .build()
    assert(kb.addFact(RelationName("person"), RelationTuple.fromConstants("alice", "bob")).isLeft)
  }
  
  test("query facts with pattern") {
    val kb = KnowledgeBase.builder[RelationValue]
      .withBinaryRelation("knows")
      .withConstFact("knows", "alice", "bob")
      .withConstFact("knows", "alice", "charlie")
      .withConstFact("knows", "bob", "charlie")
      .build()
    
    // Find all people alice knows
    val aliceKnows = kb.query(RelationName("knows"), List(Some(Const("alice")), None))
    assertEquals(aliceKnows.map(_.size), Right(2))
    
    // Find all people who know charlie
    val knowsCharlie = kb.query(RelationName("knows"), List(None, Some(Const("charlie"))))
    assertEquals(knowsCharlie.map(_.size), Right(2))
  }
  
  test("get domain from relation") {
    val kb = KnowledgeBase.builder[RelationValue]
      .withUnaryRelation("person")
      .withConstFact("person", "alice")
      .withConstFact("person", "bob")
      .withConstFact("person", "charlie")
      .build()
    
    val domain = kb.getDomain(RelationName("person"))
    assertEquals(domain.map(_.size), Right(3))
    assert(domain.exists(_.contains(Const("alice"))))
    assert(domain.exists(_.contains(Const("bob"))))
    assert(domain.exists(_.contains(Const("charlie"))))
  }
  
  test("get domain from binary relation") {
    val kb = KnowledgeBase.builder[RelationValue]
      .withBinaryRelation("knows")
      .withConstFact("knows", "alice", "bob")
      .withConstFact("knows", "alice", "charlie")
      .build()
    
    val firstPos = kb.getDomain(RelationName("knows"), 0)
    assertEquals(firstPos, Right(Set(Const("alice"))))
    
    val secondPos = kb.getDomain(RelationName("knows"), 1)
    assertEquals(secondPos, Right(Set(Const("bob"), Const("charlie"))))
  }
  
  test("getDomain returns PositionOutOfBoundsError for negative position") {
    val kb = KnowledgeBase.builder[RelationValue]
      .withBinaryRelation("knows")
      .withConstFact("knows", "alice", "bob")
      .build()

    val result = kb.getDomain(RelationName("knows"), -1)
    result match
      case Left(e: QueryError.PositionOutOfBoundsError) =>
        assertEquals(e.relationName, RelationName("knows"))
        assertEquals(e.arity, 2)
        assertEquals(e.position, -1)
        assert(e.context("position") == "-1")
        assert(e.context("arity") == "2")
      case other =>
        fail(s"Expected Left(PositionOutOfBoundsError), got $other")
  }

  test("getDomain returns PositionOutOfBoundsError for position >= arity") {
    val kb = KnowledgeBase.builder[RelationValue]
      .withBinaryRelation("knows")
      .withConstFact("knows", "alice", "bob")
      .build()

    // position == arity (off by one)
    val atArity = kb.getDomain(RelationName("knows"), 2)
    atArity match
      case Left(e: QueryError.PositionOutOfBoundsError) =>
        assertEquals(e.arity, 2)
        assertEquals(e.position, 2)
      case other =>
        fail(s"Expected Left(PositionOutOfBoundsError) at boundary, got $other")

    // position well beyond arity
    val beyond = kb.getDomain(RelationName("knows"), 99)
    beyond match
      case Left(e: QueryError.PositionOutOfBoundsError) =>
        assertEquals(e.position, 99)
      case other =>
        fail(s"Expected Left(PositionOutOfBoundsError), got $other")
  }

  test("getDomain returns RelationNotFoundError for missing relation") {
    val kb = KnowledgeBase.empty[RelationValue]
    val result = kb.getDomain(RelationName("nonexistent"))
    result match
      case Left(_: QueryError.RelationNotFoundError) => () // expected
      case other =>
        fail(s"Expected Left(RelationNotFoundError), got $other")
  }

  test("active domain includes all values") {
    val kb = KnowledgeBase.builder[RelationValue]
      .withUnaryRelation("person")
      .withBinaryRelation("knows")
      .withConstFact("person", "alice")
      .withConstFact("knows", "bob", "charlie")
      .build()
    
    val activeDom = kb.activeDomain
    assertEquals(activeDom.size, 3)
  }
  
  // ==================== Builder Tests ====================
  
  test("build knowledge base with builder") {
    val kb = KnowledgeBase.builder[RelationValue]
      .withUnaryRelation("person")
      .withBinaryRelation("knows")
      .withConstFact("person", "alice")
      .withConstFact("person", "bob")
      .withConstFact("knows", "alice", "bob")
      .build()
    
    assertEquals(kb.schema.size, 2)
    assertEquals(kb.totalFacts, 3)
  }
  
  // ==================== RelationValue Validation Tests ====================
  
  test("RelationValueValidation validates correct tuple") {
    val rel = Relation.unary("person")
    val tuple = RelationTuple.fromConstants("alice")
    assert(RelationValueValidation.validates(
      rel, tuple, List(PositionType.Constant)
    ))
  }
  
  test("RelationValueValidation rejects wrong type") {
    val rel = Relation.unary("person")
    val tuple = RelationTuple.fromNums(42)
    assert(!RelationValueValidation.validates(
      rel, tuple, List(PositionType.Constant)
    ))
  }
  
  // ==================== Risk Domain Tests ====================
  
  test("risk domain has correct schema") {
    val kb = RiskDomain.createKnowledgeBase
    
    assert(kb.hasRelation(RelationName("component")))
    assert(kb.hasRelation(RelationName("risk")))
    assert(kb.hasRelation(RelationName("mitigation")))
    assert(kb.hasRelation(RelationName("has_risk")))
    assert(kb.hasRelation(RelationName("has_mitigation")))
    assert(kb.hasRelation(RelationName("mitigates")))
  }
  
  test("risk domain has correct number of entities") {
    val kb = RiskDomain.createKnowledgeBase
    
    assertEquals(kb.count(RelationName("component")), Right(RiskDomain.components.size))
    assertEquals(kb.count(RelationName("risk")), Right(RiskDomain.risks.size))
    assertEquals(kb.count(RelationName("mitigation")), Right(RiskDomain.mitigations.size))
  }
  
  test("risk domain has component-risk relationships") {
    val kb = RiskDomain.createKnowledgeBase
    
    // Check auth_module has sql_injection risk
    val authRisks = kb.query(RelationName("has_risk"), List(Some(Const("auth_module")), None))
    assert(authRisks.exists(_.exists(t => t(1) == Const("sql_injection"))))
  }
  
  test("risk domain has component-mitigation relationships") {
    val kb = RiskDomain.createKnowledgeBase
    
    // Check database has encryption_at_rest mitigation
    val dbMitigations = kb.query(RelationName("has_mitigation"), List(Some(Const("database")), None))
    assert(dbMitigations.exists(_.exists(t => t(1) == Const("encryption_at_rest"))))
  }
  
  test("risk domain has mitigation-risk relationships") {
    val kb = RiskDomain.createKnowledgeBase
    
    // Check input_validation mitigates sql_injection
    val validationMitigates = kb.query(RelationName("mitigates"), List(Some(Const("input_validation")), None))
    assert(validationMitigates.exists(_.exists(t => t(1) == Const("sql_injection"))))
  }
  
  test("risk domain critical components are subset of components") {
    val kb = RiskDomain.createKnowledgeBase
    
    val allComponents = kb.getDomain(RelationName("component")).toOption.get
    val criticalComponents = kb.getDomain(RelationName("critical_component")).toOption.get
    
    assert(criticalComponents.subsetOf(allComponents))
    assert(criticalComponents.size < allComponents.size)
  }
  
  test("risk domain high severity risks are subset of risks") {
    val kb = RiskDomain.createKnowledgeBase
    
    val allRisks = kb.getDomain(RelationName("risk")).toOption.get
    val highSeverity = kb.getDomain(RelationName("high_severity")).toOption.get
    
    assert(highSeverity.subsetOf(allRisks))
    assert(highSeverity.size < allRisks.size)
  }
  
  test("all components in has_risk are valid components") {
    val kb = RiskDomain.createKnowledgeBase
    
    val components = kb.getDomain(RelationName("component")).toOption.get
    val componentsWithRisks = kb.getDomain(RelationName("has_risk"), 0).toOption.get
    
    assert(componentsWithRisks.subsetOf(components))
  }
  
  test("all risks in has_risk are valid risks") {
    val kb = RiskDomain.createKnowledgeBase
    
    val risks = kb.getDomain(RelationName("risk")).toOption.get
    val risksInRelation = kb.getDomain(RelationName("has_risk"), 1).toOption.get
    
    assert(risksInRelation.subsetOf(risks))
  }
  
  test("risk domain summary prints correctly") {
    val summary = RiskDomain.summary
    assert(summary.contains("Risk Management Domain"))
    assert(summary.contains("Components:"))
    assert(summary.contains("Risks:"))
    assert(summary.contains("Mitigations:"))
  }
