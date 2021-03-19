/*
 * Copyright 2019 Lightbend Inc.
 */

package com.akkaserverless.javasdk.impl.crdt

import com.akkaserverless.javasdk._
import com.akkaserverless.javasdk.crdt._
import com.akkaserverless.javasdk.impl.{AnySupport, ResolvedServiceMethod, ResolvedType}
import com.example.shoppingcart.ShoppingCart
import com.google.protobuf.any.{Any => ScalaPbAny}
import com.google.protobuf.{ByteString, Any => JavaPbAny}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import java.util.Optional

class AnnotationBasedCrdtSupportSpec extends AnyWordSpec with Matchers {

  trait BaseContext extends Context {
    override def serviceCallFactory(): ServiceCallFactory = new ServiceCallFactory {
      override def lookup[T](serviceName: String, methodName: String, messageType: Class[T]): ServiceCallRef[T] =
        throw new NoSuchElementException
    }
  }

  trait CrdtFactoryContext extends AbstractCrdtFactory {
    override protected def anySupport: AnySupport = AnnotationBasedCrdtSupportSpec.this.anySupport
    override protected def newCrdt[C <: InternalCrdt](crdt: C): C = crdt
  }

  val anySupport = new AnySupport(Array(ShoppingCart.getDescriptor), this.getClass.getClassLoader)

  object MockCreationContext extends MockCreationContext(None)
  class MockCreationContext(crdt: Option[Crdt] = None)
      extends CrdtCreationContext
      with BaseContext
      with CrdtFactoryContext {
    override def entityId(): String = "foo"
    override def state[T <: Crdt](crdtType: Class[T]): Optional[T] = crdt match {
      case Some(crdt) if crdtType.isInstance(crdt) => Optional.of(crdtType.cast(crdt))
      case None => Optional.empty()
      case Some(wrongType) =>
        throw new IllegalStateException(
          s"The current ${wrongType} CRDT state doesn't match requested type of ${crdtType.getSimpleName}"
        )
    }
    override def getWriteConsistency: WriteConsistency = WriteConsistency.LOCAL
    override def setWriteConsistency(writeConsistency: WriteConsistency): Unit = ()
  }

  object WrappedResolvedType extends ResolvedType[Wrapped] {
    override def typeClass: Class[Wrapped] = classOf[Wrapped]
    override def typeUrl: String = AnySupport.DefaultTypeUrlPrefix + "/wrapped"
    override def parseFrom(bytes: ByteString): Wrapped = Wrapped(bytes.toStringUtf8)
    override def toByteString(value: Wrapped): ByteString = ByteString.copyFromUtf8(value.value)
  }

  object StringResolvedType extends ResolvedType[String] {
    override def typeClass: Class[String] = classOf[String]
    override def typeUrl: String = AnySupport.DefaultTypeUrlPrefix + "/string"
    override def parseFrom(bytes: ByteString): String = bytes.toStringUtf8
    override def toByteString(value: String): ByteString = ByteString.copyFromUtf8(value)
  }

  case class Wrapped(value: String)
  val serviceDescriptor = ShoppingCart.getDescriptor.findServiceByName("ShoppingCartService")
  val descriptor = serviceDescriptor.findMethodByName("AddItem")
  val method = ResolvedServiceMethod(descriptor, StringResolvedType, WrappedResolvedType)

  def create(behavior: AnyRef, methods: ResolvedServiceMethod[_, _]*) =
    new AnnotationBasedCrdtSupport(behavior.getClass,
                                   anySupport,
                                   methods.map(m => m.descriptor.getName -> m).toMap,
                                   Some(_ => behavior)).create(new MockCreationContext())

  def create(clazz: Class[_], crdt: Option[Crdt] = None) =
    new AnnotationBasedCrdtSupport(clazz, anySupport, Map.empty, None).create(new MockCreationContext(crdt))

  def command(str: String) =
    ScalaPbAny.toJavaProto(ScalaPbAny(StringResolvedType.typeUrl, StringResolvedType.toByteString(str)))

  def decodeWrapped(any: JavaPbAny) = {
    any.getTypeUrl should ===(WrappedResolvedType.typeUrl)
    WrappedResolvedType.parseFrom(any.getValue)
  }

  "Event sourced annotation support" should {
    "support entity construction" when {

      "there is an optional CRDT constructor and the CRDT is empty" in {
        create(classOf[OptionalEmptyCrdtConstructorTest], None)
      }

      "there is an optional CRDT constructor and the CRDT is non empty" in {
        create(classOf[OptionalCrdtConstructorTest], Some(MockCreationContext.newVote()))
      }

      "there is an optional CRDT constructor and the CRDT is the wrong type" in {
        an[IllegalStateException] should be thrownBy create(classOf[OptionalCrdtConstructorTest],
                                                            Some(MockCreationContext.newGCounter()))
      }

      "there is a CRDT constructor and the CRDT is non empty" in {
        create(classOf[CrdtConstructorTest], Some(MockCreationContext.newVote()))
      }

      "there is a CRDT constructor and the CRDT is empty" in {
        create(classOf[CrdtConstructorTest], None)
      }

      "there is a CRDT constructor and the CRDT is the wrong type" in {
        an[IllegalStateException] should be thrownBy create(classOf[CrdtConstructorTest],
                                                            Some(MockCreationContext.newGCounter()))
      }

      "there is a provided entity factory" in {
        val factory = new EntityFactory {
          override def create(context: EntityContext): AnyRef = new CrdtFactoryTest(context)
          override def entityClass: Class[_] = classOf[CrdtFactoryTest]
        }
        val crdtSupport = new AnnotationBasedCrdtSupport(factory, anySupport, serviceDescriptor)
        crdtSupport.create(MockCreationContext)
      }

    }

    // TODO: CRDT command handlers should be tested
  }
}

import org.scalatest.matchers.should.Matchers._

@CrdtEntity
private class OptionalEmptyCrdtConstructorTest(crdt: Optional[Vote]) {
  crdt should ===(Optional.empty())
}

@CrdtEntity
private class OptionalCrdtConstructorTest(crdt: Optional[Vote]) {
  crdt.isPresent shouldBe true
  crdt.get shouldBe a[Vote]
}

@CrdtEntity
private class CrdtConstructorTest(crdt: Vote) {
  crdt shouldBe a[Vote]
}

@CrdtEntity
private class CrdtFactoryTest(ctx: EntityContext) {
  ctx shouldBe a[CrdtCreationContext]
  ctx.entityId should ===("foo")
}
