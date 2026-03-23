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
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.multiagent.workflow.graph.TravelGuideGraphConfig;
import com.alibaba.fastjson2.JSON;
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
public record WeatherSearchNode(ChatModel chatModel, org.springframework.ai.tool.ToolCallbackProvider toolCallbackProvider,
                                org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties mcpClientCommonProperties) implements AsyncNodeAction {
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
        logger.info("WeatherSearchNode execute");
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
        var config = RunnableConfig.builder()
                .threadId(state.value("sessionId").toString())
                .build();
        try {
            Optional<NodeOutput> nodeOutput = agent.invokeAndGetOutput(state.value(TravelGuideGraphConfig.SEMANTIC_ANSWER).toString(),
                    config);

            if (nodeOutput.isPresent() && nodeOutput.get() instanceof InterruptionMetadata) {
                InterruptionMetadata interruptionMetadata = (InterruptionMetadata) nodeOutput.get();

                System.out.println("检测到中断，需要人工审批");
                List<InterruptionMetadata.ToolFeedback> toolFeedbacks =
                        interruptionMetadata.toolFeedbacks();

                for (InterruptionMetadata.ToolFeedback feedback : toolFeedbacks) {
                    System.out.println("工具: " + feedback.getName());
                    System.out.println("参数: " + feedback.getArguments());
                    System.out.println("描述: " + feedback.getDescription());
                }

                // 6. 模拟人工决策（这里选择批准）
                InterruptionMetadata.Builder feedbackBuilder = InterruptionMetadata.builder()
                        .nodeId(interruptionMetadata.node())
                        .state(interruptionMetadata.state());

                toolFeedbacks.forEach(toolFeedback -> {
                    // 控制台输入，允许用户查看并修改参数
                    System.out.println("是否修改参数？当前参数: " + toolFeedback.getArguments());
                    System.out.print("请输入新参数（直接回车保持原参数）: ");
                    java.util.Scanner scanner = new java.util.Scanner(System.in);
                    String userInput = scanner.nextLine().trim();

                    String editedArguments = userInput.isEmpty()
                            ? toolFeedback.getArguments()
                            : JSON.toJSONString(userInput);

                    InterruptionMetadata.ToolFeedback approvedFeedback =
                            InterruptionMetadata.ToolFeedback.builder(toolFeedback)
                                    .arguments(editedArguments)
                                    .result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
                                    .build();
                    feedbackBuilder.addToolFeedback(approvedFeedback);
                });

                InterruptionMetadata approvalMetadata = feedbackBuilder.build();

                // 7. 第二次调用 - 使用人工反馈恢复执行
                System.out.println(" === 第二次调用：使用批准决策恢复 ===");
                RunnableConfig resumeConfig = RunnableConfig.builder()
                        .threadId(state.value("sessionId").toString())
                        .addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, approvalMetadata)
                        .build();

                Optional<NodeOutput> finalResult = agent.invokeAndGetOutput("", resumeConfig);

                if (finalResult.isPresent()) {
                    System.out.println("执行完成");
                    return CompletableFuture.completedFuture(Map.of(TravelGuideGraphConfig.WEATHER_ANSWER, finalResult));
                }
                return CompletableFuture.completedFuture(Map.of(TravelGuideGraphConfig.WEATHER_ANSWER, Optional.empty()));
            }
            return CompletableFuture.completedFuture(Map.of(TravelGuideGraphConfig.WEATHER_ANSWER, nodeOutput));
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
    }
}