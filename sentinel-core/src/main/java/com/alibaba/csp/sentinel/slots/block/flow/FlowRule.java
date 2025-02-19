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

import com.alibaba.csp.sentinel.slots.block.AbstractRule;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;

/**
 * 每条流控规则主要有三个因素组成：
 * <ul>
 *     <li>等级：代表流控的阈值类型，基于QPS还是线程数</li>
 *     <li>策略：代表基于调用关系的策略</li>
 *     <li>控制行为：代表QPS的调整行为，当QPS超过阈值时如果处理到来的请求</li>
 * </ul>
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 */
public class FlowRule extends AbstractRule {

    public FlowRule() {
        super();
        setLimitApp(RuleConstant.LIMIT_APP_DEFAULT);
    }

    public FlowRule(String resourceName) {
        super();
        setResource(resourceName);
        setLimitApp(RuleConstant.LIMIT_APP_DEFAULT);
    }

    /**
     * 流量控制的阈值类型（0：线程总数，1：QPS）
     */
    private int grade = RuleConstant.FLOW_GRADE_QPS;

    /**
     * 流控阈值
     */
    private double count;

    /**
     * 基于调用链的流控策略
     *
     * <p>流控策略可选如下：
     * <ul>
     *     <li>{@link RuleConstant#STRATEGY_DIRECT} 用于直接流量控制（按来源）</li>
     *     <li>{@link RuleConstant#STRATEGY_RELATE} 用于相关流量控制（及相关资源）</li>
     *     <li>{@link RuleConstant#STRATEGY_CHAIN} 用于链式流量控制（按入口资源）</li>
     * </ul>
     */
    private int strategy = RuleConstant.STRATEGY_DIRECT;

    /**
     * 流控中引用相关资源或上下文的资源
     */
    private String refResource;

    /**
     * 控制行为
     *
     * <p>速率限制器可选如下：
     * <ul>
     *     <li>{@link RuleConstant#CONTROL_BEHAVIOR_DEFAULT} 直接拒绝</li>
     *     <li>{@link RuleConstant#CONTROL_BEHAVIOR_WARM_UP} 热身</li>
     *     <li>{@link RuleConstant#CONTROL_BEHAVIOR_RATE_LIMITER} 速率限制器</li>
     *     <li>{@link RuleConstant#CONTROL_BEHAVIOR_WARM_UP_RATE_LIMITER} 热身+速率限制器</li>
     * </ul>
     */
    private int controlBehavior = RuleConstant.CONTROL_BEHAVIOR_DEFAULT;

    private int warmUpPeriodSec = 10;

    /**
     * 当为速率限制器行为中的最大排队时间
     */
    private int maxQueueingTimeMs = 500;

    /**
     * 是否为集群模式
     */
    private boolean clusterMode;

    /**
     * 集群模式的流控规则配置
     */
    private ClusterFlowConfig clusterConfig;

    /**
     * 流量整形（限制）控制器
     */
    private TrafficShapingController controller;

    public int getControlBehavior() {
        return controlBehavior;
    }

    public FlowRule setControlBehavior(int controlBehavior) {
        this.controlBehavior = controlBehavior;
        return this;
    }

    public int getMaxQueueingTimeMs() {
        return maxQueueingTimeMs;
    }

    public FlowRule setMaxQueueingTimeMs(int maxQueueingTimeMs) {
        this.maxQueueingTimeMs = maxQueueingTimeMs;
        return this;
    }

    FlowRule setRater(TrafficShapingController rater) {
        this.controller = rater;
        return this;
    }

    TrafficShapingController getRater() {
        return controller;
    }

    public int getWarmUpPeriodSec() {
        return warmUpPeriodSec;
    }

    public FlowRule setWarmUpPeriodSec(int warmUpPeriodSec) {
        this.warmUpPeriodSec = warmUpPeriodSec;
        return this;
    }

    public int getGrade() {
        return grade;
    }

    public FlowRule setGrade(int grade) {
        this.grade = grade;
        return this;
    }

    public double getCount() {
        return count;
    }

    public FlowRule setCount(double count) {
        this.count = count;
        return this;
    }

    public int getStrategy() {
        return strategy;
    }

    public FlowRule setStrategy(int strategy) {
        this.strategy = strategy;
        return this;
    }

    public String getRefResource() {
        return refResource;
    }

    public FlowRule setRefResource(String refResource) {
        this.refResource = refResource;
        return this;
    }

    public boolean isClusterMode() {
        return clusterMode;
    }

    public FlowRule setClusterMode(boolean clusterMode) {
        this.clusterMode = clusterMode;
        return this;
    }

    public ClusterFlowConfig getClusterConfig() {
        return clusterConfig;
    }

    public FlowRule setClusterConfig(ClusterFlowConfig clusterConfig) {
        this.clusterConfig = clusterConfig;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        if (!super.equals(o)) { return false; }

        FlowRule rule = (FlowRule)o;

        if (grade != rule.grade) { return false; }
        if (Double.compare(rule.count, count) != 0) { return false; }
        if (strategy != rule.strategy) { return false; }
        if (controlBehavior != rule.controlBehavior) { return false; }
        if (warmUpPeriodSec != rule.warmUpPeriodSec) { return false; }
        if (maxQueueingTimeMs != rule.maxQueueingTimeMs) { return false; }
        if (clusterMode != rule.clusterMode) { return false; }
        if (refResource != null ? !refResource.equals(rule.refResource) : rule.refResource != null) { return false; }
        return clusterConfig != null ? clusterConfig.equals(rule.clusterConfig) : rule.clusterConfig == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        long temp;
        result = 31 * result + grade;
        temp = Double.doubleToLongBits(count);
        result = 31 * result + (int)(temp ^ (temp >>> 32));
        result = 31 * result + strategy;
        result = 31 * result + (refResource != null ? refResource.hashCode() : 0);
        result = 31 * result + controlBehavior;
        result = 31 * result + warmUpPeriodSec;
        result = 31 * result + maxQueueingTimeMs;
        result = 31 * result + (clusterMode ? 1 : 0);
        result = 31 * result + (clusterConfig != null ? clusterConfig.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FlowRule{" +
            "resource=" + getResource() +
            ", limitApp=" + getLimitApp() +
            ", grade=" + grade +
            ", count=" + count +
            ", strategy=" + strategy +
            ", refResource=" + refResource +
            ", controlBehavior=" + controlBehavior +
            ", warmUpPeriodSec=" + warmUpPeriodSec +
            ", maxQueueingTimeMs=" + maxQueueingTimeMs +
            ", clusterMode=" + clusterMode +
            ", clusterConfig=" + clusterConfig +
            ", controller=" + controller +
            '}';
    }
}
