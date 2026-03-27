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
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.multiagent.workflow.graph.TravelGuideGraphConfig;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.Map;

/**
 * 语义理解Node
 *
 * @author NGshiyu
 */
public record SemanticUnderstandingNode(ToolCallbackProvider toolCallbackProvider,
                                        org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties mcpClientCommonProperties)
        implements NodeAction {
    private static final Logger logger = LoggerFactory.getLogger(SemanticUnderstandingNode.class);
    private final static String instruction = """
            你是一个语义理解工具，负责将用户输入转化为更利于大模型理解的表达，须严格遵守仅输出优化后的单句内容、绝不曲解原意且在保持核心意图不变的前提下尽可能完善细节。
            """;


    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        logger.info("SemanticUnderstandingNode execute");
        //二次调用则处理规避
        if (Boolean.parseBoolean(state.value("isFeedback").toString())) {
            return Map.of();
        }
        // 创建 DashScope API 实例
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
                .build();
        // 创建 ChatModel
        ChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model("qwen-plus")
                        .maxToken(2000)           // 核采样参数
                        .build())
                .build();
        Builder builder = ReactAgent.builder()
                .name("semantic_understanding_assistant")
                .model(chatModel)
                .description("Understand the user's input content and output one sentence")
                .instruction(instruction)
                .saver((MemorySaver) state.value("memorySaver").get());
        //if (toolCallbackProvider != null) {
        //    builder.toolCallbackProviders(toolCallbackProvider);
        //}
        //else {
        //    builder.tools(toolCallback);
        //}
        ReactAgent agent = builder.build();
        // 使用独立的 threadId 隔离消息历史，避免不同节点之间的消息污染
        var config = RunnableConfig.builder()
                .threadId(state.value("sessionId") + "_semantic")
                .build();
        //stream
        AssistantMessage question = agent.call(state.value("question").toString(), config);
        return Map.of(TravelGuideGraphConfig.SEMANTIC_ANSWER, StringUtils.isNotBlank(question.getText()) ? question.getText() : state.value("question").toString());
        //return Map.of(TravelGuideGraphConfig.SEMANTIC_ANSWER, Flux.fromIterable(List.of(StringUtils.isNotBlank(question.getText()) ? question.getText() : state.value
        // ("question").toString())));
    }
}
