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

package com.alibaba.cloud.ai.multiagent.workflow;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * graph执行
 *
 * @author NGshiyu
 */
@RestController
@RequestMapping
public class GraphTestController {
    private static final Logger logger = LoggerFactory.getLogger(GraphTestController.class);
    @Autowired
    private ToolCallbackProvider toolCallbackProvider;
    @Autowired
    ChatModel chatModel;

    @PostMapping("/testGraph")
    public void testGraph() throws GraphStateException {
        // 1. 创建工具回调
        ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
        List<ToolCallback> tools = Arrays.stream(toolCallbacks).filter(toolCallback ->
                toolCallback.getToolDefinition().name().contains("weather")).toList();

        // 2. 配置检查点保存器（工作流和Agent共享）
        MemorySaver saver = new MemorySaver();

        // 3. 创建带有人工介入Hook的ReactAgent
        ReactAgent qaAgent = ReactAgent.builder()
                .name("qa_agent")
                .model(chatModel)
                .instruction("你是一个问答专家，负责回答用户的问题。如果需要搜索天气信息，请使用maps_weather工具。用户问题：{cleaned_input}")
                .outputKey("qa_result")
                .saver(saver)
                .hooks(HumanInTheLoopHook.builder()
                        .approvalOn("maps_weather", ToolConfig.builder()
                                .description("搜索操作需要人工审批，请确认是否执行搜索")
                                .build())
                        .build())
                .tools(tools)
                .build();

        // 4. 创建自定义Node（预处理）
        class PreprocessorNode implements NodeAction {
            @Override
            public Map<String, Object> apply(OverAllState state) throws Exception {
                String input = state.value("input", "").toString();
                String cleaned = input.trim();
                return Map.of("cleaned_input", cleaned);
            }
        }

        // 5. 创建自定义Node（验证）
        class ValidatorNode implements NodeAction {
            @Override
            public Map<String, Object> apply(OverAllState state) throws Exception {
                Optional<Object> qaResultOpt = state.value("qa_result");
                if (qaResultOpt.isPresent() && qaResultOpt.get() instanceof Message message) {
                    boolean isValid = message.getText().length() > 30;
                    return Map.of("is_valid", isValid);
                }
                return Map.of("is_valid", false);
            }
        }

        // 6. 定义状态管理策略
        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("input", new ReplaceStrategy());
            strategies.put("cleaned_input", new ReplaceStrategy());
            strategies.put("qa_result", new ReplaceStrategy());
            strategies.put("is_valid", new ReplaceStrategy());
            return strategies;
        };

        // 7. 构建工作流
        StateGraph workflow = new StateGraph(keyStrategyFactory);

        // 添加普通Node
        workflow.addNode("preprocess", node_async(new PreprocessorNode()));
        workflow.addNode("validate", node_async(new ValidatorNode()));

        // 添加Agent Node（嵌套的ReactAgent）
        workflow.addNode(qaAgent.name(), qaAgent.asNode(
                true,   // includeContents: 传递父图的消息历史
                false   // includeReasoning: 不返回推理过程
        ));

        // 定义流程：预处理 -> Agent处理 -> 验证
        workflow.addEdge(StateGraph.START, "preprocess");
        workflow.addEdge("preprocess", qaAgent.name());
        workflow.addEdge(qaAgent.name(), "validate");

        // 条件边：验证通过则结束，否则重新处理
        workflow.addConditionalEdges(
                "validate",
                edge_async(state -> {
                    Boolean isValid = (Boolean) state.value("is_valid", false);
                    return isValid ? "end" : qaAgent.name();
                }),
                Map.of(
                        "end", StateGraph.END,
                        qaAgent.name(), qaAgent.name()
                )
        );

        // 8. 编译工作流（必须在CompileConfig中注册检查点保存器）
        CompiledGraph compiledGraph = workflow.compile(
                CompileConfig.builder()
                        .saverConfig(SaverConfig.builder().register(saver).build())
                        .build()
        );

        // 9. 执行工作流并处理中断
        String threadId = "workflow-hilt-001";
        Map<String, Object> input = Map.of("input", "上海今天天气怎么样");

        // 第一次调用 - 可能触发中断
        Optional<NodeOutput> nodeOutputOptional = compiledGraph.invokeAndGetOutput(
                input,
                RunnableConfig.builder().threadId(threadId).build()
        );

        // 检查是否发生中断
        if (nodeOutputOptional.isPresent()
                && nodeOutputOptional.get() instanceof InterruptionMetadata interruptionMetadata) {

            System.out.println("工作流被中断，等待人工审核。");
            System.out.println("中断节点: " + interruptionMetadata.node());

            List<InterruptionMetadata.ToolFeedback> feedbacks = interruptionMetadata.toolFeedbacks();

            // 显示所有需要审批的工具调用
            for (InterruptionMetadata.ToolFeedback feedback : feedbacks) {
                System.out.println("工具名称: " + feedback.getName());
                System.out.println("工具参数: " + feedback.getArguments());
                System.out.println("工具描述: " + feedback.getDescription());
            }

            // 构建人工反馈（批准所有工具调用）
            InterruptionMetadata.Builder feedbackBuilder = InterruptionMetadata.builder()
                    .nodeId(interruptionMetadata.node())
                    .state(interruptionMetadata.state());

            feedbacks.forEach(toolFeedback -> {
                feedbackBuilder.addToolFeedback(
                        InterruptionMetadata.ToolFeedback.builder(toolFeedback)
                                .result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
                                .build()
                );
            });

            InterruptionMetadata approvalMetadata = feedbackBuilder.build();

            // 使用批准决策恢复执行
            RunnableConfig resumableConfig = RunnableConfig.builder()
                    .threadId(threadId) // 相同的线程ID
                    .addHumanFeedback(approvalMetadata)
                    .build();

            // 恢复工作流执行（传入空Map，因为状态已保存在检查点中）
            nodeOutputOptional = compiledGraph.invokeAndGetOutput(Map.of(), resumableConfig);
        }
    }
}
