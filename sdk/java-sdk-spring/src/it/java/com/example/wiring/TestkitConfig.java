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

package com.example.wiring;

import kalix.javasdk.testkit.KalixTestKit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static java.time.Duration.ofMillis;

@Configuration
public class TestkitConfig {

  @Bean
  public KalixTestKit.Settings settings() {
    // here only to show how to set different `Settings` in a test. See SpringSdkIntegrationTest.java
    return KalixTestKit.Settings.DEFAULT
        .withAclEnabled()
        .withAdvancedViews()
        .withWorkflowTickInterval(ofMillis(500));
  }
}
