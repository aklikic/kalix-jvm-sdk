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

package kalix.spring;

import kalix.javasdk.JsonSupport;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.impl.JsonMessageCodec;
import kalix.javasdk.testkit.KalixTestKit;
import kalix.spring.boot.KalixConfiguration;
import kalix.spring.impl.KalixSpringApplication;
import kalix.spring.impl.WebClientProviderHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;

@Import(KalixConfiguration.class)
@TestConfiguration
public class KalixConfigurationTest {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private ApplicationContext applicationContext;
  @Autowired
  private KalixConfiguration kalixConfiguration;

  @Bean
  public KalixSpringApplication kalixSpringApplication() {
    // for this internal integration tests, we need to explicitly pass the Main class we want
    // because otherwise it will detect sbt.ForkMain instead
    return new KalixSpringApplication(applicationContext, kalixConfiguration.config());
  }

  /**
   * WebClient pointing to the proxy.
   */
  @Bean
  public WebClient createWebClient(KalixTestKit kalixTestKit) {
    return WebClient.builder()
      .baseUrl("http://localhost:" + kalixTestKit.getPort())
      .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .codecs(configurer -> {
        configurer.defaultCodecs().jackson2JsonEncoder(
          new Jackson2JsonEncoder(JsonSupport.getObjectMapper(), MediaType.APPLICATION_JSON));
      })
      .build();
  }

  @Bean
  public KalixTestKit kalixTestKit(KalixSpringApplication kalixSpringApplication, KalixTestKit.Settings settings) {
    logger.info("Starting Kalix TestKit...");
    KalixTestKit kalixTestKit = new KalixTestKit(kalixSpringApplication.kalix(), new JsonMessageCodec(), settings);
    kalixTestKit.start(kalixConfiguration.config());
    logger.info("Kalix Runtime running on port: " + kalixTestKit.getPort());
    //when ComponentClient is used in integration test, we must initiate webclient before the first request
    WebClientProviderHolder holder = WebClientProviderHolder.get(kalixTestKit.getRunner().system());
    kalixSpringApplication.kalixClient().setWebClient(holder.webClientProvider().localWebClient());
    return kalixTestKit;
  }

  @Bean
  public ComponentClient componentClient(KalixSpringApplication kalixSpringApplication) {
    return kalixSpringApplication.componentClient();
  }
}
