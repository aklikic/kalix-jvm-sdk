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

package kalix.javasdk.testkit;

import akka.actor.ActorSystem;
import akka.annotation.ApiMayChange;
import akka.annotation.InternalApi;
import com.google.protobuf.ByteString;
import kalix.javasdk.Metadata;
import kalix.javasdk.impl.MessageCodec;
import kalix.javasdk.testkit.impl.EventingTestKitImpl;
import kalix.javasdk.testkit.impl.OutgoingMessagesImpl;
import kalix.javasdk.testkit.impl.TestKitMessageImpl;
import kalix.javasdk.testkit.impl.TopicImpl$;

import java.time.Duration;
import java.util.List;


public interface EventingTestKit {

  /**
   * INTERNAL API
   */
  @InternalApi
  static EventingTestKit start(ActorSystem system, String host, int port, MessageCodec codec) {
    return EventingTestKitImpl.start(system, host, port, codec);
  }

  /**
   * Use <code>getTopicOutgoingMessages</code> or <code>getTopicIncomingMessages</code> instead.
   */
  @Deprecated
  Topic getTopic(String topic);

  OutgoingMessages getTopicOutgoingMessages(String topic);

  IncomingMessages getTopicIncomingMessages(String topic);

  IncomingMessages getValueEntityIncomingMessages(String typeId);

  IncomingMessages getEventSourcedEntityIncomingMessages(String typeId);

  IncomingMessages getStreamIncomingMessages(String service, String streamId);

  /**
   * Allows to simulate publishing messages for the purposes of testing incoming message flow.
   */
  interface IncomingMessages {
    /**
     * Simulate the publishing of a raw message.
     *
     * @param message raw bytestring to be published
     */
    void publish(ByteString message);

    /**
     * Simulate the publishing of a raw message.
     *
     * @param message  raw bytestring to be published
     * @param metadata associated with the message
     */
    void publish(ByteString message, Metadata metadata);

    /**
     * Simulate the publishing of a message.
     *
     * @param message to be published
     */
    void publish(Message<?> message);

    /**
     * Simulate the publishing of a message.
     *
     * @param message to be published
     * @param subject to identify the entity
     * @param <T>
     */
    <T> void publish(T message, String subject);

    /**
     * Publish multiple messages.
     *
     * @param messages to be published
     */
    void publish(List<Message<?>> messages);

    /**
     * Publish a predefined delete message. Supported only in case of ValueEntity incoming message flow.
     *
     * @param subject to identify the entity
     */
    void publishDelete(String subject);
  }

  /**
   * Allows to assert published messages for the purposes of testing outgoing message flow.
   */
  interface OutgoingMessages {
    /**
     * Waits for predefined amount of time (see {@link OutgoingMessagesImpl#DefaultTimeout()} for default value). If a message arrives in the meantime or
     * has arrived before but was not consumed, the test fails.
     */
    void expectNone();

    /**
     * Waits for given amount of time. If a message arrives in the meantime or
     * has arrived before but was not consumed, the test fails.
     *
     * @param timeout amount of time to wait for a message
     */
    void expectNone(Duration timeout);

    /**
     * Waits and returns the next unread message. Note the message might have been received before this
     * method was called. If no message is received, a timeout exception is thrown.
     *
     * @return a Message with a ByteString payload
     */
    Message<ByteString> expectOneRaw();

    /**
     * Waits and returns the next unread message. Note the message might have been received before this
     * method was called. If no message is received, a timeout exception is thrown.
     *
     * @param timeout amount of time to wait for a message
     * @return a Message with a ByteString payload
     */
    Message<ByteString> expectOneRaw(Duration timeout);

    /**
     * Waits for predefined amount of time (see {@link OutgoingMessagesImpl#DefaultTimeout()} for default value) and returns the next unread message.
     * Note the message might have been received before this method was called.
     * If no message is received, a timeout exception is thrown.
     *
     * @return message including ByteString payload and metadata
     */
    Message<?> expectOne();

