package logic

import munit.FunSuite
import logic.Term.*
import logic.Formula.*
import logic.FOLUtil.*
import parser.FOLParser

class FOLUtilSpec extends FunSuite:
  
  // ==================== Helper Function Tests ====================
  
  test("union combines lists without duplicates") {
    val result = union(List(1, 2), List(2, 3))
    assertEquals(result.sorted, List(1, 2, 3))
  }
  
  test("unions combines multiple lists") {
    val result = unions(List(List(1, 2), List(2, 3), List(3, 4)))
    assertEquals(result.sorted, List(1, 2, 3, 4))
  }
  
  test("subtract removes elements") {
    assertEquals(subtract(List(1, 2, 3), List(2)), List(1, 3))
  }
  
  test("insert adds if not present") {
    assertEquals(insert(4, List(1, 2, 3)), List(4, 1, 2, 3))
    assertEquals(insert(2, List(1, 2, 3)), List(1, 2, 3))
  }
  
  // ==================== Free Variables in Terms ====================
  
  test("fvt: variable has itself as free var") {
    assertEquals(fvt(Var("x")), List("x"))
  }
  
  test("fvt: constant has no free vars") {
    assertEquals(fvt(Fn("42", List())), List())
  }
  
  test("fvt: function with var args") {
    val tm = Fn("f", List(Var("x"), Var("y")))
    val freeVars = fvt(tm)
    assert(freeVars.contains("x"))
    assert(freeVars.contains("y"))
  }
  
  test("fvt: nested function") {
    val tm = Fn("f", List(Fn("g", List(Var("x"))), Var("y")))
    val freeVars = fvt(tm)
    assert(freeVars.contains("x"))
    assert(freeVars.contains("y"))
  }
  
  test("fvt: arithmetic expression") {
    // 2 * x + y
    val tm = Fn("+", List(
      Fn("*", List(Fn("2", List()), Var("x"))),
      Var("y")
    ))
    val freeVars = fvt(tm)
    assert(freeVars.contains("x"))
    assert(freeVars.contains("y"))
    assert(!freeVars.contains("2"))
  }
  
  // ==================== Variables in Formulas ====================
  
  test("varFOL: simple predicate") {
    val fm = FOLParser.parse("P(x, y)")
    val vars = varFOL(fm)
    assert(vars.contains("x"))
    assert(vars.contains("y"))
  }
  
  test("varFOL: includes bound variables") {
    val fm = FOLParser.parse("forall x . P(x)")
    val vars = varFOL(fm)
    assert(vars.contains("x"))
  }
  
  test("varFOL: conjunction") {
    val fm = FOLParser.parse("P(x) /\\ Q(y)")
    val vars = varFOL(fm)
    assert(vars.contains("x"))
    assert(vars.contains("y"))
  }
  
  // ==================== Free Variables in Formulas ====================
  
  test("fvFOL: simple predicate") {
    val fm = FOLParser.parse("P(x, y)")
    val freeVars = fvFOL(fm)
    assert(freeVars.contains("x"))
    assert(freeVars.contains("y"))
  }
  
  test("fvFOL: forall removes bound variable") {
    val fm = FOLParser.parse("forall x . P(x)")
    val freeVars = fvFOL(fm)
    assert(!freeVars.contains("x"))
  }
  
  test("fvFOL: forall with free variable") {
    val fm = FOLParser.parse("forall x . P(x, y)")
    val freeVars = fvFOL(fm)
    assert(!freeVars.contains("x"))
    assert(freeVars.contains("y"))
  }
  
  test("fvFOL: exists removes bound variable") {
    val fm = FOLParser.parse("exists x . P(x)")
    val freeVars = fvFOL(fm)
    assert(!freeVars.contains("x"))
  }
  
  test("fvFOL: nested quantifiers") {
    val fm = FOLParser.parse("forall x . exists y . P(x, y, z)")
    val freeVars = fvFOL(fm)
    assert(!freeVars.contains("x"))
    assert(!freeVars.contains("y"))
    assert(freeVars.contains("z"))
  }
  
  test("fvFOL: complex formula") {
    val fm = FOLParser.parse("forall x . P(x) ==> exists y . Q(x, y, z)")
    val freeVars = fvFOL(fm)
    assert(!freeVars.contains("x"))
    assert(!freeVars.contains("y"))
    assert(freeVars.contains("z"))
  }
  
  test("fvFOL: no free variables") {
    val fm = FOLParser.parse("forall x y . P(x, y)")
    val freeVars = fvFOL(fm)
    assertEquals(freeVars, List())
  }
  
  test("fvFOL: relation with arithmetic") {
    val fm = FOLParser.parse("x + y < z")
    val freeVars = fvFOL(fm)
    assert(freeVars.contains("x"))
    assert(freeVars.contains("y"))
    assert(freeVars.contains("z"))
  }
  
  // ==================== Universal Closure ====================
  
  test("generalizeFOL: adds forall for free variables") {
    val fm = FOLParser.parse("P(x, y)")
    val closed = generalizeFOL(fm)
    // Should be: forall x y. P(x, y) or forall y x. P(x, y)
    closed match
      case Forall(v1, Forall(v2, Atom(_))) =>
        assert(Set(v1, v2) == Set("x", "y"))
      case _ => fail(s"Expected forall forall, got: $closed")
  }
  
  test("generalizeFOL: no change if already closed") {
    val fm = FOLParser.parse("forall x y . P(x, y)")
    val closed = generalizeFOL(fm)
    assertEquals(closed, fm)
  }
  
  test("generalizeFOL: adds forall only for free vars") {
    val fm = FOLParser.parse("forall x . P(x, y)")
    val closed = generalizeFOL(fm)
    closed match
      case Forall("y", Forall("x", Atom(_))) => // OK
      case _ => fail(s"Expected forall y x, got: $closed")
  }
  
  // ==================== Substitution in Terms ====================
  
  test("tsubst: substitute variable") {
    val subst = Map("x" -> Var("y"))
    assertEquals(tsubst(subst, Var("x")), Var("y"))
  }
  
  test("tsubst: no substitution if not in map") {
    val subst = Map("x" -> Var("y"))
    assertEquals(tsubst(subst, Var("z")), Var("z"))
  }
  
  test("tsubst: substitute in function") {
    val subst = Map("x" -> Var("y"))
    val tm = Fn("f", List(Var("x"), Var("z")))
    assertEquals(tsubst(subst, tm), Fn("f", List(Var("y"), Var("z"))))
  }
  
  test("tsubst: substitute with term") {
    val subst = Map("x" -> Fn("g", List(Var("z"))))
    assertEquals(tsubst(subst, Var("x")), Fn("g", List(Var("z"))))
  }
  
  test("tsubst: nested substitution") {
    val subst = Map("x" -> Var("a"), "y" -> Var("b"))
    val tm = Fn("+", List(Var("x"), Var("y")))
    assertEquals(tsubst(subst, tm), Fn("+", List(Var("a"), Var("b"))))
  }
  
  // ==================== Variable Renaming ====================
  
  test("variant: returns same if not in list") {
    assertEquals(variant("x", List("y", "z")), "x")
  }
  
  test("variant: adds prime if in list") {
    assertEquals(variant("x", List("x", "y")), "x'")
  }
  
  test("variant: adds multiple primes if needed") {
    assertEquals(variant("x", List("x", "x'")), "x''")
  }
  
  test("variant: handles multiple primes") {
    assertEquals(variant("x", List("x", "x'", "x''")), "x'''")
  }
  
  // ==================== Substitution in Formulas ====================
  
  test("subst: substitute in atom") {
    val fm = FOLParser.parse("P(x)")
    val substMap = Map("x" -> Var("y"))
    val result = subst(substMap, fm)
    assertEquals(result, FOLParser.parse("P(y)"))
  }
  
  test("subst: substitute in relation") {
    val fm = FOLParser.parse("x < y")
    val substMap = Map("x" -> Var("z"))
    val result = subst(substMap, fm)
    assertEquals(result, FOLParser.parse("z < y"))
  }
  
  test("subst: substitute in conjunction") {
    val fm = FOLParser.parse("P(x) /\\ Q(y)")
    val substMap = Map("x" -> Var("a"), "y" -> Var("b"))
    val result = subst(substMap, fm)
    assertEquals(result, FOLParser.parse("P(a) /\\ Q(b)"))
  }
  
  test("subst: variable capture avoidance (OCaml example)") {
    // subst(Map("y" -> Var("x")), forall x. x = y)
    // Should rename bound x to avoid capture
    val fm = FOLParser.parse("forall x . x = y")
    val substMap = Map("y" -> Var("x"))
    val result = subst(substMap, fm)
    
    // Result should be: forall x'. x' = x (or similar with renamed variable)
    result match
      case Forall(x2, Atom(FOL("=", List(Var(v1), Var(v2))))) =>
        assert(x2 != "x") // Bound variable should be renamed
        assertEquals(v1, x2) // Left side should be renamed var
        assertEquals(v2, "x") // Right side should be substituted x
      case _ => fail(s"Expected forall with renamed var, got: $result")
  }
  
  test("subst: no renaming if no capture risk") {
    val fm = FOLParser.parse("forall x . P(x)")
    val substMap = Map("y" -> Var("z"))
    val result = subst(substMap, fm)
    // No y in formula, so no change
    assertEquals(result, fm)
  }
  
  test("subst: substitute under quantifier") {
    val fm = FOLParser.parse("forall x . P(x, y)")
    val substMap = Map("y" -> Var("z"))
    val result = subst(substMap, fm)
    assertEquals(result, FOLParser.parse("forall x . P(x, z)"))
  }
  
  test("subst: complex example with nested quantifiers") {
    val fm = FOLParser.parse("forall x . exists y . P(x, y, z)")
    val substMap = Map("z" -> Var("w"))
    val result = subst(substMap, fm)
    assertEquals(result, FOLParser.parse("forall x . exists y . P(x, y, w)"))
  }
