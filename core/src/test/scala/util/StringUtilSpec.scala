package util

import munit.FunSuite
import StringUtil.*

class StringUtilSpec extends FunSuite:
  
  test("explode converts string to char list") {
    assertEquals(explode("abc"), List('a', 'b', 'c'))
    assertEquals(explode(""), List())
    assertEquals(explode("x"), List('x'))
  }
  
  test("implode converts char list to string") {
    assertEquals(implode(List('a', 'b', 'c')), "abc")
    assertEquals(implode(List()), "")
    assertEquals(implode(List('x')), "x")
  }
  
  test("explode and implode are inverses") {
    val s = "hello world"
    assertEquals(implode(explode(s)), s)
    
    val chars = List('t', 'e', 's', 't')
    assertEquals(explode(implode(chars)), chars)
  }
  
  test("matches creates character predicate") {
    val isVowel = matches("aeiou")
    assert(isVowel('a'))
    assert(isVowel('e'))
    assert(!isVowel('b'))
    assert(!isVowel('x'))
  }
  
  test("space predicate") {
    assert(space(' '))
    assert(space('\t'))
    assert(space('\n'))
    assert(space('\r'))
    assert(!space('a'))
    assert(!space('1'))
  }
  
  test("numeric predicate") {
    assert(numeric('0'))
    assert(numeric('5'))
    assert(numeric('9'))
    assert(!numeric('a'))
    assert(!numeric(' '))
  }
  
  test("alphanumeric predicate") {
    assert(alphanumeric('a'))
    assert(alphanumeric('Z'))
    assert(alphanumeric('0'))
    assert(alphanumeric('_'))
    assert(alphanumeric('\''))
    assert(!alphanumeric(' '))
    assert(!alphanumeric('!'))
  }
  
  test("symbolic predicate") {
    assert(symbolic('+'))
    assert(symbolic('-'))
    assert(symbolic('*'))
    assert(symbolic('/'))
    assert(symbolic('<'))
    assert(symbolic('>'))
    assert(!symbolic('a'))
    assert(!symbolic('0'))
  }
  
  test("punctuation predicate") {
    assert(punctuation('('))
    assert(punctuation(')'))
    assert(punctuation('['))
    assert(punctuation(','))
    assert(!punctuation('a'))
    assert(!punctuation('+'))
  }
  
  test("forall checks all characters") {
    assert(forall(numeric, explode("123")))
    assert(forall(numeric, explode("0")))
    assert(!forall(numeric, explode("12a")))
    assert(!forall(numeric, explode("a23")))
    assert(forall(numeric, explode(""))) // vacuously true
  }
  
  test("isNumeric helper") {
    assert(isNumeric("123"))
    assert(isNumeric("0"))
    assert(isNumeric("999"))
    assert(!isNumeric("12a"))
    assert(!isNumeric("a23"))
    assert(!isNumeric(""))
    assert(!isNumeric(" "))
  }
  
  test("character classification on real examples") {
    // Variable name: foo_bar'
    val varChars = explode("foo_bar'")
    assert(forall(alphanumeric, varChars))
    
    // Operator: <==>
    val opChars = explode("<==>")
    assert(forall(symbolic, opChars))
    
    // Number: 42
    val numChars = explode("42")
    assert(forall(numeric, numChars))
  }
  
  test("mixed character types") {
    val mixed = explode("x+y")
    assert(alphanumeric('x'))
    assert(symbolic('+'))
    assert(alphanumeric('y'))
    assert(!forall(alphanumeric, mixed))
    assert(!forall(symbolic, mixed))
  }
