package examples

import logic.{FOL, Formula, Term}
import parser.FOLParser
import printer.FOLPrinter
import semantics.{Domain, Interpretation, Valuation, Model, FOLSemantics}

/** Demonstration of First-Order Logic (FOL) System
  * 
  * This file demonstrates all features of the FOL implementation:
  * - Building formulas programmatically
  * - Parsing formulas from strings
  * - Pretty-printing formulas
  * - Evaluating formulas in models
  * - Working with quantifiers
  * - Checking entailment
  * 
  * The FOL system provides the foundation for vague quantifier queries.
  */
@main def FOLDemo(): Unit =
  println("=" * 80)
  println("First-Order Logic (FOL) Demonstration")
  println("=" * 80)
  println()
  
  // Run all demos
  buildingFormulasDemo()
  parsingDemo()
  printingDemo()
  simpleModelDemo()
  arithmeticModelDemo()
  quantifiersDemo()
  entailmentDemo()
  
  println("=" * 80)
  println("Demo Complete!")
  println("=" * 80)

/** ============================================================================
  * SECTION 1: Building Formulas Programmatically
  * ============================================================================
  * 
  * Formulas are built using the Formula enum constructors. The basic building
  * blocks are:
  * - Term: variables (Var), constants (Const), functions (Fn)
  * - FOL: predicates/relations over terms
  * - Formula: logical connectives and quantifiers
  */
def buildingFormulasDemo(): Unit =
  println("SECTION 1: Building Formulas Programmatically")
  println("-" * 80)
  
  // Basic Terms
  println("Terms (individual objects and expressions):")
  
  // Variables represent unknowns
  val x = Term.Var("x")
  val y = Term.Var("y")
  println(s"  Variable x: $x")
  println(s"  Variable y: $y")
  
  // Constants represent specific objects
  val john = Term.Const("john")
  val five = Term.Const("5")
  println(s"  Constant john: $john")
  println(s"  Constant 5: $five")
  
  // Functions combine terms
  val age = Term.Fn("age", List(Term.Var("x")))
  println(s"  Function age(x): $age")
  
  // The Term class has methods like +, -, *, / for building expressions
  val expr1 = Term.Fn("+", List(Term.Var("x"), Term.Const("2")))  // x + 2
  val expr2 = Term.Fn("*", List(Term.Const("3"), Term.Var("y")))  // 3 * y
  println(s"  Expression x + 2: $expr1")
  println(s"  Expression 3 * y: $expr2")
  println()
  
  // Atomic formulas: predicates and relations
  println("Atomic formulas (basic statements):")
  
  val pred1 = Formula.Atom(FOL("human", List(Term.Const("socrates"))))    // human(socrates)
  val pred2 = Formula.Atom(FOL("mortal", List(Term.Var("x"))))            // mortal(x)
  val rel = Formula.Atom(FOL("=", List(Term.Var("x"), Term.Const("5"))))  // x = 5
  println(s"  human(socrates): $pred1")
  println(s"  mortal(x): $pred2")
  println(s"  x = 5: $rel")
  println()
  
  // Logical connectives
  println("Logical connectives:")
  
  val neg = Formula.Not(Formula.Atom(FOL("P", List())))
  println(s"  Negation ¬P: ${FOLPrinter.printFormula(neg)}")
  
  val conj = Formula.And(Formula.Atom(FOL("P", List())), Formula.Atom(FOL("Q", List())))
  println(s"  Conjunction P ∧ Q: ${FOLPrinter.printFormula(conj)}")
  
  val disj = Formula.Or(Formula.Atom(FOL("P", List())), Formula.Atom(FOL("Q", List())))
  println(s"  Disjunction P ∨ Q: ${FOLPrinter.printFormula(disj)}")
  
  val impl = Formula.Imp(Formula.Atom(FOL("P", List())), Formula.Atom(FOL("Q", List())))
  println(s"  Implication P → Q: ${FOLPrinter.printFormula(impl)}")
  
  val iff = Formula.Iff(Formula.Atom(FOL("P", List())), Formula.Atom(FOL("Q", List())))
  println(s"  Biconditional P ↔ Q: ${FOLPrinter.printFormula(iff)}")
  println()
  
  // Quantifiers
  println("Quantified formulas:")
  
  // Universal: forall x. P(x)
  val universal = Formula.Forall("x", Formula.Atom(FOL("P", List(Term.Var("x")))))
  println(s"  Universal ∀x.P(x): ${FOLPrinter.printFormula(universal)}")
  
  // Existential: exists y. Q(y)
  val existential = Formula.Exists("y", Formula.Atom(FOL("Q", List(Term.Var("y")))))
  println(s"  Existential ∃y.Q(y): ${FOLPrinter.printFormula(existential)}")
  
  // Nested: forall x. exists y. R(x, y)
  val nested = Formula.Forall("x", 
    Formula.Exists("y", 
      Formula.Atom(FOL("R", List(Term.Var("x"), Term.Var("y"))))))
  println(s"  Nested ∀x.∃y.R(x,y): ${FOLPrinter.printFormula(nested)}")
  println()

