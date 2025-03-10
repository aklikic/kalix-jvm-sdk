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

package kalix.javasdk.tck.model.localpersistenceeventing;

import kalix.javasdk.JsonSupport;
import kalix.javasdk.valueentity.ValueEntity;
import kalix.javasdk.valueentity.ValueEntityContext;
import kalix.tck.model.eventing.LocalPersistenceEventing;
import com.google.protobuf.Empty;

public class ValueEntityTwo extends ValueEntity<Object> {
  public ValueEntityTwo(ValueEntityContext context) {}

  public Effect<Empty> updateJsonValue(Object state, LocalPersistenceEventing.JsonValue value) {
    return effects()
        // FIXME requirement to use JSON state should be removed from TCK
        .updateState(JsonSupport.encodeJson(new JsonMessage(value.getMessage())))
        .thenReply(Empty.getDefaultInstance());
  }

  @Override
  public Object emptyState() {
    return null;
  }
}
