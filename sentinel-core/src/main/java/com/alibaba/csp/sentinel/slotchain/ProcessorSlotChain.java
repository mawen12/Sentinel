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

/**
 * 将所有处理器插槽连接成一条链。
 * 支持在头部和尾部进行处理器的插入。
 *
 * @author qinan.qn
 */
public abstract class ProcessorSlotChain extends AbstractLinkedProcessorSlot<Object> {

    /**
     * 在插槽链的头部增加一个处理器
     *
     * @param protocolProcessor 要被添加的处理器
     */
    public abstract void addFirst(AbstractLinkedProcessorSlot<?> protocolProcessor);

    /**
     * 在插槽链的尾部增加一个处理器
     *
     * @param protocolProcessor 要被添加的处理器
     */
    public abstract void addLast(AbstractLinkedProcessorSlot<?> protocolProcessor);
}
