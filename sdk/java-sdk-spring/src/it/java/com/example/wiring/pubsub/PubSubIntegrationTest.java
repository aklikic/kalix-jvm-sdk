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

package com.example.wiring.pubsub;

import com.example.Main;
import com.example.wiring.valueentities.customer.CustomerEntity.Customer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.example.wiring.pubsub.PublishBytesToTopic.CUSTOMERS_BYTES_TOPIC;
import static com.example.wiring.pubsub.PublishTopicToTopic.CUSTOMERS_2_TOPIC;
import static com.example.wiring.pubsub.PublishVEToTopic.CUSTOMERS_TOPIC;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = Main.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("docker-it-test")
public class PubSubIntegrationTest extends DockerIntegrationTest {


  public PubSubIntegrationTest(ApplicationContext applicationContext) {
    super(applicationContext);
  }

  @Test
  public void shouldVerifyActionSubscribingToCounterEventsTopic() {
    //given
    String counterId = "some-counter";

    //when
    increaseCounter(counterId, 2);
    increaseCounter(counterId, 2);
    multiplyCounter(counterId, 10);

    //then
    await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var response = DummyCounterEventStore.get(counterId);
        assertThat(response).hasSize(3);
      });
  }

  @Test
  public void shouldVerifyViewSubscribingToCounterEventsTopic() {
    //given
    String counterId1 = "some-counter-1";
    String counterId2 = "some-counter-2";

    //when
    increaseCounter(counterId1, 2);
    increaseCounter(counterId1, 2);
    multiplyCounter(counterId1, 10);
    increaseCounter(counterId2, 2);
    multiplyCounter(counterId2, 10);

    //then
    await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var response = webClient
          .get()
          .uri("/counter-view-topic-sub/less-then/" + 30)
          .retrieve()
          .bodyToFlux(CounterView.class)
          .toStream()
          .toList();

        assertThat(response).containsOnly(new CounterView(counterId2, 20));
      });
  }

  @Test
  public void shouldVerifyActionSubscribingToCustomersTopic() {
    //given
    Customer customer1 = new Customer("name1", Instant.now());
    Customer updatedCustomer1 = new Customer("name1", Instant.now());
    Customer customer2 = new Customer("name2", Instant.now());

    //when
    createCustomer(customer1);
    createCustomer(updatedCustomer1);
    createCustomer(customer2);

    //then
    await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var response = DummyCustomerStore.get(CUSTOMERS_TOPIC, customer1.name());
        assertThat(response).isEqualTo(updatedCustomer1);
      });
  }

  @Test
  public void shouldVerifyActionSubscribingAndPublishingRawBytes() {
    //given
    Customer customer1 = new Customer("name3", Instant.now());

    //when
    createCustomer(customer1);

    //then
    await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var response = DummyCustomerStore.get(CUSTOMERS_BYTES_TOPIC, customer1.name());
        assertThat(response).isEqualTo(customer1);
      });
  }

  @Test
  public void shouldVerifyActionSubscribingToCustomers2Topic() {
    //given
    Customer customer1 = new Customer("name3", Instant.now());
    Customer updatedCustomer1 = new Customer("name3", Instant.now());
    Customer customer2 = new Customer("name4", Instant.now());

    //when
    createCustomer(customer1);
    createCustomer(updatedCustomer1);
    createCustomer(customer2);

    //then
    await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var response = DummyCustomerStore.get(CUSTOMERS_2_TOPIC, customer1.name());
        assertThat(response).isEqualTo(updatedCustomer1);
      });
  }

  private void createCustomer(Customer customer) {
    String created =
      webClient
        .put()
        .uri("/customers/" + customer.name())
        .bodyValue(customer)
        .retrieve()
        .bodyToMono(String.class)
        .block(timeout);
    assertThat(created).isEqualTo("\"Ok\"");
  }

  private Integer increaseCounter(String name, int value) {
    return webClient
      .post()
      .uri("/counter/" + name + "/increase/" + value)
      .retrieve()
      .bodyToMono(Integer.class)
      .block(timeout);
  }

  private Integer multiplyCounter(String name, int value) {
    return webClient
      .post()
      .uri("/counter/" + name + "/multiply/" + value)
      .retrieve()
      .bodyToMono(Integer.class)
      .block(timeout);
  }
}
