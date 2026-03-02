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
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
        // 测试紧急账单问题
        Map<String, Object> initialState = Map.of(
                "question", "我想要从上海自驾去新疆旅行，你有没有推荐的路线？我可以走哪一条路线？另外，我需要准备一些什么物资？"
        );

        // 使用 thread_id 运行以实现持久化
        var config = RunnableConfig.builder()
                .threadId("travel_guide")
                .build();
        travelGuideGraph.invoke(initialState, config);
        //Flux<NodeOutput> stream = travelGuideGraph.strea(initialState, config);
    }
}
