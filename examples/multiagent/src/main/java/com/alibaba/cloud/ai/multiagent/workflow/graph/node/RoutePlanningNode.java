/*
 * Copyright 2024-2026 the original author or authors.
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
 *
 */

package com.alibaba.cloud.ai.multiagent.workflow.graph.node;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.InterruptableAction;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.multiagent.workflow.graph.TravelGuideGraphConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 路线规划Node
 *
 * @author NGshiyu
 */
public record RoutePlanningNode(ToolCallbackProvider toolCallbackProvider,
                                org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties mcpClientCommonProperties) implements AsyncNodeAction
        , InterruptableAction {
    private static final Logger logger = LoggerFactory.getLogger(RoutePlanningNode.class);
    private final static String instruction = """
            你是一个智能路线规划助手，具备调用地图工具获取实时路线的能力。请引导用户提供起点、终点及出行偏好（如驾车、公交、步行），基于工具返回数据，输出清晰的规划方案，包含总距离、预计耗时及关键导航步骤，选择并提供一条最优路线。只专注于处理你自己的职责范围内的工作，屏蔽其他噪音内容，不要直接回答问题。
            """;

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        logger.info("RoutePlanningNode execute");
        //简单的筛选工具避免工具爆炸
        ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
        List<ToolCallback> maps = Arrays.stream(toolCallbacks).filter(toolCallback ->
                toolCallback.getToolDefinition().name().contains("maps")).toList();
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
                .build();
        // 创建 ChatModel
        ChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-plus")
                        .maxToken(200)           // 核采样参数
                        .build())
                .build();
        //Run React Agent With MCP Tools
        Builder builder = ReactAgent.builder()
                .name("route_planning_assistant")
                .model(chatModel)
                .description("plan your route")
                .instruction(instruction)
                .tools(maps)
                .saver((MemorySaver) state.value("memorySaver").get());

        ReactAgent agent = builder.build();
        // 使用独立的 threadId 隔离消息历史，避免不同节点之间的消息污染
        var config = RunnableConfig.builder()
                .threadId(state.value("sessionId").toString() + "_route_planning")
                .build();
        //stream

        try {
            Flux<NodeOutput> stream = agent.stream(state.value(TravelGuideGraphConfig.SEMANTIC_ANSWER).toString(),
                    config);
            return CompletableFuture.completedFuture(Map.of(TravelGuideGraphConfig.ROUTE_ANSWER, stream));
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }

        //try {
        //    AssistantMessage call = agent.call(state.value(TravelGuideGraphConfig.SEMANTIC_ANSWER).toString(),
        //            config);
        //    return CompletableFuture.completedFuture(Map.of(TravelGuideGraphConfig.ROUTE_ANSWER, call));
        //} catch (GraphRunnerException e) {
        //    throw new RuntimeException(e);
        //}
    }

    /**
     * Determines whether the graph execution should be interrupted BEFORE the current node executes.
     * <p>
     * This method is called before the node action's {@code apply()} method is invoked.
     *
     * @param nodeId The identifier of the current node being processed.
     * @param state  The current state of the agent.
     * @param config The runnable configuration.
     *
     * @return An {@link Optional} containing {@link InterruptionMetadata} if the
     * execution should be interrupted. Returns an empty {@link Optional} to continue
     * execution.
     */
    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
        return Optional.empty();
    }

    /**
     * Determines whether the graph execution should be interrupted AFTER the current node executes.
     * <p>
     * This method is called after the node action's {@code apply()} method has completed,
     * but before the action result is merged into the state. This allows inspection of the
     * action result to decide whether to interrupt.
     * <p>
     * If this method returns an {@link InterruptionMetadata}, the action result will be
     * merged into the state and a checkpoint will be created before returning the interruption.
     *
     * @param nodeId       The identifier of the current node being processed.
     * @param state        The current state of the agent (before merging action result).
     * @param actionResult The result returned by the node action's {@code apply()} method.
     * @param config       The runnable configuration.
     *
     * @return An {@link Optional} containing {@link InterruptionMetadata} if the
     * execution should be interrupted. Returns an empty {@link Optional} to continue
     * execution. Default implementation returns empty (no interruption).
     */
    @Override
    public Optional<InterruptionMetadata> interruptAfter(String nodeId, OverAllState state, Map<String, Object> actionResult, RunnableConfig config) {
        return InterruptableAction.super.interruptAfter(nodeId, state, actionResult, config);
    }
}