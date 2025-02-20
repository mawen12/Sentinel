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
package com.alibaba.csp.sentinel.slots.block.degrade;

import java.util.List;

import com.alibaba.csp.sentinel.Constants;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreaker;
import com.alibaba.csp.sentinel.spi.Spi;

/**
 * 专门用于断路器的{@link ProcessorSlot}。
 *
 * @author Carpenter Lee
 * @author Eric Zhao
 */
@Spi(order = Constants.ORDER_DEGRADE_SLOT)
public class DegradeSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, DefaultNode node, int count,
                      boolean prioritized, Object... args) throws Throwable {
        // 执行断路器检查
        performChecking(context, resourceWrapper);

        // 完成entry
        fireEntry(context, resourceWrapper, node, count, prioritized, args);
    }

    void performChecking(Context context, ResourceWrapper r) throws BlockException {
        // 获取该资源名称配置的断路器
        List<CircuitBreaker> circuitBreakers = DegradeRuleManager.getCircuitBreakers(r.getName());
        // 断路器为空或不存在，跳过
        if (circuitBreakers == null || circuitBreakers.isEmpty()) {
            return;
        }
        for (CircuitBreaker cb : circuitBreakers) {
            // 使用断路器依次检查，检查失败抛出降级异常
            if (!cb.tryPass(context)) {
                throw new DegradeException(cb.getRule().getLimitApp(), cb.getRule());
            }
        }
    }

    @Override
    public void exit(Context context, ResourceWrapper r, int count, Object... args) {
        // 获取当前entry
        Entry curEntry = context.getCurEntry();
        // 当出现阻塞异常时
        if (curEntry.getBlockError() != null) {
            // 退出完成
            fireExit(context, r, count, args);
            return;
        }

        // 获取该资源的断路器列表
        List<CircuitBreaker> circuitBreakers = DegradeRuleManager.getCircuitBreakers(r.getName());
        // 如果断路器为空，则直接退出
        if (circuitBreakers == null || circuitBreakers.isEmpty()) {
            fireExit(context, r, count, args);
            return;
        }

        // 对于没有异常的情况，调用断路器的请求完成，进行统计信息
        if (curEntry.getBlockError() == null) {
            for (CircuitBreaker circuitBreaker : circuitBreakers) {
                circuitBreaker.onRequestComplete(context);
            }
        }

        // 退出完成
        fireExit(context, r, count, args);
    }
}
