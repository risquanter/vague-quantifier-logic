package fol.datastore

import munit.FunSuite
import fol.datastore.DomainElement.given

class DomainElementSpec extends FunSuite:

  // ── DomainElement[RelationValue] ────────────────────────────────

  test("RelationValue.Const show returns the name") {
    val rv = RelationValue.Const("alice")
    assertEquals(rv.show, "alice")
  }

  test("RelationValue.Num show returns the int as string") {
    val rv = RelationValue.Num(42)
    assertEquals(rv.show, "42")
  }

  // ── DomainElement[String] ───────────────────────────────────────

  test("String show returns itself") {
    assertEquals("hello".show, "hello")
  }

  test("empty String show returns empty string") {
    assertEquals("".show, "")
  }

  // ── DomainElement[Int] ──────────────────────────────────────────

  test("Int show returns decimal representation") {
    assertEquals(42.show, "42")
  }

  test("negative Int show includes minus sign") {
    assertEquals((-7).show, "-7")
  }

  // ── DomainElement summoner ──────────────────────────────────────

  test("summoner returns the correct instance") {
    val ev = DomainElement[RelationValue]
    val rv = RelationValue.Const("bob")
    assertEquals(ev.show(rv), "bob")
  }

  // ── DomainCodec[RelationValue] ──────────────────────────────────

  test("fromString creates Const") {
    val codec = DomainCodec[RelationValue]
    assertEquals(codec.fromString("alice"), RelationValue.Const("alice"))
  }

  test("fromNumericLiteral parses integer") {
    val codec = DomainCodec[RelationValue]
    assertEquals(codec.fromNumericLiteral("42"), Some(RelationValue.Num(42)))
  }

  test("fromNumericLiteral returns None for non-integer") {
    val codec = DomainCodec[RelationValue]
    assertEquals(codec.fromNumericLiteral("alice"), None)
  }

  test("fromNumericLiteral returns None for decimal (RelationValue uses Int)") {
    val codec = DomainCodec[RelationValue]
    assertEquals(codec.fromNumericLiteral("3.14"), None)
  }

  test("fromNumericLiteral handles negative integers") {
    val codec = DomainCodec[RelationValue]
    assertEquals(codec.fromNumericLiteral("-7"), Some(RelationValue.Num(-7)))
  }

  test("DomainCodec summoner returns the correct instance") {
    val codec = DomainCodec[RelationValue]
    assertEquals(codec.fromString("test"), RelationValue.Const("test"))
  }
