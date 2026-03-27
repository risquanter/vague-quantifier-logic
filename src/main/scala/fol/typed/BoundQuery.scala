package fol.typed

import fol.logic.Quantifier

case class BoundVar(name: String, sort: TypeId)

enum BoundTerm:
  case VarRef(v: BoundVar)
  case ConstRef(name: String, override val sort: TypeId)
  case FnApp(name: SymbolName, args: List[BoundTerm], resultSort: TypeId)

  def sort: TypeId = this match
    case VarRef(v) => v.sort
    case ConstRef(_, s) => s
    case FnApp(_, _, s) => s

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
