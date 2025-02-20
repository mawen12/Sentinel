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
package com.alibaba.csp.sentinel.slots.statistic;

import java.util.Collection;

import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlotEntryCallback;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlotExitCallback;
import com.alibaba.csp.sentinel.slots.block.flow.PriorityWaitException;
import com.alibaba.csp.sentinel.spi.Spi;
import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.Constants;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.ClusterNode;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;

/**
 * 用于检测实时统计信息的{@link com.alibaba.csp.sentinel.slotchain.ProcessorSlot}实现。
 * 当进入该Slot时，分别使用以下节点进行统计信息的收集：
 * <ul>
 *     <li>{@link ClusterNode}：集群节点的该资源ID的累计统计信息</li>
 *     <li>Origin node：来自不同调用者/源头的集群节点的统计信息</li>
 *     <li>{@link DefaultNode}：在特定上下文中特定资源名称的统计信息</li>
 *     <li>最终，所有入口的统计总和</li>
 * </ul>
 *
 * <p>具体的统计信息如下：
 * <ol>
 *     <li>线程总数</li>
 *     <li>通过请求总数</li>
 * </ol>
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 */
@Spi(order = Constants.ORDER_STATISTIC_SLOT)
public class StatisticSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, DefaultNode node, int count,
                      boolean prioritized, Object... args) throws Throwable {
        try {
            // 进入下一个处理器插槽
            fireEntry(context, resourceWrapper, node, count, prioritized, args);

            // 没有出现异常，代表请求通过，增加线程总数和请求通过数
            node.increaseThreadNum();
            node.addPassRequest(count);

            if (context.getCurEntry().getOriginNode() != null) {
                // 当原始节点不为空时，同时增加线程总数和请求通过数
                context.getCurEntry().getOriginNode().increaseThreadNum();
                context.getCurEntry().getOriginNode().addPassRequest(count);
            }

            if (resourceWrapper.getEntryType() == EntryType.IN) {
                // 为全局统计增加全局入站入口节点的线程总数和请求通过数
                Constants.ENTRY_NODE.increaseThreadNum();
                Constants.ENTRY_NODE.addPassRequest(count);
            }

            // 使用已注册的entry回调处理器来处理通过事件
            for (ProcessorSlotEntryCallback<DefaultNode> handler : StatisticSlotCallbackRegistry.getEntryCallbacks()) {
                handler.onPass(context, resourceWrapper, node, count, args);
            }
        } catch (PriorityWaitException ex) {// 对于该异常，不会记录，但是不会增加请求通过的数量
            // 增加线程总数
            node.increaseThreadNum();
            if (context.getCurEntry().getOriginNode() != null) {
                // 当原始节点不为空时，增加线程总数
                context.getCurEntry().getOriginNode().increaseThreadNum();
            }

            if (resourceWrapper.getEntryType() == EntryType.IN) {
                // 为全局统计增加全局入站入口节点的线程总数
                Constants.ENTRY_NODE.increaseThreadNum();
            }

            // 使用已注册的entry回调处理器来处理通过事件
            for (ProcessorSlotEntryCallback<DefaultNode> handler : StatisticSlotCallbackRegistry.getEntryCallbacks()) {
                handler.onPass(context, resourceWrapper, node, count, args);
            }
        } catch (BlockException e) {// 将异常记录到Entry#blockError上
            // 发生阻塞，将阻塞异常设置到当前entry
            context.getCurEntry().setBlockError(e);

            // 增加阻塞的QPS
            node.increaseBlockQps(count);
            if (context.getCurEntry().getOriginNode() != null) {
                // 当原始节点不为空时，增加阻塞的QPS
                context.getCurEntry().getOriginNode().increaseBlockQps(count);
            }

            if (resourceWrapper.getEntryType() == EntryType.IN) {
                // 为全局统计增加全局入站入口节点的阻塞QPS
                Constants.ENTRY_NODE.increaseBlockQps(count);
            }

            // 使用已注册的entry回调处理器来处理阻塞事件
            for (ProcessorSlotEntryCallback<DefaultNode> handler : StatisticSlotCallbackRegistry.getEntryCallbacks()) {
                handler.onBlocked(e, context, resourceWrapper, node, count, args);
            }

            throw e;
        } catch (Throwable e) {// 将异常记录到Entry#error上
            // 预期意外的错误，将异常设置到当前entry
            context.getCurEntry().setError(e);

            throw e;
        }
    }

    @Override
    public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        // 从上下文中获取当前节点
        Node node = context.getCurNode();

        if (context.getCurEntry().getBlockError() == null) {// 未出现阻塞异常时的处理，当出现阻塞异常时，不会执行到该方法
            // 未发生阻塞错误时，计算响应时间
            // 使用该变量作为完成时间
            long completeStatTime = TimeUtil.currentTimeMillis();
            // 更新响应时间
            context.getCurEntry().setCompleteTimestamp(completeStatTime);
            // 使用完成时间-创建时间，作为响应耗时
            long rt = completeStatTime - context.getCurEntry().getCreateTimestamp();

            // 获取记录的Throwable异常
            Throwable error = context.getCurEntry().getError();

            // 记录当前节点的响应耗时和请求成功总数
            recordCompleteFor(node, count, rt, error);
            // 记录原始节点的响应耗时和请求成功总数
            recordCompleteFor(context.getCurEntry().getOriginNode(), count, rt, error);
            if (resourceWrapper.getEntryType() == EntryType.IN) {
                // 为全局统计增加全局入站入口节点的响应耗时和请求成功总数
                recordCompleteFor(Constants.ENTRY_NODE, count, rt, error);
            }
        }

        // 使用已注册的entry回调处理器来处理退出事件
        Collection<ProcessorSlotExitCallback> exitCallbacks = StatisticSlotCallbackRegistry.getExitCallbacks();
        for (ProcessorSlotExitCallback handler : exitCallbacks) {
            handler.onExit(context, resourceWrapper, count, args);
        }

        // fix bug https://github.com/alibaba/Sentinel/issues/2374
        // 退出完成通知
        fireExit(context, resourceWrapper, count, args);
    }

    private void recordCompleteFor(Node node, int batchCount, long rt, Throwable error) {
        if (node == null) {
            return;
        }
        // 增加响应耗时和请求成功的总数
        node.addRtAndSuccess(rt, batchCount);
        // 减少线程总数
        node.decreaseThreadNum();

        if (error != null && !(error instanceof BlockException)) {// 存在阻塞异常时
            // 增加异常的QPS数量
            node.increaseExceptionQps(batchCount);
        }
    }
}
