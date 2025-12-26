package vague.result

import vague.error.VagueError

/** Result type that's compatible with any effect system.
  * 
  * This is intentionally minimal to avoid committing to a specific effect system.
  * Users can easily convert to their preferred effect type:
  * 
  * **ZIO Users**:
  * {{{
  * import zio.*
  * 
  * extension [A](result: VagueResult[A])
  *   def toZIO: IO[VagueError, A] = 
  *     result.toEither match
  *       case Right(value) => ZIO.succeed(value)
  *       case Left(error) => ZIO.fail(error)
  * }}}
  * 
  * **Cats-Effect Users**:
  * {{{
  * import cats.effect.IO
  * import cats.syntax.either.*
  * 
  * extension [A](result: VagueResult[A])
  *   def toIO: IO[A] =
  *     result.toEither.fold(
  *       error => IO.raiseError(error.toThrowable),
  *       value => IO.pure(value)
  *     )
  * }}}
  * 
  * **Direct Either Usage**:
  * {{{
  * val result: VagueResult[VagueQuery] = VagueQueryParser.parseResult(input)
  * result.toEither.map(query => ...)
  * }}}
  */
sealed trait VagueResult[+A]:
  /** Convert to Either for functional composition */
  def toEither: Either[VagueError, A]
  
  /** Check if successful */
  def isSuccess: Boolean
  
  /** Check if failure */
  def isFailure: Boolean = !isSuccess
  
  /** Get value (throws if error) */
  def get: A
  
  /** Get error (throws if success) */
  def getError: VagueError
  
  /** Map over successful value */
  def map[B](f: A => B): VagueResult[B]
  
  /** FlatMap for composition */
  def flatMap[B](f: A => VagueResult[B]): VagueResult[B]
  
  /** Fold both success and failure cases */
  def fold[B](onError: VagueError => B, onSuccess: A => B): B
  
  /** Get value or default */
  def getOrElse[B >: A](default: => B): B
  
  /** Recover from error */
  def recover[B >: A](f: VagueError => B): VagueResult[B]
  
  /** Recover from error with another result */
  def recoverWith[B >: A](f: VagueError => VagueResult[B]): VagueResult[B]
  
  /** Convert to Option (discarding error) */
  def toOption: Option[A]

object VagueResult:
  
  /** Successful result */
  case class Success[+A](value: A) extends VagueResult[A]:
    def toEither: Either[VagueError, A] = Right(value)
    def isSuccess: Boolean = true
    def get: A = value
    def getError: VagueError = throw new NoSuchElementException("Success.getError")
    def map[B](f: A => B): VagueResult[B] = Success(f(value))
    def flatMap[B](f: A => VagueResult[B]): VagueResult[B] = f(value)
    def fold[B](onError: VagueError => B, onSuccess: A => B): B = onSuccess(value)
    def getOrElse[B >: A](default: => B): B = value
    def recover[B >: A](f: VagueError => B): VagueResult[B] = this
    def recoverWith[B >: A](f: VagueError => VagueResult[B]): VagueResult[B] = this
    def toOption: Option[A] = Some(value)
  
  /** Failed result */
  case class Failure(error: VagueError) extends VagueResult[Nothing]:
    def toEither: Either[VagueError, Nothing] = Left(error)
    def isSuccess: Boolean = false
    def get: Nothing = throw error.toThrowable
    def getError: VagueError = error
    def map[B](f: Nothing => B): VagueResult[B] = this
    def flatMap[B](f: Nothing => VagueResult[B]): VagueResult[B] = this
    def fold[B](onError: VagueError => B, onSuccess: Nothing => B): B = onError(error)
    def getOrElse[B](default: => B): B = default
    def recover[B](f: VagueError => B): VagueResult[B] = Success(f(error))
    def recoverWith[B](f: VagueError => VagueResult[B]): VagueResult[B] = f(error)
    def toOption: Option[Nothing] = None
  
  /** Smart constructors */
  def success[A](value: A): VagueResult[A] = Success(value)
  def failure[A](error: VagueError): VagueResult[A] = Failure(error)
  
  /** From Either */
  def fromEither[A](either: Either[VagueError, A]): VagueResult[A] =
    either.fold(Failure(_), Success(_))
  
  /** From Option */
  def fromOption[A](opt: Option[A], error: => VagueError): VagueResult[A] =
    opt.map(Success(_)).getOrElse(Failure(error))
  
  /** From Try */
  def fromTry[A](t: scala.util.Try[A], phase: String = "operation"): VagueResult[A] =
    t.fold(
      error => Failure(vague.error.ErrorOps.fromThrowable(error, phase)),
      value => Success(value)
    )
  
  /** Attempt a computation */
  def attempt[A](phase: String)(f: => A): VagueResult[A] =
    fromTry(scala.util.Try(f), phase)
  
  /** Validate a condition */
  def validate(condition: Boolean, error: => VagueError): VagueResult[Unit] =
    if condition then Success(()) else Failure(error)
  
  /** Traverse a list of results */
  def sequence[A](results: List[VagueResult[A]]): VagueResult[List[A]] =
    results.foldRight[VagueResult[List[A]]](Success(Nil)) { (result, acc) =>
      for {
        value <- result
        list <- acc
      } yield value :: list
    }
  
  /** Traverse with function */
  def traverse[A, B](list: List[A])(f: A => VagueResult[B]): VagueResult[List[B]] =
    sequence(list.map(f))

/** Extension methods for working with VagueResult */
object VagueResultSyntax:
  
  extension [A](either: Either[VagueError, A])
    /** Convert Either to VagueResult */
    def toResult: VagueResult[A] = VagueResult.fromEither(either)
  
  extension [A](option: Option[A])
    /** Convert Option to VagueResult with error */
    def toResult(error: => VagueError): VagueResult[A] = VagueResult.fromOption(option, error)
  
  extension [A](t: scala.util.Try[A])
    /** Convert Try to VagueResult */
    def toResult(phase: String = "operation"): VagueResult[A] = VagueResult.fromTry(t, phase)
