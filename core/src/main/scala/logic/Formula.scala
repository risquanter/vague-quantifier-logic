package logic

enum Formula[+A]:
    case False
    case True
    case Atom(value: A)
    case Not(p: Formula[A])
    case And(p: Formula[A], q: Formula[A])
    case Or(p: Formula[A], q: Formula[A])
    case Imp(p: Formula[A], q: Formula[A])
    case Iff(p: Formula[A], q: Formula[A])
    case Forall(x: String, p: Formula[A])
    case Exists(x: String, p: Formula[A])

object Formula:
    /** Smart constructors (OCaml needs these because it can't use constructors 
    * directly in some contexts).
    * 
    * Temporarily in the Scala re-write until I figure out a better approach.
    */
    def mkAnd[A](p: Formula[A], q: Formula[A]): Formula[A] = And(p, q)

    def mkOr[A](p: Formula[A], q: Formula[A]): Formula[A] = Or(p, q)

    def mkImp[A](p: Formula[A], q: Formula[A]): Formula[A] = Imp(p, q)

    def mkIff[A](p: Formula[A], q: Formula[A]): Formula[A] = Iff(p, q)

    def mkForall[A](x: String, p: Formula[A]): Formula[A] = Forall(x, p)
    def mkExists[A](x: String, p: Formula[A]): Formula[A] = Exists(x, p)

    /** Convenience constructors for common cases to stay consistent */
    def atom[A](a: A): Formula[A] = Atom(a)
    def not[A](p: Formula[A]): Formula[A] = Not(p)

    /** Example: true */
    def trueFormula[A]: Formula[A] = True
  
  /** Example: false */
    def falseFormula[A]: Formula[A] = False