package com.alibaba.cloud.ai.graph.action;

/**
 * @Author NGshiyu
 * @Description Action lifecycle interface, providing pre and post operation support
 * <p>
 * Provide a unified pre, post and exception handling mechanism for all Action types
 * @CreateTime 2025/12/22 17:26
 */
interface ActionLifecycle<E> {
    /**
     * Override  this method to add some predecessors before the node executes.
     *
     */
    default void preHandler() {}

    /**
     * Override  this method to add some predecessors after the node executes.
     *
     */
    default void postHandler() {}

}
