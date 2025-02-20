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

/**
 * 某些处理的容器以及在处理完成时的通知方式
 *
 * @author qinan.qn
 * @author jialiang.linjl
 * @author leyou(lihao)
 * @author Eric Zhao
 */
public interface ProcessorSlot<T> {

    /**
     * 此插槽的入口
     *
     * @param context         当前上下文
     * @param resourceWrapper 当前资源的包装器
     * @param param           泛型参数，通常是{@link com.alibaba.csp.sentinel.node.Node}
     * @param count           申请的Token数量
     * @param prioritized     entry是否优先
     * @param args            原始调用的参数
     * @throws Throwable blocked exception or unexpected error
     */
    void entry(Context context, ResourceWrapper resourceWrapper, T param, int count, boolean prioritized, Object... args) throws Throwable;

    /**
     * 表示{@link #entry(Context, ResourceWrapper, Object, int, boolean, Object...)}的完成
     *
     * @param context         当前上下文
     * @param resourceWrapper 当前资源的包装器
     * @param obj             相关对象，例如{@link com.alibaba.csp.sentinel.node.Node}
     * @param count           申请的Token数量
     * @param prioritized     entry是否优先
     * @param args            原始调用的参数
     * @throws Throwable blocked exception or unexpected error
     */
    void fireEntry(Context context, ResourceWrapper resourceWrapper, Object obj, int count, boolean prioritized, Object... args) throws Throwable;

    /**
     * 退出该插槽
     *
     * @param context         当前上下文
     * @param resourceWrapper 当前资源的包装器
     * @param count           申请的Token数量
     * @param args            原始调用的参数
     */
    void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args);

    /**
     * 表示{@link #exit(Context, ResourceWrapper, int, Object...)}的完成
     *
     * @param context         当前上下文
     * @param resourceWrapper 当前资源的包装器
     * @param count           申请的Token数量
     * @param args            原始调用的参数
     */
    void fireExit(Context context, ResourceWrapper resourceWrapper, int count, Object... args);
}
