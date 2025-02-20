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
 * 基于链表的{@link ProcessorSlot}抽象实现
 *
 * @author qinan.qn
 * @author jialiang.linjl
 */
public abstract class AbstractLinkedProcessorSlot<T> implements ProcessorSlot<T> {

    /**
     * 代表链上下一个{@link ProcessorSlot}
     */
    private AbstractLinkedProcessorSlot<?> next = null;

    /**
     * 在成功进入当前的{@link ProcessorSlot#entry(Context, ResourceWrapper, Object, int, boolean, Object...)}后，
     * 检查下一个是否存在，如果存在则进入下一个{@link ProcessorSlot#entry(Context, ResourceWrapper, Object, int, boolean, Object...)}
     *
     * @param context         当前上下文
     * @param resourceWrapper 当前资源的包装器
     * @param obj             相关对象，例如{@link com.alibaba.csp.sentinel.node.Node}
     * @param count           申请的Token数量
     * @param prioritized     entry是否优先
     * @param args            原始调用的参数
     * @throws Throwable
     */
    @Override
    public void fireEntry(Context context, ResourceWrapper resourceWrapper, Object obj, int count, boolean prioritized, Object... args)
        throws Throwable {
        if (next != null) {
            next.transformEntry(context, resourceWrapper, obj, count, prioritized, args);
        }
    }

    /**
     * 用于进入{@link #entry(Context, ResourceWrapper, Object, int, boolean, Object...)}
     *
     * @param context 当前上下文
     * @param resourceWrapper 资源包装器
     * @param o 相关对象，例如{@link com.alibaba.csp.sentinel.node.Node}
     * @param count 申请的Token数量
     * @param prioritized entry是否优先
     * @param args 原始调用的参数
     * @throws Throwable
     */
    @SuppressWarnings("unchecked")
    void transformEntry(Context context, ResourceWrapper resourceWrapper, Object o, int count, boolean prioritized, Object... args)
        throws Throwable {
        T t = (T)o;
        entry(context, resourceWrapper, t, count, prioritized, args);
    }

    /**
     * 在成功退出当前{@link ProcessorSlot#exit(Context, ResourceWrapper, int, Object...)}后，
     * 检查下一个是否存在，如果存在则进入下一个{@link ProcessorSlot#exit(Context, ResourceWrapper, int, Object...)}
     *
     * @param context         当前上下文
     * @param resourceWrapper 当前资源的包装器
     * @param count           申请的Token数量
     * @param args            原始调用的参数
     */
    @Override
    public void fireExit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        if (next != null) {
            next.exit(context, resourceWrapper, count, args);
        }
    }

    public AbstractLinkedProcessorSlot<?> getNext() {
        return next;
    }

    public void setNext(AbstractLinkedProcessorSlot<?> next) {
        this.next = next;
    }

}
