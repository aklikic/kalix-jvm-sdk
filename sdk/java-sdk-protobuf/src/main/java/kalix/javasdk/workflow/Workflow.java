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

package kalix.javasdk.workflow;

import akka.annotation.ApiMayChange;
import io.grpc.Status;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.Metadata;
import kalix.javasdk.StatusCode;
import kalix.javasdk.impl.workflow.WorkflowEffectImpl;
import kalix.javasdk.timer.TimerScheduler;
import kalix.javasdk.workflow.Workflow.RecoverStrategy.MaxRetries;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Workflows are stateful components and are defined by a set of steps and transitions between them.
 * <p>
 * You can use workflows to implement business processes that span multiple services.
 * <p>
 * When implementing a workflow, you define a state type and a set of steps. Each step defines a call to be executed and
 * the transition to the next step based on the result of the call.
 * The workflow state can be updated after each successful step execution.
 * <p>
 * Kalix keeps track of the state of the workflow and the current step. If the workflow is stopped for any reason,
 * it can be resumed from the last known state and step.
 * <p>
 * Workflow methods that handle incoming commands should return an {@link Effect} describing the next processing actions.
 *
 * @param <S> The type of the state for this workflow.
 */
@ApiMayChange
public abstract class Workflow<S> {


  private Optional<CommandContext> commandContext = Optional.empty();
  private Optional<TimerScheduler> timerScheduler = Optional.empty();

  private Optional<S> currentState = Optional.empty();

  private boolean stateHasBeenSet = false;

  /**
   * Returns the initial empty state object. This object will be passed into the
   * command and step handlers, until a new state replaces it.
   *
   * <p>Also known as "zero state" or "neutral state".
   *
   * <p>The default implementation of this method returns <code>null</code>. It can be overridden to
   * return a more sensible initial state.
   */
  public S emptyState() {
    return null;
  }

  /**
   * Additional context and metadata for a command handler.
   *
   * <p>It will throw an exception if accessed from constructor.
   *
   * @throws IllegalStateException if accessed outside a handler method
   */
  protected final kalix.javasdk.workflow.CommandContext commandContext() {
    return commandContext.orElseThrow(() -> new IllegalStateException("CommandContext is only available when handling a command."));
  }


  /**
   * INTERNAL API
   */
  public void _internalSetCommandContext(Optional<CommandContext> context) {
    commandContext = context;
  }

  /**
   * INTERNAL API
   */
  public void _internalSetTimerScheduler(Optional<TimerScheduler> timerScheduler) {
    this.timerScheduler = timerScheduler;
  }

  /**
   * Returns a {@link TimerScheduler} that can be used to schedule further in time.
   */
  public final TimerScheduler timers() {
    return timerScheduler.orElseThrow(() -> new IllegalStateException("Timers can only be scheduled or cancelled when handling a command or running a step action."));
  }

  /**
   * INTERNAL API
   */
  public void _internalSetCurrentState(S state) {
    stateHasBeenSet = true;
    currentState = Optional.ofNullable(state);
  }

  /**
   * Returns the state as currently stored by Kalix.
   *
   * <p>Note that modifying the state directly will not update it in storage. To save the state, one
   * must call {{@code effects().updateState()}}.
   *
   * <p>This method can only be called when handling a command. Calling it outside a method (eg: in
   * the constructor) will raise a IllegalStateException exception.
   *
   * @throws IllegalStateException if accessed outside a handler method
   */
  @ApiMayChange
  protected final S currentState() {
    // user may call this method inside a command handler and get a null because it's legal
    // to have emptyState set to null.
    if (stateHasBeenSet) return currentState.orElse(null);
    else throw new IllegalStateException("Current state is only available when handling a command.");
  }

  /**
   * @return A workflow definition in a form of steps and transitions between them.
   */
  @ApiMayChange
  public abstract WorkflowDef<S> definition();

  protected final Effect.Builder<S> effects() {
    return WorkflowEffectImpl.apply();
  }

