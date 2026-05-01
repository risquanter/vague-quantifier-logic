package fol.typed

/** Extraction-boundary typeclass: lift a `Value` into the consumer's
  * chosen JVM carrier `A`.
  *
  * Per ADR-015 §2 and PLAN-symmetric-value-boundaries.md §3 (Phase 1):
  * the library provides instances for primitive carriers (with numeric
  * widening from `Long` to `Double`); consumers provide instances for
  * their own carriers as `given` declarations.
  *
  * The runtime carrier check inside an `Extract[A]` instance is the
  * **only** sanctioned cast site on the extraction side
  * (ADR-015 § Code Smells "❌ Bare `asInstanceOf` at dispatcher call
  * sites").
  *
  * Sort correctness is guaranteed by ADR-001 at bind time, so for a
  * well-formed query the `Either` left case indicates a consumer
  * mapping error or a library bug, not a user query error.
  */
trait Extract[A]:
  def apply(v: Value): Either[String, A]

object Extract:

  /** Extract a `Long` carrier exactly. */
  given Extract[Long] with
    def apply(v: Value): Either[String, Long] =
      v.raw match
        case n: Long => Right(n)
        case other   =>
          Left(s"Extract[Long]: expected Long carrier for sort '${v.sort.value}', got ${describe(other)}")

  /** Extract a `Double` carrier; widens a `Long` carrier to `Double`. */
  given Extract[Double] with
    def apply(v: Value): Either[String, Double] =
      v.raw match
        case d: Double => Right(d)
        case n: Long   => Right(n.toDouble)
        case other     =>
          Left(s"Extract[Double]: expected Double or Long carrier for sort '${v.sort.value}', got ${describe(other)}")

  /** Extract a `String` carrier exactly. No widening. */
  given Extract[String] with
    def apply(v: Value): Either[String, String] =
      v.raw match
        case s: String => Right(s)
        case other     =>
          Left(s"Extract[String]: expected String carrier for sort '${v.sort.value}', got ${describe(other)}")

  private def describe(a: Any): String =
    if a == null then "null" else s"${a.getClass.getSimpleName}($a)"

extension (v: Value)
  /** Lift this `Value` into consumer carrier `A` via the given
    * `Extract[A]`. Missing instance is a compile error.
    */
  def extract[A](using e: Extract[A]): Either[String, A] = e(v)
