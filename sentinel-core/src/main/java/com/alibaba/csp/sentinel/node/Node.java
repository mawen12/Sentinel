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
package com.alibaba.csp.sentinel.node;

import java.util.List;
import java.util.Map;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.node.metric.MetricNode;
import com.alibaba.csp.sentinel.slots.statistic.metric.DebugSupport;
import com.alibaba.csp.sentinel.util.function.Predicate;

/**
 * 保存资源的实时统计数据。
 *
 * @author qinan.qn
 * @author leyou
 * @author Eric Zhao
 */
public interface Node extends OccupySupport, DebugSupport {

    /**
     * @return 每分钟传入的请求总数{@code pass + block}
     */
    long totalRequest();

    /**
     * @return 每分钟通过的请求总数
     * @since 1.5.0
     */
    long totalPass();

    /**
     * 请求成功是指调用了{@link Entry#exit()}。
     *
     * @return 每分钟请求成功的总数
     */
    long totalSuccess();

    /**
     * @return 每分钟阻塞的请求总数
     */
    long blockRequest();

    /**
     * @return 每分钟发生业务异常的总数
     */
    long totalException();

    /**
     * @return 每秒允许通过请求的QPS
     */
    double passQps();

    /**
     * @return 每秒阻塞请求的QPS
     */
    double blockQps();

    /**
     * @return 每秒请求的QPS总和，{@code pass qps + block qps}
     */
    double totalQps();

    /**
     * 请求成功是指调用了{@link Entry#exit()}。
     *
     * @return 每秒已完成请求的QPS
     */
    double successQps();

    /**
     * @return 获取迄今为止估计的最大成功请求的QPS
     */
    double maxSuccessQps();

    /**
     * @return 每秒发生异常的QPS
     */
    double exceptionQps();

    /**
     * @return 每秒平均相应时间（Response Time -> RT）
     */
    double avgRt();

    /**
     * @return 获取迄今为止最小的响应时间（Response Time -> RT）
     */
    double minRt();

    /**
     * @return 当前活跃的线程数量
     */
    int curThreadNum();

    /**
     * 返回上一秒阻塞请求的QPS
     */
    double previousBlockQps();

    /**
     * 返回上一秒通用请求的QPS
     */
    double previousPassQps();

    /**
     * @return 返回资源合法的指标 {@link Node}
     */
    Map<Long, MetricNode> metrics();

    /**
     * @param timePredicate time predicate
     * @return 返回所有满足时间条件的原始指标项
     * @since 1.7.0
     */
    List<MetricNode> rawMetricsInMin(Predicate<Long> timePredicate);

    /**
     * @param count 添加通过计数
     */
    void addPassRequest(int count);

    /**
     * 添加响应时间和成功数
     *
     * @param rt      响应时间
     * @param success 要添加的成功总数
     */
    void addRtAndSuccess(long rt, int success);

    /**
     * 增加阻塞总数
     *
     * @param count 要添加的总数
     */
    void increaseBlockQps(int count);

    /**
     * 增加业务异常总数
     *
     * @param count 要添加的总数
     */
    void increaseExceptionQps(int count);

    /**
     * 增加当前线程数量
     */
    void increaseThreadNum();

    /**
     * 减少当前线程数量
     */
    void decreaseThreadNum();

    /**
     * 重置内部计数器，当{@link IntervalProperty#INTERVAL}或者
     * {@link SampleCountProperty#SAMPLE_COUNT}发生变化时才会重置。
     */
    void reset();
}