  /**
   * An Effect is a description of what Kalix needs to do after the command is handled.
   * You can think of it as a set of instructions you are passing to Kalix. Kalix will process the instructions on your
   * behalf and ensure that any data that needs to be persisted will be persisted.
   * <p>
   * Each Kalix component defines its own effects, which are a set of predefined
   * operations that match the capabilities of that component.
   * <p>
   * A Workflow Effect can:
   * <p>
   * <ul>
   *   <li>update the state of the workflow
   *   <li>define the next step to be executed (transition)
   *   <li>pause the workflow
   *   <li>end the workflow
   *   <li>fail the step or reject a command by returning an error
   *   <li>reply to incoming commands
   * </ul>
   * <p>
   * A return type to allow returning failures or attaching effects to messages.
   *
   * @param <T> The type of the message that must be returned by this call.
   */
  public interface Effect<T> {

    /**
     * Construct the effect that is returned by the command handler or a step transition.
     * <p>
     * The effect describes next processing actions, such as updating state, transition to another step
     * and sending a reply.
     *
     * @param <S> The type of the state for this workflow.
     */
    interface Builder<S> {

      @ApiMayChange
      PersistenceEffectBuilder<S> updateState(S newState);

      /**
       * Pause the workflow execution and wait for an external input, e.g. via command handler.
       */
      @ApiMayChange
      TransitionalEffect<Void> pause();

      /**
       * Defines the next step to which the workflow should transition to.
       * <p>
       * The step definition identified by {@code stepName} must have an input parameter of type I.
       * In other words, the next step call (or asyncCall) must have been defined with a {@link Function} that
       * accepts an input parameter of type I.
       *
       * @param stepName The step name that should be executed next.
       * @param input    The input param for the next step.
       */
      @ApiMayChange
      <I> TransitionalEffect<Void> transitionTo(String stepName, I input);

      /**
       * Defines the next step to which the workflow should transition to.
       * <p>
       * The step definition identified by {@code stepName} must not have an input parameter.
       * In other words, the next step call (or asyncCall) must have been defined with a {@link Supplier} function.
       *
       * @param stepName The step name that should be executed next.
       */
      @ApiMayChange
      TransitionalEffect<Void> transitionTo(String stepName);


      /**
       * Finish the workflow execution.
       * After transition to {@code end}, no more transitions are allowed.
       */
      @ApiMayChange
      TransitionalEffect<Void> end();

      /**
       * Create a message reply.
       *
       * @param replyMessage The payload of the reply.
       * @param <R>          The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> Effect<R> reply(R replyMessage);


      /**
       * Reply after for example <code>updateState</code>.
       *
       * @param message  The payload of the reply.
       * @param metadata The metadata for the message.
       * @param <R>      The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> Effect<R> reply(R message, Metadata metadata);

      /**
       * Create an error reply.
       *
       * @param description The description of the error.
       * @param <R>         The type of the message that must be returned by this call.
       * @return An error reply.
       */
      <R> ErrorEffect<R> error(String description);

      /**
       * Create an error reply with a gRPC status code.
       *
       * @param description The description of the error.
       * @param statusCode  A custom gRPC status code.
       * @param <R>         The type of the message that must be returned by this call.
       * @return An error reply.
       */
      <R> ErrorEffect<R> error(String description, Status.Code statusCode);

      /**
       * Create an error reply with an HTTP status code.
       *
       * @param description   The description of the error.
       * @param httpErrorCode A custom Kalix status code to represent the error.
       * @param <R>           The type of the message that must be returned by this call.
       * @return An error reply.
       */
      <R> ErrorEffect<R> error(String description, StatusCode.ErrorCode httpErrorCode);
    }

    interface ErrorEffect<T> extends Effect<T> {
    }

    /**
     * A workflow effect type that contains information about the transition to the next step.
     * This could be also a special transition to pause or end the workflow.
     */
    interface TransitionalEffect<T> extends Effect<T> {

      /**
       * Reply after for example <code>updateState</code>.
       *
       * @param message The payload of the reply.
       * @param <R>     The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> Effect<R> thenReply(R message);

      /**
       * Reply after for example <code>updateState</code>.
       *
       * @param message  The payload of the reply.
       * @param metadata The metadata for the message.
       * @param <R>      The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> Effect<R> thenReply(R message, Metadata metadata);
    }

    interface PersistenceEffectBuilder<T> {

      /**
       * Pause the workflow execution and wait for an external input, e.g. via command handler.
       */
      @ApiMayChange
      TransitionalEffect<Void> pause();

      /**
       * Defines the next step to which the workflow should transition to.
       * <p>
       * The step definition identified by {@code stepName} must have an input parameter of type I.
       * In other words, the next step call (or asyncCall) must have been defined with a {@link Function} that
       * accepts an input parameter of type I.
       *
       * @param stepName The step name that should be executed next.
       * @param input    The input param for the next step.
       */
      @ApiMayChange
      <I> TransitionalEffect<Void> transitionTo(String stepName, I input);

