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

import cats.effect._

object Bench extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    RedisClient.makeWithURI[IO](new java.net.URI("http://localhost:6379")).use { cmd =>
      import cmd._

      val s = System.currentTimeMillis()
      for (i <- 0 to 10000) {
        val r = for {
          a <- set(s"name$i", s"debasish ghosh $i")
        } yield a
        r.unsafeRunSync()
      }
      val timeElapsedSet = System.currentTimeMillis() - s
      println(s"Time elapsed in setting 10000 keys = $timeElapsedSet")

      val t = System.currentTimeMillis()
      for (i <- 0 to 10000) {
        val r = for {
          a <- get(s"name$i")
        } yield a
        r.unsafeRunSync()
      }
      val timeElapsed = System.currentTimeMillis() - t
      println(s"Time elapsed in getting 10000 values = $timeElapsed")

      IO(ExitCode.Success)
    }
}