/** ============================================================================
  * SECTION 2: Parsing Formulas from Strings
  * ============================================================================
  * 
  * The FOLParser can parse formulas from string representations using a simple
  * syntax. This is more convenient than building formulas manually for complex
  * expressions.
  */
def parsingDemo(): Unit =
  println("SECTION 2: Parsing Formulas from Strings")
  println("-" * 80)
  
  // Simple atomic formula
  val input1 = "P(x)"
  val parsed1 = FOLParser.parse(input1)
  println(s"Input:  $input1")
  println(s"Parsed: ${FOLPrinter.printFormula(parsed1)}")
  println()
  
  // Formula with negation
  val input2 = "~Q(x)"
  val parsed2 = FOLParser.parse(input2)
  println(s"Input:  $input2")
  println(s"Parsed: ${FOLPrinter.printFormula(parsed2)}")
  println()
  
  // Formula with conjunction
  val input3 = "P(x) /\\ Q(y)"
  val parsed3 = FOLParser.parse(input3)
  println(s"Input:  $input3")
  println(s"Parsed: ${FOLPrinter.printFormula(parsed3)}")
  println()
  
  // Quantified formula
  val input4 = "forall x. P(x) ==> Q(x)"
  val parsed4 = FOLParser.parse(input4)
  println(s"Input:  $input4")
  println(s"Parsed: ${FOLPrinter.printFormula(parsed4)}")
  println()

/** ============================================================================
  * SECTION 3: Pretty-Printing Formulas
  * ============================================================================
  * 
  * The FOLPrinter converts formulas back to readable string representations.
  * This is useful for debugging and displaying results.
  */
def printingDemo(): Unit =
  println("SECTION 3: Pretty-Printing Formulas")
  println("-" * 80)
  
  // Build a complex formula
  val formula = Formula.Forall("x", 
    Formula.Imp(
      Formula.Atom(FOL("human", List(Term.Var("x")))),
      Formula.Exists("y",
        Formula.And(
          Formula.Atom(FOL("parent", List(Term.Var("y"), Term.Var("x")))),
          Formula.Atom(FOL("human", List(Term.Var("y"))))
        )
      )
    )
  )
  
  // The pretty-printer formats the formula for readability
  val prettyPrinted = FOLPrinter.printFormula(formula)
  
  println("Formula: \"Everyone has a human parent\"")
  println(s"Pretty-printed: $prettyPrinted")
  println()
  
  // The formula can be parsed back
  val original = "forall x. human(x) ==> exists y. parent(y, x) /\\ human(y)"
  val parsed = FOLParser.parse(original)
  val roundtrip = FOLPrinter.printFormula(parsed)
  
  println(s"Original string: $original")
  println(s"After parse+print: $roundtrip")
  println()

/** ============================================================================
  * SECTION 4: Simple Model Example (Family Relationships)
  * ============================================================================
  * 
  * A MODEL gives meaning to predicates and functions by interpreting them
  * over a specific domain.
  * 
  * This example creates a simple family model with people and relationships.
  */
