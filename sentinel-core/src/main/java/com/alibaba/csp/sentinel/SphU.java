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
import com.alibaba.csp.sentinel.slots.block.Rule;
import com.alibaba.csp.sentinel.slots.system.SystemRule;

/**
 * 用于记录统计信息和对资源执行规则检查的基本Sentinel API。
 *
 * <p>从概念上来说，需要保护的物理或逻辑资源应该被条目包围。如果满足任何条件，则对该资源的请求将被阻止。
 * 例如：当超过任何{@link Rule}的阈值时，一旦请求被阻止，将抛出{@link BlockException}。
 *
 * <p>为了配置条件，我们可以使用{@code XxxRuleManager#loadRules()}来加载规则。
 *
 * <p>代码示例：{@code abc}代表被保护的资源名称
 * <pre>{@code
 *  public void foo() {
 *      Entry entry = null;
 *      try {
 *          entry = Spu.entry("abc");
 *      } catch (BlockException e) {
 *          // 当执行到此处时，代表对资源的请求被阻塞。
 *          // 在此处增加处理阻塞的代码
 *      } catch (Throwable e) {
 *          // 业务异常
 *          Tracer.trace(e);
 *      } finally {
 *          // 确保条目被释放
 *          if (entry != null) {
 *              entry.exit();
 *          }
 *      }
 *  }
 * }</pre>
 *
 * <p>确保{@link SphU#entry(String)}和{@link Entry#exit()}成对出现在同一个线程中。
 * 否则将抛出{@link ErrorEntryFreeException}。
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 * @see SphO
 */
public class SphU {

    private static final Object[] OBJECTS0 = new Object[0];

    /**
     * 私有构造器，应该通过静态方法来使用该类
     */
    private SphU() {}

    /**
     * 记录统计并对给定资源执行规则校验
     *
     * @param name 受保护资源的唯一名称
     * @return 该调用的 {@link Entry} (用于标记调用完成和获取上下文数据）
     * @throws BlockException 如果满足阻塞条件（例如：度量标准超出了任何规则的阈值）
     */
    public static Entry entry(String name) throws BlockException {
        return Env.sph.entry(name, EntryType.OUT, 1, OBJECTS0);
    }

    /**
     * 检查关于受保护方法的所有{@link Rule}
     *
     * @param method 受保护的方法
     * @return 该调用的 {@link Entry} (用于标记调用完成和获取上下文数据）
     * @throws BlockException 如果满足阻塞条件（例如：度量标准超出了任何规则的阈值）
     */
    public static Entry entry(Method method) throws BlockException {
        return Env.sph.entry(method, EntryType.OUT, 1, OBJECTS0);
    }

    /**
     * 检查关于受保护方法的所有{@link Rule}
     *
     * @param method     受保护的方法
     * @param batchCount 调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @return 该调用的 {@link Entry} (用于标记调用完成和获取上下文数据）
     * @throws BlockException 如果满足阻塞条件（例如：度量标准超出了任何规则的阈值）
     */
    public static Entry entry(Method method, int batchCount) throws BlockException {
        return Env.sph.entry(method, EntryType.OUT, batchCount, OBJECTS0);
    }

    /**
     * 记录统计并对给定资源执行规则检查
     *
     * @param name       资源的唯一名称
     * @param batchCount 调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @return 该调用的 {@link Entry} (用于标记调用完成和获取上下文数据）
     * @throws BlockException 如果满足阻塞条件（例如：度量标准超出了任何规则的阈值）
     */
    public static Entry entry(String name, int batchCount) throws BlockException {
        return Env.sph.entry(name, EntryType.OUT, batchCount, OBJECTS0);
    }

    /**
     * 检查受保护方法的所有{@link Rule}
     *
     * @param method      受保护的方法
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @throws BlockException 如果满足阻塞条件（例如：度量标准超出了任何规则的阈值）
     */
    public static Entry entry(Method method, EntryType trafficType) throws BlockException {
        return Env.sph.entry(method, trafficType, 1, OBJECTS0);
    }

