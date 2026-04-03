package fol.typed

import fol.logic.ParsedQuery
import logic.{FOL, Formula, Term}
import LiteralValue.*

object QueryBinder:

  private type Env = Map[String, TypeId]

  def bind(query: ParsedQuery, catalog: TypeCatalog): Either[List[TypeCheckError], BoundQuery] =
    for
      rangeResult <- bindAtom(query.range, Map.empty, catalog)
      (boundRange, envAfterRange) = rangeResult
      quantifiedVarSort <- envAfterRange.get(query.variable).toRight(List(TypeCheckError.UnconstrainedVar(query.variable)))
      _ <- if catalog.domainTypes.contains(quantifiedVarSort) then Right(())
           else Left(List(TypeCheckError.TypeNotQuantifiable(quantifiedVarSort.value)))
      scopeResult <- bindFormula(query.scope, envAfterRange, catalog)
      (boundScope, envAfterScope) = scopeResult
      boundAnswers <- bindAnswerVars(query.answerVars, envAfterScope)
    yield
      BoundQuery(
        quantifier = query.quantifier,
        variable = BoundVar(query.variable, quantifiedVarSort),
        range = boundRange,
        scope = boundScope,
        answerVars = boundAnswers
      )

  private def bindAnswerVars(names: List[String], env: Env): Either[List[TypeCheckError], List[BoundVar]] =
    names.foldLeft[Either[List[TypeCheckError], List[BoundVar]]](Right(Nil)) { (acc, name) =>
      for
        xs <- acc
        s <- env.get(name).toRight(List(TypeCheckError.UnboundAnswerVar(name)))
      yield xs :+ BoundVar(name, s)
    }

  private def bindFormula(formula: Formula[FOL], env: Env, catalog: TypeCatalog): Either[List[TypeCheckError], (BoundFormula, Env)] =
    formula match
      case Formula.True  => Right((BoundFormula.True, env))
      case Formula.False => Right((BoundFormula.False, env))
      case Formula.Atom(a) =>
        bindAtom(a, env, catalog).map((atom, newEnv) => (BoundFormula.Atom(atom), newEnv))
      case Formula.Not(p) =>
        bindFormula(p, env, catalog).map((bp, e) => (BoundFormula.Not(bp), e))
      case Formula.And(p, q) => bindBinary(p, q, env, catalog, BoundFormula.And.apply)
      case Formula.Or(p, q)  => bindBinary(p, q, env, catalog, BoundFormula.Or.apply)
      case Formula.Imp(p, q) => bindBinary(p, q, env, catalog, BoundFormula.Imp.apply)
      case Formula.Iff(p, q) => bindBinary(p, q, env, catalog, BoundFormula.Iff.apply)
      case Formula.Forall(x, p) => bindQuantified(x, p, env, catalog, BoundFormula.Forall.apply)
      case Formula.Exists(x, p) => bindQuantified(x, p, env, catalog, BoundFormula.Exists.apply)

  private def bindBinary(
    p: Formula[FOL],
    q: Formula[FOL],
    env: Env,
    catalog: TypeCatalog,
    mk: (BoundFormula, BoundFormula) => BoundFormula
  ): Either[List[TypeCheckError], (BoundFormula, Env)] =
    for
      left  <- bindFormula(p, env, catalog)
      right <- bindFormula(q, env, catalog)
      mergedEnv <- mergeEnvs(left._2, right._2)
    yield (mk(left._1, right._1), mergedEnv)

  private def bindQuantified(
    name: String,
    body: Formula[FOL],
    env: Env,
    catalog: TypeCatalog,
    mk: (BoundVar, BoundFormula) => BoundFormula
  ): Either[List[TypeCheckError], (BoundFormula, Env)] =
    val scopedEnv = env - name
    bindFormula(body, scopedEnv, catalog).flatMap { case (boundBody, envAfterBody) =>
      envAfterBody.get(name) match
        case None => Left(List(TypeCheckError.UnconstrainedVar(name)))
        case Some(sort) =>
          if !catalog.domainTypes.contains(sort) then
            Left(List(TypeCheckError.TypeNotQuantifiable(sort.value)))
          else
            val escaped = envAfterBody - name
            val outerMerged = env ++ escaped
            Right((mk(BoundVar(name, sort), boundBody), outerMerged))
    }

  private def bindAtom(atom: FOL, env: Env, catalog: TypeCatalog): Either[List[TypeCheckError], (BoundAtom, Env)] =
    val symbol = SymbolName(atom.predicate)
    catalog.predicates.get(symbol) match
      case None => Left(List(TypeCheckError.UnknownPredicate(atom.predicate)))
      case Some(sig) =>
        if sig.params.length != atom.terms.length then
          Left(List(TypeCheckError.ArityMismatch(atom.predicate, sig.params.length, atom.terms.length)))
        else
          bindTermsExpected(atom.terms, sig.params, env, catalog).map { case (args, newEnv) =>
            (BoundAtom(symbol, args), newEnv)
          }

  private def bindTermsExpected(
    terms: List[Term],
    expected: List[TypeId],
    env: Env,
    catalog: TypeCatalog
  ): Either[List[TypeCheckError], (List[BoundTerm], Env)] =
    val zipped = terms.zip(expected)
    zipped.foldLeft[Either[List[TypeCheckError], (List[BoundTerm], Env)]](Right((Nil, env))) {
      case (acc, (term, sort)) =>
        for
          state <- acc
          bound <- bindTermExpected(term, sort, state._2, catalog)
        yield (state._1 :+ bound._1, bound._2)
    }

  private def bindTermExpected(
    term: Term,
    expected: TypeId,
    env: Env,
    catalog: TypeCatalog
  ): Either[List[TypeCheckError], (BoundTerm, Env)] =
    term match
      case Term.Var(name) =>
        env.get(name) match
          case None =>
            val newEnv = env + (name -> expected)
            Right((BoundTerm.VarRef(BoundVar(name, expected)), newEnv))
          case Some(actual) if actual == expected =>
            Right((BoundTerm.VarRef(BoundVar(name, actual)), env))
          case Some(actual) =>
            Left(List(TypeCheckError.TypeMismatch(expected, actual, s"variable '$name'")))

      case Term.Const(name) =>
        catalog.constants.get(name) match
          case Some(actual) if actual == expected =>
            // Named constant: no LiteralValue — stored as TextLiteral of source text.
            // See TODOS.md T-002 for the open design question on named constants.
            Right((BoundTerm.ConstRef(name, expected, TextLiteral(name)), env))
          case Some(actual) =>
            Left(List(TypeCheckError.TypeMismatch(expected, actual, s"constant '$name'")))
          case None =>
            catalog.literalValidators.get(expected).flatMap(v => v(name)) match
              case Some(literal) => Right((BoundTerm.ConstRef(name, expected, literal), env))
              case None          => Left(List(TypeCheckError.UnknownConstantOrLiteral(name)))

      case Term.Fn(name, args) =>
        val symbol = SymbolName(name)
        catalog.functions.get(symbol) match
          case None => Left(List(TypeCheckError.UnknownFunction(name)))
          case Some(sig) =>
            if sig.params.length != args.length then
              Left(List(TypeCheckError.ArityMismatch(name, sig.params.length, args.length)))
            else
              for
                boundArgs <- bindTermsExpected(args, sig.params, env, catalog)
                _ <- if sig.returns == expected then Right(())
                     else Left(List(TypeCheckError.TypeMismatch(expected, sig.returns, s"function '$name' return")))
              yield (BoundTerm.FnApp(symbol, boundArgs._1, sig.returns), boundArgs._2)

  private def mergeEnvs(left: Env, right: Env): Either[List[TypeCheckError], Env] =
    val keys = left.keySet union right.keySet
    keys.foldLeft[Either[List[TypeCheckError], Env]](Right(Map.empty)) { (acc, k) =>
      acc.flatMap { merged =>
        (left.get(k), right.get(k)) match
          case (Some(a), Some(b)) if a != b => Left(List(TypeCheckError.ConflictingTypes(k, a, b)))
          case (Some(a), Some(_))           => Right(merged + (k -> a))
          case (Some(a), None)              => Right(merged + (k -> a))
          case (None, Some(b))              => Right(merged + (k -> b))
          case (None, None)                 => Right(merged)
      }
    }
