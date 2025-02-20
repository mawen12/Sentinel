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
package com.alibaba.csp.sentinel.slots.block.degrade;

import com.alibaba.csp.sentinel.slots.block.AbstractRule;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;

import java.util.Objects;

/**
 * 当资源处于不可用状态时便会触发降级。这些资源将会在下一个定义的时间窗口被降级。
 * 有两种方式检查一个资源是否稳定：
 * <ul>
 *     <li>
 *         平均响应时间（Average response time），对应{@link RuleConstant#DEGRADE_GRADE_RT}，
 *         当平均RT超过阈值{@link #count}时，资源将进入“准降级”的状态。如果后续5次请求的RT仍然超过{@link #count}时，
 *         该资源将被降级，意味着在下一个时间窗口{@link #timeWindow}内对该资源的访问都会被阻塞。
 *     </li>
 *     <li>
 *         异常率（Exception ratio），当每秒异常总数与成功QPS的比率超过阈值，在下一个时间窗口，
 *         对该资源的访问都会被阻塞。
 *     </li>
 * </ul>
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 */
public class DegradeRule extends AbstractRule {

    public DegradeRule() {}

    public DegradeRule(String resourceName) {
        setResource(resourceName);
    }

    /**
     * 断路器策略：
     * <ul>
     *     <li>0: 基于平均响应时间(average RT)</li>
     *     <li>1: 基于异常率(exception ratio)</li>
     *     <li>2: 基于异常总数(exception count)</li>
     * </ul>
     */
    private int grade = RuleConstant.DEGRADE_GRADE_RT;

    /**
     * 阈值总和。具体含义取决于{@link #grade}
     * <ul>
     *     <li>average RT: 意味着最大响应时间（毫秒）</li>
     *     <li>exception ratio: 意味着[0, 1]之间的异常率</li>
     *     <li>exception count: 意味着异常总和</li>
     * </ul>
     */
    private double count;

    /**
     * 当断路器打开时的恢复时间（秒）。当超时后，断路器将从OPEN进入HALF_OPEN，允许尝试部分请求
     */
    private int timeWindow;

    /**
     * 可以打断断路器的最小请求数（在获取统计时间跨度内）
     *
     * @since 1.7.0
     */
    private int minRequestAmount = RuleConstant.DEGRADE_DEFAULT_MIN_REQUEST_AMOUNT;

    /**
     * average RT：慢请求率的阈值
     *
     * @since 1.8.0
     */
    private double slowRatioThreshold = 1.0d;

    /**
     * 间隔统计持续时间（毫秒）
     *
     * @since 1.8.0
     */
    private int statIntervalMs = 1000;

    public int getGrade() {
        return grade;
    }

    public DegradeRule setGrade(int grade) {
        this.grade = grade;
        return this;
    }

    public double getCount() {
        return count;
    }

    public DegradeRule setCount(double count) {
        this.count = count;
        return this;
    }

    public int getTimeWindow() {
        return timeWindow;
    }

    public DegradeRule setTimeWindow(int timeWindow) {
        this.timeWindow = timeWindow;
        return this;
    }

    public int getMinRequestAmount() {
        return minRequestAmount;
    }

    public DegradeRule setMinRequestAmount(int minRequestAmount) {
        this.minRequestAmount = minRequestAmount;
        return this;
    }

    public double getSlowRatioThreshold() {
        return slowRatioThreshold;
    }

    public DegradeRule setSlowRatioThreshold(double slowRatioThreshold) {
        this.slowRatioThreshold = slowRatioThreshold;
        return this;
    }

    public int getStatIntervalMs() {
        return statIntervalMs;
    }

    public DegradeRule setStatIntervalMs(int statIntervalMs) {
        this.statIntervalMs = statIntervalMs;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        if (!super.equals(o)) { return false; }
        DegradeRule rule = (DegradeRule)o;
        return Double.compare(rule.count, count) == 0 &&
            timeWindow == rule.timeWindow &&
            grade == rule.grade &&
            minRequestAmount == rule.minRequestAmount &&
            Double.compare(rule.slowRatioThreshold, slowRatioThreshold) == 0 &&
            statIntervalMs == rule.statIntervalMs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), count, timeWindow, grade, minRequestAmount,
            slowRatioThreshold, statIntervalMs);
    }

    @Override
    public String toString() {
        return "DegradeRule{" +
            "resource=" + getResource() +
            ", grade=" + grade +
            ", count=" + count +
            ", limitApp=" + getLimitApp() +
            ", timeWindow=" + timeWindow +
            ", minRequestAmount=" + minRequestAmount +
            ", slowRatioThreshold=" + slowRatioThreshold +
            ", statIntervalMs=" + statIntervalMs +
            '}';
    }
}
