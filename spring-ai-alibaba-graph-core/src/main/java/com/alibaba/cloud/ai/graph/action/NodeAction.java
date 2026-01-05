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

import java.util.Map;

/**
 * Represents a node action that operates on an agent state and returns state updates.
 *
 */
@FunctionalInterface
public interface NodeAction extends ActionLifecycle<Exception> {

    /**
     * Applies this action to the given agent state.
     *
     * @param state the agent state
     *
     * @return state updates as a map
     *
     * @throws Exception if an error occurs during the action
     */
    Map<String, Object> apply(OverAllState state) throws Exception;

    /**
     * execute the action with pre- and post-operations
     *
     * @param state the agent state
     *
     * @return state updates as a map
     *
     * @throws Exception if an error occurs during the action
     */
    default Map<String, Object> execute(OverAllState state) throws Exception {
        try {
            preHandler();
            return apply(state);
        } finally {
            postHandler();
        }
    }

}
