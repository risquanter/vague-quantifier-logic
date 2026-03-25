package fol

import fol.datastore.{RelationValue, RelationTuple}

/** Shared test helpers for `RelationValue`-typed fixtures.
  *
  * Mix into any `munit.FunSuite` that needs `const`, `unary`, or
  * `binary` shorthand for building KB facts.
  *
  * {{{
  * class MySpec extends munit.FunSuite, RelationValueFixtures:
  *   test("example"):
  *     val tuple = binary("France", "Germany")
  * }}}
  */
trait RelationValueFixtures:

  /** Wrap a string as `RelationValue.Const`. */
  def const(s: String): RelationValue = RelationValue.Const(s)

  /** Build a unary `RelationTuple[RelationValue]`. */
  def unary(s: String): RelationTuple[RelationValue] = RelationTuple(List(const(s)))

  /** Build a binary `RelationTuple[RelationValue]`. */
  def binary(a: String, b: String): RelationTuple[RelationValue] =
    RelationTuple(List(const(a), const(b)))