    /**
     * 记录统计并对给定资源执行规则检查
     *
     * @param name        受保护资源的唯一名称
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @throws BlockException 如果满足阻塞条件（例如：度量标准超出了任何规则的阈值）
     */
    public static Entry entry(String name, EntryType trafficType) throws BlockException {
        return Env.sph.entry(name, trafficType, 1, OBJECTS0);
    }

    /**
     * 检查受保护方法的所有{@link Rule}
     *
     *
     * @param method      受保护的方法
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @param batchCount  调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @throws BlockException 如果满足阻塞条件（例如：度量标准超出了任何规则的阈值）
     */
    public static Entry entry(Method method, EntryType trafficType, int batchCount) throws BlockException {
        return Env.sph.entry(method, trafficType, batchCount, OBJECTS0);
    }

    /**
     * 记录统计并对给定资源执行规则校验
     *
     * @param name        受保护资源的唯一名称
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @param batchCount  调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @return 该调用的 {@link Entry} (用于标记调用完成和获取上下文数据）
     * @throws BlockException 如果满足阻塞条件（例如：度量标准超出了任何规则的阈值）
     */
    public static Entry entry(String name, EntryType trafficType, int batchCount) throws BlockException {
        return Env.sph.entry(name, trafficType, batchCount, OBJECTS0);
    }

    /**
     * 校验受保护方法的所有{@link Rule}
     *
     * @param method      受保护方法
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @param batchCount  调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @param args        用于参数流控或自定义Slot的参数
     * @return 该调用的 {@link Entry} (用于标记调用完成和获取上下文数据）
     * @throws BlockException 如果满足阻塞条件（例如：度量标准超出了任何规则的阈值）
     */
    public static Entry entry(Method method, EntryType trafficType, int batchCount, Object... args)
        throws BlockException {
        return Env.sph.entry(method, trafficType, batchCount, args);
    }

    /**
     * 记录统计并对给定资源执行规则检查
     *
     * @param name        受保护资源的唯一名称
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @param batchCount  调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @param args        用于参数流控或自定义Slot的参数
     * @throws BlockException 如果满足阻塞条件（例如：度量标准超出了任何规则的阈值）
     */
    public static Entry entry(String name, EntryType trafficType, int batchCount, Object... args)
        throws BlockException {
        return Env.sph.entry(name, trafficType, batchCount, args);
    }

    /**
     * 记录统计和检查资源的规则，这是一个异步调用
     *
     * @param name 受保护资源的统一名称
     * @throws BlockException 如果满足阻塞条件（例如：度量标准超出了任何规则的阈值）
     * @since 0.2.0
     */
    public static AsyncEntry asyncEntry(String name) throws BlockException {
        return Env.sph.asyncEntry(name, EntryType.OUT, 1, OBJECTS0);
    }

    /**
     * 记录统计和检查资源的规则，这是一个异步调用
     *
     * @param name        受保护资源的统一名称
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @return Entry 该调用的 {@link Entry} (用于标记调用完成和获取上下文数据）
     * @throws BlockException 如果满足阻塞条件（例如：度量标准超出了任何规则的阈值）
     * @since 0.2.0
     */
    public static AsyncEntry asyncEntry(String name, EntryType trafficType) throws BlockException {
        return Env.sph.asyncEntry(name, trafficType, 1, OBJECTS0);
    }

    /**
     * 记录统计和检查资源的规则，这是一个异步调用
     *
     * @param name        受保护资源的统一名称
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @param batchCount  调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @param args        用于参数流控的参数
     * @return 该调用的 {@link Entry} (用于标记调用完成和获取上下文数据）
     * @throws BlockException 如果满足阻塞条件（例如：度量标准超出了任何规则的阈值）
     * @since 0.2.0
     */
    public static AsyncEntry asyncEntry(String name, EntryType trafficType, int batchCount, Object... args)
        throws BlockException {
        return Env.sph.asyncEntry(name, trafficType, batchCount, args);
    }

