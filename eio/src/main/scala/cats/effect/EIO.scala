/*
 * Copyright 2023 Iván Molina Rebolledo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cats.effect

import cats.MonadError

sealed private trait ErrorIO private () extends Throwable {
  type ErrorType
  def error: ErrorType
}

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

  def either: EIO[Nothing, Either[E, A]] =
    EIO.unsafePureIOLift(io.map(Right(_)).recover { case e: ErrorIO =>
      Left(e.error.asInstanceOf[E])
    })

  def down: IO[Either[E, A]] = either.io

  def mapError[E2](f: E => E2): EIO[E2, A] =
    new EIO[E2, A](io.recover { case e: ErrorIO =>
      throw new ErrorIO {
        type ErrorType = E2
        def error: ErrorType = f(e.error.asInstanceOf[E])
      }
    }) {}

  def flatMapError[A1 >: A, E2](f: E => EIO[E2, A1]): EIO[E2, A1] =
    new EIO[E2, A1](io.recoverWith { case e: ErrorIO =>
      f(e.error.asInstanceOf[E]).io
    }) {}
}

object EIO {
  def error[E](e: E): EIO[E, Nothing] =
    new EIO[E, Nothing](IO.raiseError(new ErrorIO {
      type ErrorType = E
      def error: ErrorType = e
    })) {}

  def apply[A](a: => A): EIO[Nothing, A] = new EIO[Nothing, A](IO(a)) {}

  def bloking[A](a: => A): EIO[Nothing, A] =
    new EIO[Nothing, A](IO.blocking(a)) {}

  def fromIO[A](io: IO[A]): EIO[Throwable, A] = new EIO[Throwable, A](io) {}

  def fromEither[E, A](either: Either[E, A]): EIO[E, A] = either match {
    case Left(e)  => error(e)
    case Right(a) => apply(a)
  }

  private def unsafePureIOLift[A](a: IO[A]): EIO[Nothing, A] =
    new EIO[Nothing, A](a) {}

  implicit def monadError[E]: MonadError[[A] =>> EIO[E, A], E] =
    new MonadError[[A] =>> EIO[E, A], E] {

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

sealed abstract trait MRIO[-R, +E, +A] private (
    private val unMRIO: R => EIO[E, A]
) {
  def run(r: R): EIO[E, A] = unMRIO(r)

  def flatMap[R1 <: R, E1 >: E, B](f: A => MRIO[R1, E1, B]): MRIO[R1, E1, B] =
    new MRIO[R1, E1, B](r => unMRIO(r).flatMap(a => f(a).unMRIO(r))) {}

  def map[B](f: A => B): MRIO[R, E, B] = new MRIO[R, E, B](r =>
    unMRIO(r).map(f)
  ) {}

  /** Alias for [[flatMap]] */
  def >>=[R1 <: R, E1 >: E, B](f: A => MRIO[R1, E1, B]): MRIO[R1, E1, B] =
    flatMap(f)

  def flatMapError[R1 <: R, A1 >: A, E1](
      f: E => MRIO[R1, E1, A1]
  ): MRIO[R1, E1, A1] =
    new MRIO[R1, E1, A1](r => unMRIO(r).flatMapError(e => f(e).unMRIO(r))) {}

  def mapError[E1](f: E => E1): MRIO[R, E1, A] = new MRIO[R, E1, A](r =>
    unMRIO(r).mapError(f)
  ) {}

  def either: MRIO[R, Nothing, Either[E, A]] =
    new MRIO[R, Nothing, Either[E, A]](r => unMRIO(r).either) {}
}

object MRIO {
  def apply[A](a: => A): MRIO[Any, Nothing, A] =
    new MRIO[Any, Nothing, A](_ => EIO(a)) {}

  def blocking[A](a: => A): MRIO[Any, Nothing, A] =
    new MRIO[Any, Nothing, A](_ => EIO.bloking(a)) {}

  def error[E](e: E): MRIO[Any, E, Nothing] =
    new MRIO[Any, E, Nothing](_ => EIO.error(e)) {}

  def fromEIO[Any, E, A](eio: EIO[E, A]): MRIO[Any, E, A] =
    new MRIO[Any, E, A](r => eio) {}

  def fromIO[A](io: IO[A]): MRIO[Any, Throwable, A] =
    new MRIO[Any, Throwable, A](_ => EIO.fromIO(io)) {}

  def fromEither[E, A](either: Either[E, A]): MRIO[Any, E, A] =
    new MRIO[Any, E, A](_ => EIO.fromEither(either)) {}

  implicit def monadError[R, E]: MonadError[[A] =>> MRIO[R, E, A], E] =
    new MonadError[[A] =>> MRIO[R, E, A], E] {
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

@main def run(): Unit = {
  import cats.implicits.*
  val eio: EIO[String, Int] = EIO.error("error").handleErrorWith {
    case "error" => EIO(100)
  }
  val d = eio.mapError(_.length) >>= (x => EIO(x + 1000000))
  val io: IO[Either[Int, Int]] = d.down
  import cats.effect.unsafe.implicits.global 
  println(io.unsafeRunSync())
}

/*

README


*/