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
import cats.effect._
import cats.implicits._
import log4cats._

object Transaction extends LoggerIOApp {
  override def run(args: List[String]): IO[ExitCode] =
    RedisClient.transact[IO](new URI("http://localhost:6379")).use { cli =>
      import cli._

      val r1 = RedisClient.transaction(cli) { () =>
        List(
          set("k1", "v1"),
          set("k2", 100),
          incrby("k2", 12),
          get("k1"),
          get("k2"),
          lpop("k1")
          // discard,
          // get("k2"),
        ).sequence
      }

      r1.unsafeRunSync() match {

        case Value(ls)        => ls.foreach(println)
        case TxnDiscarded(cs) => println(s"Transaction discarded $cs")
        case Error(err)       => println(s"oops! $err")
        case err              => println(err)
      }
      IO(ExitCode.Success)
    }
}
