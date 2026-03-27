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
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.multiagent.workflow.graph.TravelGuideGraphConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 计划生成
 *
 * @author NGshiyu
 */
public record PlanGenerationNode() implements NodeAction {
    private static final Logger logger = LoggerFactory.getLogger(PlanGenerationNode.class);
    private final static String instruction = """
                 # Role: 资深项目规划与执行专家
            
                 # Profile:
                 你擅长从杂乱的信息中提炼核心目标，制定逻辑清晰、可落地的执行计划，并能基于经验提供具有前瞻性的建议。你的表达风格简洁、专业且极具可读性。
            
                 # Task:
                 请根据我提供的【原始信息】，整理并输出一份结构化的执行计划。
            
                 # Constraints & Style:
                 1. **高可读性**：使用 Markdown 格式，合理运用标题、加粗、列表和表格，避免大段文字堆砌。
                 2. **表达清晰**：语言简练，去除冗余信息，确保每个步骤都有明确的主语和动作。
                 3. **逻辑严密**：计划需按照时间顺序或优先级排序，确保流程顺畅。
                 4. **建议务实**：给出的建议需针对潜在风险或优化点，具有实际指导意义。
            
                 # Workflow:
                 1. **信息梳理**：分析原始信息，提炼核心目标、关键资源和限制条件。
                 2. **计划制定**：将任务拆解为具体步骤（Action Items），明确阶段或时间节点。
                 3. **建议补充**：基于计划内容，识别潜在难点，给出优化建议或风险提示。
                 4. **格式优化**：检查排版，确保视觉清晰。
            
                 # Output Format:
                 请严格按照以下结构输出：
            
                 ## 1. 🎯 核心目标总结
                 (用一句话或简短段落概括计划的核心目的)
            
                 ## 2. 📅 执行计划详情
                 (建议使用表格或有序列表，包含阶段、关键任务、预期产出/备注)
                 | 阶段/时间 | 关键任务 | 执行要点 | 预期结果 |
                 | :--- | :--- | :--- | :--- |
                 | ... | ... | ... | ... |
            
                 ## 3. 💡 专家建议与风险提示
                 - **优化建议**：(针对效率、资源分配等方面的具体建议)
                 - **潜在风险**：(可能遇到的问题及应对预案)
                 - **关键成功因素**：(决定计划成败的 1-3 个关键点)
            
                 ---
            """;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        logger.info("PlanGenerationNode execute");
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
        //Run React Agent With MCP Tools
        Builder builder = ReactAgent.builder()
                .name("plan_generation_assistant")
                .model(chatModel)
                .description("generate plan")
                .instruction(instruction)
                .saver((MemorySaver) state.value("memorySaver").get());
        var config = RunnableConfig.builder()
                .threadId(state.value("sessionId").get().toString())
                .build();
        ReactAgent agent = builder.build();
        Flux<NodeOutput> stream = agent.stream(state.value(TravelGuideGraphConfig.ROUTE_ANSWER).toString(),
                config);
        //ReActAgent agent = ReActAgent.builder();
        //        .name("plan_generation_assistant")
        //        .sysPrompt("You are a helpful AI assistant.")
        //        .model(DashScopeChatModel.builder()
        //                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
        //                //.enableThinking(true)  // 自动启用流式输出
        //                .modelName("qwen-plus")
        //                .stream(true)
        //                .build())
        //        .build();
        //Msg msg = Msg.builder().content().role(MsgRole.USER).build();
        //Flux<Event> stream = agent.stream(msg);


        return Map.of(TravelGuideGraphConfig.GENERATION_ANSWER, stream);
    }
}