package cats.effect

import cats.MonadError

/** A monadic ReaderT-like data type that can fail with an error of type `E`.
  * This is a wrapper around [EIO] that adds a type parameter `R` for the
  * environment. This has an instance of `MonadError`.
  */
sealed abstract class MRIO[-R, +E, +A] private (
    private val unMRIO: R => EIO[E, A]
) {

  /** Runs this `MRIO` with the given environment. */
  def run(r: R): EIO[E, A] = unMRIO(r)

  def flatMap[R1 <: R, E1 >: E, B](f: A => MRIO[R1, E1, B]): MRIO[R1, E1, B] =
    new MRIO[R1, E1, B](r => unMRIO(r).flatMap(a => f(a).unMRIO(r))) {}

  def map[B](f: A => B): MRIO[R, E, B] = new MRIO[R, E, B](r =>
    unMRIO(r).map(f)
  ) {}

  /** Alias for [[flatMap]] */
  def >>=[R1 <: R, E1 >: E, B](f: A => MRIO[R1, E1, B]): MRIO[R1, E1, B] =
    flatMap(f)

  /** Like [[flatMap]], but for the error channel. */
  def flatMapError[R1 <: R, A1 >: A, E1](
      f: E => MRIO[R1, E1, A1]
  ): MRIO[R1, E1, A1] =
    new MRIO[R1, E1, A1](r => unMRIO(r).flatMapError(e => f(e).unMRIO(r))) {}

  /** Like [[flatMapError]], but for the error channel. */
  def mapError[E1](f: E => E1): MRIO[R, E1, A] = new MRIO[R, E1, A](r =>
    unMRIO(r).mapError(f)
  ) {}

  /** Moves the error channel into the value channel. */
  def either: MRIO[R, Nothing, Either[E, A]] =
    new MRIO[R, Nothing, Either[E, A]](r => unMRIO(r).either) {}
}

object MRIO {

  /** Constructor for pure values that cannot throw exceptions. For an
    * exception-safe version, use [[impure]] instead.
    */
  def apply[A](a: => A): MRIO[Any, Nothing, A] =
    new MRIO[Any, Nothing, A](_ => EIO(a)) {}

  /** Constructor for values that can throw exceptions. */
  def impure[A](a: => A): MRIO[Any, Throwable, A] =
    new MRIO[Any, Throwable, A](_ => EIO.impure(a)) {}

  /** Constructor for impure values that can throw exceptions, with a custom
    * error handler that narrows the error type.
    */
  def impure[E, A](errHandler: Throwable => E)(a: => A): MRIO[Any, E, A] =
    new MRIO[Any, E, A](_ => EIO.impure(errHandler)(a)) {}

  /** Constructor for blocking IO. See [[IO.blocking]]. */
  def blocking[A](a: => A): MRIO[Any, Nothing, A] =
    new MRIO[Any, Nothing, A](_ => EIO.bloking(a)) {}

  /** Constructor for errors. Pure values, not exception-safe. */
  def error[E](e: E): MRIO[Any, E, Nothing] =
    new MRIO[Any, E, Nothing](_ => EIO.error(e)) {}

  /** Lifts an `EIO` into an `MRIO`. */
  def fromEIO[Any, E, A](eio: EIO[E, A]): MRIO[Any, E, A] =
    new MRIO[Any, E, A](r => eio) {}

  /** Lifts an `IO` into an `MRIO`. */
  def fromIO[A](io: IO[A]): MRIO[Any, Throwable, A] =
    new MRIO[Any, Throwable, A](_ => EIO.fromIO(io)) {}

  /** Lifts an `Either` into an `MRIO`. */
  def fromEither[E, A](either: Either[E, A]): MRIO[Any, E, A] =
    new MRIO[Any, E, A](_ => EIO.fromEither(either)) {}

  implicit def monadError[R, E]: MonadError[MRIO[R, E, _], E] =
    new MonadError[MRIO[R, E, _], E] {
      override def flatMap[A, B](fa: MRIO[R, E, A])(
          f: A => MRIO[R, E, B]
      ): MRIO[R, E, B] = fa >>= f

      override def pure[A](x: A): MRIO[R, E, A] = MRIO(x)

      override def handleErrorWith[A](fa: MRIO[R, E, A])(
          f: E => MRIO[R, E, A]
      ): MRIO[R, E, A] = fa.flatMapError(f)

      override def raiseError[A](e: E): MRIO[R, E, A] = MRIO.error(e)

      override def tailRecM[A, B](
          a: A
      )(f: A => MRIO[R, E, Either[A, B]]): MRIO[R, E, B] = f(a) >>= {
        case Left(a)  => tailRecM(a)(f)
        case Right(b) => pure(b)
      }
    }
}
