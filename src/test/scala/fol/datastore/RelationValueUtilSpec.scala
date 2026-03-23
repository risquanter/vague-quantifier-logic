package fol.datastore

import munit.FunSuite
import fol.datastore.RelationValue
import fol.datastore.RelationValueUtil.*

/** Test suite for RelationValueUtil
  * 
  * Tests the conversion utilities between RelationValue (KB representation)
  * and domain values (FOL semantics).
  */
class RelationValueUtilSpec extends FunSuite:
  
  // ==================== toDomainValue Tests ====================
  
  test("toDomainValue: Const to String") {
    val rv = RelationValue.Const("alice")
    val result = toDomainValue(rv)
    assertEquals(result, "alice")
    assert(result.isInstanceOf[String])
  }
  
  test("toDomainValue: Num to Int") {
    val rv = RelationValue.Num(42)
    val result = toDomainValue(rv)
    assertEquals(result, 42)
    assert(result.isInstanceOf[Int])
  }
  
  test("toDomainValue: multiple Const values") {
    assertEquals(toDomainValue(RelationValue.Const("bob")), "bob")
    assertEquals(toDomainValue(RelationValue.Const("C1")), "C1")
    assertEquals(toDomainValue(RelationValue.Const("france")), "france")
  }
  
  test("toDomainValue: multiple Num values") {
    assertEquals(toDomainValue(RelationValue.Num(0)), 0)
    assertEquals(toDomainValue(RelationValue.Num(-10)), -10)
    assertEquals(toDomainValue(RelationValue.Num(999)), 999)
  }
  
  test("toDomainValue: empty string Const") {
    assertEquals(toDomainValue(RelationValue.Const("")), "")
  }
  
  // ==================== fromDomainValue Tests ====================
  
  test("fromDomainValue: String to Const") {
    val result = fromDomainValue("alice")
    assertEquals(result, RelationValue.Const("alice"))
  }
  
  test("fromDomainValue: Int to Num") {
    val result = fromDomainValue(42)
    assertEquals(result, RelationValue.Num(42))
  }
  
  test("fromDomainValue: round-trip Const") {
    val original = RelationValue.Const("test")
    val roundTrip = fromDomainValue(toDomainValue(original))
    assertEquals(roundTrip, original)
  }
  
  test("fromDomainValue: round-trip Num") {
    val original = RelationValue.Num(123)
    val roundTrip = fromDomainValue(toDomainValue(original))
    assertEquals(roundTrip, original)
  }
  
  test("fromDomainValue: unsupported type throws exception") {
    intercept[IllegalArgumentException] {
      fromDomainValue(3.14)  // Double not supported
    }
  }
  
  test("fromDomainValue: unsupported type exception message") {
    val ex = intercept[IllegalArgumentException] {
      fromDomainValue(true)  // Boolean not supported
    }
    assert(ex.getMessage.contains("Unsupported domain value type"))
  }
  
  // ==================== toDomainSet Tests ====================
  
  test("toDomainSet: empty set") {
    val input = Set.empty[RelationValue]
    val result = toDomainSet(input)
    assertEquals(result, Set.empty[Any])
  }
  
  test("toDomainSet: single Const") {
    val input = Set(RelationValue.Const("alice"))
    val result = toDomainSet(input)
    assertEquals(result, Set[Any]("alice"))
  }
  
  test("toDomainSet: single Num") {
    val input = Set(RelationValue.Num(42))
    val result = toDomainSet(input)
    assertEquals(result, Set[Any](42))
  }
  
  test("toDomainSet: mixed Const and Num") {
    val input = Set(
      RelationValue.Const("alice"),
      RelationValue.Num(30),
      RelationValue.Const("bob"),
      RelationValue.Num(25)
    )
    val result = toDomainSet(input)
    assertEquals(result, Set[Any]("alice", 30, "bob", 25))
  }
  
  test("toDomainSet: multiple Const values") {
    val input = Set(
      RelationValue.Const("france"),
      RelationValue.Const("germany"),
      RelationValue.Const("italy")
    )
    val result = toDomainSet(input)
    assertEquals(result, Set[Any]("france", "germany", "italy"))
  }
  
  test("toDomainSet: preserves set size") {
    val input = Set(
      RelationValue.Const("a"),
      RelationValue.Const("b"),
      RelationValue.Num(1),
      RelationValue.Num(2)
    )
    assertEquals(toDomainSet(input).size, 4)
  }
  
  // ==================== toDomainSetTyped Tests ====================
  
  test("toDomainSetTyped: Const to String set") {
    val input = Set(
      RelationValue.Const("alice"),
      RelationValue.Const("bob")
    )
    val result: Set[String] = toDomainSetTyped[String](input)
    assertEquals(result, Set("alice", "bob"))
  }
  
  test("toDomainSetTyped: Num to Int set") {
    val input = Set(
      RelationValue.Num(1),
      RelationValue.Num(2),
      RelationValue.Num(3)
    )
    val result: Set[Int] = toDomainSetTyped[Int](input)
    assertEquals(result, Set(1, 2, 3))
  }
  
  test("toDomainSetTyped: empty set") {
    val input = Set.empty[RelationValue]
    val result: Set[String] = toDomainSetTyped[String](input)
    assertEquals(result, Set.empty[String])
  }
  
  test("toDomainSetTyped: type mismatch with asInstanceOf") {
    // This test shows the danger of toDomainSetTyped - it will cast incorrectly
    val input = Set(RelationValue.Num(42))
    val result: Set[String] = toDomainSetTyped[String](input)
    // The cast succeeds but the value is wrong type at runtime
    // This is why toDomainSetTyped has a warning in its documentation
    assert(result.nonEmpty)
  }
  
  // ==================== toDomainList Tests ====================
  
  test("toDomainList: empty list") {
    val input = List.empty[RelationValue]
    val result = toDomainList(input)
    assertEquals(result, List.empty[Any])
  }
  
  test("toDomainList: single element") {
    val input = List(RelationValue.Const("alice"))
    val result = toDomainList(input)
    assertEquals(result, List("alice"))
  }
  
  test("toDomainList: preserves order") {
    val input = List(
      RelationValue.Const("first"),
      RelationValue.Num(2),
      RelationValue.Const("third"),
      RelationValue.Num(4)
    )
    val result = toDomainList(input)
    assertEquals(result, List[Any]("first", 2, "third", 4))
  }
  
  test("toDomainList: multiple same values") {
    val input = List(
      RelationValue.Const("a"),
      RelationValue.Const("a"),
      RelationValue.Const("a")
    )
    val result = toDomainList(input)
    assertEquals(result, List("a", "a", "a"))
    assertEquals(result.size, 3)
  }
  
  // ==================== Integration Tests ====================
  
  test("integration: active domain conversion (mixed types)") {
    // Simulate converting KB active domain to FOL model domain
    val activeDomain = Set(
      RelationValue.Const("alice"),
      RelationValue.Const("bob"),
      RelationValue.Num(30),
      RelationValue.Num(25)
    )
    
    val domainElements = toDomainSet(activeDomain)
    
    assert(domainElements.contains("alice"))
    assert(domainElements.contains("bob"))
    assert(domainElements.contains(30))
    assert(domainElements.contains(25))
    assertEquals(domainElements.size, 4)
  }
  
  test("integration: relation domain extraction") {
    // Simulate extracting domain from "person" relation
    val personDomain = Set(
      RelationValue.Const("alice"),
      RelationValue.Const("bob"),
      RelationValue.Const("charlie")
    )
    
    val population: Set[String] = toDomainSetTyped[String](personDomain)
    assertEquals(population, Set("alice", "bob", "charlie"))
  }
  
  test("integration: full round-trip with set") {
    val original = Set(
      RelationValue.Const("paris"),
      RelationValue.Const("berlin"),
      RelationValue.Num(100)
    )
    
    val domainValues = toDomainSet(original)
    val backToRV = domainValues.map(fromDomainValue)
    
    assertEquals(backToRV, original)
  }
  
  test("integration: Query DSL population extraction pattern") {
    // Simulate the pattern used in Query.scala
    val domainValues = Set(
      RelationValue.Const("component1"),
      RelationValue.Const("component2"),
      RelationValue.Const("component3")
    )
    
    // This is exactly how Query uses toDomainSetTyped
    val population: Set[String] = toDomainSetTyped[String](domainValues)
    
    assertEquals(population.size, 3)
    assert(population.forall(_.isInstanceOf[String]))
  }
