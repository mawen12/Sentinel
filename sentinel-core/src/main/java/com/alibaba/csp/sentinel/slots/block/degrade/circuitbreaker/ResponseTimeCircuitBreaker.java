/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.statistic.base.LeapArray;
import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.TimeUtil;

/**
 * 基于响应时间检测的{@link CircuitBreaker}实现。
 *
 * <p>通过{@link Entry#completeTimestamp}-{@link Entry#createTimestamp}来计算响应耗时，
 * 并和{@link #maxAllowedRt}做比较。如果超过了，就被视为慢请求。
 *
 * <p>其次还通过慢请求的总体比例来计算
 *
 * @author Eric Zhao
 * @since 1.8.0
 */
public class ResponseTimeCircuitBreaker extends AbstractCircuitBreaker {

    private static final double SLOW_REQUEST_RATIO_MAX_VALUE = 1.0d;

    /**
     * 最大允许的响应耗时
     */
    private final long maxAllowedRt;
    /**
     * 最大慢请求的速率
     */
    private final double maxSlowRequestRatio;
    /**
     * 可以打断断路器的最小请求数
     */
    private final int minRequestAmount;

    /**
     * 慢请求计数器
     */
    private final LeapArray<SlowRequestCounter> slidingCounter;

    public ResponseTimeCircuitBreaker(DegradeRule rule) {
        this(rule, new SlowRequestLeapArray(1, rule.getStatIntervalMs()));
    }

    ResponseTimeCircuitBreaker(DegradeRule rule, LeapArray<SlowRequestCounter> stat) {
        super(rule);
        // 该降级规则的策略应该为基于平均响应时间
        AssertUtil.isTrue(rule.getGrade() == RuleConstant.DEGRADE_GRADE_RT, "rule metric type should be RT");
        AssertUtil.notNull(stat, "stat cannot be null");
        // 返回最接近的整数部分
        this.maxAllowedRt = Math.round(rule.getCount());
        // 慢请求率的阈值
        this.maxSlowRequestRatio = rule.getSlowRatioThreshold();
        // 可以打断断路器的最小请求数
        this.minRequestAmount = rule.getMinRequestAmount();
        // 计数器
        this.slidingCounter = stat;
    }

    @Override
    public void resetStat() {
        // 重置当前桶，bucket数量为1
        slidingCounter.currentWindow().value().reset();
    }

    @Override
    public void onRequestComplete(Context context) {
        // 获取当前窗口的慢请求计数器
        SlowRequestCounter counter = slidingCounter.currentWindow().value();
        // 获取当前entry
        Entry entry = context.getCurEntry();
        // entry不存在时直接返回
        if (entry == null) {
            return;
        }
        // 获取该entry的完成时间
        long completeTime = entry.getCompleteTimestamp();
        if (completeTime <= 0) {
            completeTime = TimeUtil.currentTimeMillis();
        }
        // 计算响应耗时
        long rt = completeTime - entry.getCreateTimestamp();
        // 当此次响应耗时超出了最大允许的耗时
        if (rt > maxAllowedRt) {
            // 将慢请求计数+1
            counter.slowCount.add(1);
        }
        // 将总请求计数+1
        counter.totalCount.add(1);

        // 根据断路器状态进行检查
        handleStateChangeWhenThresholdExceeded(rt);
    }

    private void handleStateChangeWhenThresholdExceeded(long rt) {
        if (currentState.get() == State.OPEN) {// 如果本身已经打开了，就不需要再处理了
            return;
        }
        
        if (currentState.get() == State.HALF_OPEN) {// 对于半开状态的处理
            // In detecting request
            // TODO: improve logic for half-open recovery
            if (rt > maxAllowedRt) {// 当本次响应超过了慢响应阈值，便将半开状态切换为打开
                fromHalfOpenToOpen(1.0d);
            } else {// 当本次响应没有超过慢响应阈值，便将半开状态切换为关闭
                fromHalfOpenToClose();
            }
            return;
        }

        // 获取滑动窗口中所有的慢请求计数器
        List<SlowRequestCounter> counters = slidingCounter.values();
        long slowCount = 0;
        long totalCount = 0;
        for (SlowRequestCounter counter : counters) {
            // 累加慢请求数
            slowCount += counter.slowCount.sum();
            // 累加总请求数
            totalCount += counter.totalCount.sum();
        }
        // 如果尚未达到最小请求总数，则退出
        if (totalCount < minRequestAmount) {
            return;
        }
        // 计算慢请求占比
        double currentRatio = slowCount * 1.0d / totalCount;
        // 当慢请求率超过了阈值，需要打开断路器
        if (currentRatio > maxSlowRequestRatio) {
            transformToOpen(currentRatio);
        }
        // 当全部为慢请求时，关闭断路器
        if (Double.compare(currentRatio, maxSlowRequestRatio) == 0 && Double.compare(maxSlowRequestRatio, SLOW_REQUEST_RATIO_MAX_VALUE) == 0) {
            transformToOpen(currentRatio);
        }
    }

    static class SlowRequestCounter {
        /**
         * 慢请求总数
         */
        private LongAdder slowCount;
        /**
         * 请求总数
         */
        private LongAdder totalCount;

        public SlowRequestCounter() {
            this.slowCount = new LongAdder();
            this.totalCount = new LongAdder();
        }

        public LongAdder getSlowCount() {
            return slowCount;
        }

        public LongAdder getTotalCount() {
            return totalCount;
        }

        public SlowRequestCounter reset() {
            slowCount.reset();
            totalCount.reset();
            return this;
        }

        @Override
        public String toString() {
            return "SlowRequestCounter{" +
                "slowCount=" + slowCount +
                ", totalCount=" + totalCount +
                '}';
        }
    }

    static class SlowRequestLeapArray extends LeapArray<SlowRequestCounter> {

        public SlowRequestLeapArray(int sampleCount, int intervalInMs) {
            super(sampleCount, intervalInMs);
        }

        @Override
        public SlowRequestCounter newEmptyBucket(long timeMillis) {
            return new SlowRequestCounter();
        }

        @Override
        protected WindowWrap<SlowRequestCounter> resetWindowTo(WindowWrap<SlowRequestCounter> w, long startTime) {
            w.resetTo(startTime);
            w.value().reset();
            return w;
        }
    }
}
