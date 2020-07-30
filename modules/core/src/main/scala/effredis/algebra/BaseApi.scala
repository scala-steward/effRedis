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

package effredis.algebra

import effredis.serialization.{ Format, Parse }

trait BaseApi[F[_]] {

  /**
    * sort keys in a set, and optionally pull values for them
    */
  def sort[A](
      key: String,
      limit: Option[(Int, Int)] = None,
      desc: Boolean = false,
      alpha: Boolean = false,
      by: Option[String] = None,
      get: List[String] = Nil
  )(implicit format: Format, parse: Parse[A]): F[Option[List[Option[A]]]]

  /**
    * sort keys in a set, and stores result in the supplied key
    */
  def sortNStore[A](
      key: String,
      limit: Option[(Int, Int)] = None,
      desc: Boolean = false,
      alpha: Boolean = false,
      by: Option[String] = None,
      get: List[String] = Nil,
      storeAt: String
  )(implicit format: Format, parse: Parse[A]): F[Option[Long]]

  /**
    * returns all the keys matching the glob-style pattern.
    */
  def keys[A](pattern: Any = "*")(implicit format: Format, parse: Parse[A]): F[Option[List[Option[A]]]]

  /**
    * returns the current server time as a two items lists:
    * a Unix timestamp and the amount of microseconds already elapsed in the current second.
    */
  def time[A](implicit format: Format, parse: Parse[A]): F[Option[List[Option[A]]]]

  /**
    * returns a randomly selected key from the currently selected DB.
    */
  def randomkey[A](implicit parse: Parse[A]): F[Option[A]]

  /**
    * atomically renames the key oldkey to newkey.
    */
  def rename(oldkey: Any, newkey: Any)(implicit format: Format): F[Boolean]

  /**
    * rename oldkey into newkey but fails if the destination key newkey already exists.
    */
  def renamenx(oldkey: Any, newkey: Any)(implicit format: Format): F[Boolean]

  /**
    * returns the size of the db.
    */
  def dbsize: F[Option[Long]]

  /**
    * test if the specified key exists.
    */
  def exists(key: Any)(implicit format: Format): F[Boolean]

  /**
    * deletes the specified keys.
    */
  def del(key: Any, keys: Any*)(implicit format: Format): F[Option[Long]]

  /**
    * returns the type of the value stored at key in form of a string.
    */
  def getType(key: Any)(implicit format: Format): F[Option[String]]

  /**
    * sets the expire time (in sec.) for the specified key.
    */
  def expire(key: Any, ttl: Int)(implicit format: Format): F[Boolean]

  /**
    * sets the expire time (in milli sec.) for the specified key.
    */
  def pexpire(key: Any, ttlInMillis: Int)(implicit format: Format): F[Boolean]

  /**
    * sets the expire time for the specified key.
    */
  def expireat(key: Any, timestamp: Long)(implicit format: Format): F[Boolean]

  /**
    * sets the expire timestamp in millis for the specified key.
    */
  def pexpireat(key: Any, timestampInMillis: Long)(implicit format: Format): F[Boolean]

  /**
    * returns the remaining time to live of a key that has a timeout
    */
  def ttl(key: Any)(implicit format: Format): F[Option[Long]]

  /**
    * returns the remaining time to live of a key that has a timeout in millis
    */
  def pttl(key: Any)(implicit format: Format): F[Option[Long]]

  /**
    * selects the DB to connect, defaults to 0 (zero).
    */
  def select(index: Int): F[Boolean]

  /**
    * removes all the DB data.
    */
  def flushdb: F[Boolean]

  /**
    * removes data from all the DB's.
    */
  def flushall: F[Boolean]

  /**
    * Move the specified key from the currently selected DB to the specified destination DB.
    */
  def move(key: Any, db: Int)(implicit format: Format): F[Boolean]

  /**
    * exits the server.
    */
  def quit: F[Boolean]

  /**
    * auths with the server.
    */
  def auth(secret: Any)(implicit format: Format): F[Boolean]

  /**
    * Remove the existing timeout on key, turning the key from volatile (a key with an expire set)
    * to persistent (a key that will never expire as no timeout is associated).
    */
  def persist(key: Any)(implicit format: Format): F[Boolean]

  /**
    * Incrementally iterate the keys space (since 2.8)
    */
  def scan[A](cursor: Int, pattern: Any = "*", count: Int = 10)(
      implicit format: Format,
      parse: Parse[A]
  ): F[Option[(Option[Int], Option[List[Option[A]]])]]

  /**
    * ping
    */
  def ping: F[Option[String]]

  protected val pong: Option[String] = Some("PONG")

  /**
    * Marks the given keys to be watched for conditional execution of a transaction.
    */
  def watch(key: Any, keys: Any*)(implicit format: Format): F[Boolean]

  /**
    * Flushes all the previously watched keys for a transaction
    */
  def unwatch(): F[Boolean]

  /**
    * CONFIG GET
    */
  def getConfig(key: Any = "*")(implicit format: Format): F[Option[Map[String, Option[String]]]]

  /**
    * CONFIG SET
    */
  def setConfig(key: Any, value: Any)(implicit format: Format): F[Option[String]]

}
