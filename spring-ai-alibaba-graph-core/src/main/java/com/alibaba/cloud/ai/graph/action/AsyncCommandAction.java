/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.graph.action;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import io.opentelemetry.context.Context;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Represents an asynchronous command action that operates on an agent state with configuration and returns a command.
 *
 */
public interface AsyncCommandAction extends BiFunction<OverAllState, RunnableConfig, CompletableFuture<Command>>, ActionLifecycle<Exception> {

    /**
     * Applies this action to the given agent state and configuration.
     *
     * @param state  the agent state
     * @param config the runnable configuration
     *
     * @return a CompletableFuture representing the command result
     */
    CompletableFuture<Command> apply(OverAllState state, RunnableConfig config);

    /**
     * execute the action with pre- and post-operations
     *
     * @param state  the agent state
     * @param config the runnable configuration
     *
     * @return a CompletableFuture representing the command result
     */
    default CompletableFuture<Command> execute(OverAllState state, RunnableConfig config) {
        preHandler();
        return apply(state, config)
                .whenComplete((command, throwable) -> postHandler());

    }

    static AsyncCommandAction node_async(CommandAction syncAction) {
        return (state, config) -> {
            Context context = Context.current();
            var result = new CompletableFuture<Command>();
            try {
                result.complete(syncAction.execute(state, config));
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
            return result;
        };
    }

    static AsyncCommandAction of(AsyncEdgeAction action) {
        return (state, config) -> action.execute(state).thenApply(Command::new);
    }

}
