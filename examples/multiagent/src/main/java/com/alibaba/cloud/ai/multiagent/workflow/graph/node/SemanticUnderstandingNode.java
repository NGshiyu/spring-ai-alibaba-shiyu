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

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.multiagent.workflow.graph.utils.ChatModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;

/**
 * 语义理解Node
 *
 * @author NGshiyu
 */
public record SemanticUnderstandingNode() implements NodeAction {
    private static final Logger logger = LoggerFactory.getLogger(SemanticUnderstandingNode.class);

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        ChatModel chatModel = ChatModelUtils.getChatModel();


        logger.info("SemanticUnderstandingNode execute");
        return Map.of();
    }
}
