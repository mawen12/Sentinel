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
package com.alibaba.csp.sentinel;

import java.lang.reflect.Method;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.system.SystemRule;

/**
 * 用于记录统计并对给定资源执行规则检查的基本接口
 *
 * @author qinan.qn
 * @author jialiang.linjl
 * @author leyou
 * @author Eric Zhao
 */
public interface Sph extends SphResourceTypeSupport {

    /**
     * 记录统计并对给定资源执行规则检查
     *
     * @param name 受保护资源的唯一字符串名称
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data).
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     */
    Entry entry(String name) throws BlockException;

    /**
     * 记录统计并对给定方法执行规则检查
     *
     * @param method 受保护的方法
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data).
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     */
    Entry entry(Method method) throws BlockException;

    /**
     * 记录统计并对给定方法执行规则检查
     *
     * @param method     受保护的方法
     * @param batchCount 调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data).
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     */
    Entry entry(Method method, int batchCount) throws BlockException;

    /**
     * 记录统计并对给定资源执行规则检查
     *
     * @param name       受保护资源的唯一字符串名称
     * @param batchCount 调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data).
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     */
    Entry entry(String name, int batchCount) throws BlockException;

    /**
     * 记录统计并对给定方法执行规则检查
     *
     * @param method      受保护的方法
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data).
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     */
    Entry entry(Method method, EntryType trafficType) throws BlockException;

    /**
     * 记录统计并对给定方法执行规则检查
     *
     * @param name        受保护资源的唯一字符串名称
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data).
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     */
    Entry entry(String name, EntryType trafficType) throws BlockException;

    /**
     * 记录统计并对给定方法执行规则检查
     *
     * @param method      受保护的方法
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @param batchCount  调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data).
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     */
    Entry entry(Method method, EntryType trafficType, int batchCount) throws BlockException;

    /**
     * 记录统计并对给定方法执行规则检查
     *
     * @param name        受保护资源的唯一字符串名称
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @param batchCount  调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data).
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     */
    Entry entry(String name, EntryType trafficType, int batchCount) throws BlockException;

    /**
     * 记录统计并对给定资源执行规则检查
     *
     * @param method      受保护的方法
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @param batchCount  调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @param args        用于参数流控或自定义Slot的参数
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data).
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     */
    Entry entry(Method method, EntryType trafficType, int batchCount, Object... args) throws BlockException;

    /**
     * 记录统计并对给定资源执行规则检查
     *
     * @param name        受保护资源的唯一字符串名称
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @param batchCount  调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @param args        用于参数流控或自定义Slot的参数
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     */
    Entry entry(String name, EntryType trafficType, int batchCount, Object... args) throws BlockException;

    /**
     * 创建带有受保护的异步资源
     *
     * @param name        受保护资源的唯一名称字符串
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @param batchCount  调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @param args        用于参数流控或自定义Slot的参数
     * @return created asynchronous entry
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     * @since 0.2.0
     */
    AsyncEntry asyncEntry(String name, EntryType trafficType, int batchCount, Object... args) throws BlockException;

    /**
     * 创建带有优先级的受保护的资源
     *
     * @param name        受保护资源的唯一名称字符串
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @param batchCount  调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @param prioritized 该条目是否优先
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     * @since 1.4.0
     */
    Entry entryWithPriority(String name, EntryType trafficType, int batchCount, boolean prioritized)
        throws BlockException;

    /**
     * 创建带有优先级的受保护的资源
     *
     * @param name        受保护资源的唯一名称字符串
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @param batchCount  调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @param prioritized 该条目是否优先
     * @param args        用于参数流控或自定义Slot的参数
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     * @since 1.5.0
     */
    Entry entryWithPriority(String name, EntryType trafficType, int batchCount, boolean prioritized, Object... args)
        throws BlockException;
}