def simpleModelDemo(): Unit =
  println("SECTION 4: Simple Model Example (Family Relationships)")
  println("-" * 80)
  
  // Domain: set of people
  println("Creating a family model:")
  println("  Domain: {alice, bob, charlie}")
  
  val domain = Domain(Set("alice", "bob", "charlie"))
  
  // Functions: constants (0-ary functions that return themselves)
  val functions = Map[String, List[Any] => String](
    "alice" -> (_ => "alice"),
    "bob" -> (_ => "bob"),
    "charlie" -> (_ => "charlie")
  )
  
  // Predicates: define parent and sibling relations
  val predicates = Map[String, List[Any] => Boolean](
    "parent" -> { 
      case List(parent: String, child: String) =>
        // bob is parent of alice and charlie
        (parent == "bob" && child == "alice") ||
        (parent == "bob" && child == "charlie")
      case _ => false
    },
    "sibling" -> {
      case List(p1: String, p2: String) =>
        // alice and charlie are siblings
        (p1 == "alice" && p2 == "charlie") ||
        (p1 == "charlie" && p2 == "alice")
      case _ => false
    }
  )
  
  println("  parent(bob, alice) = true")
  println("  parent(bob, charlie) = true")
  println("  sibling(alice, charlie) = true")
  println()
  
  val interpretation = Interpretation(domain, functions, predicates)
  val model = Model(interpretation)
  
  // Query the model
  println("Queries:")
  
  // Is bob parent of alice? (using constants - no valuation needed)
  val q1 = Formula.Atom(FOL("parent", List(Term.Const("bob"), Term.Const("alice"))))
  println(s"  parent(bob, alice)? ${FOLSemantics.holds(q1, model, Valuation(Map.empty))}")
  
  // Is alice parent of bob?
  val q2 = Formula.Atom(FOL("parent", List(Term.Const("alice"), Term.Const("bob"))))
  println(s"  parent(alice, bob)? ${FOLSemantics.holds(q2, model, Valuation(Map.empty))}")
  
  // Are alice and charlie siblings?
  val q3 = Formula.Atom(FOL("sibling", List(Term.Const("alice"), Term.Const("charlie"))))
  println(s"  sibling(alice, charlie)? ${FOLSemantics.holds(q3, model, Valuation(Map.empty))}")
  println()
  
  // Quantified query: "Everyone has a parent"
  val everyoneHasParent = Formula.Forall("x",
    Formula.Exists("y", Formula.Atom(FOL("parent", List(Term.Var("y"), Term.Var("x"))))))
  
  println(s"Query: ${FOLPrinter.printFormula(everyoneHasParent)}")
  println(s"Meaning: \"Everyone has a parent\"")
  println(s"True in this model? ${FOLSemantics.holds(everyoneHasParent, model, Valuation(Map.empty))}")
  println(s"  (False because bob has no parent in this model)")
  println()

/** ============================================================================
  * SECTION 5: Arithmetic Model Example
  * ============================================================================
  * 
  * Models can interpret functions and predicates over numeric domains.
  * This example shows a model of integers with arithmetic operations.
  */
