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
import kalix.javasdk.annotations.JWT;
import kalix.spring.testmodels.Message;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

public class ActionsTestModels {

  public static class GetWithoutParam extends Action {
    @GetMapping("/message")
    public Action.Effect<Message> message() {
      return effects().reply(new Message("hello"));
    }

    public Action.Effect<Message> missingRestAnnotation() {
      return effects().reply(new Message("hello"));
    }
  }

  public static class GetWithOneParam extends Action {
    @GetMapping("/message/{one_param}")
    public Action.Effect<Message> message(@PathVariable("one_param") String one) {
      return effects().reply(new Message(one));
    }
  }

  public static class GetWithOneOptionalPathParam extends Action {
    //this kind of mapping would not work, since missing path variables result in 404 error from the proxy
    //in Spring it is possible to mark path variables as optional for a mapping like:  @GetMapping({"/message/{one}", "/message"})
    //which is currently not supported
    @GetMapping("/message/{one}")
    public Action.Effect<Message> message(@PathVariable(required = false) String one) {
      return effects().reply(new Message(one));
    }
  }

  public static class GetWithOneQueryParam extends Action {
    @GetMapping("/message")
    public Action.Effect<Message> message(@RequestParam String one) {
      return effects().reply(new Message(one));
    }
  }

  public static class GetWithOnePathVariableAndQueryParam extends Action {
    @GetMapping("/message/{one}")
    public Action.Effect<Message> message(@PathVariable String one, @RequestParam String two) {
      return effects().reply(new Message(one));
    }
  }

  public static class GetWithOneOptionalQueryParam extends Action {
    @GetMapping("/message")
    public Action.Effect<Message> message(@RequestParam(required = false) String one) {
      return effects().reply(new Message(one));
    }
  }

  @RequestMapping("/action/{one}")
  public static class GetClassLevel extends Action {
    @GetMapping("/message/{two}")
    public Action.Effect<Message> message(@PathVariable String one, @PathVariable Long two) {
      return effects().reply(new Message("hello"));
    }

    @GetMapping("/message2/{two}")
    public Action.Effect<Message> message2(@PathVariable String one, @PathVariable Long two, @RequestParam String three, @RequestParam int four) {
      return effects().reply(new Message("hello"));
    }

    @GetMapping("/message3/{two}")
    public Action.Effect<Message> message3(@PathVariable String one, @PathVariable Long two, @RequestParam String three, @RequestParam List<String> four) {
      return effects().reply(new Message("hello"));
    }
  }

  public static class PostWithoutParam extends Action {
    @PostMapping("/message")
    public Action.Effect<Message> message(@RequestBody Message msg) {
      return effects().reply(msg);
    }
  }

  public static class ActionWithMethodLevelJWT extends Action {
    @PostMapping("/message")
    @JWT(
      validate = JWT.JwtMethodMode.BEARER_TOKEN,
      bearerTokenIssuer = {"a", "b"},
      staticClaims = {
          @JWT.StaticClaim(claim = "role", value = "admin"),
          @JWT.StaticClaim(claim = "aud", value = "${ENV}.kalix.io")
      })
    public Action.Effect<Message> message(@RequestBody Message msg) {
      return effects().reply(msg);
    }
  }


  @JWT(
    validate = JWT.JwtMethodMode.BEARER_TOKEN,
    bearerTokenIssuer = {"a", "b"},
    staticClaims = {
        @JWT.StaticClaim(claim = "role", value = "admin"),
        @JWT.StaticClaim(claim = "aud", value = "${ENV}.kalix.io")
    })
  public static class ActionWithServiceLevelJWT extends Action {
    @PostMapping("/message")
    public Action.Effect<Message> message(@RequestBody Message msg) {
      return effects().reply(msg);
    }
  }

  public static class PostWithOneParam extends Action {
    @PostMapping("/message/{one}")
    public Action.Effect<Message> message(@PathVariable String one, @RequestBody Message msg) {
      return effects().reply(new Message(msg.value()));
    }
  }

  public static class PostWithOneQueryParam extends Action {
    @PostMapping("/message")
    public Action.Effect<Message> message(@RequestParam String dest, @RequestBody Message msg) {
      return effects().reply(new Message(msg.value()));
    }
  }

  public static class PostWithTwoParam extends Action {
    @PostMapping("/message/{one}/{two}")
    public Action.Effect<Message> message(
      @PathVariable String one, @PathVariable Long two, @RequestBody Message msg) {
      return effects().reply(new Message(msg.value()));
    }
  }

  public static class PostWithTwoMethods extends Action {
    @PostMapping("/message/{text}")
    public Action.Effect<Message> message(@PathVariable String text, @RequestBody Message msg) {
      return effects().reply(new Message(msg.value()));
    }

    @PostMapping("/message/{num}")
    public Action.Effect<Message> message(@PathVariable Long num, @RequestBody Message msg) {
      return effects().reply(new Message("msg.value"));
    }
  }

  public static class PutWithoutParam extends Action {
    @PutMapping("/message")
    public Action.Effect<Message> message(@RequestBody Message msg) {
      return effects().reply(new Message(msg.value()));
    }
  }

  public static class PutWithOneParam extends Action {
    @PutMapping("/message/{one}")
    public Action.Effect<Message> message(@PathVariable String one, @RequestBody Message msg) {
      return effects().reply(new Message(msg.value()));
    }
  }

  public static class PatchWithoutParam extends Action {
    @PatchMapping("/message")
    public Action.Effect<Message> message(@RequestBody Message msg) {
      return effects().reply(new Message(msg.value()));
    }
  }

  public static class PatchWithOneParam extends Action {
    @PatchMapping("/message/{one}")
    public Action.Effect<Message> message(@PathVariable String one, @RequestBody Message msg) {
      return effects().reply(new Message(msg.value()));
    }
  }

  public static class DeleteWithOneParam extends Action {
    @DeleteMapping("/message/{one}")
    public Action.Effect<Message> message(@PathVariable String one) {
      return effects().reply(new Message(one));
    }
  }

  public static class StreamOutAction extends Action {
    @GetMapping("/message")
    public Flux<Effect<Message>> message() {
      return Flux.just(effects().reply(new Message("hello")));
    }
  }

  public static class StreamInAction extends Action {
    @PostMapping("/message")
    public Action.Effect<Message> message(@RequestBody Flux<Message> messages) {
      return effects().reply(new Message("hello"));
    }
  }

  public static class StreamInOutAction extends Action {
    @PostMapping("/message")
    public Flux<Effect<Message>> message(@RequestBody Flux<Message> messages) {
      return messages.map(msg -> effects().reply(msg));
    }
  }
}
