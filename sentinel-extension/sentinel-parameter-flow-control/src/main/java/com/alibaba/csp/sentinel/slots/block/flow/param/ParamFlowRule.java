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
package com.alibaba.csp.sentinel.slots.block.flow.param;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.alibaba.csp.sentinel.slots.block.AbstractRule;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;

/**
 * 热点频繁参数流控规则
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 * @since 0.2.0
 */
public class ParamFlowRule extends AbstractRule {

    public ParamFlowRule() {
    }

    public ParamFlowRule(String resourceName) {
        setResource(resourceName);
    }

    /**
     * QPS流控类型
     */
    private int grade = RuleConstant.FLOW_GRADE_QPS;

    /**
     * 参数索引
     */
    private Integer paramIdx;

    /**
     * 阈值
     */
    private double count;

    /**
     * 流量整形行为，默认拒绝
     */
    private int controlBehavior = RuleConstant.CONTROL_BEHAVIOR_DEFAULT;

    /**
     * 最大排队时间
     */
    private int maxQueueingTimeMs = 0;

    /**
     * 突发计数
     */
    private int burstCount = 0;
    /**
     * 持续时间（秒）
     */
    private long durationInSec = 1;

    /**
     * 参数的原始排除项
     */
    private List<ParamFlowItem> paramFlowItemList = new ArrayList<ParamFlowItem>();

    /**
     * 解析参数排除项，仅供内部使用
     */
    private Map<Object, Integer> hotItems = new HashMap<Object, Integer>();

    /**
     * 指示该规则是否用于集群模式
     */
    private boolean clusterMode = false;
    /**
     * 用于参数流控规则的集群模式下特定配置
     */
    private ParamFlowClusterConfig clusterConfig;

    public int getControlBehavior() {
        return controlBehavior;
    }

    public ParamFlowRule setControlBehavior(int controlBehavior) {
        this.controlBehavior = controlBehavior;
        return this;
    }

    public int getMaxQueueingTimeMs() {
        return maxQueueingTimeMs;
    }

    public ParamFlowRule setMaxQueueingTimeMs(int maxQueueingTimeMs) {
        this.maxQueueingTimeMs = maxQueueingTimeMs;
        return this;
    }

    public int getBurstCount() {
        return burstCount;
    }

    public ParamFlowRule setBurstCount(int burstCount) {
        this.burstCount = burstCount;
        return this;
    }

    public long getDurationInSec() {
        return durationInSec;
    }

    public ParamFlowRule setDurationInSec(long durationInSec) {
        this.durationInSec = durationInSec;
        return this;
    }

    public int getGrade() {
        return grade;
    }

    public ParamFlowRule setGrade(int grade) {
        this.grade = grade;
        return this;
    }

    public Integer getParamIdx() {
        return paramIdx;
    }

    public ParamFlowRule setParamIdx(Integer paramIdx) {
        this.paramIdx = paramIdx;
        return this;
    }

    public double getCount() {
        return count;
    }

    public ParamFlowRule setCount(double count) {
        this.count = count;
        return this;
    }

    public List<ParamFlowItem> getParamFlowItemList() {
        return paramFlowItemList;
    }

    public ParamFlowRule setParamFlowItemList(List<ParamFlowItem> paramFlowItemList) {
        this.paramFlowItemList = paramFlowItemList;
        return this;
    }

    public Integer retrieveExclusiveItemCount(Object value) {
        if (value == null || hotItems == null) {
            return null;
        }
        return hotItems.get(value);
    }

    Map<Object, Integer> getParsedHotItems() {
        return hotItems;
    }

    ParamFlowRule setParsedHotItems(Map<Object, Integer> hotItems) {
        this.hotItems = hotItems;
        return this;
    }

    public boolean isClusterMode() {
        return clusterMode;
    }

    public ParamFlowRule setClusterMode(boolean clusterMode) {
        this.clusterMode = clusterMode;
        return this;
    }

    public ParamFlowClusterConfig getClusterConfig() {
        return clusterConfig;
    }

    public ParamFlowRule setClusterConfig(ParamFlowClusterConfig clusterConfig) {
        this.clusterConfig = clusterConfig;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        ParamFlowRule that = (ParamFlowRule) o;

        if (grade != that.grade) {
            return false;
        }
        if (Double.compare(that.count, count) != 0) {
            return false;
        }
        if (controlBehavior != that.controlBehavior) {
            return false;
        }
        if (maxQueueingTimeMs != that.maxQueueingTimeMs) {
            return false;
        }
        if (burstCount != that.burstCount) {
            return false;
        }
        if (durationInSec != that.durationInSec) {
            return false;
        }
        if (clusterMode != that.clusterMode) {
            return false;
        }
        if (!Objects.equals(paramIdx, that.paramIdx)) {
            return false;
        }
        if (!Objects.equals(paramFlowItemList, that.paramFlowItemList)) {
            return false;
        }
        return Objects.equals(clusterConfig, that.clusterConfig);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        long temp;
        result = 31 * result + grade;
        result = 31 * result + (paramIdx != null ? paramIdx.hashCode() : 0);
        temp = Double.doubleToLongBits(count);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + controlBehavior;
        result = 31 * result + maxQueueingTimeMs;
        result = 31 * result + burstCount;
        result = 31 * result + (int) (durationInSec ^ (durationInSec >>> 32));
        result = 31 * result + (paramFlowItemList != null ? paramFlowItemList.hashCode() : 0);
        result = 31 * result + (clusterMode ? 1 : 0);
        result = 31 * result + (clusterConfig != null ? clusterConfig.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ParamFlowRule{" +
                "grade=" + grade +
                ", paramIdx=" + paramIdx +
                ", count=" + count +
                ", controlBehavior=" + controlBehavior +
                ", maxQueueingTimeMs=" + maxQueueingTimeMs +
                ", burstCount=" + burstCount +
                ", durationInSec=" + durationInSec +
                ", paramFlowItemList=" + paramFlowItemList +
                ", clusterMode=" + clusterMode +
                ", clusterConfig=" + clusterConfig +
                '}';
    }
}
