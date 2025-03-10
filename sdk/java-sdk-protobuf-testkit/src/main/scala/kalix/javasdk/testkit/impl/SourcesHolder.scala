/*
 * Copyright 2021 Lightbend Inc.
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

package kalix.javasdk.testkit.impl

import scala.collection.mutable.ArrayBuffer

import akka.actor.Actor
import akka.event.Logging
import com.google.protobuf.ByteString
import kalix.javasdk.testkit.KalixTestKit
import kalix.javasdk.testkit.impl.EventingTestKitImpl.RunningSourceProbe
import kalix.javasdk.testkit.impl.SourcesHolder.AddSource
import kalix.javasdk.testkit.impl.SourcesHolder.Publish
import kalix.javasdk.{ Metadata => SdkMetadata }
import org.slf4j.LoggerFactory

object SourcesHolder {

  case class AddSource(runningSourceProbe: RunningSourceProbe)
  case class Publish(message: ByteString, metadata: SdkMetadata)
}

class SourcesHolder extends Actor {

  private val log = LoggerFactory.getLogger(classOf[KalixTestKit])

  private val sources: ArrayBuffer[RunningSourceProbe] = ArrayBuffer.empty
  private val publishedMessages: ArrayBuffer[PublishedMessage] = ArrayBuffer.empty

  private case class PublishedMessage(message: ByteString, metadata: SdkMetadata)

  override def receive: Receive = {
    case AddSource(runningSourceProbe) =>
      if (publishedMessages.nonEmpty) {
        log.debug(
          s"Emitting ${publishedMessages.size} messages to new source ${runningSourceProbe.serviceName}/${runningSourceProbe.source.source}")
        publishedMessages.foreach { msg =>
          runningSourceProbe.emit(msg.message, msg.metadata)
        }
      }
      sources.addOne(runningSourceProbe)
      log.debug(s"Source added ${runningSourceProbe.serviceName}/${runningSourceProbe.source.source}")
      sender() ! "ok"
    case Publish(message, metadata) =>
      sources.foreach { source =>
        log.debug(s"Emitting message to source ${source.serviceName}/${source.source.source}")
        source.emit(message, metadata)
      }
      publishedMessages.addOne(PublishedMessage(message, metadata))
      sender() ! "ok"
  }
}
