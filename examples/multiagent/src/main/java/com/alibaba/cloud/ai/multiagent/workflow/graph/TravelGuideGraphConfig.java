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

package com.alibaba.cloud.ai.multiagent.workflow.graph;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.multiagent.workflow.graph.node.SemanticUnderstandingNode;
import com.alibaba.cloud.ai.multiagent.workflow.graph.node.WeatherSearchNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static java.lang.String.format;

/**
 * 旅行向导graph定义
 *
 * @author NGshiyu
 */
@Configuration
public class TravelGuideGraphConfig {
    private static final Logger logger = LoggerFactory.getLogger(TravelGuideGraphConfig.class);
    @Autowired
    private ToolCallbackProvider toolCallbackProvider;
    @Autowired
    ChatModel chatModel;
    @Autowired
    McpClientCommonProperties mcpClientCommonProperties;
    public static final String SEMANTIC_ANSWER = "semantic_understanding_answer";
    public static final String ROUTE_ANSWER = "route_planning_answer";
    public static final String PROCUREMENT_ANSWER = "mcdonald_procurement_answer";
    public static final String WEATHER_ANSWER = "weather_search_answer";
    public static final String WEATHER_ANSWER_SECOND = "weather_search_answer_sec";
    public static final String GENERATION_ANSWER = "plan_generation_answer";

    KeyStrategyFactory keyStrategyFactory = () -> {
        HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
        keyStrategyHashMap.put(SEMANTIC_ANSWER, new ReplaceStrategy());
        keyStrategyHashMap.put(ROUTE_ANSWER, new ReplaceStrategy());
        //keyStrategyHashMap.put(PROCUREMENT_ANSWER, new ReplaceStrategy());
        keyStrategyHashMap.put(WEATHER_ANSWER, new ReplaceStrategy());
        keyStrategyHashMap.put(GENERATION_ANSWER, new ReplaceStrategy());
        return keyStrategyHashMap;
    };


    /**
     * 定义一个执行流程
     *
     * @return {@link StateGraph } 返回一个流程信息
     */
    @Bean("travelGuideGraphOneNode")
    public CompiledGraph travelGuideGraphOneNode() {
        try {
            StateGraph stateGraph = new StateGraph(keyStrategyFactory)
                    //节点添加
                    .addNode("semantic_understanding", node_async(new SemanticUnderstandingNode(toolCallbackProvider, mcpClientCommonProperties))) // 语义理解节点
                    //.addNode("route_planning", new RoutePlanningNode(toolCallbackProvider, mcpClientCommonProperties)) // 路线规划节点
                    .addNode("weather_search", new WeatherSearchNode(toolCallbackProvider, mcpClientCommonProperties)) // 天气查询节点
                    ; // 方案生成节点 // 方案生成节点

            //定义一个流转的边界路线图
            stateGraph.addEdge(StateGraph.START, "semantic_understanding")
                    //.addEdge("semantic_understanding", "route_planning")
                    .addEdge("semantic_understanding", "weather_search")
                    //.addEdge("route_planning", StateGraph.END)
                    .addEdge("weather_search", StateGraph.END);

            // 配置持久化
            var memory = new MemorySaver();
            var compileConfig = CompileConfig.builder()
                    .saverConfig(SaverConfig.builder()
                            .register(memory)
                            .build())
                    .build();

            // 添加 PlantUML 打印
            GraphRepresentation representation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML, "travel_guide_graph");
            logger.info("\n=== expander UML Flow ===");
            logger.info(format("\n\n%s\n\n", representation.content()));
            logger.info("==================================\n");
            return stateGraph.compile(compileConfig);
        } catch (GraphStateException e) {
            throw new RuntimeException("Failed to create [Travel Guide Agent]", e);
        }
    }

}
