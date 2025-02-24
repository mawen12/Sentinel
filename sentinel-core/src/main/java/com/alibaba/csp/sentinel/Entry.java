/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.util.function.BiConsumer;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.context.Context;

/**
 * 每一个{@link SphU}#entry()方法都会返回一个{@link Entry}。该类持有当前调用的信息：
 * <ul>
 *  <li>createTime: 当前Entry的创建时间，用于RT统计</li>
 *  <li>current {@link Node}: 在当前上下文中该资源的统计信息</li>
 *  <li>origin {@link Node}: 特定原始的统计数据，通常来说原始源应该为服务消费者的App名称，查看{@link ContextUtil#enter(String, String)}</li>
 *  <li>{@link ResourceWrapper}：资源名称</li>
 * </ul>
 *
 * <p>如果我们在同一个{@link Context}中多次调用{@link SphU}#entry()，就会创建一个调用树，
 * 因此父条目或子条目可能会由此保存形成树。
 *
 * @author qinan.qn
 * @author jialiang.linjl
 * @author leyou(lihao)
 * @author Eric Zhao
 * @see SphU
 * @see Context
 * @see ContextUtil
 */
public abstract class Entry implements AutoCloseable {

    protected static final Object[] OBJECTS0 = new Object[0];

    /**
     * 该条目的创建时间，用于计算RT
     */
    private final long createTimestamp;
    /**
     * 该条目的完成时间，用于计算RT
     */
    private long completeTimestamp;

    /**
     * 当前节点，保存了该资源的统计信息
     */
    private Node curNode;
    /**
     * {@link Node} of the specific origin, Usually the origin is the Service Consumer.
     */
    /**
     * 特定原始的节点，通常应为服务消费者
     */
    private Node originNode;

    /**
     * 执行出现的异常
     */
    private Throwable error;

    /**
     * 执行出现的阻塞异常
     */
    private BlockException blockError;

    /**
     * 资源名称
     */
    protected final ResourceWrapper resourceWrapper;

    /**
     * 总数
     */
    protected final int count;

    /**
     * 方法调用的参数
     */
    protected final Object[] args;

    public Entry(ResourceWrapper resourceWrapper) {
        this(resourceWrapper, 1, OBJECTS0);
    }

    public Entry(ResourceWrapper resourceWrapper, int count, Object[] args) {
        this.resourceWrapper = resourceWrapper;
        this.createTimestamp = TimeUtil.currentTimeMillis();
        this.count = count;
        this.args = args;
    }

    public ResourceWrapper getResourceWrapper() {
        return resourceWrapper;
    }

    /**
     * 完成当前资源条目，并在上下文中恢复入口栈。不需要携带count或参数，初始化时才需要。
     *
     * @throws ErrorEntryFreeException 如果当前上下文中的条目与当前条目不匹配
     */
    public void exit() throws ErrorEntryFreeException {
        exit(count, args);
    }

    public void exit(int count) throws ErrorEntryFreeException {
        exit(count, args);
    }

    /**
     * 等于{@link #exit()}
     *
     * @since 1.5.0
     */
    @Override
    public void close() {
        exit();
    }

    /**
     * 退出该条目。该方法应在资源保护结束时且仅当调用一次时调用
     *
     * @param count 释放的令牌数
     * @param args 退出参数
     * @throws ErrorEntryFreeException 如果当前上下文中的条目与当前条目不匹配
     */
    public abstract void exit(int count, Object... args) throws ErrorEntryFreeException;

    /**
     * 退出该条目。
     *
     * @param count 释放的令牌数
     * @param args 退出参数
     * @return 在推出后下一个可用的条目，即父条目
     * @throws ErrorEntryFreeException, 如果当前上下文中的条目与当前条目不匹配
     */
    protected abstract Entry trueExit(int count, Object... args) throws ErrorEntryFreeException;

    /**
     * @return 返回父级相关的 {@link Node}
     */
    public abstract Node getLastNode();

    public long getCreateTimestamp() {
        return createTimestamp;
    }

    public long getCompleteTimestamp() {
        return completeTimestamp;
    }

    public Entry setCompleteTimestamp(long completeTimestamp) {
        this.completeTimestamp = completeTimestamp;
        return this;
    }

    public Node getCurNode() {
        return curNode;
    }

    public void setCurNode(Node node) {
        this.curNode = node;
    }

    public BlockException getBlockError() {
        return blockError;
    }

    public Entry setBlockError(BlockException blockError) {
        this.blockError = blockError;
        return this;
    }

    public Throwable getError() {
        return error;
    }

    public void setError(Throwable error) {
        this.error = error;
    }

    /**
     * Get origin {@link Node} of the this {@link Entry}.
     *
     * @return origin {@link Node} of the this {@link Entry}, may be null if no origin specified by
     * {@link ContextUtil#enter(String name, String origin)}.
     */
    public Node getOriginNode() {
        return originNode;
    }

    public void setOriginNode(Node originNode) {
        this.originNode = originNode;
    }

    /**
     * Like {@code CompletableFuture} since JDK 8, it guarantees specified handler
     * is invoked when this entry terminated (exited), no matter it's blocked or permitted.
     * Use it when you did some STATEFUL operations on entries.
     * 
     * @param handler handler function on the invocation terminates
     * @since 1.8.0
     */
    public abstract void whenTerminate(BiConsumer<Context, Entry> handler);
    
}