def arithmeticModelDemo(): Unit =
  println("SECTION 5: Arithmetic Model Example")
  println("-" * 80)
  
  // Domain: integers from 1 to 10
  println("Creating an arithmetic model:")
  println("  Domain: {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}")
  
  val domain = Domain((1 to 10).toSet)
  
  // Functions: constants (numbers as 0-ary functions) and arithmetic operations
  val functions = Map[String, List[Any] => Int](
    // Constants for each number in the domain
    "1" -> (_ => 1), "2" -> (_ => 2), "3" -> (_ => 3), "4" -> (_ => 4), "5" -> (_ => 5),
    "6" -> (_ => 6), "7" -> (_ => 7), "8" -> (_ => 8), "9" -> (_ => 9), "10" -> (_ => 10),
    // Arithmetic operations
    "+" -> {
      case List(a: Int, b: Int) => a + b
      case _ => 0
    },
    "*" -> {
      case List(a: Int, b: Int) => a * b
      case _ => 0
    }
  )
  
  // Predicates: properties and relations
  val predicates = Map[String, List[Any] => Boolean](
    "=" -> {
      case List(a: Int, b: Int) => a == b
      case _ => false
    },
    "even" -> {
      case List(n: Int) => n % 2 == 0
      case _ => false
    },
    "prime" -> {
      case List(n: Int) =>
        n > 1 && (2 until n).forall(i => n % i != 0)
      case _ => false
    },
    "<" -> {
      case List(a: Int, b: Int) => a < b
      case _ => false
    }
  )
  
  println("  Functions: constants (1-10), +, *")
  println("  Predicates: =, even, prime, <")
  println()
  
  val interpretation = Interpretation(domain, functions, predicates)
  val model = Model(interpretation)
  
  // Test arithmetic
  println("Queries:")
  
  // Parse and evaluate: 2 + 3 = 5
  val sum = FOLParser.parse("2 + 3 = 5")
  println(s"  2 + 3 = 5? ${FOLSemantics.holds(sum, model, Valuation(Map.empty))}")
  
  // 4 is even
  val evenFour = Formula.Atom(FOL("even", List(Term.Const("4"))))
  println(s"  4 is even? ${FOLSemantics.holds(evenFour, model, Valuation(Map.empty))}")
  
  // 7 is prime
  val primeSeven = Formula.Atom(FOL("prime", List(Term.Const("7"))))
  println(s"  7 is prime? ${FOLSemantics.holds(primeSeven, model, Valuation(Map.empty))}")
  
  // 3 < 5
  val comparison = Formula.Atom(FOL("<", List(Term.Const("3"), Term.Const("5"))))
  println(s"  3 < 5? ${FOLSemantics.holds(comparison, model, Valuation(Map.empty))}")
  println()

/** ============================================================================
  * SECTION 6: Quantifiers in Action
  * ============================================================================
  * 
  * Quantifiers allow us to make statements about all elements or some elements
  * in the domain. The semantics of quantifiers is crucial to understanding FOL.
  */
def quantifiersDemo(): Unit =
  println("SECTION 6: Quantifiers in Action")
  println("-" * 80)
  
  // Use the arithmetic model from previous section
  val domain = Domain((1 to 10).toSet)
  val predicates = Map[String, List[Any] => Boolean](
    "even" -> {
      case List(n: Int) => n % 2 == 0
      case _ => false
    },
    "<" -> {
      case List(a: Int, b: Int) => a < b
      case _ => false
    }
  )
  val interpretation = Interpretation(domain, Map.empty, predicates)
  val model = Model(interpretation)
  
  // Universal quantification: "All numbers are even"
  println("1. Universal quantification (forall):")
  val allEven = Formula.Forall("x", Formula.Atom(FOL("even", List(Term.Var("x")))))
  println(s"   Formula: ${FOLPrinter.printFormula(allEven)}")
  println(s"   Meaning: \"All numbers are even\"")
  println(s"   True? ${FOLSemantics.holds(allEven, model, Valuation(Map.empty))}")
  println(s"   (False because 1, 3, 5 are odd)")
  println()
  
  // Existential quantification: "Some number is even"
  println("2. Existential quantification (exists):")
  val someEven = Formula.Exists("x", Formula.Atom(FOL("even", List(Term.Var("x")))))
  println(s"   Formula: ${FOLPrinter.printFormula(someEven)}")
  println(s"   Meaning: \"Some number is even\"")
  println(s"   True? ${FOLSemantics.holds(someEven, model, Valuation(Map.empty))}")
  println(s"   (True because 2, 4, 6, 8, 10 are even)")
  println()
  
  // Nested quantifiers: "For every x, there exists y such that x < y"
  println("3. Nested quantifiers:")
  val hasGreater = Formula.Forall("x",
    Formula.Exists("y", Formula.Atom(FOL("<", List(Term.Var("x"), Term.Var("y"))))))
  
  println(s"   Formula: ${FOLPrinter.printFormula(hasGreater)}")
  println(s"   Meaning: \"Every number has a greater number\"")
  println(s"   True? ${FOLSemantics.holds(hasGreater, model, Valuation(Map.empty))}")
  println(s"   (False because 10 is the maximum)")
  println()
  
  // Quantifier order matters!
  println("4. Quantifier order matters:")
  val version1 = Formula.Forall("x", Formula.Exists("y", Formula.Atom(FOL("<", List(Term.Var("x"), Term.Var("y"))))))
  val version2 = Formula.Exists("y", Formula.Forall("x", Formula.Atom(FOL("<", List(Term.Var("x"), Term.Var("y"))))))
  
  println(s"   ∀x.∃y. x < y: ${FOLSemantics.holds(version1, model, Valuation(Map.empty))}")
  println(s"     (\"For each x, there is some y > x\" - False)")
  println(s"   ∃y.∀x. x < y: ${FOLSemantics.holds(version2, model, Valuation(Map.empty))}")
  println(s"     (\"There is some y greater than all x\" - False)")
  println()

