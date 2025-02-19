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
package com.alibaba.csp.sentinel.slots.statistic.metric;

import java.util.List;

import com.alibaba.csp.sentinel.node.metric.MetricNode;
import com.alibaba.csp.sentinel.slots.statistic.data.MetricBucket;
import com.alibaba.csp.sentinel.util.function.Predicate;

/**
 * 代表记录受保护资源的调用指标的基本结构
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 */
public interface Metric extends DebugSupport {

    /**
     * @return 返回总的请求成功总数
     */
    long success();

    /**
     * @return 返回最大请求成功总数
     */
    long maxSuccess();

    /**
     * @return 返回总的请求异常总数
     */
    long exception();

    /**
     * @return 返回总的阻塞总数
     */
    long block();

    /**
     * @return 返回总的通信证总数，不包含{@link #occupiedPass()}
     */
    long pass();

    /**
     * @return 返回总的响应时间
     */
    long rt();

    /**
     * @return 返回最小的响应时间
     */
    long minRt();

    /**
     * @return 获取所有资源的聚合指标节点
     */
    List<MetricNode> details();

    /**
     * @param timePredicate time predicate
     * @return 返回满足时间条件的聚合度量项
     * @since 1.7.0
     */
    List<MetricNode> detailsOnCondition(Predicate<Long> timePredicate);

    /**
     * @return 返回原始的时间指标数组
     */
    MetricBucket[] windows();

    /**
     * 增加当前异常总和
     *
     * @param n 要增加的总数
     */
    void addException(int n);

    /**
     * 增加当前阻塞总数
     *
     * @param n 要增加的总数
     */
    void addBlock(int n);

    /**
     * 增加当前已完成的总数
     *
     * @param n 要增加的总数
     */
    void addSuccess(int n);

    /**
     * 增加当前已通过的总数
     *
     * @param n 要增加的总数
     */
    void addPass(int n);

    /**
     * 增加响应时间
     *
     * @param rt 响应时间
     */
    void addRT(long rt);

    /**
     * @return 返回秒级的滑动窗口长度
     */
    double getWindowIntervalInSec();

    /**
     * @return 返回滑动窗口的样本计数
     */
    int getSampleCount();

    /**
     * 该操作不会执行刷新，因此不会生成新的bucket。
     *
     * @param timeMillis valid time in ms
     * @return 与提供的时间戳精确关联的存储桶的传递计数，如果时间戳非法，则被视作0
     * @since 1.5.0
     */
    long getWindowPass(long timeMillis);

    // Occupy-based (@since 1.5.0)

    /**
     * 增加已占用通过，表示借用后一个窗口的令牌的通信证请求。
     *
     * @param acquireCount Token总和
     * @since 1.5.0
     */
    void addOccupiedPass(int acquireCount);

    /**
     * 添加已占用的请求
     *
     * @param futureTime   应该添加{@code acquireCount}的未来时间戳
     * @param acquireCount 令牌总数
     * @since 1.5.0
     */
    void addWaiting(long futureTime, int acquireCount);

    /**
     * @return 返回等待中的通过的总数
     * @since 1.5.0
     */
    long waiting();

    /**
     * @return 返回已占用的通过的总数
     * @since 1.5.0
     */
    long occupiedPass();

    // Tool methods.

    long previousWindowBlock();

    long previousWindowPass();
}
