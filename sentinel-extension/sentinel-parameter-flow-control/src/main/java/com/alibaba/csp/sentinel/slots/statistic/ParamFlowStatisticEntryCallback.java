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

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlotEntryCallback;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParameterMetric;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParameterMetricStorage;

/**
 * 基于参数流控统计的entry回调实现
 *
 * @author Eric Zhao
 * @since 0.2.0
 */
public class ParamFlowStatisticEntryCallback implements ProcessorSlotEntryCallback<DefaultNode> {

    @Override
    public void onPass(Context context, ResourceWrapper resourceWrapper, DefaultNode node, int count, Object... args) {
        // 仅当该资源存在参数流控规则时，才会提供热点参数指标
        ParameterMetric parameterMetric = ParameterMetricStorage.getParamMetric(resourceWrapper);

        if (parameterMetric != null) {
            // 增加线程总数
            parameterMetric.addThreadCount(args);
        }
    }

    @Override
    public void onBlocked(BlockException ex, Context context, ResourceWrapper resourceWrapper, DefaultNode param, int count, Object... args) {
        // 此处我们不会增加阻塞总数，因为校验阻塞异常的类型会影响性能。
        // 我们将采取在抛出ParamFlowException时增加阻塞总数的方式来替代。
    }
}