/** ============================================================================
  * SECTION 7: Entailment (Logical Consequence)
  * ============================================================================
  * 
  * ENTAILMENT: Γ ⊨ φ means φ is true in every model where all formulas in Γ
  * are true.
  * 
  * This is the foundation of logical reasoning: what conclusions follow from
  * our premises?
  */
def entailmentDemo(): Unit =
  println("SECTION 7: Entailment (Logical Consequence)")
  println("-" * 80)
  
  // Classic syllogism:
  // Premise 1: All humans are mortal
  // Premise 2: Socrates is human
  // Conclusion: Socrates is mortal
  
  println("Classic example: Socrates syllogism")
  println()
  println("Premises:")
  println("  1. All humans are mortal: ∀x. human(x) → mortal(x)")
  println("  2. Socrates is human: human(socrates)")
  println()
  println("Conclusion:")
  println("  Socrates is mortal: mortal(socrates)")
  println()
  
  // Create a model for this
  val domain = Domain(Set("socrates", "plato", "fido"))
  
  val functions = Map[String, List[Any] => String](
    "socrates" -> (_ => "socrates"),
    "plato" -> (_ => "plato"),
    "fido" -> (_ => "fido")
  )
  
  val predicates = Map[String, List[Any] => Boolean](
    "human" -> {
      case List(x: String) => x == "socrates" || x == "plato"
      case _ => false
    },
    "mortal" -> {
      case List(x: String) => x == "socrates" || x == "plato"
      case _ => false
    }
  )
  
  val interpretation = Interpretation(domain, functions, predicates)
  val model = Model(interpretation)
  
  // The premises
  val premise1 = Formula.Forall("x", 
    Formula.Imp(Formula.Atom(FOL("human", List(Term.Var("x")))), 
        Formula.Atom(FOL("mortal", List(Term.Var("x"))))))
  
  val premise2 = Formula.Atom(FOL("human", List(Term.Const("socrates"))))
  val conclusion = Formula.Atom(FOL("mortal", List(Term.Const("socrates"))))
  
  // Check if premises and conclusion hold
  println("In our model:")
  println(s"  Premise 1 holds? ${FOLSemantics.holds(premise1, model, Valuation(Map.empty))}")
  println(s"  Premise 2 holds? ${FOLSemantics.holds(premise2, model, Valuation(Map.empty))}")
  println(s"  Conclusion holds? ${FOLSemantics.holds(conclusion, model, Valuation(Map.empty))}")
  println()
  
  // The entailment function checks if conclusion holds in ALL models where premises hold
  val premises = List(premise1, premise2)
  println(s"Do premises entail conclusion? ${FOLSemantics.entails(premises, conclusion, model)}")
  println()
  
  // Invalid entailment: trying to conclude something not implied
  val invalidConclusion = Formula.Atom(FOL("human", List(Term.Const("fido"))))
  println("Testing invalid entailment:")
  println(s"  Can we conclude human(fido)? ${FOLSemantics.entails(premises, invalidConclusion, model)}")
  println(s"  (No, because fido is not stated to be human)")
  println()
