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
import RedisClient.DESC

import EffRedisFunSuite._

trait TestSortedSetScenarios {
  implicit def cs: ContextShift[IO]

  private def add(cmd: RedisClient[IO]): IO[Unit] =
    for {
      x <- cmd.zadd("hackers", 1965, "yukihiro matsumoto")
      _ <- IO(assert(getResp(x).get == 1))
      x <- cmd.zadd(
            "hackers",
            1953,
            "richard stallman",
            (1916, "claude shannon"),
            (1969, "linus torvalds"),
            (1940, "alan kay"),
            (1912, "alan turing")
          )
      _ <- IO(assert(getResp(x).get == 5))
    } yield ()

  def sortedSetsZrangeByLex(cmd: RedisClient[IO]): IO[Unit] = {
    import cmd._
    for {
      // should return the elements between min and max
      x <- zadd("hackers-joker", 0, "a", (0, "b"), (0, "c"), (0, "d"))
      _ <- IO(assert(getResp(x).get == 4))
      x <- zrangebylex("hackers-joker", "[a", "[b", None)
      _ <- IO(assert(getResp(x).get == List("a", "b")))

      // should return the elements between min and max with offset and count
      x <- zrangebylex("hackers-joker", "[a", "[c", Some((0, 1)))
      _ <- IO(assert(getResp(x).get == List("a")))
    } yield ()
  }

  def sortedSetsZAdd(client: RedisClient[IO]): IO[Unit] = {
    import client._
    for {
      // should add based on proper sorted set semantics
      _ <- add(client)
      x <- zadd("hackers", 1912, "alan turing")
      _ <- IO(assert(getResp(x).get == 0))
      x <- zcard("hackers")
      _ <- IO(assert(getResp(x).get == 6))
    } yield ()
  }

  def sortedSetsZRem(client: RedisClient[IO]): IO[Unit] = {
    import client._
    for {
      // should remove
      _ <- add(client)
      x <- zrem("hackers", "alan turing")
      _ <- IO(assert(getResp(x).get == 1))
      x <- zrem("hackers", "alan kay", "linus torvalds")
      _ <- IO(assert(getResp(x).get == 2))
      x <- zrem("hackers", "alan kay", "linus torvalds")
      _ <- IO(assert(getResp(x).get == 0))
    } yield ()
  }

  def sortedSetsZRange(client: RedisClient[IO]): IO[Unit] = {
    import client._
    for {
      _ <- add(client)
      x <- zrange("hackers")
      _ <- IO(assert(getRespListSize(x).get == 6))
      x <- zrangeWithScore("hackers")
      _ <- IO(assert(getRespListSize(x).get == 6))
    } yield ()
  }

  def sortedSetsZRank(client: RedisClient[IO]): IO[Unit] = {
    import client._
    for {
      _ <- add(client)
      x <- zrank("hackers", "yukihiro matsumoto")
      _ <- IO(assert(getResp(x).get == 4))
      x <- zrank("hackers", "yukihiro matsumoto", reverse = true)
      _ <- IO(assert(getResp(x).get == 1))
    } yield ()
  }

  def sortedSetsZRemRange(client: RedisClient[IO]): IO[Unit] = {
    import client._
    for {
      _ <- add(client)
      x <- zremrangebyrank("hackers", 0, 2)
      _ <- IO(assert(getResp(x).get == 3))
      _ <- flushdb
      _ <- add(client)
      x <- zremrangebyscore("hackers", 1912, 1940)
      _ <- IO(assert(getResp(x).get == 3))
      x <- zremrangebyscore("hackers", 0, 3)
      _ <- IO(assert(getResp(x).get == 0))
    } yield ()
  }

  def sortedSetsZUnion(client: RedisClient[IO]): IO[Unit] = {
    import client._
    for {
      _ <- zadd("hackers 1", 1965, "yukihiro matsumoto")
      _ <- zadd("hackers 1", 1953, "richard stallman")
      _ <- zadd("hackers 2", 1916, "claude shannon")
      _ <- zadd("hackers 2", 1969, "linus torvalds")
      _ <- zadd("hackers 3", 1940, "alan kay")
      _ <- zadd("hackers 4", 1912, "alan turing")

      // union with weight = 1
      x <- zunionstore("hackers", List("hackers 1", "hackers 2", "hackers 3", "hackers 4"))
      _ <- IO(assert(getResp(x).get == 6))
      x <- zcard("hackers")
      _ <- IO(assert(getResp(x).get == 6))

      x <- zrangeWithScore("hackers")
      _ <- IO(assert(getRespList[(String, Double)](x).get.map(_._2) == List(1912, 1916, 1940, 1953, 1965, 1969)))

      // union with modified weights
      x <- zunionstoreWeighted(
            "hackers weighted",
            Map("hackers 1" -> 1.0, "hackers 2" -> 2.0, "hackers 3" -> 3.0, "hackers 4" -> 4.0)
          )
      _ <- IO(assert(getResp(x).get == 6))
      x <- zrangeWithScore(
            "hackers weighted"
          )
      _ <- IO(assert(getRespList[(String, Double)](x).get.map(_._2.toInt) == List(1953, 1965, 3832, 3938, 5820, 7648)))
    } yield ()
  }

