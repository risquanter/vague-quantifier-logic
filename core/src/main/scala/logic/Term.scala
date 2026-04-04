package logic

/** Terms in first-order logic
  * 
  * Corresponds to OCaml:
  *   type term = Var of string | Fn of string * term list
  */
enum Term:
    /** Variable: x, y, foo, etc. */
  case Var(name: String)
  /** Function application: f(t1, ..., tn)
    */
  case Fn(name: String, args: List[Term])

  /** Type-safe representation for constants */
  case Const(name: String) 

object Term:
    /** Example from fol.ml: sqrt(1 - cos(power(x + y, 2)))
      *
      * Uses [[Term.Const]] for the numeric literals 1 and 2 — the idiomatic
      * representation since `Term.Const` was added (deviation from Harrison OCaml)
      * to distinguish inline literals from zero-arity function applications.
      */
    def example: Term =
        Fn("sqrt", List(
            Fn("-", List(
                Const("1"),
                Fn("cos", List(
                    Fn("power", List(
                        Fn("+", List(Var("x"), Var("y"))),
                        Const("2")
                    ))
                ))
            ))
        ))