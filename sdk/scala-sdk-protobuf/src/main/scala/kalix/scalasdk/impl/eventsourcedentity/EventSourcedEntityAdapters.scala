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

package kalix.scalasdk.impl.eventsourcedentity

import akka.stream.Materializer
import com.google.protobuf.Descriptors
import kalix.javasdk
import kalix.javasdk.Metadata
import kalix.javasdk.eventsourcedentity.{ CommandContext => JavaSdkCommandContext }
import kalix.javasdk.eventsourcedentity.{ EventContext => JavaSdkEventContext }
import kalix.javasdk.eventsourcedentity.{ EventSourcedEntity => JavaSdkEventSourcedEntity }
import kalix.javasdk.eventsourcedentity.{ EventSourcedEntityContext => JavaSdkEventSourcedEntityContext }
import kalix.javasdk.eventsourcedentity.{ EventSourcedEntityOptions => JavaSdkEventSourcedEntityOptions }
import kalix.javasdk.eventsourcedentity.{ EventSourcedEntityProvider => JavaSdkEventSourcedEntityProvider }
import kalix.javasdk.impl.eventsourcedentity.{ EventSourcedEntityRouter => JavaSdkEventSourcedEntityRouter }
import kalix.scalasdk.eventsourcedentity.CommandContext
import kalix.scalasdk.eventsourcedentity.EventContext
import kalix.scalasdk.eventsourcedentity.EventSourcedEntity
import kalix.scalasdk.eventsourcedentity.EventSourcedEntityContext
import kalix.scalasdk.eventsourcedentity.EventSourcedEntityOptions
import kalix.scalasdk.eventsourcedentity.EventSourcedEntityProvider
import kalix.scalasdk.impl.InternalContext
import kalix.scalasdk.impl.MetadataConverters
import kalix.scalasdk.impl.PassivationStrategyConverters

import java.util.Optional
import scala.jdk.CollectionConverters.SetHasAsJava
import scala.jdk.CollectionConverters.SetHasAsScala
import scala.jdk.OptionConverters._

private[scalasdk] final class JavaEventSourcedEntityAdapter[S](scalaSdkEventSourcedEntity: EventSourcedEntity[S])
    extends JavaSdkEventSourcedEntity[S, Any] {

  override def emptyState(): S = scalaSdkEventSourcedEntity.emptyState

  override def _internalSetEventContext(context: Optional[JavaSdkEventContext]): Unit =
    scalaSdkEventSourcedEntity._internalSetEventContext(context.map(new JavaEventContextAdapter(_)).toScala)

  override def _internalSetCommandContext(context: Optional[JavaSdkCommandContext]): Unit =
    scalaSdkEventSourcedEntity._internalSetCommandContext(context.map(new JavaCommandContextAdapter(_)).toScala)

}

private[scalasdk] final class JavaEventSourcedEntityProviderAdapter[S, ES <: EventSourcedEntity[S]](
    scalaSdkProvider: EventSourcedEntityProvider[S, ES])
    extends JavaSdkEventSourcedEntityProvider[S, Any, JavaSdkEventSourcedEntity[S, Any]] {

  def additionalDescriptors(): Array[Descriptors.FileDescriptor] = scalaSdkProvider.additionalDescriptors.toArray

  def entityType(): String = scalaSdkProvider.entityType

  def newRouter(context: JavaSdkEventSourcedEntityContext)
      : JavaSdkEventSourcedEntityRouter[S, Any, JavaSdkEventSourcedEntity[S, Any]] = {
    val scaladslRouter = scalaSdkProvider
      .newRouter(new ScalaEventSourcedEntityContextAdapter(context))
      .asInstanceOf[EventSourcedEntityRouter[S, EventSourcedEntity[S]]]
    new JavaEventSourcedEntityRouterAdapter[S](
      new JavaEventSourcedEntityAdapter[S](scaladslRouter.entity),
      scaladslRouter)
  }

  def options(): JavaSdkEventSourcedEntityOptions = new JavaEventSourcedEntityOptionsAdapter(scalaSdkProvider.options)
  def serviceDescriptor(): Descriptors.ServiceDescriptor = scalaSdkProvider.serviceDescriptor
}

