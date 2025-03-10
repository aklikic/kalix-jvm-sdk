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

package kalix.spring.testmodels.action;

import kalix.javasdk.action.Action;
import kalix.spring.testmodels.Message;
import org.springframework.web.bind.annotation.*;

public class EchoAction extends Action {

  @GetMapping("/echo/{msg}")
  public Effect<Message> stringMessage(@PathVariable String msg) {
    return effects().reply(new Message(msg));
  }

  @PostMapping("/echo")
  public Effect<Message> messageBody(@RequestParam("add") String add, @RequestBody Message msg) {
    return effects().reply(new Message(msg.value() + add));
  }
}
