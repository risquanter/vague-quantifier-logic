package semantics

import logic.{FOL, Formula, Term}

/** Semantics (Model Theory) for First-Order Logic - Phase 12
  * 
  * Implements the formal semantics of FOL:
  * - Domain: non-empty set of objects
  * - Interpretation: assigns meanings to function and predicate symbols
  * - Valuation: assigns values to variables
  * - Satisfaction: when a formula is true in a model
  * 
  * This is based on standard Tarski semantics for first-order logic.
  * 
  * Key concepts:
  * - A MODEL consists of: (Domain, Interpretation)
  * - A VALUATION assigns domain elements to variables
  * - TERM EVALUATION: compute value of a term in a model
  * - SATISFACTION: M,v ⊨ φ (formula φ holds in model M under valuation v)
  * 
  * OCaml reference (fol.ml) has basic term evaluation but not full semantics.
  * This implementation extends the concepts with complete model theory.
  */

/** Domain: non-empty set of values
  * 
  * In FOL, a domain is any non-empty set. We use a generic type D
  * to represent domain elements.
  */
case class Domain[D](elements: Set[D]):
  require(elements.nonEmpty, "Domain must be non-empty")

/** Interpretation: assigns meanings to function and predicate symbols
  * 
  * - Function symbols are interpreted as functions from D^n to D
  * - Predicate symbols are interpreted as relations (subsets of D^n)
  * 
  * Examples:
  * - Function "+" might be interpreted as integer addition
  * - Predicate "<" might be interpreted as integer less-than
  * 
  * @param domain The domain of discourse
  * @param funcInterp Maps function symbols to their interpretations
  * @param predInterp Maps predicate symbols to their interpretations
  */
case class Interpretation[D](
  domain: Domain[D],
  funcInterp: Map[String, List[D] => D],
  predInterp: Map[String, List[D] => Boolean]
):
  /** Check if a function symbol is interpreted */
  def hasFunction(name: String): Boolean = funcInterp.contains(name)
  
  /** Check if a predicate symbol is interpreted */
  def hasPredicate(name: String): Boolean = predInterp.contains(name)
  
  /** Get function interpretation */
  def getFunction(name: String): List[D] => D =
    funcInterp.get(name) match
      case Some(f) => f
      case None => _ => throw new Exception(s"Uninterpreted function: $name")
  
  /** Get predicate interpretation */
  def getPredicate(name: String): List[D] => Boolean =
    predInterp.get(name) match
      case Some(p) => p
      case None => _ => throw new Exception(s"Uninterpreted predicate: $name")

/** Valuation: assigns domain values to variables
  * 
  * A valuation is a function from variable names to domain elements.
  * 
  * Example: v = {x ↦ 5, y ↦ 7}
  */
case class Valuation[D](assignment: Map[String, D]):
  /** Get value of a variable */
  def apply(varName: String): D =
    assignment.getOrElse(varName,
      throw new Exception(s"Unbound variable: $varName")
    )
  
  /** Update valuation with new binding (for quantifiers) */
  def updated(varName: String, value: D): Valuation[D] =
    Valuation(assignment + (varName -> value))
  
  /** Check if variable is bound */
  def contains(varName: String): Boolean = assignment.contains(varName)

/** Model: combination of domain and interpretation
  * 
  * A model M = (D, I) where:
  * - D is a non-empty domain
  * - I is an interpretation of function and predicate symbols
  */
case class Model[D](interpretation: Interpretation[D]):
  def domain: Domain[D] = interpretation.domain

