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
import scala.concurrent.duration._

import io.chrisdavenport.keypool._
import cats.effect._
import RedisClient._

case class RedisClientPool[F[+_]: Concurrent: ContextShift: Log: Timer]()

object RedisClientPool {
  def poolResource[F[+_]: Concurrent: ContextShift: Log: Timer, M <: Mode](
      clientMode: M = SINGLE
  ): Resource[F, KeyPool[F, URI, (RedisClient[F, M], F[Unit])]] =
    KeyPoolBuilder[F, URI, (RedisClient[F, M], F[Unit])](
      { uri: URI => RedisClient.make(uri, clientMode).allocated },
      { case (_, shutdown) => shutdown }
    ).withDefaultReuseState(Reusable.Reuse)
      .withIdleTimeAllowedInPool(Duration.Inf)
      .build
}
