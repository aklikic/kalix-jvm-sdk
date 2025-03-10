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

package kalix.spring.testmodels.valueentity;

import kalix.javasdk.annotations.Migration;

@Migration(CounterStateMigration.class)
public class CounterState {

  public final String id;
  public final int value;

  public CounterState(String id, int value) {
    this.id = id;
    this.value = value;
  }

  public CounterState increase(int increaseBy) {
    return new CounterState(id, value + increaseBy);
  }
}
