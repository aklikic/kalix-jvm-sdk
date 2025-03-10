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

package kalix.scalasdk.testkit

import akka.annotation.{ ApiMayChange, InternalApi }
import com.google.protobuf.ByteString
import kalix.javasdk.impl.MessageCodec
import kalix.javasdk.testkit.{ EventingTestKit => JEventingTestKit }
import kalix.scalasdk.Metadata
import kalix.scalasdk.testkit.impl.MessageImpl
import kalix.scalasdk.testkit.impl.TopicImpl
import scalapb.GeneratedMessage
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

import kalix.scalasdk.testkit.impl.IncomingMessagesImpl
import kalix.scalasdk.testkit.impl.OutgoingMessagesImpl

/**
 * Testkit utility to mock incoming message flow. Useful when doing integration tests for services that do eventing.in.
 */
@ApiMayChange
trait IncomingMessages {

  /**
   * Simulate the publishing of a raw message.
   *
   * @param message
   *   raw bytestring to be published
   */
  def publish(message: ByteString): Unit

  /**
   * Simulate the publishing of a raw message.
   *
   * @param message
   *   raw bytestring to be published
   * @param metadata
   *   associated with the message
   */
  def publish(message: ByteString, metadata: Metadata): Unit

  /**
   * Simulate the publishing of a message.
   *
   * @param message
   *   to be published in the topic
   */
  def publish[T <: GeneratedMessage](message: Message[T]): Unit

  /**
   * Simulate the publishing of a message.
   *
   * @param message
   *   to be published
   * @param subject
   *   to identify the entity
   */
  def publish[T <: GeneratedMessage](message: T, subject: String): Unit

  /**
   * Publish multiple messages.
   *
   * @param messages
   *   to be published
   */
  def publish[T <: GeneratedMessage](messages: List[Message[T]]): Unit

  /**
   * Publish a predefined delete message. Supported only in case of ValueEntity incoming message flow.
   *
   * @param subject
   *   to identify the entity
   */
  def publishDelete(subject: String)
}

@InternalApi
object IncomingMessages {
  def apply(delegate: JEventingTestKit.IncomingMessages): IncomingMessages = IncomingMessagesImpl(delegate)
}

/**
 * Allows to assert published messages for the purposes of testing outgoing message flow.
 */
@ApiMayChange
trait OutgoingMessages {

  /**
   * Waits for predefined amount of time (see [[kalix.javasdk.testkit.impl.OutgoingMessagesImpl.DefaultTimeout]]). If a
   * message arrives in the meantime or has arrived before but was not consumed, the test fails.
   */
  def expectNone(): Unit

  /**
   * Waits for given amount of time. If a message arrives in the meantime or has arrived before but was not consumed,
   * the test fails.
   *
   * @param timeout
   *   amount of time to wait for a message
   */
  def expectNone(timeout: FiniteDuration): Unit

  /**
   * Waits and returns the next unread message. Note the message might have been received before this method was called.
   * If no message is received, a timeout exception is thrown.
   *
   * @return
   *   a Message with a ByteString payload
   */
  def expectOneRaw(): Message[ByteString]

  /**
   * Waits and returns the next unread message. Note the message might have been received before this method was called.
   * If no message is received, a timeout exception is thrown.
   *
   * @param timeout
   *   amount of time to wait for a message
   * @return
   *   a Message with a ByteString payload
   */
  def expectOneRaw(timeout: FiniteDuration): Message[ByteString]

  /**
   * Waits and returns the next unread message. Note the message might have been received before this method was called.
   * If no message is received, a timeout exception is thrown.
   *
   * @return
   *   message including ByteString payload and metadata
   */
  def expectOne(): Message[_]

  /**
   * Waits for a specific amount and returns the next unread message. Note the message might have been received before
   * this method was called. If no message is received, a timeout exception is thrown.
   *
   * @param timeout
   *   amount of time to wait for a message if it was not received already
   * @return
   *   message including ByteString payload and metadata
   */
  def expectOne(timeout: FiniteDuration): Message[_]

