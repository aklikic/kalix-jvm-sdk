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

package kalix.scalasdk.testkit.impl

import kalix.javasdk.impl.GrpcDeferredCall
import kalix.scalasdk.impl.ScalaDeferredCallAdapter
import kalix.javasdk.impl.MetadataImpl
import kalix.javasdk.impl.effect.{ SideEffectImpl => JavaSideEffectImpl }
import kalix.scalasdk.impl.ScalaSideEffectAdapter
import kalix.scalasdk.impl.action.ActionEffectImpl
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ActionResultSpec extends AnyWordSpec with Matchers {

  "Action Results" must {
    "extract side effects" in {
      val replyWithSideEffectResult = new ActionResultImpl[String](
        ActionEffectImpl.Builder
          .reply("reply")
          .addSideEffect(ScalaSideEffectAdapter(JavaSideEffectImpl(
            GrpcDeferredCall[String, Any]("request", MetadataImpl.Empty, "full.service.Name", "MethodName", _ => ???),
            synchronous = false))))

      replyWithSideEffectResult.isReply should ===(true)
      replyWithSideEffectResult.sideEffects should have size 1
    }

    "extract forward details" in {
      val forwardResult = new ActionResultImpl[String](ActionEffectImpl.Builder.forward(ScalaDeferredCallAdapter(
        GrpcDeferredCall[String, String]("request", MetadataImpl.Empty, "full.service.Name", "MethodName", _ => ???))))

      forwardResult.isForward should ===(true)
      forwardResult.forwardedTo.message should ===("request")
    }
  }

}
