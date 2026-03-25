package fol.datastore

/** Typed wrapper for relation names.
  *
  * An opaque type backed by `String` — zero runtime overhead, full
  * compile-time distinction from other `String`-shaped values (variable
  * names, entity labels, error messages).
  *
  * Construct via `RelationName("component")`.
  * Extract the raw string via `.value`.
  *
  * @see docs/ADR-010.md
  */
opaque type RelationName = String

object RelationName:

  /** Smart constructor — rejects empty names. */
  def apply(name: String): RelationName =
    require(name.nonEmpty, "Relation name must not be empty")
    name

  extension (rn: RelationName)
    /** Unwrap to the underlying `String`. */
    def value: String = rn

  /** Ordering delegates to `String` ordering. */
  given Ordering[RelationName] = Ordering.String