  /**
   * Waits and returns the next unread message and automatically parses and casts it to the specified given type.
   *
   * @param t
   *   class tag used to cast the deserialized object
   * @tparam T
   *   a given domain type
   * @return
   *   a Message of type T
   */
  def expectOneTyped[T <: GeneratedMessage](implicit t: ClassTag[T]): Message[T]

  /**
   * Waits and returns the next unread message and automatically parses and casts it to the specified given type. Note
   * the message might have been received before this method was called. If no message is received, a timeout exception
   * is thrown.
   *
   * @param timeout
   *   amount of time to wait for a message if it was not received already
   * @tparam T
   *   a given domain type
   * @return
   *   a Message of type T
   */
  def expectOneTyped[T <: GeneratedMessage](timeout: FiniteDuration)(implicit t: ClassTag[T]): Message[T]

  /**
   * Waits for a default amount of time before returning all unread messages. If no message is received, a timeout
   * exception is thrown.
   *
   * @return
   *   collection of messages, each message including the deserialized payload object and metadata
   */
  def expectN(): Seq[Message[_]]

  /**
   * Waits for a given amount of unread messages to be received before returning. If no message is received, a timeout
   * exception is thrown.
   *
   * @param total
   *   number of messages to wait for before returning
   * @return
   *   collection of messages, each message including the deserialized payload object and metadata
   */
  def expectN(total: Int): Seq[Message[_]]

  /**
   * Waits for a given amount of unread messages to be received before returning up to a given timeout. If no message is
   * received, a timeout exception is thrown.
   *
   * @param total
   *   number of messages to wait for before returning
   * @param timeout
   *   maximum amount of time to wait for the messages
   * @return
   *   collection of messages, each message including the deserialized payload object and metadata
   */
  def expectN(total: Int, timeout: FiniteDuration): Seq[Message[_]]

  /**
   * Clear outgoing messages. Any existing messages are not considered on subsequent expect call.
   *
   * @return
   *   the list of the unread messages when cleared.
   */
  def clear(): Seq[Message[_]]
}

@InternalApi
object OutgoingMessages {
  def apply(delegate: JEventingTestKit.OutgoingMessages, codec: MessageCodec): OutgoingMessages =
    OutgoingMessagesImpl(delegate, codec)
}

/**
 * Testkit utility to mock broker's topic. Useful when doing integration tests for services that do eventing (in or out)
 * to a broker's topic.
 *
 * <p><b>Note: </b> messages written to the topic with this utility are not readable with the expect* methods, unless
 * they have been properly forwarded through an eventing.out flow to the same topic.
 */
@ApiMayChange
trait Topic {

  /**
   * Waits for predefined amount of time (see [[kalix.javasdk.testkit.impl.TopicImpl.DefaultTimeout]]). If a message
   * arrives in the meantime or has arrived before but was not consumed, the test fails.
   */
  def expectNone(): Unit

  /**
   * Waits for given amount of time. If a message arrives in the meantime or has arrived before but was not consumed,
   * the test fails.
   *
   * @param timeout
   *   amount of time to wait for a message
   */
  def expectNone(timeout: FiniteDuration): Unit

  /**
   * Waits and returns the next unread message on this topic. Note the message might have been received before this
   * method was called. If no message is received, a timeout exception is thrown.
   *
   * @return
   *   a Message with a ByteString payload
   */
  def expectOneRaw(): Message[ByteString]

  /**
   * Waits and returns the next unread message on this topic. Note the message might have been received before this
   * method was called. If no message is received, a timeout exception is thrown.
   *
   * @param timeout
   *   amount of time to wait for a message
   * @return
   *   a Message with a ByteString payload
   */
  def expectOneRaw(timeout: FiniteDuration): Message[ByteString]

  /**
   * Waits and returns the next unread message on this topic. Note the message might have been received before this
   * method was called. If no message is received, a timeout exception is thrown.
   *
   * @return
   *   message including ByteString payload and metadata
   */
  def expectOne(): Message[_]

  /**
   * Waits for a specific amount and returns the next unread message on this topic. Note the message might have been
   * received before this method was called. If no message is received, a timeout exception is thrown.
   *
   * @param timeout
   *   amount of time to wait for a message if it was not received already
   * @return
   *   message including ByteString payload and metadata
   */
  def expectOne(timeout: FiniteDuration): Message[_]

