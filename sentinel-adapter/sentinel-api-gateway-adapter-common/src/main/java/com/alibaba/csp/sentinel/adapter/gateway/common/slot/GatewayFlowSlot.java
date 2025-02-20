/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
 */
package com.alibaba.csp.sentinel.adapter.gateway.common.slot;

import java.util.List;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowChecker;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParameterMetricStorage;
import com.alibaba.csp.sentinel.spi.Spi;

/**
 * 用于网关限流的{@link com.alibaba.csp.sentinel.slotchain.ProcessorSlot}实现。
 *
 * @author Eric Zhao
 * @since 1.6.1
 */
@Spi(order = -4000)
public class GatewayFlowSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    @Override
    public void entry(Context context, ResourceWrapper resource, DefaultNode node, int count, boolean prioritized, Object... args) throws Throwable {
        // 检查网关参数限流
        checkGatewayParamFlow(resource, count, args);

        // 进入完成
        fireEntry(context, resource, node, count, prioritized, args);
    }

    private void checkGatewayParamFlow(ResourceWrapper resourceWrapper, int count, Object... args)
        throws BlockException {
        // 参数为空，不进行校验
        if (args == null) {
            return;
        }

        // 获取该资源对应的网关规则
        List<ParamFlowRule> rules = GatewayRuleManager.getConvertedParamRules(resourceWrapper.getName());
        if (rules == null || rules.isEmpty()) {
            return;
        }

        for (ParamFlowRule rule : rules) {
            // 初始化参数指标
            ParameterMetricStorage.initParamMetricsFor(resourceWrapper, rule);

            // 校验参数
            if (!ParamFlowChecker.passCheck(resourceWrapper, rule, count, args)) {
                String triggeredParam = "";
                if (args.length > rule.getParamIdx()) {
                    Object value = args[rule.getParamIdx()];
                    triggeredParam = String.valueOf(value);
                }
                throw new ParamFlowException(resourceWrapper.getName(), triggeredParam, rule);
            }
        }
    }

    @Override
    public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        // 退出完成
        fireExit(context, resourceWrapper, count, args);
    }
}
