/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

import java.util.List;

/**
 * 致力于通用默认断路器的{@link ProcessorSlot}实现。
 *
 * <p>如果用户指定了断路器{@link DegradeSlot}，则不会启用该类。
 *
 * @author wuwen
 * @since 2.0.0
 */
@Spi(order = Constants.ORDER_DEFAULT_CIRCUIT_BREAKER_SLOT)
public class DefaultCircuitBreakerSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, DefaultNode node, int count, boolean prioritized, Object... args) throws Throwable {
        // 执行断路器检测
        performChecking(context, resourceWrapper);

        // entry 完成
        fireEntry(context, resourceWrapper, node, count, prioritized, args);
    }

    private void performChecking(Context context, ResourceWrapper r) throws BlockException {
        // 如果用户指定了资源的降级规则，默认规则将不会激活
        if (DegradeRuleManager.hasConfig(r.getName())) {
            return;
        }

        // 获取该资源的默认断路器
        List<CircuitBreaker> circuitBreakers = DefaultCircuitBreakerRuleManager.getDefaultCircuitBreakers(r.getName());

        // 断路器不存在，可能是未配置降级规则{@link DefaultCircuitBreakerRuleManager#rules}，
        // 也可能该资源被配置了排除{@link DefaultCircuitBreakerRuleManager#excludedResource}
        if (circuitBreakers == null || circuitBreakers.isEmpty()) {
            return;
        }

        // 应用断路器，对于不满足的请求，将抛出异常
        for (CircuitBreaker cb : circuitBreakers) {
            if (!cb.tryPass(context)) {
                throw new DegradeException(cb.getRule().getLimitApp(), cb.getRule());
            }
        }
    }

    @Override
    public void exit(Context context, ResourceWrapper r, int count, Object... args) {
        // 从上下文中获取当前entry
        Entry curEntry = context.getCurEntry();
        // 检查如果存在异常，则跳过处理
        if (curEntry.getBlockError() != null) {
            fireExit(context, r, count, args);
            return;
        }

        // 如果用户手动配置了断路器，则跳过处理
        if (DegradeRuleManager.hasConfig(r.getName())) {
            fireExit(context, r, count, args);
            return;
        }

        // 获取关联该资源的默认断路器
        List<CircuitBreaker> circuitBreakers = DefaultCircuitBreakerRuleManager.getDefaultCircuitBreakers(r.getName());

        // 如果不存在断路器，则跳过处理
        if (circuitBreakers == null || circuitBreakers.isEmpty()) {
            fireExit(context, r, count, args);
            return;
        }

        // 如果未出现异常，回调断路器的请求完成
        if (curEntry.getBlockError() == null) {
            // passed request
            for (CircuitBreaker circuitBreaker : circuitBreakers) {
                circuitBreaker.onRequestComplete(context);
            }
        }

        fireExit(context, r, count, args);
    }
}
