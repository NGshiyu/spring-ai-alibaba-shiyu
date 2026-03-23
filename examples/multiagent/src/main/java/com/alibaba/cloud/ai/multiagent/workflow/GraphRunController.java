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

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Sinks;

import java.util.*;

/**
 * graph执行
 *
 * @author NGshiyu
 */
@RestController
@RequestMapping
public class GraphRunController {
    private static final Logger logger = LoggerFactory.getLogger(GraphRunController.class);

    @Resource
    @Qualifier("travelGuideGraph")
    CompiledGraph travelGuideGraph;


    @PostMapping("/travelGuide")
    public void runGraph() throws GraphStateException {
        //!!! 关键配置1：全局的sessionId
        String sessionId = "travel_guide" + UUID.randomUUID();
        //!!! 关键配置2： 全局的检查点保存器（工作流和Agent共享）
        MemorySaver memorySaver = new MemorySaver();
        //!!! 关键配置3：定义一个全局的 config 和 graph 的 ReactAgent 智能体共享，以便于 HIL
        var config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();

        Map<String, Object> initialState = Map.of(
                "question", """
                        我想要周六从上海浦东新区浦软大厦开车出发去杭州滨江安恒大厦开会，我可以走哪一条路线？
                        杭州当天天气怎么样，我需要穿什么衣服？
                        """,
                //"question", """
                //        我想要周六从上海自驾出发去乌鲁木齐旅行，你有没有推荐的路线？我可以走哪一条路线？
                //        我沿途会经过哪些城市，天气怎么样？
                //        同时帮我查询一下沿途的麦当劳门店，我比较喜欢吃麦当劳""",
                "sessionId", sessionId,
                //!!! 定义一个全局的 ThreadId 和graph的智能体共享，以便于 HIL
                "memorySaver", memorySaver,
                "sink", Sinks.many().multicast().onBackpressureBuffer()
        );


        // 用于收集各 agent 的流式输出内容（使用 LinkedHashMap 保持顺序）
        Map<String, List<String>> agentOutputs = new LinkedHashMap<>();
        //Optional<NodeOutput> nodeOutputOptional = travelGuideGraph.invokeAndGetOutput(initialState);
        //// 检查是否发生中断
        //if (nodeOutputOptional.isPresent()
        //        && nodeOutputOptional.get() instanceof InterruptionMetadata interruptionMetadata) {
        //
        //    System.out.println("工作流被中断，等待人工审核。");
        //    System.out.println("中断节点: " + interruptionMetadata.node());
        //
        //    List<InterruptionMetadata.ToolFeedback> feedbacks = interruptionMetadata.toolFeedbacks();
        //
        //    // 显示所有需要审批的工具调用
        //    for (InterruptionMetadata.ToolFeedback feedback : feedbacks) {
        //        System.out.println("工具名称: " + feedback.getName());
        //        System.out.println("工具参数: " + feedback.getArguments());
        //        System.out.println("工具描述: " + feedback.getDescription());
        //    }
        //
        //    // 构建人工反馈（批准所有工具调用）
        //    InterruptionMetadata.Builder feedbackBuilder = InterruptionMetadata.builder()
        //            .nodeId(interruptionMetadata.node())
        //            .state(interruptionMetadata.state());
        //
        //    feedbacks.forEach(toolFeedback -> {
        //        feedbackBuilder.addToolFeedback(
        //                InterruptionMetadata.ToolFeedback.builder(toolFeedback)
        //                        .result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
        //                        .build()
        //        );
        //    });
        //}
        Set<String> setStr = new HashSet<>();

        travelGuideGraph.stream(initialState, config)
                .doOnNext(output -> {
                    setStr.add(output.node());
                    // 处理流式输出
                    if (output instanceof StreamingOutput<?> streamingOutput) {
                        // 流式输出块
                        String agent = streamingOutput.agent();
                        String chunk = streamingOutput.chunk();
                        if (chunk != null && !chunk.isEmpty()) {
                            // 按顺序收集每个 agent 的所有 chunks
                            agentOutputs.computeIfAbsent(agent, k -> new LinkedList<>()).add(chunk);
                        }
                    }
                    //else if (output instanceof InterruptionMetadata interruptionMetadata) {
                    //
                    //}
                    else {
                        // 普通节点输出
                        String nodeId = output.node();
                        Map<String, Object> state = output.state().data();
                        System.out.println(" 节点 '" + output.agent() + nodeId + "' 执行完成");
                        String answerKey = output.node() + "_answer";
                        if (state.containsKey(answerKey)) {
                            agentOutputs.computeIfAbsent(output.node(),
                                    k -> new LinkedList<>()).add(state.get(answerKey).toString());
                        }
                    }
                })
                .doOnError(error -> {
                    System.err.println("流式输出错误: " + error.getMessage());
                })
                .blockLast(); // 阻塞等待流完成

        // 打印各 agent 的完整输出
        System.out.println("\n========== Agent Set ==========");
        System.out.println(JSON.toJSONString(setStr));
        System.out.println("\n========== 各 Agent 流式输出内容 ==========");
        agentOutputs.forEach((agent, chunks) -> {
            System.out.println("\n【Agent: " + agent + "】");
            chunks.forEach(System.out::print);
            System.out.println("\n------------------------------------------------------------");
        });
        System.out.println("\n流式输出完成");
    }
}
