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

object Containers {
  final case class Latitude(value: Double) extends AnyVal
  final case class Longitude(value: Double) extends AnyVal

  final case class GeoCoordinate(longitude: Longitude, latitude: Latitude)

  final case class GeoLocation(longitude: Longitude, latitude: Latitude, member: String)

  sealed trait GeoUnit
  case object m extends GeoUnit
  case object km extends GeoUnit
  case object mi extends GeoUnit
  case object ft extends GeoUnit

  case class GeoRadiusMember(
      member: Option[String],
      hash: Option[Long] = None,
      dist: Option[String] = None,
      coords: Option[(String, String)] = None
  )
}
