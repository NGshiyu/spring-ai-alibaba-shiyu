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
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.multiagent.workflow.graph.TravelGuideGraphConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * McDonald选购
 *
 * @author NGshiyu
 */
@Deprecated
public record McDonaldProcurementNode(org.springframework.ai.chat.model.ChatModel chatModel, ToolCallbackProvider toolCallbackProvider,
                                      org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties mcpClientCommonProperties)
        implements AsyncNodeAction {
    private static final Logger logger = LoggerFactory.getLogger(McDonaldProcurementNode.class);
    private final static String instruction = """
            # Role
            麦当劳餐厅查询助手
            
            # Profile
            你擅长调用工具检索特定区域的麦当劳门店数据。
            
            # Rules
            1. **地点识别**：提取用户提问中的位置信息（城市、商圈、地址）。
            2. **缺省逻辑**：若未提供具体位置，请先追问用户所在区域。
            3. **输出要求**：结构化展示门店列表（名称、地址、营业状态），确保数据真实。
            4. **噪音屏蔽**：仅关注你自己的智能，屏蔽其他无关信息
            """;

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        logger.info("McDonaldProcurementNode execute");
        Object messages = state.value(TravelGuideGraphConfig.ROUTE_ANSWER).orElse("");
        //简单的筛选工具避免工具爆炸
        ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
        List<ToolCallback> tools = Arrays.stream(toolCallbacks).filter(toolCallback ->
                !toolCallback.getToolDefinition().name().contains("weather") && !toolCallback.getToolDefinition().name().contains("maps")).toList();

        // 创建人工介入Hook - 麦当劳所有工具调用都需要用户确认
        HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder()
                // === 餐品信息查询 ===
                .approvalOn("list-nutrition-foods", ToolConfig.builder()
                        .description("餐品营养信息列表：获取麦当劳常见餐品的营养成分数据，包括能量、蛋白质、脂肪、碳水化合物、钠、钙等信息")
                        .build())
                .approvalOn("query-meals", ToolConfig.builder()
                        .description("查询当前门店可售卖的餐品列表：查询当前门店可售卖的餐品菜单（分类、餐品编码、标签等），用于点餐选品")
                        .build())
                .approvalOn("meal-detail", ToolConfig.builder()
                        .description("查询餐品详情：根据餐品编码查询餐品详情（套餐组成、默认选择等），用于查看套餐包含内容")
                        .build())
                // === 配送服务 ===
                .approvalOn("delivery-query-addresses", ToolConfig.builder()
                        .description("获取用户可配送地址列表：查询用户已创建的配送地址列表，用于外送点餐时选择配送地址，并获取对应门店信息")
                        .build())
                .approvalOn("delivery-create-address", ToolConfig.builder()
                        .description("新增配送地址：当用户无可配送地址或需新增收货地址时使用，用于创建新的可配送地址")
                        .build())
                // === 优惠券服务 ===
                .approvalOn("query-usable-coupons", ToolConfig.builder()
                        .description("查询用户在当前门店可用券：查询用户在当前门店下可使用的优惠券列表，用于点餐时选择可用优惠")
                        .build())
                .approvalOn("available-coupons", ToolConfig.builder()
                        .description("麦麦省券列表查询：查询用户当前可领取的麦麦省的优惠券列表")
                        .build())
                .approvalOn("auto-bind-coupons", ToolConfig.builder()
                        .description("麦麦省一键领券：自动领取麦麦省所有当前可用的麦当劳优惠券，系统会自动领取用户可领的所有券")
                        .build())
                .approvalOn("my-coupons", ToolConfig.builder()
                        .description("我的优惠券查询：查询我有哪些可用的优惠券，就像打开麦当劳App的【我的优惠券】页面")
                        .build())
                // === 订单服务 ===
                .approvalOn("calculate-price", ToolConfig.builder()
                        .description("商品价格计算：根据用户选购商品列表（可含优惠券）计算商品金额、配送费、优惠金额及应付总价")
                        .build())
                .approvalOn("create-order", ToolConfig.builder()
                        .description("创建外送订单：根据门店信息、配送地址、商品列表创建外送订单，返回订单详情与支付链接")
                        .build())
                .approvalOn("query-order", ToolConfig.builder()
                        .description("查询订单详情：查询订单状态、订单内容、配送信息等，用于用户查看订单进度或确认订单信息")
                        .build())
                // === 活动与积分 ===
                .approvalOn("campaign-calendar", ToolConfig.builder()
                        .description("活动日历查询工具：查询麦当劳中国当月的营销活动日历，返回进行中、往期和未来日期的活动")
                        .build())
                .approvalOn("query-my-account", ToolConfig.builder()
                        .description("我的积分查询：查询用户积分账户信息，包括可用积分、累计积分、冻结积分、即将过期积分等")
                        .build())
                // === 积分商城 ===
                .approvalOn("mall-points-products", ToolConfig.builder()
                        .description("积分兑换商品列表：查询麦麦商城内可以用积分兑换的餐品券")
                        .build())
                .approvalOn("mall-product-detail", ToolConfig.builder()
                        .description("积分兑换商品详情：查询指定积分兑换商品券的详细信息（图片、积分、有效期、说明、详情等）")
                        .build())
                .approvalOn("mall-create-order", ToolConfig.builder()
                        .description("积分兑换商品下单：使用积分兑换指定餐品券，完成积分扣减并发放券码，返回兑换订单号与券码信息")
                        .build())
                // === 辅助工具 ===
                .approvalOn("now-time-info", ToolConfig.builder()
                        .description("获取当前时间信息：返回当前的完整时间信息，以便于LLM知道当前的时间和日期")
                        .build())
                .build();

        //Run React Agent With MCP Tools
        Builder builder = ReactAgent.builder()
                .name("procurement_assistant")
                .model(chatModel)
                .description("search restaurant or order some McDonald,use the tools")
                .tools(tools)
                .instruction(instruction)
                //定义HITL的Hooks,用于中断
                .hooks(List.of(humanInTheLoopHook));
        //.saver((MemorySaver) state.value("memorySaver").get());
        ReactAgent agent = builder.build();
        var config = RunnableConfig.builder()
                .threadId(state.value("sessionId").toString())
                .build();
        //stream
        Optional<NodeOutput> result = Optional.empty();
        try {
            result = agent.invokeAndGetOutput(state.value(TravelGuideGraphConfig.ROUTE_ANSWER).toString(),
                    config);
            // 5. 检查中断并处理
            if (result.isPresent() && result.get() instanceof InterruptionMetadata) {
                InterruptionMetadata interruptionMetadata = (InterruptionMetadata) result.get();

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
                    String editedArguments = toolFeedback.getArguments()
                            .replace("DELETE FROM records", "DELETE FROM old_records");

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
                        .addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, approvalMetadata)
                        .build();

                Optional<NodeOutput> finalResult = agent.invokeAndGetOutput("", resumeConfig);

                if (finalResult.isPresent()) {
                    System.out.println("执行完成");
                    System.out.println("最终结果: " + finalResult.get());
                }
            }
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
        return CompletableFuture.completedFuture(Map.of(TravelGuideGraphConfig.PROCUREMENT_ANSWER, result));
        //Flux<NodeOutput> stream = agent.stream(state.value(TravelGuideGraphConfig.ROUTE_ANSWER).toString(),
        //        config);
        //return Map.of(TravelGuideGraphConfig.PROCUREMENT_ANSWER, stream);
        //AssistantMessage call = agent.call(state.value(TravelGuideGraphConfig.ROUTE_ANSWER).toString(),
        //        config);
        //return Map.of(TravelGuideGraphConfig.PROCUREMENT_ANSWER, call);
    }
}