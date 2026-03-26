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
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    @Resource
    @Qualifier("travelGuideGraphOneNode")
    CompiledGraph travelGuideGraphOneNode;


    @PostMapping("/travelGuide")
    public void runGraph(@RequestParam("isFeedback") Boolean isFeedback) throws GraphStateException {
        //!!! 关键配置1：全局的sessionId
        String sessionId = "travel_guide" + UUID.randomUUID();
        //!!! 关键配置2： 全局的检查点保存器（工作流和Agent共享）
        MemorySaver memorySaver = new MemorySaver();
        //!!! 关键配置3：定义一个全局的 config 和 graph 的 ReactAgent 智能体共享，以便于 HIL
        var config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();

        Map<String, Object> initialState = new HashMap<>();
        initialState.put("question", """
                        我想要周六从上海浦东新区浦软大厦开车出发去杭州滨江安恒大厦开会，我可以走哪一条路线？
                        杭州当天天气怎么样，我需要穿什么衣服？
                        """);
        initialState.put("sessionId", sessionId);
        initialState.put("memorySaver", memorySaver);
        initialState.put("isFeedback", isFeedback);

        // 用于收集各 agent 的流式输出内容（使用 LinkedHashMap 保持顺序）
        Map<String, List<String>> agentOutputs = new LinkedHashMap<>();
        Set<String> setStr = new HashSet<>();
        travelGuideGraph.stream(initialState, config)
                .doOnNext(output -> {
                    //setStr.add(output.node());
                    //System.out.println(JSON.toJSONString(output));
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
                    else if (output instanceof InterruptionMetadata interruptionMetadata) {
                        List<InterruptionMetadata.ToolFeedback> toolFeedbacks =
                                interruptionMetadata.toolFeedbacks();
                        StringBuilder builder = new StringBuilder();
                        builder.append("检测到中断，需要人工审批").append("\n");
                        for (InterruptionMetadata.ToolFeedback feedback : toolFeedbacks) {
                            builder.append("\n").append("工具: ").append(feedback.getName()).append("\n");
                            builder.append("参数: ").append(feedback.getArguments()).append("\n");
                            builder.append("描述: ").append(feedback.getDescription()).append("\n");
                        }
                        agentOutputs.computeIfAbsent(output.node(),k -> new LinkedList<>()).add(builder.toString());
                    }
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
    }

    @PostMapping("/travelGuideOneNode")
    public void travelGuideGraphOneNode() throws GraphStateException {
        //!!! 关键配置1：全局的sessionId
        String sessionId = "travel_guide" + UUID.randomUUID();
        //!!! 关键配置2： 全局的检查点保存器（工作流和Agent共享）
        MemorySaver memorySaver = new MemorySaver();
        //!!! 关键配置3：定义一个全局的 config 和 graph 的 ReactAgent 智能体共享，以便于 HIL
        var config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();

        Map<String, Object> initialState = new HashMap<>();
        initialState.put("question", """
                我想要周六从上海浦东新区浦软大厦开车出发去杭州滨江安恒大厦开会，我可以走哪一条路线？
                杭州当天天气怎么样，我需要穿什么衣服？
                """);
        initialState.put("sessionId", sessionId);
        initialState.put("memorySaver", memorySaver);

        // 用于收集各 agent 的流式输出内容（使用 LinkedHashMap 保持顺序）
        Map<String, List<String>> agentOutputs = new LinkedHashMap<>();
        Set<String> setStr = new HashSet<>();
        travelGuideGraph.stream(initialState, config)
                .doOnNext(output -> {
                    //setStr.add(output.node());
                    //System.out.println(JSON.toJSONString(output));
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
                    else if (output instanceof InterruptionMetadata interruptionMetadata) {
                        List<InterruptionMetadata.ToolFeedback> toolFeedbacks =
                                interruptionMetadata.toolFeedbacks();
                        StringBuilder builder = new StringBuilder();
                        builder.append("检测到中断，需要人工审批").append("\n");
                        for (InterruptionMetadata.ToolFeedback feedback : toolFeedbacks) {
                            builder.append("\n").append("工具: ").append(feedback.getName()).append("\n");
                            builder.append("参数: ").append(feedback.getArguments()).append("\n");
                            builder.append("描述: ").append(feedback.getDescription()).append("\n");
                        }
                        agentOutputs.computeIfAbsent(output.node(), k -> new LinkedList<>()).add(builder.toString());
                    }
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
    }
}
