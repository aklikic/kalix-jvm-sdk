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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import kalix.javasdk.JsonSupport;
import kalix.spring.impl.KalixSpringApplication;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import scala.jdk.FutureConverters;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.awaitility.Awaitility.await;

public abstract class DockerIntegrationTest {
  protected WebClient webClient;
  protected Duration timeout = Duration.of(5, SECONDS);


  private KalixSpringApplication kalixSpringApplication;


  public Config defaultConfig() {
    Map<String, Object> confMap = new HashMap<>();
    // don't kill the test JVM when terminating the KalixRunner
    confMap.put("kalix.system.akka.coordinated-shutdown.exit-jvm", "off");
    confMap.put("kalix.user-function-interface", "0.0.0.0");
    confMap.put("kalix.dev-mode.docker-compose-file", "docker-compose-integration.yml");
    return ConfigFactory.parseMap(confMap);
  }


  public DockerIntegrationTest(ApplicationContext applicationContext) {
    Config config = defaultConfig().withFallback(ConfigFactory.load());
    kalixSpringApplication = new KalixSpringApplication(applicationContext, config);
  }

  public DockerIntegrationTest(ApplicationContext applicationContext, Config config){
    Config finalConfig = defaultConfig().withFallback(config).withFallback(ConfigFactory.load());
    kalixSpringApplication = new KalixSpringApplication(applicationContext, finalConfig);
  }

  @BeforeAll
  public void beforeAll() {
    kalixSpringApplication.start();
    webClient = createClient("http://localhost:9000");
  }

  @AfterAll
  public void afterAll() throws ExecutionException, InterruptedException {
      new FutureConverters.FutureOps<>(kalixSpringApplication.stop())
        .asJava()
        .toCompletableFuture().get();
  }

  private HttpStatusCode assertSourceServiceIsUp(WebClient webClient) {
    try {
      return webClient.get()
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
          Mono.empty()
        )
        .toBodilessEntity()
        .block(timeout)
        .getStatusCode();

    } catch (WebClientRequestException ex) {
      throw new RuntimeException("This test requires an external kalix service to be running on localhost:9000 but was not able to reach it.");
    }
  }

  /* create the client but only return it after verifying that service is reachable */
  private WebClient createClient(String url) {

    var webClient =
      WebClient
        .builder()
        .baseUrl(url)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .codecs(configurer ->
          configurer.defaultCodecs().jackson2JsonEncoder(
            new Jackson2JsonEncoder(JsonSupport.getObjectMapper(), MediaType.APPLICATION_JSON)
          )
        )
        .build();

    // wait until customer service is up
    await()
      .ignoreExceptions()
      .pollInterval(5, TimeUnit.SECONDS)
      .atMost(120, TimeUnit.SECONDS)
      .until(() -> assertSourceServiceIsUp(webClient),
        new IsEqual(HttpStatus.NOT_FOUND)  // NOT_FOUND is a sign that the customer registry service is there
      );

    return webClient;
  }
}