  def sortedSetsZInter(client: RedisClient[IO]): IO[Unit] = {
    import client._
    for {
      _ <- zadd("hackers", 1912, "alan turing")
      _ <- zadd("hackers", 1916, "claude shannon")
      _ <- zadd("hackers", 1927, "john mccarthy")
      _ <- zadd("hackers", 1940, "alan kay")
      _ <- zadd("hackers", 1953, "richard stallman")
      _ <- zadd("hackers", 1954, "larry wall")
      _ <- zadd("hackers", 1956, "guido van rossum")
      _ <- zadd("hackers", 1965, "paul graham")
      _ <- zadd("hackers", 1965, "yukihiro matsumoto")
      _ <- zadd("hackers", 1969, "linus torvalds")

      _ <- zadd("baby boomers", 1948, "phillip bobbit")
      _ <- zadd("baby boomers", 1953, "richard stallman")
      _ <- zadd("baby boomers", 1954, "cass sunstein")
      _ <- zadd("baby boomers", 1954, "larry wall")
      _ <- zadd("baby boomers", 1956, "guido van rossum")
      _ <- zadd("baby boomers", 1961, "lawrence lessig")
      _ <- zadd("baby boomers", 1965, "paul graham")
      _ <- zadd("baby boomers", 1965, "yukihiro matsumoto")

      // intersection with weight = 1
      x <- zinterstore("baby boomer hackers", List("hackers", "baby boomers"))
      _ <- IO(assert(getResp(x).get == 5))
      x <- zcard("baby boomer hackers")
      _ <- IO(assert(getResp(x).get == 5))

      x <- zrange("baby boomer hackers")
      _ <- IO(
            assert(
              getResp(x).get == List(
                    "richard stallman",
                    "larry wall",
                    "guido van rossum",
                    "paul graham",
                    "yukihiro matsumoto"
                  )
            )
          )

      // intersection with modified weights
      x <- zinterstoreWeighted("baby boomer hackers weighted", Map("hackers" -> 0.5, "baby boomers" -> 0.5))
      _ <- IO(assert(getResp(x).get == 5))
      x <- zrangeWithScore("baby boomer hackers weighted")
      _ <- IO(assert(getRespList[(String, Double)](x).get.map(_._2.toInt) == List(1953, 1954, 1956, 1965, 1965)))
    } yield ()
  }

  def sortedSetsZCount(client: RedisClient[IO]): IO[Unit] = {
    import client._
    for {
      _ <- add(client)
      x <- zcount("hackers", 1912, 1920)
      _ <- IO(assert(getResp(x).get == 2))
    } yield ()
  }

  def sortedSetsZRangeByScore(client: RedisClient[IO]): IO[Unit] = {
    import client._
    for {
      // should return the elements between min and max") {
      _ <- add(client)
      x <- zrangebyscore("hackers", 1940, true, 1969, true, None)
      _ <- IO(assert(getResp(x).get == List("alan kay", "richard stallman", "yukihiro matsumoto", "linus torvalds")))

      x <- zrangebyscore("hackers", 1940, true, 1969, true, None, DESC)
      _ <- IO(assert(getResp(x).get == List("linus torvalds", "yukihiro matsumoto", "richard stallman", "alan kay")))

      _ <- flushdb

      // should return the elements between min and max and allow offset and limit
      _ <- add(client)
      x <- zrangebyscore("hackers", 1940, true, 1969, true, Some((0, 2)))
      _ <- IO(assert(getResp(x).get == List("alan kay", "richard stallman")))

      x <- zrangebyscore("hackers", 1940, true, 1969, true, Some((0, 2)), DESC)
      _ <- IO(assert(getResp(x).get == List("linus torvalds", "yukihiro matsumoto")))

      x <- zrangebyscore("hackers", 1940, true, 1969, true, Some((3, 1)))
      _ <- IO(assert(getResp(x).get == List("linus torvalds")))

      x <- zrangebyscore("hackers", 1940, true, 1969, true, Some((3, 1)), DESC)
      _ <- IO(assert(getResp(x).get == List("alan kay")))

      x <- zrangebyscore("hackers", 1940, false, 1969, true, Some((0, 2)))
      _ <- IO(assert(getResp(x).get == List("richard stallman", "yukihiro matsumoto")))

      x <- zrangebyscore("hackers", 1940, true, 1969, false, Some((0, 2)), DESC)
      _ <- IO(assert(getResp(x).get == List("yukihiro matsumoto", "richard stallman")))
    } yield ()
  }

  def sortedSetsZRangeByScoreWithScore(client: RedisClient[IO]): IO[Unit] = {
    import client._
    for {
      // should return the elements between min and max") {
      _ <- add(client)
      x <- zrangebyscoreWithScore("hackers", 1940, true, 1969, true, None)
      _ <- IO(
            assert(
              getResp(x).get == List(
                    ("alan kay", 1940.0),
                    ("richard stallman", 1953.0),
                    ("yukihiro matsumoto", 1965.0),
                    ("linus torvalds", 1969.0)
                  )
            )
          )

      x <- zrangebyscoreWithScore("hackers", 1940, true, 1969, true, None, DESC)
      _ <- IO(
            assert(
              getResp(x).get == List(
                    ("linus torvalds", 1969.0),
                    ("yukihiro matsumoto", 1965.0),
                    ("richard stallman", 1953.0),
                    ("alan kay", 1940.0)
                  )
            )
          )

      x <- zrangebyscoreWithScore("hackers", 1940, true, 1969, true, Some((3, 1)))
      _ <- IO(assert(getResp(x).get == List(("linus torvalds", 1969.0))))

      x <- zrangebyscoreWithScore("hackers", 1940, true, 1969, true, Some((3, 1)), DESC)
      _ <- IO(assert(getResp(x).get == List(("alan kay", 1940.0))))
    } yield ()
  }
}