    /**
     * 记录统计和检查资源的规则。该条目具有优先权。
     *
     * @param name 受保护资源的唯一名称
     * @throws BlockException 如果满足阻塞条件（例如：度量标准超出了任何规则的阈值）
     * @since 1.4.0
     */
    public static Entry entryWithPriority(String name) throws BlockException {
        return Env.sph.entryWithPriority(name, EntryType.OUT, 1, true);
    }

    /**
     * 记录统计并对给定资源执行规则校验。该条目具有优先权。
     *
     * @param name        受保护资源的唯一名称
     * @param trafficType 流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     * @since 1.4.0
     */
    public static Entry entryWithPriority(String name, EntryType trafficType) throws BlockException {
        return Env.sph.entryWithPriority(name, trafficType, 1, true);
    }

    /**
     * 记录统计并对给定资源执行规则检查
     *
     * @param name         受保护资源的唯一字符串名称
     * @param resourceType 资源分类 (e.g. Web or RPC)
     * @param trafficType  流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     * @since 1.7.0
     */
    public static Entry entry(String name, int resourceType, EntryType trafficType) throws BlockException {
        return Env.sph.entryWithType(name, resourceType, trafficType, 1, OBJECTS0);
    }

    /**
     * 记录统计并对给定资源执行规则检查
     *
     * @param name         受保护资源的唯一字符串名称
     * @param resourceType 资源分类 (e.g. Web or RPC)
     * @param trafficType  流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @param args         用于参数流控或自定义Slot的参数
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     * @since 1.7.0
     */
    public static Entry entry(String name, int resourceType, EntryType trafficType, Object[] args)
        throws BlockException {
        return Env.sph.entryWithType(name, resourceType, trafficType, 1, args);
    }

    /**
     * 记录统计并对给定资源执行规则检查，这是一个异步调用
     *
     * @param name         受保护资源的唯一字符串名称
     * @param resourceType 资源分类 (e.g. Web or RPC)
     * @param trafficType  流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     * @since 1.7.0
     */
    public static AsyncEntry asyncEntry(String name, int resourceType, EntryType trafficType)
        throws BlockException {
        return Env.sph.asyncEntryWithType(name, resourceType, trafficType, 1, false, OBJECTS0);
    }

    /**
     * 记录统计并对给定资源执行规则检查，这是一个异步调用
     *
     * @param name         受保护资源的唯一字符串名称
     * @param resourceType 资源分类 (e.g. Web or RPC)
     * @param trafficType  流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @param args         用于参数流控或自定义Slot的参数
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     * @since 1.7.0
     */
    public static AsyncEntry asyncEntry(String name, int resourceType, EntryType trafficType, Object[] args)
        throws BlockException {
        return Env.sph.asyncEntryWithType(name, resourceType, trafficType, 1, false, args);
    }

    /**
     * 记录统计并对给定资源执行规则检查，这是一个异步调用
     *
     * @param name         受保护资源的唯一字符串名称
     * @param trafficType  流量类型（入站，出战或内部），被用于标记当系统不稳定时是否阻塞。
     *                    需要注意的是，只有入站流量才会被{@link SystemRule}阻塞。
     * @param resourceType 资源分类 (e.g. Web or RPC)
     * @param batchCount   调用中的调用次数（例如：batchCount=2 代表请求2个令牌）
     * @param args         用于参数流控或自定义Slot的参数
     * @return the {@link Entry} of this invocation (used for mark the invocation complete and get context data)
     * @throws BlockException 如果满足阻塞条件（指标超出了任何阈值）
     * @since 1.7.0
     */
    public static AsyncEntry asyncEntry(String name, int resourceType, EntryType trafficType, int batchCount, Object[] args) throws BlockException {
        return Env.sph.asyncEntryWithType(name, resourceType, trafficType, batchCount, false, args);
    }
}