    /**
     * Waits for a specific amount and returns the next unread message.
     * Note the message might have been received before this method was called.
     * If no message is received, a timeout exception is thrown.
     *
     * @param timeout amount of time to wait for a message if it was not received already
     * @return message including ByteString payload and metadata
     */
    Message<?> expectOne(Duration timeout);

    /**
     * Waits and returns the next unread message and automatically parses
     * and casts it to the specified given type.
     *
     * @param instance class type to cast the received message bytes to
     * @param <T>      a given domain type
     * @return a Message of type T
     */
    <T> Message<T> expectOneTyped(Class<T> instance);

    /**
     * Waits and returns the next unread message and automatically parses
     * and casts it to the specified given type.
     * Note the message might have been received before this method was called.
     * If no message is received, a timeout exception is thrown.
     *
     * @param timeout amount of time to wait for a message if it was not received already
     * @return message including ByteString payload and metadata
     */
    <T> Message<T> expectOneTyped(Class<T> instance, Duration timeout);

    /**
     * Waits for a default amount of time before returning all unread messages.
     * If no message is received, a timeout exception is thrown.
     *
     * @return list of messages, each message including the deserialized payload object and metadata
     */
    List<Message<?>> expectN();

    /**
     * Waits for a given amount of unread messages to be received before returning.
     * If no message is received, a timeout exception is thrown.
     *
     * @param total number of messages to wait for before returning
     * @return list of messages, each message including the deserialized payload object and metadata
     */
    List<Message<?>> expectN(int total);

    /**
     * Waits for a given amount of unread messages to be received before returning up to a given timeout.
     * If no message is received, a timeout exception is thrown.
     *
     * @param total   number of messages to wait for before returning
     * @param timeout maximum amount of time to wait for the messages
     * @return list of messages, each message including the deserialized payload object and metadata
     */
    List<Message<?>> expectN(int total, Duration timeout);

    /**
     * Clear the destination so any existing messages are not considered on subsequent expect call.
     *
     * @return the list of the unread messages when the destination was cleared.
     */
    List<Message<?>> clear();
  }

  /**
   * Testkit utility to mock broker's topic. Useful when doing integration tests for services that do incoming/outgoing flow of events.
   *
   * <p><b>Note: </b> messages written to the topic with this utility are not readable with the expect* methods,
   * unless they have been properly forwarded through a outgoing event to the same topic.
   */
  @ApiMayChange
  interface Topic {

    /**
     * Simulate the publishing of a raw message to this topic for the purposes of testing incoming message flow.
     *
     * @param message raw bytestring to be published in the topic
     */
    void publish(ByteString message);

    /**
     * Simulate the publishing of a raw message to this topic for the purposes of testing incoming message flow.
     *
     * @param message  raw bytestring to be published in the topic
     * @param metadata associated with the message
     */
    void publish(ByteString message, Metadata metadata);

    /**
     * Simulate the publishing of a message to this topic for the purposes of testing incoming message flow.
     *
     * @param message to be published in the topic
     */
    void publish(Message<?> message);

    /**
     * Simulate the publishing of a message to this topic for the purposes of testing incoming message flow.
     *
     * @param message to be published in the topic
     * @param subject to identify the entity
     * @param <T>
     */
    <T> void publish(T message, String subject);

    /**
     * Publish multiple messages to this topic for the purposes of testing incoming message flow.
     *
     * @param messages to be published in the topic
     */
    void publish(List<Message<?>> messages);

    /**
     * Waits for predefined amount of time (see {@link TopicImpl$#DefaultTimeout()} for default value). If a message arrives in the meantime or
     * has arrived before but was not consumed, the test fails.
     */
    void expectNone();

    /**
     * Waits for given amount of time. If a message arrives in the meantime or
     * has arrived before but was not consumed, the test fails.
     *
     * @param timeout amount of time to wait for a message
     */
    void expectNone(Duration timeout);

    /**
     * Waits and returns the next unread message on this topic. Note the message might have been received before this
     * method was called. If no message is received, a timeout exception is thrown.
     *
     * @return a Message with a ByteString payload
     */
    Message<ByteString> expectOneRaw();

