package fol.datastore

/** Conversion from parser tokens to domain values.
  *
  * Only needed for the '''string-parsed query path''':
  * {{{
  *   VagueQueryParser → ParsedQuery(FOL) → FOLBridge → KnowledgeSource[D]
  * }}}
  *
  * The '''typed-KB path''' (register building `KnowledgeBase[D]` directly)
  * does not need this — domain values are already typed.
  *
  * Separate from [[DomainElement]] because it is a parser concern, not a
  * general engine concern.
  *
  * @see docs/DRAFT-IMPLEMENTATION-PLAN-DOMAIN-TYPE-SAFETY.md §4 (Parser Codec)
  */
trait DomainCodec[D]:

  /** Convert a parsed string constant to domain value.
    *
    * Called by `RangeExtractor` when translating `Term.Const("alice")`
    * from FOL range predicates into KB lookup patterns.
    *
    * @param s The string token from the parser (e.g. `"alice"`, `"C1"`)
    * @return A domain value of type `D`
    */
  def fromString(s: String): D

  /** Try to interpret a string as a numeric literal.
    *
    * Called by `LiteralResolver` to install a function fallback that
    * turns digit-strings (e.g. `"5000000"`) into domain values.
    *
    * Returns `None` if the string is not numeric, allowing the
    * fallback chain to continue.
    *
    * @param s The string to try parsing (e.g. `"42"`, `"3.14"`, `"alice"`)
    * @return `Some(d)` if numeric, `None` otherwise
    */
  def fromNumericLiteral(s: String): Option[D]

object DomainCodec:

  /** Summon a DomainCodec instance. */
  def apply[D](using ev: DomainCodec[D]): DomainCodec[D] = ev

  // ── Built-in instances ────────────────────────────────────────────

  /** Codec for RelationValue — the library's ready-made domain type.
    *
    * - String constants become `Const(s)`
    * - Integer literals become `Num(i)` (matching existing `RelationValue` semantics)
    */
  given DomainCodec[RelationValue] with
    def fromString(s: String): RelationValue =
      RelationValue.Const(s)

    def fromNumericLiteral(s: String): Option[RelationValue] =
      s.toIntOption.map(RelationValue.Num(_))
