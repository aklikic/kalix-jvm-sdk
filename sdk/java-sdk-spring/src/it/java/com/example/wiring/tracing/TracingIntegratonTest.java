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

package com.example.wiring.tracing;

import com.example.Main;
import com.example.wiring.pubsub.DockerIntegrationTest;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = Main.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("docker-it-test")
public class TracingIntegratonTest extends DockerIntegrationTest {

    Logger logger = LoggerFactory.getLogger(TracingIntegratonTest.class);
    static Config config = ConfigFactory.parseString("""
                kalix.proxy.telemetry.tracing.enabled = true
                kalix.telemetry.tracing.collector-endpoint = "http://localhost:4317"
                """);

    public TracingIntegratonTest(ApplicationContext applicationContext) {
        super(applicationContext, config);
    }

    @Disabled //disabled ATM for https://github.com/lightbend/kalix-jvm-sdk/pull/1810
    public void shouldSendTraces() {
        String counterId = "some-counter";
        callTCounter(counterId, 10);

        await().ignoreExceptions().atMost(60, TimeUnit.of(SECONDS)).untilAsserted(() -> {
           Traces traces = selectTraces();
           assertThat(traces.traces().isEmpty()).isFalse();
           Batches batches = selectBatches(traces.traces().get(0).traceID());
           assertThat(batches.batches().isEmpty()).isFalse();
           logger.debug("Batches found: [{}]", batches.batches());
           assertThat(batches.batches().get(0).scopeSpans().get(0).scope().name()).isEqualTo("kalix.proxy.telemetry.TraceInstrumentationImpl");
           assertThat(batches.batches().get(1).scopeSpans().get(0).spans().get(0).name()).isEqualTo("some-counter");
           assertThat(batches.batches().get(2).scopeSpans().get(0).spans().get(0).name()).isEqualTo("PrintIncrease");
        }
        );

    }

    private Integer callTCounter(String counterId, Integer increase) {
        return webClient.post().uri("/tcounter/" + counterId + "/increase/" + increase).retrieve().bodyToMono(Integer.class).block(timeout);
    }
    public Traces selectTraces(){
        Traces traces = WebClient.create("http://0.0.0.0:3200/api/search").get().retrieve().bodyToMono(Traces.class).block(timeout);
        return traces;
    }

    public Batches selectBatches(String traceId){
        Batches batches =  WebClient.create("http://0.0.0.0:3200/api/traces/" + traceId).get().retrieve().bodyToMono(Batches.class).block(timeout);
        return batches;
    }

}

