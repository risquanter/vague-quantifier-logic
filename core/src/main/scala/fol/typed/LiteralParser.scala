package fol.typed

/** Injection-boundary typeclass: parse a query-string literal into the
  * consumer's chosen JVM carrier `A`.
  *
  * Per ADR-015 §1 and PLAN-symmetric-value-boundaries.md §3 (Phase 1):
  * the library provides instances for primitive carriers; consumers
  * provide instances for their own carriers and register them in
  * `TypeCatalog.literalValidators` (Phase 2).
  *
  * The library-internal adapter that widens `LiteralParser[A].parse:
  * String => Either[String, A]` to the catalog's
  * `String => Option[Any]` shape is the **only** sanctioned cast site
  * on the injection side (ADR-015 § Code Smells).
  */
trait LiteralParser[A]:
  def parse(s: String): Either[String, A]

object LiteralParser:

  /** Parse a `Long` literal. Rejects decimal text — no implicit
    * float→long conversion at the boundary.
    */
  given LiteralParser[Long] with
    def parse(s: String): Either[String, Long] =
      s.toLongOption.toRight(s"LiteralParser[Long]: cannot parse '$s' as Long")

  /** Parse a `Double` literal. Accepts integer text (widens to
    * `Double`).
    */
  given LiteralParser[Double] with
    def parse(s: String): Either[String, Double] =
      s.toDoubleOption.toRight(s"LiteralParser[Double]: cannot parse '$s' as Double")

  /** A `String` parser is intentionally not provided as a library
    * given. Consumers opt in per-sort by writing a domain-specific
    * `LiteralParser[String]` (or wrapper carrier) so that the carrier
    * shape for a `String`-backed sort is an explicit consumer choice.
    */

  /** Lift a `LiteralParser[A]` into the
    * `String => Option[Any]` shape required by
    * `TypeCatalog.literalValidators`.
    *
    * This is the **only** sanctioned location for the widening from
    * the consumer's carrier `A` to the catalog's `Any` (ADR-015 §
    * Code Smells "❌ String => Option[Any] + asInstanceOf at the
    * dispatcher" — here the widening is safe because the parser's
    * result type is `A` and the catalog re-tightens via `Extract[A]`
    * at the extraction boundary).
    */
  def asValidator[A](using p: LiteralParser[A]): String => Option[Any] =
    s => p.parse(s).toOption
