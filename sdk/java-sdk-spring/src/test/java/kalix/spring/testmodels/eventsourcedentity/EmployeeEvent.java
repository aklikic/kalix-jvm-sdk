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

package kalix.spring.testmodels.eventsourcedentity;

import kalix.javasdk.annotations.Migration;
import kalix.javasdk.annotations.TypeName;

public interface EmployeeEvent {

  @TypeName("created")
  @Migration(EmployeeCreatedMigration.class)
  final class EmployeeCreated implements EmployeeEvent {

    public final String firstName;
    public final String lastName;
    public final String email;

    public EmployeeCreated(String firstName, String lastName, String email) {
      this.firstName = firstName;
      this.lastName = lastName;
      this.email = email;
    }
  }

  @TypeName("emailUpdated")
  final class EmployeeEmailUpdated implements EmployeeEvent {

    public final String email;

    public EmployeeEmailUpdated(String email) {
      this.email = email;
    }
  }

}
