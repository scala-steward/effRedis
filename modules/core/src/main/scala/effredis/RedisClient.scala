/*
 * Copyright 2020 Debasish Ghosh
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

package effredis

import java.net.URI
import javax.net.ssl.SSLContext

import shapeless.HList

import cats.effect._
import cats.implicits._
// import enumeratum._

object RedisClient {
  sealed trait SortOrder
  case object ASC extends SortOrder
  case object DESC extends SortOrder

  sealed trait Aggregate
  case object SUM extends Aggregate
  case object MIN extends Aggregate
  case object MAX extends Aggregate

  sealed trait Mode
  case object SINGLE extends Mode
  case object TRANSACT extends Mode
  case object PIPE extends Mode

  /*
  sealed abstract class Mode(override val entryName: String) extends EnumEntry
  object Mode extends Enum[Mode] {
    case object SINGLE extends Mode("single")
    case object TRANSACT extends Mode("transact")
    case object PIPE extends Mode("pipe")

    val values = findValues
  }
   */

  private[effredis] def extractDatabaseNumber(connectionUri: java.net.URI): Int =
    Option(connectionUri.getPath)
      .map(path =>
        if (path.isEmpty) 0
        else Integer.parseInt(path.tail)
      )
      .getOrElse(0)

  private[effredis] def acquireAndRelease[F[+_]: Concurrent: ContextShift: Log, M <: Mode](
      uri: URI,
      blocker: Blocker,
      mode: M
  ): Resource[F, RedisClient[F, M]] = {

    val acquire: F[RedisClient[F, M]] = {
      F.debug(s"Acquiring client for uri $uri $blocker") *>
        blocker.blockOn {
          F.delay(new RedisClient[F, M](uri, blocker, mode))
        }
    }
    val release: RedisClient[F, M] => F[Unit] = { c =>
      F.debug(s"Releasing client for uri $uri") *> {
        F.delay(c.close())
      }
    }

    Resource.make(acquire)(release)
  }

  /**
    * Make a single normal connection
    *
    * @param uri
    * @return
    */
  def single[F[+_]: ContextShift: Concurrent: Log](
      uri: URI
  ): Resource[F, RedisClient[F, SINGLE.type]] =
    for {
      blocker <- RedisBlocker.make
      client <- acquireAndRelease(uri, blocker, SINGLE)
    } yield client

  /**
    * Make a connection for transaction
    *
    * @param uri
    * @return
    */
  def transact[F[+_]: ContextShift: Concurrent: Log](
      uri: URI
  ): Resource[F, RedisClient[F, TRANSACT.type]] =
    for {
      blocker <- RedisBlocker.make
      client <- acquireAndRelease(uri, blocker, TRANSACT)
    } yield client

  /**
    * Make a connection for pipelines
    *
    * @param uri
    * @return
    */
  def pipe[F[+_]: ContextShift: Concurrent: Log](
      uri: URI
  ): Resource[F, RedisClient[F, PIPE.type]] =
    for {
      blocker <- RedisBlocker.make
      client <- acquireAndRelease(uri, blocker, PIPE)
    } yield client

  def pipeline[F[+_]: Concurrent: ContextShift: Log](
      client: RedisClient[F, PIPE.type]
  )(f: () => Any): F[Resp[Option[List[Any]]]] = {
    implicit val b = client.blocker
    try {
      val _ = f()
      client
        .send(client.commandBuffer.toString, true)(Some(client.handlers.map(_._2).map(_()).toList))
    } catch {
      case e: Exception => Error(e.getMessage).pure[F]
    }
  }

  def hpipeline[F[+_]: Concurrent: ContextShift: Log, In <: HList](
      client: RedisClient[F, PIPE.type]
  )(commands: () => In): F[Resp[Option[List[Any]]]] = {
    implicit val b = client.blocker
    try {
      val _ = commands()
      client
        .send(client.commandBuffer.toString, true)(Option(client.handlers.map(_._2).map(_()).toList))
        .flatTap { r =>
          client.handlers = Vector.empty
          client.commandBuffer = new StringBuffer
          F.delay(r)
        }
    } catch {
      case e: Exception => F.delay(Error(e.getMessage))
    }
  }

  def htransaction[F[+_]: Concurrent: ContextShift: Log, In <: HList](
      client: RedisClient[F, TRANSACT.type]
  )(commands: () => In): F[Resp[Option[List[Any]]]] =
    client.multi.flatMap { _ =>
      try {
        val _ = commands()

        if (client.handlers
              .map(_._1)
              .filter(_ == "DISCARD")
              .isEmpty) {

          // exec only if no discard
          F.debug(s"Executing transaction ..") >> {
            try {
              client.exec(client.handlers.map(_._2)).flatTap { _ =>
                client.handlers = Vector.empty
                ().pure[F]
              }
            } catch {
              case e: Exception =>
                Error(e.getMessage()).pure[F]
            }
          }

        } else {
          // no exec if discard
          F.debug(s"Got DISCARD .. discarding transaction") >> {
            TxnDiscarded(client.handlers).pure[F].flatTap { r =>
              client.handlers = Vector.empty
              r.pure[F]
            }
          }
        }
      } catch {
        case e: Exception =>
          Error(e.getMessage()).pure[F]
      }
    }

  def transaction[F[+_]: Concurrent: ContextShift: Log](
      client: RedisClient[F, TRANSACT.type]
  )(f: () => Any): F[Resp[Option[List[Any]]]] = {

    implicit val b = client.blocker
    import client._

    send("MULTI")(asString).flatMap { _ =>
      try {
        val _ = f()

        // no exec if discard
        if (client.handlers
              .map(_._1)
              .filter(_ == "DISCARD")
              .isEmpty) {

          send("EXEC")(asExec(client.handlers.map(_._2))).flatTap { _ =>
            client.handlers = Vector.empty
            ().pure[F]
          }
        } else {
          TxnDiscarded(client.handlers).pure[F].flatTap { r =>
            client.handlers = Vector.empty
            r.pure[F]
          }
        }

      } catch {
        case e: Exception =>
          Error(e.getMessage()).pure[F]
      }
    }
  }
}

private[effredis] class RedisClient[F[+_]: Concurrent: ContextShift: Log, M <: RedisClient.Mode](
    override val host: String,
    override val port: Int,
    override val database: Int = 0,
    override val secret: Option[Any] = None,
    override val timeout: Int = 0,
    override val sslContext: Option[SSLContext] = None,
    val blocker: Blocker,
    val mode: M
) extends RedisCommand[F, M](mode) {

  def conc: cats.effect.Concurrent[F]  = implicitly[Concurrent[F]]
  def ctx: cats.effect.ContextShift[F] = implicitly[ContextShift[F]]
  def l: Log[F]                        = implicitly[Log[F]]

  def this(b: Blocker, m: M) = this("localhost", 6379, blocker = b, mode = m)
  def this(connectionUri: java.net.URI, b: Blocker, m: M) = this(
    host = connectionUri.getHost,
    port = connectionUri.getPort,
    database = RedisClient.extractDatabaseNumber(connectionUri),
    secret = Option(connectionUri.getUserInfo)
      .flatMap(_.split(':') match {
        case Array(_, password, _*) => Some(password)
        case _                      => None
      }),
    blocker = b,
    mode = m
  )
  override def toString: String = host + ":" + String.valueOf(port) + "/" + database
  override def close(): Unit    = { disconnect; () }
}
