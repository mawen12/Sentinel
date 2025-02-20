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
package com.alibaba.csp.sentinel.slots.block.flow;

import com.alibaba.csp.sentinel.Constants;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.spi.Spi;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.function.Function;

import java.util.Collection;

/**
 * 从之前的插槽{@link com.alibaba.csp.sentinel.slots.nodeselector.NodeSelectorSlot}、
 * {@link com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot}、
 * {@link com.alibaba.csp.sentinel.slots.statistic.StatisticSlot}中组合运行时的统计信息。
 * 该类将使用预设的规则来决定是否到来的请求是否被阻塞。
 *
 * <p>如果触发了任何规则，{@link com.alibaba.csp.sentinel.SphU#entry(String)}将抛出{@link FlowException}
 * 异常，用户可以通过捕获{@link FlowException}来自定义它们自己的逻辑。
 *
 * <p>一个资源可以存在多个流控规则。该类将遍历这些规则，直到其中一个被触发，或者所有规则都遍历完毕。
 *
 * <p>每一个{@link FlowRule}主要由几个因素组成：降级、策略、路径。我们可以组合这些因素来获得不同的影响。
 *
 * <p>降级是被定义在{@link FlowRule#grade}字段中的。
 * <ul>
 *     <li>0: 用于线程隔离</li>
 *     <li>1: 请求计数整形（QPS）</li>
 * </ul>
 * 以上两个数据都在运行时被收集，我们可以通过以下命令查看这些统计：
 * <pre>{@code
 *  curl http://localhost:8719/tree
 *
 *  idx id    thread pass  blocked   success total aRt   1m-pass   1m-block   1m-all   exception
 *  2   abc647 0      460    46          46   1    27      630       276        897      0
 * }</pre>
 *
 * <ul>
 *     <li>{@code thread} 用于</li>
 * </ul>
 *
 *
 * <ul>
 * <li>{@code thread} for the count of threads that is currently processing the resource</li>
 * <li>{@code pass} for the count of incoming request within one second</li>
 * <li>{@code blocked} for the count of requests blocked within one second</li>
 * <li>{@code success} for the count of the requests successfully handled by Sentinel within one second</li>
 * <li>{@code RT} for the average response time of the requests within a second</li>
 * <li>{@code total} for the sum of incoming requests and blocked requests within one second</li>
 * <li>{@code 1m-pass} is for the count of incoming requests within one minute</li>
 * <li>{@code 1m-block} is for the count of a request blocked within one minute</li>
 * <li>{@code 1m-all} is the total of incoming and blocked requests within one minute</li>
 * <li>{@code exception} is for the count of business (customized) exceptions in one second</li>
 * </ul>
 *
 * This stage is usually used to protect resources from occupying. If a resource
 * takes long time to finish, threads will begin to occupy. The longer the
 * response takes, the more threads occupy.
 *
 * Besides counter, thread pool or semaphore can also be used to achieve this.
 *
 * - Thread pool: Allocate a thread pool to handle these resource. When there is
 * no more idle thread in the pool, the request is rejected without affecting
 * other resources.
 *
 * - Semaphore: Use semaphore to control the concurrent count of the threads in
 * this resource.
 *
 * The benefit of using thread pool is that, it can walk away gracefully when
 * time out. But it also bring us the cost of context switch and additional
 * threads. If the incoming requests is already served in a separated thread,
 * for instance, a Servlet HTTP request, it will almost double the threads count if
 * using thread pool.
 *
 * <h3>Traffic Shaping</h3>
 * <p>
 * When QPS exceeds the threshold, Sentinel will take actions to control the incoming request,
 * and is configured by {@code controlBehavior} field in flow rules.
 * </p>
 * <ol>
 * <li>Immediately reject ({@code RuleConstant.CONTROL_BEHAVIOR_DEFAULT})</li>
 * <p>
 * This is the default behavior. The exceeded request is rejected immediately
 * and the FlowException is thrown
 * </p>
 *
 * <li>Warmup ({@code RuleConstant.CONTROL_BEHAVIOR_WARM_UP})</li>
 * <p>
 * If the load of system has been low for a while, and a large amount of
 * requests comes, the system might not be able to handle all these requests at
 * once. However if we steady increase the incoming request, the system can warm
 * up and finally be able to handle all the requests.
 * This warmup period can be configured by setting the field {@code warmUpPeriodSec} in flow rules.
 * </p>
 *
 * <li>Uniform Rate Limiting ({@code RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER})</li>
 * <p>
 * This strategy strictly controls the interval between requests.
 * In other words, it allows requests to pass at a stable, uniform rate.
 * </p>
 * <img src="https://raw.githubusercontent.com/wiki/alibaba/Sentinel/image/uniform-speed-queue.png" style="max-width:
 * 60%;"/>
 * <p>
 * This strategy is an implement of <a href="https://en.wikipedia.org/wiki/Leaky_bucket">leaky bucket</a>.
 * It is used to handle the request at a stable rate and is often used in burst traffic (e.g. message handling).
 * When a large number of requests beyond the system’s capacity arrive
 * at the same time, the system using this strategy will handle requests and its
 * fixed rate until all the requests have been processed or time out.
 * </p>
 * </ol>
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 */
@Spi(order = Constants.ORDER_FLOW_SLOT)
public class FlowSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    private final FlowRuleChecker checker;

    public FlowSlot() {
        this(new FlowRuleChecker());
    }

    /**
     * Package-private for test.
     *
     * @param checker flow rule checker
     * @since 1.6.1
     */
    FlowSlot(FlowRuleChecker checker) {
        AssertUtil.notNull(checker, "flow checker should not be null");
        this.checker = checker;
    }

    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, DefaultNode node, int count,
                      boolean prioritized, Object... args) throws Throwable {
        checkFlow(resourceWrapper, context, node, count, prioritized);

        fireEntry(context, resourceWrapper, node, count, prioritized, args);
    }

    void checkFlow(ResourceWrapper resource, Context context, DefaultNode node, int count, boolean prioritized)
        throws BlockException {
        checker.checkFlow(ruleProvider, resource, context, node, count, prioritized);
    }

    @Override
    public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        fireExit(context, resourceWrapper, count, args);
    }

    private final Function<String, Collection<FlowRule>> ruleProvider = new Function<String, Collection<FlowRule>>() {
        @Override
        public Collection<FlowRule> apply(String resource) {
            return FlowRuleManager.getFlowRules(resource);
        }
    };
}
