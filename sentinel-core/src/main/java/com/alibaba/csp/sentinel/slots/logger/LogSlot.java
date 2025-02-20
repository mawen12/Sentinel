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
package com.alibaba.csp.sentinel.slots.logger;

import com.alibaba.csp.sentinel.Constants;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.spi.Spi;

/**
 * 记录{@link BlockException}以提供用于异常排除的具体日志的
 * {@link com.alibaba.csp.sentinel.slotchain.ProcessorSlot}实现。
 */
@Spi(order = Constants.ORDER_LOG_SLOT)
public class LogSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, DefaultNode obj, int count, boolean prioritized, Object... args) throws Throwable {
        try {
            // entry 完成
            fireEntry(context, resourceWrapper, obj, count, prioritized, args);
        } catch (BlockException e) {// 捕获阻塞异常
            // 记录阻塞异常到日志中
            EagleEyeLogUtil.log(resourceWrapper.getName(), e.getClass().getSimpleName(), e.getRuleLimitApp(), context.getOrigin(), e.getRule() != null ? e.getRule().getId() : null, count);
            throw e;
        } catch (Throwable e) {
            RecordLog.warn("Unexpected entry exception", e);
        }
    }

    @Override
    public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        try {
            // exit完成
            fireExit(context, resourceWrapper, count, args);
        } catch (Throwable e) {
            RecordLog.warn("Unexpected entry exit exception", e);
        }
    }
}