      /**
       * Defines the next step to which the workflow should transition to.
       * <p>
       * The step definition identified by {@code stepName} must not have an input parameter.
       * In other words, the next step call (or asyncCall) must have been defined with a {@link Supplier}.
       *
       * @param stepName The step name that should be executed next.
       */
      @ApiMayChange
      TransitionalEffect<Void> transitionTo(String stepName);

      /**
       * Finish the workflow execution.
       * After transition to {@code end}, no more transitions are allowed.
       */
      @ApiMayChange
      TransitionalEffect<Void> end();
    }


  }

  public static class WorkflowDef<S> {

    final private List<Step> steps = new ArrayList<>();
    final private List<StepConfig> stepConfigs = new ArrayList<>();
    final private Set<String> uniqueNames = new HashSet<>();
    private Optional<Duration> workflowTimeout = Optional.empty();
    private Optional<String> failoverStepName = Optional.empty();
    private Optional<Object> failoverStepInput = Optional.empty();
    private Optional<MaxRetries> failoverMaxRetries = Optional.empty();
    private Optional<Duration> stepTimeout = Optional.empty();
    private Optional<RecoverStrategy<?>> stepRecoverStrategy = Optional.empty();


    private WorkflowDef() {
    }

    public Optional<Step> findByName(String name) {
      return steps.stream().filter(s -> s.name().equals(name)).findFirst();
    }

    /**
     * Add step to workflow definition. Step name must be unique.
     *
     * @param step A workflow step
     */
    public WorkflowDef<S> addStep(Step step) {
      addStepWithValidation(step);
      return this;
    }

    /**
     * Add step to workflow definition with a dedicated {@link RecoverStrategy}. Step name must be unique.
     *
     * @param step            A workflow step
     * @param recoverStrategy A Step recovery strategy
     */
    public WorkflowDef<S> addStep(Step step, RecoverStrategy<?> recoverStrategy) {
      addStepWithValidation(step);
      stepConfigs.add(new StepConfig(step.name(), step.timeout(), Optional.of(recoverStrategy)));
      return this;
    }

    private void addStepWithValidation(Step step) {
      if (uniqueNames.contains(step.name()))
        throw new IllegalArgumentException("Name '" + step.name() + "' is already in use by another step in this workflow");

      this.steps.add(step);
      this.uniqueNames.add(step.name());
    }


    /**
     * Define a timeout for the duration of the entire workflow. When the timeout expires, the workflow is finished and no transitions are allowed.
     *
     * @param timeout Timeout duration
     */
    public WorkflowDef<S> timeout(Duration timeout) {
      this.workflowTimeout = Optional.of(timeout);
      return this;
    }

    /**
     * Define a failover step name after workflow timeout. Note that recover strategy for this step can set only the number of max retries.
     *
     * @param stepName   A failover step name
     * @param maxRetries A recovery strategy for failover step.
     */
    public WorkflowDef<S> failoverTo(String stepName, MaxRetries maxRetries) {
      this.failoverStepName = Optional.of(stepName);
      this.failoverMaxRetries = Optional.of(maxRetries);
      return this;
    }

    /**
     * Define a failover step name after workflow timeout. Note that recover strategy for this step can set only the number of max retries.
     *
     * @param stepName   A failover step name
     * @param stepInput  A failover step input
     * @param maxRetries A recovery strategy for failover step.
     */
    public <I> WorkflowDef<S> failoverTo(String stepName, I stepInput, MaxRetries maxRetries) {
      this.failoverStepName = Optional.of(stepName);
      this.failoverStepInput = Optional.of(stepInput);
      this.failoverMaxRetries = Optional.of(maxRetries);
      return this;
    }

    /**
     * Define a default step timeout. If not set, a default value of 5 seconds is used.
     * Can be overridden with step configuration.
     */
    public WorkflowDef<S> defaultStepTimeout(Duration timeout) {
      this.stepTimeout = Optional.of(timeout);
      return this;
    }

    /**
     * Define a default step recovery strategy. Can be overridden with step configuration.
     */
    public WorkflowDef<S> defaultStepRecoverStrategy(RecoverStrategy recoverStrategy) {
      this.stepRecoverStrategy = Optional.of(recoverStrategy);
      return this;
    }