    /**
     * Waits and returns the next unread message on this topic. Note the message might have been received before this
     * method was called. If no message is received, a timeout exception is thrown.
     *
     * @param timeout amount of time to wait for a message
     * @return a Message with a ByteString payload
     */
    Message<ByteString> expectOneRaw(Duration timeout);

    /**
     * Waits for predefined amount of time (see {@link TopicImpl$#DefaultTimeout()} for default value) and returns the next unread message on this topic.
     * Note the message might have been received before this method was called.
     * If no message is received, a timeout exception is thrown.
     *
     * @return message including ByteString payload and metadata
     */
    Message<?> expectOne();

    /**
     * Waits for a specific amount and returns the next unread message on this topic.
     * Note the message might have been received before this method was called.
     * If no message is received, a timeout exception is thrown.
     *
     * @param timeout amount of time to wait for a message if it was not received already
     * @return message including ByteString payload and metadata
     */
    Message<?> expectOne(Duration timeout);

    /**
     * Waits and returns the next unread message on this topic and automatically parses
     * and casts it to the specified given type.
     *
     * @param instance class type to cast the received message bytes to
     * @return a Message of type T
     * @param <T> a given domain type
     */
    <T> Message<T> expectOneTyped(Class<T> instance);

    /**
     * Waits and returns the next unread message on this topic and automatically parses
     * and casts it to the specified given type.
     * Note the message might have been received before this method was called.
     * If no message is received, a timeout exception is thrown.
     *
     * @param timeout amount of time to wait for a message if it was not received already
     * @return message including ByteString payload and metadata
     */
    <T> Message<T> expectOneTyped(Class<T> instance, Duration timeout);

    /**
     * Waits for a default amount of time before returning all unread messages in the topic.
     * If no message is received, a timeout exception is thrown.
     *
     * @return list of messages, each message including the deserialized payload object and metadata
     */
    List<Message<?>> expectN();

    /**
     * Waits for a given amount of unread messages to be received before returning.
     * If no message is received, a timeout exception is thrown.
     *
     * @param total number of messages to wait for before returning
     * @return list of messages, each message including the deserialized payload object and metadata
     */
    List<Message<?>> expectN(int total);

    /**
     * Waits for a given amount of unread messages to be received before returning up to a given timeout.
     * If no message is received, a timeout exception is thrown.
     *
     * @param total number of messages to wait for before returning
     * @param timeout maximum amount of time to wait for the messages
     * @return list of messages, each message including the deserialized payload object and metadata
     */
    List<Message<?>> expectN(int total, Duration timeout);

    /**
     * Clear the topic so any existing messages are not considered on subsequent expect call.
     *
     * @return the list of the unread messages when the topic was cleared.
     */
    List<Message<?>> clear();
  }

  @ApiMayChange
  class MessageBuilder {
    private final MessageCodec messageCodec;

    public MessageBuilder(MessageCodec messageCodec) {
      this.messageCodec = messageCodec;
    }

    /**
     * Create a message from a payload plus a subject (that is, the entity key).
     * Automatically adds required default metadata for a CloudEvent.
     *
     * @param payload the message payload
     * @param subject the entity key of which the message is concerned about
     * @param <T>
     * @return a Message object to be used in the context of the Testkit
     */
    public <T> Message<T> of(T payload, String subject) {
      return new TestKitMessageImpl<>(payload, TestKitMessageImpl.defaultMetadata(payload, subject, messageCodec));
    }

    /**
     * Create a message object from a payload plus metadata.
     *
     * @param payload  the message payload
     * @param metadata the metadata associated with the message
     * @param <T>
     * @return a Message object to be used in the context of the Testkit
     */
    public <T> Message<T> of(T payload, Metadata metadata) {
      return new TestKitMessageImpl<>(payload, metadata);
    }
  }

  @ApiMayChange
  interface Message<P> {
    P getPayload();

    Metadata getMetadata();

    /**
     * Expects message payload to conform to type passed in and returns the typed object if so.
     * Otherwise, throws an exception.
     *
     * @param clazz expected class type for the payload of the message
     * @param <T>   the type of the payload
     * @return a typed object from the payload
     */
    <T> T expectType(Class<T> clazz);
  }
}