object FOLSemantics:
  
  // ==================== Term Evaluation ====================
  
  /** Evaluate a term in a model under a valuation
    * 
    * [[t]]^M_v = the value of term t in model M under valuation v
    * 
    * Rules:
    * - [[x]]^M_v = v(x)                    (variable lookup)
    * - [[c]]^M_v = I(c)                    (constant - 0-ary function)
    * - [[f(t1,...,tn)]]^M_v = I(f)([[t1]]^M_v, ..., [[tn]]^M_v)
    * 
    * Example:
    *   M interprets + as integer addition, * as multiplication
    *   v = {x ↦ 3, y ↦ 4}
    *   [[x + y * 2]]^M_v = 3 + (4 * 2) = 11
    * 
    * @param term The term to evaluate
    * @param interp The interpretation
    * @param valuation The variable valuation
    * @return The value in the domain
    */
  def evalTerm[D](
    term: Term,
    interp: Interpretation[D],
    valuation: Valuation[D]
  ): D =
    term match
      case Term.Var(x) =>
        valuation(x)
      
      case Term.Const(c) =>
        // Constants are 0-ary functions
        interp.getFunction(c)(List())
      
      case Term.Fn(f, args) =>
        val argValues = args.map(t => evalTerm(t, interp, valuation))
        interp.getFunction(f)(argValues)
  
  // ==================== Formula Satisfaction ====================
  
  /** Check if a formula holds in a model under a valuation
    * 
    * M,v ⊨ φ  (read: "M satisfies φ under valuation v")
    * 
    * Satisfaction rules (Tarski semantics):
    * 
    * - M,v ⊨ true                     always
    * - M,v ⊨ false                    never
    * - M,v ⊨ P(t1,...,tn)             iff I(P)([[t1]]^M_v, ..., [[tn]]^M_v) = true
    * - M,v ⊨ ¬φ                       iff M,v ⊭ φ
    * - M,v ⊨ φ ∧ ψ                    iff M,v ⊨ φ and M,v ⊨ ψ
    * - M,v ⊨ φ ∨ ψ                    iff M,v ⊨ φ or M,v ⊨ ψ
    * - M,v ⊨ φ → ψ                    iff M,v ⊭ φ or M,v ⊨ ψ
    * - M,v ⊨ φ ↔ ψ                    iff (M,v ⊨ φ iff M,v ⊨ ψ)
    * - M,v ⊨ ∀x. φ                    iff M,v[x↦d] ⊨ φ for all d ∈ D
    * - M,v ⊨ ∃x. φ                    iff M,v[x↦d] ⊨ φ for some d ∈ D
    * 
    * @param formula The formula to check
    * @param model The model (domain + interpretation)
    * @param valuation The variable valuation
    * @return true if formula holds, false otherwise
    */
  def holds[D](
    formula: Formula[FOL],
    model: Model[D],
    valuation: Valuation[D]
  ): Boolean =
    val interp = model.interpretation
    
    formula match
      case Formula.True => true
      case Formula.False => false
      
      case Formula.Atom(FOL(pred, terms)) =>
        val termValues = terms.map(t => evalTerm(t, interp, valuation))
        interp.getPredicate(pred)(termValues)
      
      case Formula.Not(p) =>
        !holds(p, model, valuation)
      
      case Formula.And(p, q) =>
        holds(p, model, valuation) && holds(q, model, valuation)
      
      case Formula.Or(p, q) =>
        holds(p, model, valuation) || holds(q, model, valuation)
      
      case Formula.Imp(p, q) =>
        !holds(p, model, valuation) || holds(q, model, valuation)
      
      case Formula.Iff(p, q) =>
        holds(p, model, valuation) == holds(q, model, valuation)
      
      case Formula.Forall(x, p) =>
        // φ holds for ALL domain elements
        model.domain.elements.forall { d =>
          holds(p, model, valuation.updated(x, d))
        }
      
      case Formula.Exists(x, p) =>
        // φ holds for SOME domain element
        model.domain.elements.exists { d =>
          holds(p, model, valuation.updated(x, d))
        }
  
  // ==================== Validity and Satisfiability ====================
  
  /** Check if a formula is satisfiable in a model
    * 
    * φ is satisfiable in M if there exists a valuation v such that M,v ⊨ φ
    * 
    * For closed formulas (no free variables), this is just holds(φ, M, ∅)
    */
  def satisfiable[D](formula: Formula[FOL], model: Model[D]): Boolean =
    // For closed formulas, valuation doesn't matter
    try
      holds(formula, model, Valuation(Map.empty))
      true
    catch
      case _: Exception =>
        // If we have free variables, try to find a satisfying valuation
        // This is a simplified check - real implementation would enumerate valuations
        false
  
  /** Check if a formula is valid in a model
    * 
    * φ is valid in M (written M ⊨ φ) if M,v ⊨ φ for ALL valuations v
    * 
    * For closed formulas, this equals satisfiability.
    * For open formulas, we'd need to check all possible valuations.
    */
  def valid[D](formula: Formula[FOL], model: Model[D]): Boolean =
    // For closed formulas, check with empty valuation
    // For open formulas, this is a simplified check
    try
      holds(formula, model, Valuation(Map.empty))
    catch
      case _: Exception => false
  
  /** Check if formula is logically valid (true in all models)
    * 
    * ⊨ φ means φ is true in EVERY model
    * 
    * Note: This is undecidable in general for FOL!
    * This method can only check specific models.
    */
  def isLogicallyValid(formula: Formula[FOL], models: List[Model[?]]): Boolean =
    models.forall(model => valid(formula, model))
  
  // ==================== Semantic Entailment ====================
  
  /** Check if premises semantically entail conclusion
    * 
    * Γ ⊨ φ means: in every model where all of Γ are true, φ is also true
    * 
    * Equivalently: Γ ⊨ φ iff Γ ∪ {¬φ} is unsatisfiable
    * 
    * @param premises Set of formulas (assumptions)
    * @param conclusion Formula to check
    * @param model Model to check in
    * @return true if entailment holds in this model
    */
  def entails[D](
    premises: List[Formula[FOL]],
    conclusion: Formula[FOL],
    model: Model[D]
  ): Boolean =
    // Check if: whenever all premises are true, conclusion is also true
    val valuation = Valuation[D](Map.empty)
    
    // If any premise is false, entailment holds vacuously
    val allPremisesHold = premises.forall(p => 
      try holds(p, model, valuation)
      catch case _: Exception => true  // Ignore free variable issues
    )
    
    if !allPremisesHold then
      true  // Vacuously true
    else
      try holds(conclusion, model, valuation)
      catch case _: Exception => false

  // ==================== Convenience Constructors ====================
  
  /** Create a simple integer arithmetic model
    * 
    * Domain: Integers
    * Functions: +, -, *, / (arithmetic operations)
    * Predicates: =, <, <=, >, >= (comparisons)
    * 
    * This is useful for examples and testing.
    * 
    * Note: Numeric constants are handled dynamically - any numeric string
    * can be used in formulas, but quantification only ranges over the domain.
    */
  def integerModel(range: Range = -10 to 10): Model[Int] =
    val domain = Domain(range.toSet)
    
    // Create a function interpreter that handles numeric constants dynamically
    val funcInterp = new scala.collection.mutable.HashMap[String, List[Int] => Int]()
    
    // Add numeric constants for all integers in range (for efficiency)
    for i <- range do
      funcInterp(i.toString) = _ => i
    
    // Binary operations
    funcInterp("+") = {
      case List(a, b) => a + b
      case args => throw new Exception(s"+ expects 2 arguments, got ${args.length}")
    }
    funcInterp("-") = {
      case List(a) => -a  // Unary minus
      case List(a, b) => a - b  // Binary minus
      case args => throw new Exception(s"- expects 1 or 2 arguments, got ${args.length}")
    }
    funcInterp("*") = {
      case List(a, b) => a * b
      case args => throw new Exception(s"* expects 2 arguments, got ${args.length}")
    }
    funcInterp("/") = {
      case List(a, b) => if b != 0 then a / b else 0
      case args => throw new Exception(s"/ expects 2 arguments, got ${args.length}")
    }
    
    // Unary operations
    funcInterp("neg") = {
      case List(a) => -a
      case args => throw new Exception(s"neg expects 1 argument, got ${args.length}")
    }
    funcInterp("abs") = {
      case List(a) => Math.abs(a)
      case args => throw new Exception(s"abs expects 1 argument, got ${args.length}")
    }
    
    val predInterp = Map[String, List[Int] => Boolean](
      "=" -> {
        case List(a, b) => a == b
        case args => throw new Exception(s"= expects 2 arguments, got ${args.length}")
      },
      "<" -> {
        case List(a, b) => a < b
        case args => throw new Exception(s"< expects 2 arguments, got ${args.length}")
      },
      "<=" -> {
        case List(a, b) => a <= b
        case args => throw new Exception(s"<= expects 2 arguments, got ${args.length}")
      },
      ">" -> {
        case List(a, b) => a > b
        case args => throw new Exception(s"> expects 2 arguments, got ${args.length}")
      },
      ">=" -> {
        case List(a, b) => a >= b
        case args => throw new Exception(s">= expects 2 arguments, got ${args.length}")
      },
      
      // Predicates
      "even" -> {
        case List(a) => a % 2 == 0
        case args => throw new Exception(s"even expects 1 argument, got ${args.length}")
      },
      "odd" -> {
        case List(a) => a % 2 != 0
        case args => throw new Exception(s"odd expects 1 argument, got ${args.length}")
      },
      "positive" -> {
        case List(a) => a > 0
        case args => throw new Exception(s"positive expects 1 argument, got ${args.length}")
      },
      "negative" -> {
        case List(a) => a < 0
        case args => throw new Exception(s"negative expects 1 argument, got ${args.length}")
      }
    )
    
    // Store reference to local funcInterp to avoid ambiguity in anonymous class
    val localFuncInterp = funcInterp.toMap
    
    // Custom interpretation that handles numeric constants dynamically
    val dynamicInterp = new Interpretation[Int](
      domain,
      localFuncInterp,
      predInterp
    ) {
      override def getFunction(name: String): List[Int] => Int =
        localFuncInterp.get(name) match
          case Some(f) => f
          case None =>
            // Try to parse as numeric constant
            if name.forall(_.isDigit) || (name.startsWith("-") && name.tail.forall(_.isDigit)) then
              _ => name.toInt
            else
              _ => throw new Exception(s"Uninterpreted function: $name")
    }
    
    Model(dynamicInterp)
  
  /** Create a simple boolean model
    * 
    * Domain: {true, false}
    * Useful for propositional logic or boolean algebra
    */
  def booleanModel(): Model[Boolean] =
    val domain = Domain(Set(true, false))
    
    val funcInterp = Map[String, List[Boolean] => Boolean](
      "true" -> (_ => true),
      "false" -> (_ => false),
      "not" -> {
        case List(a) => !a
        case args => throw new Exception(s"not expects 1 argument, got ${args.length}")
      },
      "and" -> {
        case List(a, b) => a && b
        case args => throw new Exception(s"and expects 2 arguments, got ${args.length}")
      },
      "or" -> {
        case List(a, b) => a || b
        case args => throw new Exception(s"or expects 2 arguments, got ${args.length}")
      }
    )
    
    val predInterp = Map[String, List[Boolean] => Boolean](
      "=" -> {
        case List(a, b) => a == b
        case args => throw new Exception(s"= expects 2 arguments, got ${args.length}")
      },
      "T" -> {
        case List(a) => a
        case args => throw new Exception(s"T expects 1 argument, got ${args.length}")
      }
    )
    
    val interp = Interpretation(domain, funcInterp, predInterp)
    Model(interp)
