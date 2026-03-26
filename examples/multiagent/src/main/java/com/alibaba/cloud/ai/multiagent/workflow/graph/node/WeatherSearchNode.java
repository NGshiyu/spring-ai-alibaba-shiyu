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
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.multiagent.workflow.graph.TravelGuideGraphConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 语义理解Node
 *
 * @author NGshiyu
 */
public record WeatherSearchNode(org.springframework.ai.tool.ToolCallbackProvider toolCallbackProvider,
                                org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties mcpClientCommonProperties)
        //implements NodeAction,
implements AsyncNodeAction,
        InterruptableAction {
    private static final Logger logger = LoggerFactory.getLogger(WeatherSearchNode.class);
    private static final String instruction = """
            # Role
            智能天气助手
            
            # Profile
            你擅长调用工具获取实时天气数据，并将信息转化为清晰易读的预报计划。
            
            # Rules
            1. **日期逻辑**：若用户提供日期，查询该日天气；若未提供，默认查询最近 7 天预报。
            2. **信息确认**：提取输入中的城市信息，避免干扰，若缺失城市信息，请先追问。
            3. **输出要求**：数据展示结构化（温度、状况），并根据天气适当给出穿衣或出行建议。
            """;

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
    //public Map<String, Object> apply(OverAllState state) {
        logger.info("WeatherSearchNode execute");
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
        //简单的筛选工具避免工具爆炸
        ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
        List<ToolCallback> tools = Arrays.stream(toolCallbacks).filter(toolCallback ->
                toolCallback.getToolDefinition().name().contains("weather")).toList();
        // 创建人工介入Hook - 麦当劳所有工具调用都需要用户确认
        HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder()
                // === 餐品信息查询 ===
                .approvalOn("maps_weather", ToolConfig.builder()
                        .description("根据城市名称或者标准adcode查询指定城市的天气")
                        .build())
                .build();
        //Run React Agent With MCP Tools
        Builder builder = ReactAgent.builder()
                .name("weather_search_assistant")
                .model(chatModel)
                .description("Use the tool to search the weather for a given profile")
                .tools(tools)
                .hooks(humanInTheLoopHook)
                .instruction(instruction)
                .saver((MemorySaver) state.value("memorySaver").get());

        ReactAgent agent = builder.build();
        // 使用独立的 threadId 隔离消息历史，避免不同节点之间的消息污染
        var config = RunnableConfig.builder()
                .threadId(state.value("sessionId") + "_weather_search")
                .build();
        try {
            Optional<NodeOutput> nodeOutput = agent.invokeAndGetOutput(state.value(TravelGuideGraphConfig.SEMANTIC_ANSWER).get().toString(),
                    config);
            return CompletableFuture.completedFuture(Map.of(TravelGuideGraphConfig.WEATHER_ANSWER, nodeOutput));
            //return Map.of(TravelGuideGraphConfig.WEATHER_ANSWER, nodeOutput);
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
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
        Optional<NodeOutput> nodeOutput = (Optional<NodeOutput>) actionResult.get(TravelGuideGraphConfig.WEATHER_ANSWER);
        if (nodeOutput.isPresent() && nodeOutput.get() instanceof InterruptionMetadata interruptionMetadata) {
            //直接返回前端，终止本次执行
            return Optional.of(interruptionMetadata);
        }
        else {
            return InterruptableAction.super.interruptAfter(nodeId, state, actionResult, config);
        }
    }
}