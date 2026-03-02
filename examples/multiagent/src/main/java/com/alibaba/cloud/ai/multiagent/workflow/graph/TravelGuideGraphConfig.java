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
import com.alibaba.cloud.ai.multiagent.workflow.graph.node.MaterialProcurementNode;
import com.alibaba.cloud.ai.multiagent.workflow.graph.node.PlanGenerationNode;
import com.alibaba.cloud.ai.multiagent.workflow.graph.node.RoutePlanningNode;
import com.alibaba.cloud.ai.multiagent.workflow.graph.node.SemanticUnderstandingNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public static final String SEMANTIC_ANSWER = "semantic_answer";
    public static final String ROUTE_ANSWER = "route_answer";
    public static final String MATERIAL_ANSWER = "material_answer";

    KeyStrategyFactory keyStrategyFactory = () -> {
        HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
        keyStrategyHashMap.put(SEMANTIC_ANSWER, new ReplaceStrategy());
        keyStrategyHashMap.put(ROUTE_ANSWER, new ReplaceStrategy());
        keyStrategyHashMap.put(MATERIAL_ANSWER, new ReplaceStrategy());

        return keyStrategyHashMap;
    };

    /**
     * 生成式问答的子graph
     *
     * @return {@link StateGraph } 返回一个流程信息
     */
    @Bean("travelGuideGraph")
    public CompiledGraph travelGuideGraph() {
        try {
            //构建一个新的 ParallelNode 实例。
            //形参:
            //id – 并行节点的标识符（格式化为 PARALLEL_PREFIX）
            //targetNodeId —— 目标节点的ID，位于并行分支之后（合并节点）。该方法用于查询 RunnableConfig 中的聚合策略配置（ANY_OF 或 ALL_OF）。该策略决定是等待所有分支完成（ALL_OF）还是继续第一个完成的分支（ANY_OF）。
            //actions —— 并行执行的动作列表。每个动作代表并行执行流中的一个分支。
            //actionNodeIds ——对应每个动作的节点ID列表。必须和动作列表大小相同。每个ID标识对应并行分支中实际执行的节点。
            //channels ——关键策略映射，定义了当多个并行分支产生相同密钥结果时状态值如何合并。该映射中的键对应状态键，值定义合并策略（例如，AppendStrategy、ReplaceStrategy）。
            //compileConfig – 包含生命周期监听器及其他编译时设置的编译配置，这些设置会影响并行节点的执行方式。
            //ParallelNode planElement = new ParallelNode("plan_element", "plan_generation",
            //        List.of(
            //                AsyncNodeActionWithConfig.of(node_async(new RoutePlanningNode())),
            //                AsyncNodeActionWithConfig.of(node_async(new MaterialProcurementNode()))
            //        ), List.of("route_planning", "material_procurement"), keyStrategyFactory.apply(), CompileConfig.builder().build());
            //
            //StateGraph stateGraph = new StateGraph(keyStrategyFactory)
            //        //节点添加
            //        .addNode("semantic_understanding", node_async(new SemanticUnderstandingNode()))
            //        .addNode("plan_element", planElement)
            //        .addNode("plan_generation", node_async(new PlanGenerationNode()));
            //
            ////定义一个流转的边界路线图
            //stateGraph.addEdge(StateGraph.START, "semantic_understanding")
            //        .addEdge("semantic_understanding", "plan_element")
            //        .addEdge("plan_element", "plan_generation")
            //        .addEdge("plan_generation", StateGraph.END);

            StateGraph stateGraph = new StateGraph(keyStrategyFactory)
                    //节点添加
                    .addNode("semantic_understanding", node_async(new SemanticUnderstandingNode())) // 语义理解节点
                    .addNode("route_planning", node_async(new RoutePlanningNode(toolCallbackProvider))) // 路线规划节点
                    .addNode("material_procurement", node_async(new MaterialProcurementNode(toolCallbackProvider))) // 物资采购节点
                    .addNode("plan_generation", node_async(new PlanGenerationNode())); // 方案生成节点 // 方案生成节点

            //定义一个流转的边界路线图
            stateGraph.addEdge(StateGraph.START, "semantic_understanding")
                    .addEdge("semantic_understanding", "route_planning")
                    .addEdge("semantic_understanding", "material_procurement")
                    .addEdge("route_planning", "plan_generation")
                    .addEdge("material_procurement", "plan_generation")
                    .addEdge("plan_generation", StateGraph.END);

            // 配置持久化
            var memory = new MemorySaver();
            var compileConfig = CompileConfig.builder()
                    .saverConfig(SaverConfig.builder()
                            .register(memory)
                            .build())
                    //.interruptBefore("human_review")  // 在人工审核前中断
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
