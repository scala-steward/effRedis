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
package algebra

import codecs.{ Format, Parse }

import scala.concurrent.duration.Duration

trait StringApi[F[+_]] {
  import StringApi._

  /**
    * sets the key with the specified value.
    * Starting with Redis 2.6.12 SET supports a set of options that modify its behavior:
    *
    * NX -- Only set the key if it does not already exist.
    * XX -- Only set the key if it already exist.
    * PX milliseconds -- Set the specified expire time, in milliseconds.
    */
  def set(key: Any, value: Any, whenSet: SetBehaviour = Always, expire: Duration = null, keepTTL: Boolean = false)(
      implicit format: Format
  ): F[Resp[Boolean]]

  /**
    * gets the value for the specified key.
    */
  def get[A](key: Any)(implicit format: Format, parse: Parse[A]): F[Resp[Option[A]]]

  /**
    * is an atomic set this value and return the old value command.
    */
  def getset[A](key: Any, value: Any)(implicit format: Format, parse: Parse[A]): F[Resp[Option[A]]]

  /**
    * sets the value for the specified key, only if the key is not there.
    */
  def setnx(key: Any, value: Any)(implicit format: Format): F[Resp[Boolean]]

  def setex(key: Any, expiry: Long, value: Any)(implicit format: Format): F[Resp[Boolean]]

  def psetex(key: Any, expiryInMillis: Long, value: Any)(implicit format: Format): F[Resp[Boolean]]

  /**
    * increments the specified key by 1
    */
  def incr(key: Any)(implicit format: Format): F[Resp[Long]]

  /**
    * increments the specified key by increment
    */
  def incrby(key: Any, increment: Long)(implicit format: Format): F[Resp[Long]]

  def incrbyfloat(key: Any, increment: Float)(implicit format: Format): F[Resp[Option[Float]]]

  /**
    * decrements the specified key by 1
    */
  def decr(key: Any)(implicit format: Format): F[Resp[Long]]

  /**
    * decrements the specified key by increment
    */
  def decrby(key: Any, increment: Long)(implicit format: Format): F[Resp[Long]]

  /**
    * get the values of all the specified keys.
    */
  def mget[A](key: Any, keys: Any*)(implicit format: Format, parse: Parse[A]): F[Resp[List[Option[A]]]]

  /**
    * set the respective key value pairs. Overwrite value if key exists
    */
  def mset(kvs: (Any, Any)*)(implicit format: Format): F[Resp[Boolean]]

  /**
    * set the respective key value pairs. Noop if any key exists
    */
  def msetnx(kvs: (Any, Any)*)(implicit format: Format): F[Resp[Boolean]]

  /**
    * SETRANGE key offset value
    * Overwrites part of the string stored at key, starting at the specified offset,
    * for the entire length of value.
    */
  def setrange(key: Any, offset: Int, value: Any)(implicit format: Format): F[Resp[Long]]

  /**
    * Returns the substring of the string value stored at key, determined by the offsets
    * start and end (both are inclusive).
    */
  def getrange[A](key: Any, start: Int, end: Int)(implicit format: Format, parse: Parse[A]): F[Resp[Option[A]]]

  /**
    * gets the length of the value associated with the key
    */
  def strlen(key: Any)(implicit format: Format): F[Resp[Long]]

  /**
    * appends the key value with the specified value.
    */
  def append(key: Any, value: Any)(implicit format: Format): F[Resp[Long]]

  /**
    * Returns the bit value at offset in the string value stored at key
    */
  def getbit(key: Any, offset: Int)(implicit format: Format): F[Resp[Long]]

  /**
    * Sets or clears the bit at offset in the string value stored at key
    */
  def setbit(key: Any, offset: Int, value: Any)(implicit format: Format): F[Resp[Long]]

  /**
    * Perform a bitwise operation between multiple keys (containing string values) and store the result in the destination key.
    */
  def bitop(op: String, destKey: Any, srcKeys: Any*)(implicit format: Format): F[Resp[Long]]

  /**
    * Count the number of set bits in the given key within the optional range
    */
  def bitcount(key: Any, range: Option[(Int, Int)] = None)(implicit format: Format): F[Resp[Long]]

}

object StringApi {

  sealed abstract class SetBehaviour(val command: List[String]) // singleton list
  case object NX extends SetBehaviour(List("NX"))
  case object XX extends SetBehaviour(List("XX"))
  case object Always extends SetBehaviour(List.empty)
  case object KeepTTL
}
