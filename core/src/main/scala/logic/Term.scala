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
    // Helper (doesn't change the type structure)
    def const(name: String): Term = Fn(name, Nil)
  
    /** Example from fol.ml:
    * sqrt(1 - cos(power(x + y, 2)))
    */
    def example: Term =
        Fn("sqrt", List(
            Fn("-", List(
                const("1"),  // or Fn("1", Nil) directly
                Fn("cos", List(
                    Fn("power", List(
                        Fn("+", List(Var("x"), Var("y"))),
                        const("2")
                    ))
                ))
            ))
        ))