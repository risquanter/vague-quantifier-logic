package fol.typed

import fol.error.QueryError
import fol.quantifier.VagueQuantifier
import fol.result.{EvaluationOutput, VagueQueryResult}
import fol.sampling.{HDRConfig, HDRSampler, ProportionEstimate, ProportionEstimator, SampleSizeCalculator, SamplingParams}

object TypedSemantics:

  private type Env = Map[String, Value]

  def evaluate(
    query: BoundQuery,
    model: RuntimeModel,
    answerTuple: Map[String, Value] = Map.empty,
    samplingParams: SamplingParams = SamplingParams.exact,
    hdrConfig: HDRConfig = HDRConfig.default
  ): Either[QueryError, EvaluationOutput[Value]] =
    val quantifier = VagueQuantifier.fromQuantifier(query.quantifier)
    for
      baseEnv <- validateAnswerTuple(query, answerTuple)
      rootDomain <- model.domains.get(query.variable.sort)
        .toRight(QueryError.DomainNotFoundError(
          typeName       = query.variable.sort.value,
          availableTypes = model.domains.keySet.map(_.value)
        ))
      rangeElements <- collectRangeElements(query, model, baseEnv, rootDomain)
      output <- evaluateOverRange(query, model, baseEnv, rangeElements, quantifier, samplingParams, hdrConfig)
    yield output

  private def validateAnswerTuple(
    query: BoundQuery,
    answerTuple: Map[String, Value]
  ): Either[QueryError, Env] =
    query.answerVars.foldLeft[Either[QueryError, Env]](Right(answerTuple)) { (acc, boundVar) =>
      acc.flatMap { env =>
        env.get(boundVar.name) match
          case None =>
            Left(QueryError.ValidationError(
              message = s"Missing answer tuple value for '${boundVar.name}'",
              field = "answer_tuple"
            ))
          case Some(v) if v.sort == boundVar.sort => Right(env)
          case Some(v) =>
            Left(QueryError.ValidationError(
              message = s"Type mismatch for answer variable '${boundVar.name}': expected ${boundVar.sort.value}, actual ${v.sort.value}",
              field = "answer_tuple"
            ))
      }
    }

  private def collectRangeElements(
    query: BoundQuery,
    model: RuntimeModel,
    baseEnv: Env,
    rootDomain: Set[Value]
  ): Either[QueryError, Set[Value]] =
    rootDomain.foldLeft[Either[QueryError, Set[Value]]](Right(Set.empty)) { (acc, candidate) =>
      for
        accepted <- acc
        inRange <- evalAtom(query.range, baseEnv + (query.variable.name -> candidate), model)
      yield if inRange then accepted + candidate else accepted
    }

  private def evaluateOverRange(
    query: BoundQuery,
    model: RuntimeModel,
    baseEnv: Env,
    rangeElements: Set[Value],
    quantifier: VagueQuantifier,
    samplingParams: SamplingParams,
    hdrConfig: HDRConfig
  ): Either[QueryError, EvaluationOutput[Value]] =
    if rangeElements.isEmpty then
      Right(EvaluationOutput(
        result = emptyResult(quantifier),
        rangeElements = Set.empty,
        satisfyingElements = Set.empty
      ))
    else
      val n = SampleSizeCalculator.calculateSampleSize(rangeElements.size, samplingParams)
      val sampler = HDRSampler[Value](hdrConfig)
      val sample = sampler.sample(rangeElements, n)
      for
        satisfying <- collectSatisfyingSample(query, model, baseEnv, sample)
      yield
        val estimate = ProportionEstimator.estimateFromCount(
          successes = satisfying.size,
          sampleSize = sample.size,
          params = samplingParams
        )
        val result = VagueQueryResult.fromEstimate(quantifier, estimate, rangeElements.size)
        EvaluationOutput(
          result = result,
          rangeElements = rangeElements,
          satisfyingElements = satisfying
        )

  private def collectSatisfyingSample(
    query: BoundQuery,
    model: RuntimeModel,
    baseEnv: Env,
    sample: Set[Value]
  ): Either[QueryError, Set[Value]] =
    sample.foldLeft[Either[QueryError, Set[Value]]](Right(Set.empty)) { (acc, candidate) =>
      for
        sat <- acc
        ok <- evalFormula(query.scope, baseEnv + (query.variable.name -> candidate), model)
      yield if ok then sat + candidate else sat
    }

  private def evalFormula(
    formula: BoundFormula,
    env: Env,
    model: RuntimeModel
  ): Either[QueryError, Boolean] =
    formula match
      case BoundFormula.True  => Right(true)
      case BoundFormula.False => Right(false)
      case BoundFormula.Atom(a) => evalAtom(a, env, model)
      case BoundFormula.Not(p) => evalFormula(p, env, model).map(v => !v)
      case BoundFormula.And(p, q) =>
        for
          left <- evalFormula(p, env, model)
          right <- evalFormula(q, env, model)
        yield left && right
      case BoundFormula.Or(p, q) =>
        for
          left <- evalFormula(p, env, model)
          right <- evalFormula(q, env, model)
        yield left || right
      case BoundFormula.Imp(p, q) =>
        for
          left <- evalFormula(p, env, model)
          right <- evalFormula(q, env, model)
        yield !left || right
      case BoundFormula.Iff(p, q) =>
        for
          left <- evalFormula(p, env, model)
          right <- evalFormula(q, env, model)
        yield left == right
      case BoundFormula.Forall(v, body) =>
        model.domains.get(v.sort) match
          case None => Left(QueryError.DomainNotFoundError(
            typeName       = v.sort.value,
            availableTypes = model.domains.keySet.map(_.value)
          ))
          case Some(domain) =>
            domain.foldLeft[Either[QueryError, Boolean]](Right(true)) { (acc, value) =>
              for
                allSoFar <- acc
                current <- if !allSoFar then Right(false) else evalFormula(body, env + (v.name -> value), model)
              yield allSoFar && current
            }
      case BoundFormula.Exists(v, body) =>
        model.domains.get(v.sort) match
          case None => Left(QueryError.DomainNotFoundError(
            typeName       = v.sort.value,
            availableTypes = model.domains.keySet.map(_.value)
          ))
          case Some(domain) =>
            domain.foldLeft[Either[QueryError, Boolean]](Right(false)) { (acc, value) =>
              for
                anySoFar <- acc
                current <- if anySoFar then Right(true) else evalFormula(body, env + (v.name -> value), model)
              yield anySoFar || current
            }

  private def evalAtom(atom: BoundAtom, env: Env, model: RuntimeModel): Either[QueryError, Boolean] =
    for
      args <- evalTerms(atom.args, env, model)
      value <- model.dispatcher.evalPredicate(atom.name, args).left.map(msg =>
        QueryError.EvaluationError(
          message = msg,
          phase = s"predicate:${atom.name.value}"
        )
      )
    yield value

  private def evalTerms(terms: List[BoundTerm], env: Env, model: RuntimeModel): Either[QueryError, List[Value]] =
    terms.foldLeft[Either[QueryError, List[Value]]](Right(Nil)) { (acc, term) =>
      for
        xs <- acc
        x <- evalTerm(term, env, model)
      yield xs :+ x
    }

  private def evalTerm(term: BoundTerm, env: Env, model: RuntimeModel): Either[QueryError, Value] =
    term match
      case BoundTerm.VarRef(v) =>
        env.get(v.name).toRight(
          QueryError.UnboundVariableError(v.name, env.keySet)
        ).flatMap { value =>
          if value.sort == v.sort then Right(value)
          else Left(QueryError.EvaluationError(
            message = s"Variable '${v.name}' has type ${value.sort.value}, expected ${v.sort.value}",
            phase = "typed_term"
          ))
        }

      case BoundTerm.ConstRef(name, sort) =>
        Right(Value(sort, name))

      case BoundTerm.FnApp(name, args, resultSort) =>
        for
          argValues <- evalTerms(args, env, model)
          value <- model.dispatcher.evalFunction(name, argValues).left.map(msg =>
            QueryError.EvaluationError(
              message = msg,
              phase = s"function:${name.value}"
            )
          )
          _ <- if value.sort == resultSort then Right(())
               else Left(QueryError.EvaluationError(
                 message = s"Function '${name.value}' returned type ${value.sort.value}, expected ${resultSort.value}",
                 phase = "typed_term"
               ))
        yield value

  private def emptyResult(quantifier: VagueQuantifier): VagueQueryResult =
    VagueQueryResult(
      satisfied = false,
      proportion = 0.0,
      confidenceInterval = (0.0, 1.0),
      quantifier = quantifier,
      domainSize = 0,
      sampleSize = 0,
      satisfyingCount = 0,
      estimate = ProportionEstimate.empty()
    )
