package fol.typed

import fol.quantifier.Quantifier

case class BoundVar(name: String, sort: TypeId)

enum BoundTerm:
  /** Sort of this term — derived via a single match, all branches live.
    *
    * `ConstRef` and `FnApp` carry their sort in a constructor parameter named
    * `typeId` and `resultSort` respectively (distinct from the method name
    * `sort`) so the auto-generated `val` on each case does not shadow this
    * `def`. `VarRef` delegates to `v.sort`.
    */
  def sort: TypeId = this match
    case VarRef(v)         => v.sort
    case ConstRef(_, s, _) => s
    case FnApp(_, _, s)    => s

  case VarRef(v: BoundVar)

  /** A resolved inline literal from the query text.
    *
    * @param sourceText The original source token (e.g. "10000000", "0.05").
    * @param typeId     The sort the literal was validated against.
    * @param raw        The parsed literal carrier produced by the sort's
    *                   validator. After ADR-015 §4 /
    *                   PLAN-symmetric-value-boundaries Phase 3 this is
    *                   the consumer's chosen carrier as `Any` (e.g. `Long`,
    *                   `Double`, or a consumer wrapper) and flows into
    *                   `Value.raw` at evaluation time. Dispatcher lambdas
    *                   recover the typed view via `Extract[A]` (ADR-015 §2).
    */
  case ConstRef(sourceText: String, typeId: TypeId, raw: Any)

  case FnApp(name: SymbolName, args: List[BoundTerm], resultSort: TypeId)

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
