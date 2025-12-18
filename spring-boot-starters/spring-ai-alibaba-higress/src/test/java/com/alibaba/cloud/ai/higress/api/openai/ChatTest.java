package com.alibaba.cloud.ai.higress.api.openai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * @Author NGshiyu
 * @Description chatTest
 * @CreateTime 2025/12/16 17:04
 */
@SpringBootTest(classes = ChatTest.TestApplication.class)
public class ChatTest {

    @SpringBootApplication
    public static class TestApplication {

    }

    /**
     * Tool class using @Tool annotation for method-based tools
     */
    public static class CalculatorTools {
        public static int callCount = 0;

        @Tool(description = "Add two numbers together")
        public String add(
                @ToolParam(description = "First number") int a,
                @ToolParam(description = "Second number") int b) {
            callCount++;
            return "CalculatorTools:" + (a + b);
        }

        @Tool(description = "Multiply two numbers together")
        public String multiply(
                @ToolParam(description = "First number") int a,
                @ToolParam(description = "Second number") int b) {
            callCount++;
            return "CalculatorTools:" + (a * b);
        }
    }


    /**
     * Tool class with no-parameter method using @Tool annotation
     */
    public static class SystemInfoTools {
        public static int callCount = 0;

        @Tool(description = "Get current system time in milliseconds")
        public String getCurrentTime() {
            callCount++;
            return "Current time: " + System.currentTimeMillis() + " ms";
        }

        @Tool(description = "Get system information")
        public String getSystemInfo() {
            callCount++;
            return String.format("System: %s, Java Version: %s, Available Processors: %d",
                    System.getProperty("os.name"),
                    System.getProperty("java.version"),
                    Runtime.getRuntime().availableProcessors());
        }
    }


    @Test
    public void testChatOpenAi() {
        ChatClient openAiClient = getOpenAiClient();
        //ChatClient openAiClient = getHigressOpenAiClient();
        //CallResponse是一个Spring Ai通用的返回模型
        System.out.println("==============================CHAT==============================");
        testChat(openAiClient);
        System.out.println("==============================GEN==============================");
        testGen(openAiClient);
    }