    public Optional<Duration> getWorkflowTimeout() {
      return workflowTimeout;
    }

    public Optional<Duration> getStepTimeout() {
      return stepTimeout;
    }

    public Optional<RecoverStrategy<?>> getStepRecoverStrategy() {
      return stepRecoverStrategy;
    }

    public List<Step> getSteps() {
      return steps;
    }

    public List<StepConfig> getStepConfigs() {
      return stepConfigs;
    }

    public Optional<String> getFailoverStepName() {
      return failoverStepName;
    }

    public Optional<?> getFailoverStepInput() {
      return failoverStepInput;
    }

    public Optional<MaxRetries> getFailoverMaxRetries() {
      return failoverMaxRetries;
    }
  }


  public WorkflowDef<S> workflow() {
    return new WorkflowDef<>();
  }


  public interface Step {
    String name();

    Optional<Duration> timeout();
  }

  public static class CallStep<CallInput, DefCallInput, DefCallOutput, FailoverInput> implements Step {

    final private String _name;
    final public Function<CallInput, DeferredCall<DefCallInput, DefCallOutput>> callFunc;
    final public Function<DefCallOutput, Effect.TransitionalEffect<Void>> transitionFunc;
    final public Class<CallInput> callInputClass;
    final public Class<DefCallOutput> transitionInputClass;
    private Optional<Duration> _timeout = Optional.empty();

    public CallStep(String name,
                    Class<CallInput> callInputClass,
                    Function<CallInput, DeferredCall<DefCallInput, DefCallOutput>> callFunc,
                    Class<DefCallOutput> transitionInputClass,
                    Function<DefCallOutput, Effect.TransitionalEffect<Void>> transitionFunc) {
      _name = name;
      this.callInputClass = callInputClass;
      this.callFunc = callFunc;
      this.transitionInputClass = transitionInputClass;
      this.transitionFunc = transitionFunc;
    }

    @Override
    public String name() {
      return this._name;
    }

    @Override
    public Optional<Duration> timeout() {
      return this._timeout;
    }

    /**
     * Define a step timeout.
     */
    public CallStep<CallInput, DefCallInput, DefCallOutput, FailoverInput> timeout(Duration timeout) {
      this._timeout = Optional.of(timeout);
      return this;
    }
  }

  public static class AsyncCallStep<CallInput, CallOutput, FailoverInput> implements Step {

    final private String _name;
    final public Function<CallInput, CompletionStage<CallOutput>> callFunc;
    final public Function<CallOutput, Effect.TransitionalEffect<Void>> transitionFunc;
    final public Class<CallInput> callInputClass;
    final public Class<CallOutput> transitionInputClass;
    private Optional<Duration> _timeout = Optional.empty();

    public AsyncCallStep(String name,
                         Class<CallInput> callInputClass,
                         Function<CallInput, CompletionStage<CallOutput>> callFunc,
                         Class<CallOutput> transitionInputClass,
                         Function<CallOutput, Effect.TransitionalEffect<Void>> transitionFunc) {
      _name = name;
      this.callInputClass = callInputClass;
      this.callFunc = callFunc;
      this.transitionInputClass = transitionInputClass;
      this.transitionFunc = transitionFunc;
    }

    @Override
    public String name() {
      return this._name;
    }

    @Override
    public Optional<Duration> timeout() {
      return this._timeout;
    }

    /**
     * Define a step timeout.
     */
    public AsyncCallStep<CallInput, CallOutput, FailoverInput> timeout(Duration timeout) {
      this._timeout = Optional.of(timeout);
      return this;
    }
  }

  /**
   * Start a step definition with a given step name.
   *
   * @param name Step name.
   * @return Step builder.
   */
  @ApiMayChange
  public static Workflow.StepBuilder step(String name) {
    return new Workflow.StepBuilder(name);
  }

  public static class StepConfig {
    public final String stepName;
    public final Optional<Duration> timeout;
    public final Optional<RecoverStrategy<?>> recoverStrategy;

    public StepConfig(String stepName, Optional<Duration> timeout, Optional<RecoverStrategy<?>> recoverStrategy) {
      this.stepName = stepName;
      this.timeout = timeout;
      this.recoverStrategy = recoverStrategy;
    }
  }

  public static class RecoverStrategy<T> {

    public final int maxRetries;
    public final String failoverStepName;
    public final Optional<T> failoverStepInput;

