package fol.typed

import fol.quantifier.Quantifier

case class BoundVar(name: String, sort: TypeId)

enum BoundTerm:

  case VarRef(v: BoundVar)

  /** A reference to a named constant declared in [[TypeCatalog.constants]].
    *
    * The constant's value is resolved at evaluation time by the runtime
    * model; no payload is carried in the IR.
    *
    * @param name The constant's identifier as it appears in the query text.
    * @param sort The declared sort of the constant.
    */
  case ConstRef(name: String, sort: TypeId)

  /** A resolved inline literal from the query text.
    *
    * @param sourceText The original source token (e.g. "10000000", "0.05").
    * @param sort       The sort the literal was validated against.
    * @param value      The parsed literal carrier produced by the sort's
    *                   validator. After ADR-015 §4 / ADR-016 / PLAN Phase 5a
    *                   this is the consumer's chosen carrier as `Any`
    *                   (e.g. `Long`, `Double`) and flows into `Value.raw`
    *                   at evaluation time. Dispatcher lambdas recover the
    *                   typed view via `Extract[A]` (ADR-015 §2).
    *                   The `Carrier[A]` GADT refinement is deferred (T-005).
    */
  case LiteralRef(sourceText: String, sort: TypeId, value: Any)

  case FnApp(name: SymbolName, args: List[BoundTerm], sort: TypeId)

case class BoundAtom(name: SymbolName, args: List[BoundTerm])

enum BoundFormula:
  case True
  case False
  case Atom(value: BoundAtom)
  case Not(p: BoundFormula)
  case And(p: BoundFormula, q: BoundFormula)
  case Or(p: BoundFormula, q: BoundFormula)
  case Imp(p: BoundFormula, q: BoundFormula)
  case Iff(p: BoundFormula, q: BoundFormula)
  case Forall(v: BoundVar, body: BoundFormula)
  case Exists(v: BoundVar, body: BoundFormula)

case class BoundQuery(
  quantifier: Quantifier,
  variable: BoundVar,
  range: BoundAtom,
  scope: BoundFormula,
  answerVars: List[BoundVar] = Nil
)