    private static ChatClient getOpenAiClient() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(Optional.ofNullable(System.getenv("API_KEY")).orElse("dummy-key"))
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/").build();
        //1.1.2 test
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
        builder
                //模型
                .model("qwen-plus")
                //温度
                .temperature(0.5)
                .extraBody(
                        Map.of(
                                "enable_thinking", false,
                                "enable_search", true,
                                "incremental_output", false,
                                "search_options", Map.of("forced_search", true,
                                        "enable_search_extension", true,
                                        "search_strategy", "max")
                        ));
        //开启思考，开启增量输出
        //.enableThinking(false)
        //.incrementalOutput(true)
        //.enableSearch()
        //.searchOptions(dialogue.getSearchOptions());
        OpenAiChatOptions openAiChatOptions = builder.build();
        OpenAiChatModel openAiChatModel = OpenAiChatModel.builder().openAiApi(openAiApi).defaultOptions(openAiChatOptions).build();
        //在这里可以直接做知识库的注入，知识库的管理可以放在另一个模块去处理
        return ChatClient.builder(openAiChatModel).build();
    }

    @Test
    public void testChatHigressOpenAi() {
        ChatClient openAiClient = getHigressOpenAiClient();
        //ChatClient openAiClient = getHigressOpenAiClient();
        //CallResponse是一个Spring Ai通用的返回模型
        System.out.println("==============================CHAT==============================");
        testChat(openAiClient);
        System.out.println("==============================GEN==============================");
        testGen(openAiClient);
    }

    private static ChatClient getHigressOpenAiClient() {
        HigressOpenAiApi openAiApi = HigressOpenAiApi.builder()
                .apiKey(Optional.ofNullable(System.getenv("API_KEY")).orElse("dummy-key"))
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/").build();
        HigressOpenAiChatOptions.Builder builder = HigressOpenAiChatOptions.builder();
        builder
                //模型
                .model("qwen-plus")
                .enableThinking(true)
                .enableSearch(true)
                .incrementalOutput(true)
                .searchOptions(HigressOpenAiApi.QwenOpenAiCompatibleSearchOptions.builder()
                        .forcedSearch(true)
                        .enableSearchExtension(true)
                        .searchStrategy("turbo")
                        .build());
        //开启思考，开启增量输出
        //.enableThinking(false)
        //.incrementalOutput(true)
        //.enableSearch()
        //.searchOptions(dialogue.getSearchOptions());
        HigressOpenAiChatOptions openAiChatOptions = builder.build();
        HigressOpenAiChatModel openAiChatModel = HigressOpenAiChatModel.builder().higressOpenAiApi(openAiApi).defaultOptions(openAiChatOptions).build();
        return ChatClient.builder(openAiChatModel).build();
    }


    @Test
    void testTool() {
        // Use the tool through Spring AI's interfaces
        CalculatorTools calculatorTools = new CalculatorTools();
        CalculatorTools.callCount = 0; // Reset call count
        ChatClient openAiClient = getHigressOpenAiClient();
        ChatClient.ChatClientRequestSpec doChat = openAiClient
                //.prompt("今天上海的天气怎么样？建议穿什么衣服？")
                .prompt("123乘31")
                .toolCallbacks(Arrays.asList(ToolCallbacks.from(calculatorTools)));

        doChat.stream().chatResponse().map(data -> {
                    // 大模型流式处理
                    if (Objects.nonNull(data)
                            && !CollectionUtils.isEmpty(data.getResults())
                            && Objects.nonNull(data.getResult().getOutput())
                            && anyMatchNotNull(() -> data.getResult().getOutput().getMetadata(), () -> data.getResult().getOutput().getText())
                    ) {
                        if (Objects.nonNull(data.getResult().getOutput().getMetadata().get("reasoningContent"))
                                && !StringUtils.isEmpty(String.valueOf(data.getResult()
                                .getOutput()
                                .getMetadata()
                                .get("reasoningContent")))) {
                            return "\uD83E\uDD14thinking:" + data.getResult().getOutput().getMetadata().get("reasoningContent");
                        }
                        else {
                            return "\uD83D\uDCE3answer:" + data.getResult().getOutput().getText();
                        }
                    }
                    else {
                        return "null text";
                    }
                })
                .doOnNext(System.out::println) // 在流的过程中打印
                .blockLast();
    }

    private static void testChat(ChatClient openAiClient) {
        ChatClient.ChatClientRequestSpec doChat = openAiClient
                //.prompt("今天上海的天气怎么样？建议穿什么衣服？")
                .prompt("杭州明天天气怎么样？")
                .system(systemSpec -> {
                    // 设置系统角色
                    systemSpec.text("你是一个智能助手");
                    // 动态参数替换
                });
        doChat.stream().chatResponse().map(data -> {
                    // 大模型流式处理
                    if (Objects.nonNull(data)
                            && !CollectionUtils.isEmpty(data.getResults())
                            && Objects.nonNull(data.getResult().getOutput())
                            && anyMatchNotNull(() -> data.getResult().getOutput().getMetadata(), () -> data.getResult().getOutput().getText())
                    ) {
                        if (Objects.nonNull(data.getResult().getOutput().getMetadata().get("reasoningContent"))
                                && !StringUtils.isEmpty(String.valueOf(data.getResult()
                                .getOutput()
                                .getMetadata()
                                .get("reasoningContent")))) {
                            return "\uD83E\uDD14thinking:" + data.getResult().getOutput().getMetadata().get("reasoningContent");
                        }
                        else {
                            return "\uD83D\uDCE3answer:" + data.getResult().getOutput().getText();
                        }
                    }
                    else {
                        return "null text";
                    }
                })
                .doOnNext(System.out::println) // 在流的过程中打印
                .blockLast();
    }


    private static void testGen(ChatClient openAiClient) {
        ChatClient.ChatClientRequestSpec doChat = openAiClient
                //.prompt("今天上海的天气怎么样？建议穿什么衣服？")
                .prompt("查一下标普500的当前点数？")
                .system(systemSpec -> {
                    // 设置系统角色
                    systemSpec.text("你是一个智能助手");
                    // 动态参数替换
                });
        System.out.println("\uD83D\uDCE3generatedAnswer:" + doChat.call().content());
    }


    /**
     * 安全地检查一系列 Supplier 表达式所返回的值是否都为非空。
     * 如果任一 Supplier 在执行时抛出 NullPointerException，或返回 null，则返回 false。
     *
     * @param suppliers 一个可变参数列表，包含要检查的 Supplier 表达式
     *
     * @return 如果所有表达式返回的值都非空，则返回 true；否则返回 false。
     */
    public static boolean anyMatchNotNull(Supplier<?>... suppliers) {
        // 将 Supplier 数组转为 Stream，并使用 allMatch 进行检查
        return Arrays.stream(suppliers)
                .anyMatch(supplier -> {
                    try {
                        // 尝试执行 Supplier 的 get() 方法
                        Object result = supplier.get();
                        // 成功获取值后，使用 Optional.ofNullable 检查该值是否为 null
                        return Optional.ofNullable(result).isPresent();
                    } catch (NullPointerException e) {
                        // 如果在执行 lambda 表达式时发生 NullPointerException，
                        // 捕获并视为该表达式返回了 null，因此返回 false
                        return false;
                    }
                });
    }


}
