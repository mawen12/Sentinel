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
package com.alibaba.csp.sentinel.slotchain;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slots.block.BlockException;

/**
 * 用于当进入{@link com.alibaba.csp.sentinel.slots.statistic.StatisticSlot}时触发的回调。
 * 进入被分为{@code passed}和{@code blocked}。
 *
 * @author Eric Zhao
 * @since 0.2.0
 */
public interface ProcessorSlotEntryCallback<T> {

    /**
     * 在请求通过后触发的回调
     *
     * @param context 当前上下文
     * @param resourceWrapper 资源包装器
     * @param param 相关对象，例如{@link com.alibaba.csp.sentinel.node.Node}
     * @param count 申请的Token总数
     * @param args 原始调用参数
     * @throws Exception
     */
    void onPass(Context context, ResourceWrapper resourceWrapper, T param, int count, Object... args) throws Exception;

    /**
     * 当请求阻塞后触发的回调
     *
     * @param ex Sentinel阻塞异常
     * @param context 当前上下文
     * @param resourceWrapper 资源包装器
     * @param param 相关对象，例如{@link com.alibaba.csp.sentinel.node.Node}
     * @param count 申请的Token总数
     * @param args 原始调用参数
     */
    void onBlocked(BlockException ex, Context context, ResourceWrapper resourceWrapper, T param, int count, Object... args);
}
