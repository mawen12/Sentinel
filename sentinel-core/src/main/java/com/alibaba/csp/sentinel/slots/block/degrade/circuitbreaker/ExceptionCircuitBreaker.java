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
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.statistic.base.LeapArray;
import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;
import com.alibaba.csp.sentinel.util.AssertUtil;

import static com.alibaba.csp.sentinel.slots.block.RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT;
import static com.alibaba.csp.sentinel.slots.block.RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO;

/**
 * 基于异常检测的{@link CircuitBreaker}实现。
 *
 * @author Eric Zhao
 * @since 1.8.0
 */
public class ExceptionCircuitBreaker extends AbstractCircuitBreaker {

    /**
     *
     */
    private final int strategy;
    /**
     * 可以打断断路器的最小请求总数
     */
    private final int minRequestAmount;
    /**
     * 阈值
     */
    private final double threshold;

    /**
     * 错误请求计数器
     */
    private final LeapArray<SimpleErrorCounter> stat;

    public ExceptionCircuitBreaker(DegradeRule rule) {
        this(rule, new SimpleErrorCounterLeapArray(1, rule.getStatIntervalMs()));
    }

    ExceptionCircuitBreaker(DegradeRule rule, LeapArray<SimpleErrorCounter> stat) {
        super(rule);
        // 获取策略
        this.strategy = rule.getGrade();
        // 应为基于异常比率或异常总数的模式
        boolean modeOk = strategy == DEGRADE_GRADE_EXCEPTION_RATIO || strategy == DEGRADE_GRADE_EXCEPTION_COUNT;
        AssertUtil.isTrue(modeOk, "rule strategy should be error-ratio or error-count");
        AssertUtil.notNull(stat, "stat cannot be null");
        // 可以打断断路器的最小请求数
        this.minRequestAmount = rule.getMinRequestAmount();
        // 获取异常总数
        this.threshold = rule.getCount();
        this.stat = stat;
    }

    @Override
    protected void resetStat() {
        // 重置当前桶，bucket数量为1
        stat.currentWindow().value().reset();
    }

    @Override
    public void onRequestComplete(Context context) {
        // 获取当前entry
        Entry entry = context.getCurEntry();
        // entry不存在时直接返回
        if (entry == null) {
            return;
        }
        // 获取异常
        Throwable error = entry.getError();
        // 获取当前窗口的慢请求计数器
        SimpleErrorCounter counter = stat.currentWindow().value();
        if (error != null) {
            // 将异常请求数+1
            counter.getErrorCount().add(1);
        }
        // 将总请求数+1
        counter.getTotalCount().add(1);

        handleStateChangeWhenThresholdExceeded(error);
    }

    private void handleStateChangeWhenThresholdExceeded(Throwable error) {
        if (currentState.get() == State.OPEN) { // 如果本身已经打开了，就不需要再处理了
            return;
        }
        
        if (currentState.get() == State.HALF_OPEN) {// 对于半开状态的处理
            // In detecting request
            if (error == null) {// 未出现异常时，
                // 从HALF_OPEN->CLOSED
                fromHalfOpenToClose();
            } else {
                // 从HALF_OPEN->OPEN
                fromHalfOpenToOpen(1.0d);
            }
            return;
        }

        // 获取滑动窗口中所有的异常请求计数器
        List<SimpleErrorCounter> counters = stat.values();
        long errCount = 0;
        long totalCount = 0;
        for (SimpleErrorCounter counter : counters) {
            // 累加错误请求数
            errCount += counter.errorCount.sum();
            // 累加总请求数
            totalCount += counter.totalCount.sum();
        }
        // 如果尚未达到最小请求总数，则退出
        if (totalCount < minRequestAmount) {
            return;
        }
        double curCount = errCount;
        // 异常比率策略，计算异常比率
        if (strategy == DEGRADE_GRADE_EXCEPTION_RATIO) {
            curCount = errCount * 1.0d / totalCount;
        }
        // 当超过阈值时，打开断路器
        if (curCount > threshold) {
            transformToOpen(curCount);
        }
    }

    static class SimpleErrorCounter {
        private LongAdder errorCount;
        private LongAdder totalCount;

        public SimpleErrorCounter() {
            this.errorCount = new LongAdder();
            this.totalCount = new LongAdder();
        }

        public LongAdder getErrorCount() {
            return errorCount;
        }

        public LongAdder getTotalCount() {
            return totalCount;
        }

        public SimpleErrorCounter reset() {
            errorCount.reset();
            totalCount.reset();
            return this;
        }

        @Override
        public String toString() {
            return "SimpleErrorCounter{" +
                "errorCount=" + errorCount +
                ", totalCount=" + totalCount +
                '}';
        }
    }

    static class SimpleErrorCounterLeapArray extends LeapArray<SimpleErrorCounter> {

        public SimpleErrorCounterLeapArray(int sampleCount, int intervalInMs) {
            super(sampleCount, intervalInMs);
        }

        @Override
        public SimpleErrorCounter newEmptyBucket(long timeMillis) {
            return new SimpleErrorCounter();
        }

        @Override
        protected WindowWrap<SimpleErrorCounter> resetWindowTo(WindowWrap<SimpleErrorCounter> w, long startTime) {
            // Update the start time and reset value.
            w.resetTo(startTime);
            w.value().reset();
            return w;
        }
    }
}
