package fol.datastore

/** Minimal contract for a domain element type.
  *
  * The FOL engine needs to:
  *   1. Display domain values in error messages and debugging (show)
  *   2. Compare for equality (standard Scala `equals`)
  *   3. Use as Map keys / Set elements (standard Scala `hashCode`)
  *
  * That's it.  No numeric extraction, no string parsing —
  * those are augmenter-level concerns, not engine-level.
  *
  * Consumers define a `given DomainElement[D]` for their domain type.
  * The engine provides instances for `RelationValue`, `String`, and `Int`.
  *
  * @see [[DomainCodec]] for parser-path conversions (separate concern)
  * @see docs/DRAFT-IMPLEMENTATION-PLAN-DOMAIN-TYPE-SAFETY.md §5
  */
trait DomainElement[D]:
  extension (d: D) def show: String

object DomainElement:

  /** Summon a DomainElement instance. */
  def apply[D](using ev: DomainElement[D]): DomainElement[D] = ev

  // ── Built-in instances ────────────────────────────────────────────

  /** Instance for RelationValue — the library's ready-made domain type. */
  given DomainElement[RelationValue] with
    extension (d: RelationValue) def show: String = d.toString

  /** Instance for String — useful for foundation-layer tests and
    * consumers whose KB elements are plain strings.
    */
  given DomainElement[String] with
    extension (d: String) def show: String = d

  /** Instance for Int — used by Harrison's `integerModel`. */
  given DomainElement[Int] with
    extension (d: Int) def show: String = d.toString