  /**
   * Waits and returns the next unread message on this topic and automatically parses and casts it to the specified
   * given type.
   *
   * @param t
   *   class tag used to cast the deserialized object
   * @tparam T
   *   a given domain type
   * @return
   *   a Message of type T
   */
  def expectOneTyped[T <: GeneratedMessage](implicit t: ClassTag[T]): Message[T]

  /**
   * Waits and returns the next unread message on this topic and automatically parses and casts it to the specified
   * given type. Note the message might have been received before this method was called. If no message is received, a
   * timeout exception is thrown.
   *
   * @param timeout
   *   amount of time to wait for a message if it was not received already
   * @tparam T
   *   a given domain type
   * @return
   *   a Message of type T
   */
  def expectOneTyped[T <: GeneratedMessage](timeout: FiniteDuration)(implicit t: ClassTag[T]): Message[T]

  /**
   * Waits for a default amount of time before returning all unread messages in the topic. If no message is received, a
   * timeout exception is thrown.
   *
   * @return
   *   collection of messages, each message including the deserialized payload object and metadata
   */
  def expectN(): Seq[Message[_]]

  /**
   * Waits for a given amount of unread messages to be received before returning. If no message is received, a timeout
   * exception is thrown.
   *
   * @param total
   *   number of messages to wait for before returning
   * @return
   *   collection of messages, each message including the deserialized payload object and metadata
   */
  def expectN(total: Int): Seq[Message[_]]

  /**
   * Waits for a given amount of unread messages to be received before returning up to a given timeout. If no message is
   * received, a timeout exception is thrown.
   *
   * @param total
   *   number of messages to wait for before returning
   * @param timeout
   *   maximum amount of time to wait for the messages
   * @return
   *   collection of messages, each message including the deserialized payload object and metadata
   */
  def expectN(total: Int, timeout: FiniteDuration): Seq[Message[_]]

  /**
   * Clear the topic so any existing messages are not considered on subsequent expect call.
   *
   * @return
   *   the list of the unread messages when the topic was cleared.
   */
  def clear(): Seq[Message[_]]

  /**
   * Simulate the publishing of a raw message to this topic for the purposes of testing eventing.in flows into a
   * specific service.
   *
   * @param message
   *   raw bytestring to be published in the topic
   */
  def publish(message: ByteString): Unit

  /**
   * Simulate the publishing of a raw message to this topic for the purposes of testing eventing.in flows into a
   * specific service.
   *
   * @param message
   *   raw bytestring to be published in the topic
   * @param metadata
   *   associated with the message
   */
  def publish(message: ByteString, metadata: Metadata): Unit

  /**
   * Simulate the publishing of a message to this topic for the purposes of testing eventing.in flows into a specific
   * service.
   *
   * @param message
   *   to be published in the topic
   */
  def publish[T <: GeneratedMessage](message: Message[T]): Unit

  /**
   * Simulate the publishing of a message to this topic for the purposes of testing eventing.in flows into a specific
   * service.
   *
   * @param message
   *   to be published in the topic
   * @param subject
   *   to identify the entity
   */
  def publish[T <: GeneratedMessage](message: T, subject: String): Unit

  /**
   * Publish multiple messages to this topic for the purposes of testing eventing.in flows into a specific service.
   *
   * @param messages
   *   to be published in the topic
   */
  def publish[T <: GeneratedMessage](messages: List[Message[T]]): Unit
}

@InternalApi
object Topic {
  def apply(delegate: JEventingTestKit.Topic, codec: MessageCodec): Topic = TopicImpl(delegate, codec)
}

@ApiMayChange
final case class Message[P](payload: P, metadata: Metadata) {

  /**
   * Expects message payload to conform to type passed in and returns the typed object if so. Otherwise, throws an
   * exception.
   *
   * @param t
   *   the type of the payload
   * @tparam T
   *   expected class type for the payload of the message
   * @return
   *   a typed object from the payload
   */
  def expectType[T <: GeneratedMessage](implicit t: ClassTag[T]): T = MessageImpl.expectType(payload)
}
