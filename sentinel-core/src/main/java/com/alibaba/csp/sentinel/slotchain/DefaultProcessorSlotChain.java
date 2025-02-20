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
 * 责任链设计模式的应用。
 *
 * @author qinan.qn
 * @author jialiang.linjl
 */
public class DefaultProcessorSlotChain extends ProcessorSlotChain {

    /**
     * 默认头部的{@link #entry(Context, ResourceWrapper, Object, int, boolean, Object...)}
     * 和{@link #exit(Context, ResourceWrapper, int, Object...)}不做任何处理。
     */
    AbstractLinkedProcessorSlot<?> first = new AbstractLinkedProcessorSlot<Object>() {

        @Override
        public void entry(Context context, ResourceWrapper resourceWrapper, Object t, int count, boolean prioritized, Object... args)
            throws Throwable {
            super.fireEntry(context, resourceWrapper, t, count, prioritized, args);
        }

        @Override
        public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
            super.fireExit(context, resourceWrapper, count, args);
        }

    };
    /**
     * 初始尾部和头部指向同一个
     */
    AbstractLinkedProcessorSlot<?> end = first;

    @Override
    public void addFirst(AbstractLinkedProcessorSlot<?> protocolProcessor) {
        // 设置该处理器的下一个为之前头部的下一个
        protocolProcessor.setNext(first.getNext());
        // 将该处理器插槽放到头部的下一个
        first.setNext(protocolProcessor);
        if (end == first) {// 如果头尾相同，此时新加元素，需要将尾部指向新加入的处理器插槽
            end = protocolProcessor;
        }
    }

    @Override
    public void addLast(AbstractLinkedProcessorSlot<?> protocolProcessor) {
        // 将该处理器放到当前尾部的下一个
        end.setNext(protocolProcessor);
        // 将尾部指向新加入的处理器插槽
        end = protocolProcessor;
    }

    /**
     * Same as {@link #addLast(AbstractLinkedProcessorSlot)}.
     *
     * @param next processor to be added.
     */
    @Override
    public void setNext(AbstractLinkedProcessorSlot<?> next) {
        addLast(next);
    }

    @Override
    public AbstractLinkedProcessorSlot<?> getNext() {
        return first.getNext();
    }

    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, Object t, int count, boolean prioritized, Object... args)
        throws Throwable {
        first.transformEntry(context, resourceWrapper, t, count, prioritized, args);
    }

    @Override
    public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        first.exit(context, resourceWrapper, count, args);
    }

}
