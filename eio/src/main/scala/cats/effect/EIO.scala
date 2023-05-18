/*
 * Copyright 2023 Iván Molina Rebolledo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package cats.effect

import cats.MonadError
import scala.util.Failure
import scala.util.Success

sealed private abstract class ErrorIO extends Throwable {
  type ErrorType
  def error: ErrorType
}

/** An IO monad that can fail with an error of type `E` or produce a value of
  * type `A`. This has an instance of `MonadError` for any `E`.
  */
sealed abstract class EIO[+E, +A] private (private val io: IO[A]) {

  def flatMap[E1 >: E, B](f: A => EIO[E1, B]): EIO[E1, B] =
    new EIO[E1, B](io.flatMap(a => f(a).io)) {}

  /** Alias for [[flatMap]] */
  def >>=[E1 >: E, B](f: A => EIO[E1, B]): EIO[E1, B] = flatMap(f)

  def map[B](f: A => B): EIO[E, B] = new EIO[E, B](io.map(f)) {}

  def getError: IO[Option[E]] = io
    .recover[Any] {
      case e: ErrorIO => Some(e.error)
      case _          => None
    }
    .asInstanceOf[IO[Option[E]]]

  private def setError[E2](e: E2): EIO[E2, A] =
    new EIO[E2, A](IO.raiseError[A](new Error {
      type ErrorType = E2
      def error: ErrorType = e
    })) {}

  /** Moves the error from the error channel to the value channel, making this
    * `EIO` infallible.
    */
  def either: EIO[Nothing, Either[E, A]] =
    EIO.unsafePureIOLift(io.map(Right(_)).recover { case e: ErrorIO =>
      Left(e.error.asInstanceOf[E])
    })

  /** Downgrades this `EIO` to an `IO` by returning the error and the value in
    * an `Either`.
    */
  def down: IO[Either[E, A]] = either.io

  /** Like [[map]], but for the error channel. */
  def mapError[E2](f: E => E2): EIO[E2, A] =
    new EIO[E2, A](io.recover { case e: ErrorIO =>
      throw new ErrorIO {
        type ErrorType = E2
        def error: ErrorType = f(e.error.asInstanceOf[E])
      }
    }) {}

  /** Like [[flatMap]], but for the error channel. */
  def flatMapError[A1 >: A, E2](f: E => EIO[E2, A1]): EIO[E2, A1] =
    new EIO[E2, A1](io.recoverWith { case e: ErrorIO =>
      f(e.error.asInstanceOf[E]).io
    }) {}
}

object EIO {
  /** Constructor for errors. Pure values, not exception-safe. */
  def error[E](e: E): EIO[E, Nothing] =
    new EIO[E, Nothing](IO.raiseError(new ErrorIO {
      type ErrorType = E
      def error: ErrorType = e
    })) {}

  /** Constructor for pure infallible values. These values are lazily evaluated,
    * and we assume that they are pure and cannot throw exceptions. For an
    * exception-safe version, use [[impure]] instead.
    */
  def apply[A](a: => A): EIO[Nothing, A] = new EIO[Nothing, A](IO(a)) {}

  /** Constructor for impure values that can throw exceptions. */
  def impure[A](a: => A): EIO[Throwable, A] = new EIO[Throwable, A](IO(a)) {}

  /** Constructor for impure values that can throw exceptions, with a custom
    * error handler, such that the exceptions are narrowed to a specific error
    * type.
    */
  def impure[E, A](errHandler: Throwable => E)(a: => A): EIO[E, A] =
    scala.util.Try(a) match {
      case Failure(exception) => error(errHandler(exception))
      case Success(value)     => apply(value)
    }

  /** Blocking IO. See [[IO.blocking]]. */
  def bloking[A](a: => A): EIO[Nothing, A] =
    new EIO[Nothing, A](IO.blocking(a)) {}

  /** Lifts an `IO` into an `EIO`. */
  def fromIO[A](io: IO[A]): EIO[Throwable, A] = new EIO[Throwable, A](io) {}

  /** Lifts an `Either` into an `EIO`. */
  def fromEither[E, A](either: Either[E, A]): EIO[E, A] = either match {
    case Left(e)  => error(e)
    case Right(a) => apply(a)
  }

  private def unsafePureIOLift[A](a: IO[A]): EIO[Nothing, A] =
    new EIO[Nothing, A](a) {}

  implicit def monadError[E]: MonadError[EIO[E, _], E] =
    new MonadError[EIO[E, _], E] {

      override def flatMap[A, B](fa: EIO[E, A])(f: A => EIO[E, B]): EIO[E, B] =
        fa >>= f

      override def pure[A](x: A): EIO[E, A] = EIO(x)

      override def handleErrorWith[A](fa: EIO[E, A])(
          f: E => EIO[E, A]
      ): EIO[E, A] = fa.flatMapError(f)

      override def raiseError[A](e: E): EIO[E, A] = EIO.error(e)

      override def tailRecM[A, B](
          a: A
      )(f: A => EIO[E, Either[A, B]]): EIO[E, B] =
        f(a) >>= {
          case Left(a)  => tailRecM(a)(f)
          case Right(b) => pure(b)
        }
    }
}




