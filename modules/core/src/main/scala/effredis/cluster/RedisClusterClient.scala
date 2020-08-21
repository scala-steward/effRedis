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

package effredis.cluster

import java.net.URI
import scala.collection.immutable.BitSet

import effredis.{ Error, Log, RedisBlocker, RedisClient, Value }
import util.ClusterUtils
import cats.effect._
import cats.implicits._

final case class RedisClusterClient[F[+_]: Concurrent: ContextShift: Log] private (
    seedURI: URI,
    topology: List[RedisClusterNode[F]]
)

object RedisClusterClient {
  private[effredis] def acquireAndRelease[F[+_]: Concurrent: ContextShift: Log](
      seedURI: URI,
      blocker: Blocker
  ): Resource[F, RedisClusterClient[F]] = {

    def toRedisClusterNode(
        ts: ClusterUtils.TopologyString
    ): RedisClusterNode[F] = {
      import ts._
      RedisClusterNode[F](
        new RedisClient(new java.net.URI(uri), blocker),
        nodeId,
        if (linkState == "connected") true else false,
        replicaUpstreamNodeId,
        pingTimestamp,
        pongTimestamp,
        configEpoch,
        slots.map(ClusterUtils.parseSlotString).getOrElse(BitSet.empty),
        ClusterUtils.parseNodeFlags(nodeFlags)
      )
    }

    val acquire: F[RedisClusterClient[F]] = {
      F.info(s"Acquiring cluster client with sample seed URI $seedURI") *> {

        val topology =
          RedisClient.make(seedURI).use { cl =>
            cl.clusterNodes.flatMap {
              case Value(Some(nodeInfo)) => {
                ClusterUtils.fromRedisServer(nodeInfo) match {
                  case Right(value) => value.toList.map(toRedisClusterNode).pure[F]
                  case Left(err) =>
                    F.error(s"Error fetching topology $err") *> List.empty[RedisClusterNode[F]].pure[F]
                }
              }
              case Error(err) =>
                F.error(s"Error fetching topology $err") *> List.empty[RedisClusterNode[F]].pure[F]
              case err =>
                F.error(s"Error fetching topology $err") *> List.empty[RedisClusterNode[F]].pure[F]
            }
          }
        blocker.blockOn {
          topology.map(new RedisClusterClient(seedURI, _))
        }
      }
    }

    val release: RedisClusterClient[F] => F[Unit] = { clusterClient =>
      F.info(s"Releasing cluster client with topology of ${clusterClient.topology.size} members") *> {
        clusterClient.topology.foreach(_.client.disconnect)
        ().pure[F]
      }
    }

    Resource.make(acquire)(release)
  }

  def make[F[+_]: ContextShift: Concurrent: Log](
      uri: URI
  ): Resource[F, RedisClusterClient[F]] =
    for {
      blocker <- RedisBlocker.make
      client <- acquireAndRelease(uri, blocker)
    } yield client
}