    public RecoverStrategy(int maxRetries, String failoverStepName, Optional<T> failoverStepInput) {
      this.maxRetries = maxRetries;
      this.failoverStepName = failoverStepName;
      this.failoverStepInput = failoverStepInput;
    }

    /**
     * Retry strategy without failover configuration
     */
    public static class MaxRetries {
      public final int maxRetries;

      public MaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
      }

      /**
       * Once max retries is exceeded, transition to a given step name.
       */
      public RecoverStrategy<?> failoverTo(String stepName) {
        return new RecoverStrategy<>(maxRetries, stepName, Optional.<Void>empty());
      }

      /**
       * Once max retries is exceeded, transition to a given step name with the input parameter.
       */
      public <T> RecoverStrategy<T> failoverTo(String stepName, T input) {
        return new RecoverStrategy<>(maxRetries, stepName, Optional.of(input));
      }

      public int getMaxRetries() {
        return maxRetries;
      }
    }

    /**
     * Set the number of retires for a failed step, <code>maxRetries</code> equals 0 means that the step won't retry in case of failure.
     */
    public static MaxRetries maxRetries(int maxRetries) {
      return new MaxRetries(maxRetries);
    }

    /**
     * In case of a step failure don't retry but transition to a given step name.
     */
    public static RecoverStrategy<?> failoverTo(String stepName) {
      return new RecoverStrategy<>(0, stepName, Optional.<Void>empty());
    }