private[scalasdk] final class JavaEventSourcedEntityOptionsAdapter(
    scalaSdkEventSourcedEntityOptions: EventSourcedEntityOptions)
    extends JavaSdkEventSourcedEntityOptions {

  def forwardHeaders(): java.util.Set[String] = scalaSdkEventSourcedEntityOptions.forwardHeaders.asJava

  def snapshotEvery(): Int = scalaSdkEventSourcedEntityOptions.snapshotEvery

  def withSnapshotEvery(numberOfEvents: Int) = new JavaEventSourcedEntityOptionsAdapter(
    scalaSdkEventSourcedEntityOptions.withSnapshotEvery(numberOfEvents))

  def withForwardHeaders(headers: java.util.Set[String]): JavaSdkEventSourcedEntityOptions =
    new JavaEventSourcedEntityOptionsAdapter(
      scalaSdkEventSourcedEntityOptions.withForwardHeaders(Set.from(headers.asScala)))

  def passivationStrategy(): javasdk.PassivationStrategy =
    PassivationStrategyConverters.toJava(scalaSdkEventSourcedEntityOptions.passivationStrategy)

  def withPassivationStrategy(passivationStrategy: javasdk.PassivationStrategy): JavaSdkEventSourcedEntityOptions =
    new JavaEventSourcedEntityOptionsAdapter(
      scalaSdkEventSourcedEntityOptions.withPassivationStrategy(
        PassivationStrategyConverters.toScala(passivationStrategy)))
}

private[scalasdk] final class JavaEventSourcedEntityRouterAdapter[S](
    javaSdkEventSourcedEntity: JavaSdkEventSourcedEntity[S, Any],
    scalaSdkRouter: EventSourcedEntityRouter[S, EventSourcedEntity[S]])
    extends JavaSdkEventSourcedEntityRouter[S, Any, JavaSdkEventSourcedEntity[S, Any]](javaSdkEventSourcedEntity) {

  override def handleEvent(state: S, event: Any): S = {
    scalaSdkRouter.handleEvent(state, event)
  }

  override def handleCommand(
      commandName: String,
      state: S,
      command: Any,
      context: JavaSdkCommandContext): JavaSdkEventSourcedEntity.Effect[_] = {
    scalaSdkRouter.handleCommand(commandName, state, command, new JavaCommandContextAdapter(context)) match {
      case EventSourcedEntityEffectImpl(javasdkEffectImpl) => javasdkEffectImpl
    }
  }
}

private[scalasdk] final class ScalaEventSourcedEntityContextAdapter(javaSdkContext: JavaSdkEventSourcedEntityContext)
    extends EventSourcedEntityContext {

  def entityId: String = javaSdkContext.entityId()

  override def materializer(): Materializer = javaSdkContext.materializer()
}

private[scalasdk] final class JavaCommandContextAdapter(val javaSdkContext: JavaSdkCommandContext)
    extends CommandContext
    with InternalContext {

  override def sequenceNumber: Long = javaSdkContext.sequenceNumber()

  override def commandName: String = javaSdkContext.commandName()

  override def commandId: Long = javaSdkContext.commandId()

  override def entityId: String = javaSdkContext.entityId()

  override def metadata: kalix.scalasdk.Metadata =
    MetadataConverters.toScala(javaSdkContext.metadata())

  def getComponentGrpcClient[T](serviceClass: Class[T]): T = javaSdkContext match {
    case ctx: javasdk.impl.AbstractContext => ctx.getComponentGrpcClient(serviceClass)
  }

  override def materializer(): Materializer = javaSdkContext.materializer()
}

private[scalasdk] final class JavaEventContextAdapter(val javasdkContext: JavaSdkEventContext) extends EventContext {
  override def sequenceNumber: Long = javasdkContext.sequenceNumber()

  override def entityId: String = javasdkContext.entityId()

  override def materializer(): Materializer = javasdkContext.materializer()
}
