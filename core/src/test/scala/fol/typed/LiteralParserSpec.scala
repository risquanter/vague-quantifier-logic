package fol.typed

import munit.FunSuite

/** Tests for [[LiteralParser]] (ADR-015 §1 + PLAN-symmetric-value-boundaries §3).
  *
  * Coverage:
  *   - Long parser: positive/negative integers; rejects non-numeric, rejects
  *     decimal text (no implicit float→long).
  *   - Double parser: decimals; integer literals widen to Double; rejects
  *     non-numeric.
  */
class LiteralParserSpec extends FunSuite:

  test("LiteralParser[Long] parses positive integers"):
    assertEquals(summon[LiteralParser[Long]].parse("42"), Right(42L))

  test("LiteralParser[Long] parses negative integers"):
    assertEquals(summon[LiteralParser[Long]].parse("-7"), Right(-7L))

  test("LiteralParser[Long] rejects non-numeric input"):
    assert(summon[LiteralParser[Long]].parse("abc").isLeft)

  test("LiteralParser[Long] rejects decimal text (no implicit float→long)"):
    assert(summon[LiteralParser[Long]].parse("42.0").isLeft)

  test("LiteralParser[Double] parses decimals"):
    assertEquals(summon[LiteralParser[Double]].parse("3.14"), Right(3.14))

  test("LiteralParser[Double] accepts integer literal (widens to Double)"):
    assertEquals(summon[LiteralParser[Double]].parse("42"), Right(42.0))

  test("LiteralParser[Double] rejects non-numeric input"):
    assert(summon[LiteralParser[Double]].parse("abc").isLeft)