    /**
     * In case of a step failure don't retry but transition to a given step name with the input parameter.
     */
    public static <T> RecoverStrategy<T> failoverTo(String stepName, T input) {
      return new RecoverStrategy<>(0, stepName, Optional.of(input));
    }
  }

  public static class StepBuilder {

    final private String name;

    public StepBuilder(String name) {
      this.name = name;
    }

    /**
     * Build a step action with a call to an existing Kalix component via {@link DeferredCall}.
     * <p>
     * The {@link Function} passed to this method should return a {@link DeferredCall}.
     * The {@link DeferredCall} is then executed by Kalix and its result, if successful, is made available to this workflow via the {@code andThen} method.
     * In the {@code andThen} method, we can use the result to update the workflow state and transition to the next step.
     * <p>
     * On failure, the step will be retried according to the default retry strategy or the one defined in the step configuration.
     *
     * @param callInputClass  Input class for call factory.
     * @param callFactory     Factory method for creating deferred call.
     * @param <Input>         Input for deferred call factory, provided by transition method.
     * @param <DefCallInput>  Input for deferred call.
     * @param <DefCallOutput> Output of deferred call.
     * @return Step builder.
     */
    @ApiMayChange
    public <Input, DefCallInput, DefCallOutput> CallStepBuilder<Input, DefCallInput, DefCallOutput> call(Class<Input> callInputClass, Function<Input, DeferredCall<DefCallInput, DefCallOutput>> callFactory) {
      return new CallStepBuilder<>(name, callInputClass, callFactory);
    }

    /**
     * Build a step action with a call to an existing Kalix component via {@link DeferredCall}.
     * <p>
     * The {@link Supplier} function passed to this method should return a {@link DeferredCall}.
     * The {@link DeferredCall} is then executed by Kalix and its result, if successful, is made available to this workflow via the {@code andThen} method.
     * In the {@code andThen} method, we can use the result to update the workflow state and transition to the next step.
     * <p>
     * On failure, the step will be retried according to the default retry strategy or the one defined in the step configuration.
     *
     * @param callSupplier    Factory method for creating deferred call.
     * @param <DefCallInput>  Input for deferred call.
     * @param <DefCallOutput> Output of deferred call.
     * @return Step builder.
     */
    @ApiMayChange
    public <DefCallInput, DefCallOutput> CallStepBuilder<Void, DefCallInput, DefCallOutput> call(Supplier<DeferredCall<DefCallInput, DefCallOutput>> callSupplier) {
      return new CallStepBuilder<>(name, Void.class, (Void v) -> callSupplier.get());
    }

    /**
     * Build a step action with an async call.
     * <p>
     * The {@link Function} passed to this method should return a {@link CompletionStage}.
     * On successful completion, its result is made available to this workflow via the {@code andThen} method.
     * In the {@code andThen} method, we can use the result to update the workflow state and transition to the next step.
     * <p>
     * On failure, the step will be retried according to the default retry strategy or the one defined in the step configuration.
     *
     * @param callInputClass Input class for call factory.
     * @param callFactory    Factory method for creating async call.
     * @param <Input>        Input for async call factory, provided by transition method.
     * @param <Output>       Output of async call.
     * @return Step builder.
     */
    @ApiMayChange
    public <Input, Output> AsyncCallStepBuilder<Input, Output> asyncCall(Class<Input> callInputClass, Function<Input, CompletionStage<Output>> callFactory) {
      return new AsyncCallStepBuilder<>(name, callInputClass, callFactory);
    }


    /**
     * Build a step action with an async call.
     * <p>
     * The {@link Supplier} function passed to this method should return a {@link CompletionStage}.
     * On successful completion, its result is made available to this workflow via the {@code andThen} method.
     * In the {@code andThen} method, we can use the result to update the workflow state and transition to the next step.
     * <p>
     * On failure, the step will be retried according to the default retry strategy or the one defined in the step configuration.
     *
     * @param callSupplier Factory method for creating async call.
     * @param <Output>     Output of async call.
     * @return Step builder.
     */
    @ApiMayChange
    public <Output> AsyncCallStepBuilder<Void, Output> asyncCall(Supplier<CompletionStage<Output>> callSupplier) {
      return new AsyncCallStepBuilder<>(name, Void.class, (Void v) -> callSupplier.get());
    }


    public static class CallStepBuilder<Input, DefCallInput, DefCallOutput> {

      final private String name;

      final private Class<Input> callInputClass;
      /* callFactory builds the DeferredCall that will be passed to proxy for execution */
      final private Function<Input, DeferredCall<DefCallInput, DefCallOutput>> callFunc;

      public CallStepBuilder(String name, Class<Input> callInputClass, Function<Input, DeferredCall<DefCallInput, DefCallOutput>> callFunc) {
        this.name = name;
        this.callInputClass = callInputClass;
        this.callFunc = callFunc;
      }

      /**
       * Transition to the next step based on the result of the step call.
       * <p>
       * The {@link Function} passed to this method should receive the return type of the step call and return
       * an {@link Effect.TransitionalEffect} describing the next step to transition to.
       * <p>
       * When defining the Effect, you can update the workflow state and indicate the next step to transition to.
       * This can be another step, or a pause or end of the workflow.
       * <p>
       * When transition to another step, you can also pass an input parameter to the next step.
       *
       * @param transitionInputClass Input class for transition.
       * @param transitionFunc       Function that transform the action result to a {@link Effect.TransitionalEffect}
       * @return CallStep
       */
      @ApiMayChange
      public CallStep<Input, DefCallInput, DefCallOutput, ?> andThen(Class<DefCallOutput> transitionInputClass, Function<DefCallOutput, Effect.TransitionalEffect<Void>> transitionFunc) {
        return new CallStep<>(name, callInputClass, callFunc, transitionInputClass, transitionFunc);
      }
    }

    public static class AsyncCallStepBuilder<CallInput, CallOutput> {

      final private String name;

      final private Class<CallInput> callInputClass;
      final private Function<CallInput, CompletionStage<CallOutput>> callFunc;

      public AsyncCallStepBuilder(String name, Class<CallInput> callInputClass, Function<CallInput, CompletionStage<CallOutput>> callFunc) {
        this.name = name;
        this.callInputClass = callInputClass;
        this.callFunc = callFunc;
      }

      /**
       * Transition to the next step based on the result of the step call.
       * <p>
       * The {@link Function} passed to this method should receive the return type of the step call and return
       * an {@link Effect.TransitionalEffect} describing the next step to transition to.
       * <p>
       * When defining the Effect, you can update the workflow state and indicate the next step to transition to.
       * This can be another step, or a pause or end of the workflow.
       * <p>
       * When transition to another step, you can also pass an input parameter to the next step.
       *
       * @param transitionInputClass Input class for transition.
       * @param transitionFunc       Function that transform the action result to a {@link Effect.TransitionalEffect}
       * @return AsyncCallStep
       */
      @ApiMayChange
      public AsyncCallStep<CallInput, CallOutput, ?> andThen(Class<CallOutput> transitionInputClass, Function<CallOutput, Effect.TransitionalEffect<Void>> transitionFunc) {
        return new AsyncCallStep<>(name, callInputClass, callFunc, transitionInputClass, transitionFunc);
      }
    }
  }
}
