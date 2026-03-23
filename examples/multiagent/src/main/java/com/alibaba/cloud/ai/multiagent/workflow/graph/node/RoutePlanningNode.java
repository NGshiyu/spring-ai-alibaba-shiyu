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

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.multiagent.workflow.graph.TravelGuideGraphConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 路线规划Node
 *
 * @author NGshiyu
 */
public record RoutePlanningNode(org.springframework.ai.chat.model.ChatModel chatModel, ToolCallbackProvider toolCallbackProvider,
                                org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties mcpClientCommonProperties) implements AsyncNodeAction {
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
        //Run React Agent With MCP Tools
        Builder builder = ReactAgent.builder()
                .name("route_planning_assistant")
                .model(chatModel)
                .description("plan your route")
                .instruction(instruction)
                .tools(maps)
                .saver((MemorySaver) state.value("memorySaver").get());

        ReactAgent agent = builder.build();
        var config = RunnableConfig.builder()
                .threadId(state.value("sessionId").toString())
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
}