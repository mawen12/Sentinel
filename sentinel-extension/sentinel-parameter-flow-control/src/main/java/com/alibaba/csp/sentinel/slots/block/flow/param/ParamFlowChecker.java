/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.TokenResult;
import com.alibaba.csp.sentinel.cluster.TokenResultStatus;
import com.alibaba.csp.sentinel.cluster.TokenService;
import com.alibaba.csp.sentinel.cluster.client.TokenClientProvider;
import com.alibaba.csp.sentinel.cluster.server.EmbeddedClusterTokenServerProvider;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.statistic.cache.CacheMap;
import com.alibaba.csp.sentinel.util.TimeUtil;

import java.lang.reflect.Array;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Rule checker for parameter flow control.
 *
 * @author Eric Zhao
 * @since 0.2.0
 */
public final class ParamFlowChecker {

    public static boolean passCheck(ResourceWrapper resourceWrapper, /*@Valid*/ ParamFlowRule rule, /*@Valid*/ int count, Object... args) {
        // 当参数为空时，直接返回true
        if (args == null) {
            return true;
        }

        // 获取要检查参数的索引
        int paramIdx = rule.getParamIdx();
        // 如果实际参数个数小于指定所索引，直接返回true
        if (args.length <= paramIdx) {
            return true;
        }

        // 获取参数值
        Object value = args[paramIdx];

        // 使用paramFlowKey方法的结果分配值
        if (value instanceof ParamFlowArgument) {
            value = ((ParamFlowArgument) value).paramFlowKey();
        }
        // 如果值为空，直接返回true
        if (value == null) {
            return true;
        }

        // 集群模式下，且基于QPS流控策略时，基于集群检查
        if (rule.isClusterMode() && rule.getGrade() == RuleConstant.FLOW_GRADE_QPS) {
            return passClusterCheck(resourceWrapper, rule, count, value);
        }

        // 非集群模式下，基于本地检查
        return passLocalCheck(resourceWrapper, rule, count, value);
    }

    /**
     * 执行本地检查
     *
     * @param resourceWrapper 资源包装器
     * @param rule 参数流控规则
     * @param count 申请令牌总数
     * @param value 要校验的参数值
     * @return 校验结果
     */
    private static boolean passLocalCheck(ResourceWrapper resourceWrapper, ParamFlowRule rule, int count, Object value) {
        try {
            if (Collection.class.isAssignableFrom(value.getClass())) { // 对于集合类型的检查
                for (Object param : ((Collection) value)) {
                    if (!passSingleValueCheck(resourceWrapper, rule, count, param)) {
                        return false;
                    }
                }
            } else if (value.getClass().isArray()) {// 对于数组类型的参数检查
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    Object param = Array.get(value, i);
                    if (!passSingleValueCheck(resourceWrapper, rule, count, param)) {
                        return false;
                    }
                }
            } else {// 单值参数检查
                return passSingleValueCheck(resourceWrapper, rule, count, value);
            }
        } catch (Throwable e) {
            RecordLog.warn("[ParamFlowChecker] Unexpected error", e);
        }

        return true;
    }

    /**
     * 对单值进行校验
     *
     * @param resourceWrapper 资源包装器
     * @param rule 参数流控规则
     * @param acquireCount 申请的令牌总数
     * @param value 参数值
     * @return 校验结果
     */
    static boolean passSingleValueCheck(ResourceWrapper resourceWrapper, ParamFlowRule rule, int acquireCount, Object value) {
        if (rule.getGrade() == RuleConstant.FLOW_GRADE_QPS) {// 基于降级的QPS策略
            if (rule.getControlBehavior() == RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER) {// 使用速率限制器
                return passThrottleLocalCheck(resourceWrapper, rule, acquireCount, value);
            } else {
                return passDefaultLocalCheck(resourceWrapper, rule, acquireCount, value);
            }
        } else if (rule.getGrade() == RuleConstant.FLOW_GRADE_THREAD) {
            Set<Object> exclusionItems = rule.getParsedHotItems().keySet();
            long threadCount = getParameterMetric(resourceWrapper).getThreadCount(rule.getParamIdx(), value);
            if (exclusionItems.contains(value)) {
                int itemThreshold = rule.getParsedHotItems().get(value);
                return ++threadCount <= itemThreshold;
            }
            long threshold = (long) rule.getCount();
            return ++threadCount <= threshold;
        }

        return true;
    }

    static boolean passDefaultLocalCheck(ResourceWrapper resourceWrapper, ParamFlowRule rule, int acquireCount,
                                         Object value) {
        ParameterMetric metric = getParameterMetric(resourceWrapper);
        CacheMap<Object, AtomicReference<TokenUpdateStatus>> tokenCounters = metric == null ? null : metric.getRuleStampedTokenCounter(rule);

        DateTimeFormatter dtf = DateTimeFormatter.ISO_DATE_TIME;
        if (tokenCounters == null) {
            return true;
        }

        // Calculate max token count (threshold)
        Set<Object> exclusionItems = rule.getParsedHotItems().keySet();
        long tokenCount = (long) rule.getCount();
        if (exclusionItems.contains(value)) {
            tokenCount = rule.getParsedHotItems().get(value);
        }

        if (tokenCount == 0) {
            return false;
        }

        long maxCount = tokenCount + rule.getBurstCount();
        if (acquireCount > maxCount) {
            return false;
        }

        while (true) {
            long currentTime = TimeUtil.currentTimeMillis();

            AtomicReference<TokenUpdateStatus> atomicLastStatus = tokenCounters.putIfAbsent(value, new AtomicReference<>(
                    new TokenUpdateStatus(currentTime, maxCount - acquireCount)
            ));
            if (atomicLastStatus == null) {
                // Token never added, just replenish the tokens and consume {@code acquireCount} immediately.
                return true;
            }

            // Calculate the time duration since last token was added.
            TokenUpdateStatus lastStatus = atomicLastStatus.get();
            long passTime = currentTime - lastStatus.getLastAddTokenTime();
            // A simplified token bucket algorithm that will replenish the tokens only when statistic window has passed.
            long newQps;
            if (passTime > rule.getDurationInSec() * 1000) {
                long restQps = lastStatus.getRestQps();
                long toAddCount = (passTime * tokenCount) / (rule.getDurationInSec() * 1000);
                newQps = toAddCount + restQps > maxCount ? (maxCount - acquireCount)
                        : (restQps + toAddCount - acquireCount);

                if (newQps < 0) {
                    return false;
                }
                TokenUpdateStatus newStatus = new TokenUpdateStatus(currentTime, newQps);
                if (atomicLastStatus.compareAndSet(lastStatus, newStatus)) {
                    return true;
                }
                Thread.yield();
            } else {
                newQps = lastStatus.getRestQps() - acquireCount;
                if (newQps >= 0) {
                    TokenUpdateStatus newStatus = new TokenUpdateStatus(lastStatus.getLastAddTokenTime(), newQps);
                    if (atomicLastStatus.compareAndSet(lastStatus, newStatus)) {
                        return true;
                    }
                } else {
                    return false;
                }

                Thread.yield();
            }
        }
    }

    static boolean passThrottleLocalCheck(ResourceWrapper resourceWrapper, ParamFlowRule rule, int acquireCount, Object value) {
        // 获取该资源的参数指标
        ParameterMetric metric = getParameterMetric(resourceWrapper);
        // 获取该规则的时间计数器
        CacheMap<Object, AtomicLong> timeRecorderMap = metric == null ? null : metric.getRuleTimeCounter(rule);
        // 为空的时候，返回通过
        if (timeRecorderMap == null) {
            return true;
        }

        // 计算最大的令牌总数
        Set<Object> exclusionItems = rule.getParsedHotItems().keySet();
        // 获取令牌总数
        long tokenCount = (long) rule.getCount();
        if (exclusionItems.contains(value)) {
            tokenCount = rule.getParsedHotItems().get(value);
        }
        if (tokenCount == 0) {
            return false;
        }

        // 计算预期申请这么多数量的token/已申请的token总数的
        long costTime = Math.round(1.0 * 1000 * acquireCount * rule.getDurationInSec() / tokenCount);
        while (true) {
            long currentTime = TimeUtil.currentTimeMillis();
            AtomicLong timeRecorder = timeRecorderMap.putIfAbsent(value, new AtomicLong(currentTime));
            if (timeRecorder == null) {
                return true;
            }
            //AtomicLong timeRecorder = timeRecorderMap.get(value);
            long lastPassTime = timeRecorder.get();
            long expectedTime = lastPassTime + costTime;

            if (expectedTime <= currentTime || expectedTime - currentTime < rule.getMaxQueueingTimeMs()) {
                AtomicLong lastPastTimeRef = timeRecorderMap.get(value);
                if (lastPastTimeRef.compareAndSet(lastPassTime, currentTime)) {
                    long waitTime = expectedTime - currentTime;
                    if (waitTime > 0) {
                        lastPastTimeRef.set(expectedTime);
                        try {
                            TimeUnit.MILLISECONDS.sleep(waitTime);
                        } catch (InterruptedException e) {
                            RecordLog.warn("passThrottleLocalCheck: wait interrupted", e);
                        }
                    }
                    return true;
                } else {
                    Thread.yield();
                }
            } else {
                return false;
            }
        }
    }

    private static ParameterMetric getParameterMetric(ResourceWrapper resourceWrapper) {
        // Should not be null.
        return ParameterMetricStorage.getParamMetric(resourceWrapper);
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> toCollection(Object value) {
        if (value instanceof Collection) {
            return (Collection<Object>) value;
        } else if (value.getClass().isArray()) {
            List<Object> params = new ArrayList<Object>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object param = Array.get(value, i);
                params.add(param);
            }
            return params;
        } else {
            return Collections.singletonList(value);
        }
    }

    private static boolean passClusterCheck(ResourceWrapper resourceWrapper, ParamFlowRule rule, int count, Object value) {
        try {
            // 将值转换为集合
            Collection<Object> params = toCollection(value);

            // 获取令牌服务
            TokenService clusterService = pickClusterService();
            if (clusterService == null) {
                // 当没有可用的集群客户端或服务器时，需要触发fallback，转而执行本地检查
                return fallbackToLocalOrPass(resourceWrapper, rule, count, params);
            }

            // 获取参数令牌
            TokenResult result = clusterService.requestParamToken(rule.getClusterConfig().getFlowId(), count, params);
            switch (result.getStatus()) {
                case TokenResultStatus.OK:
                    return true;
                case TokenResultStatus.BLOCKED:
                    return false;
                default:
                    return fallbackToLocalOrPass(resourceWrapper, rule, count, params);
            }
        } catch (Throwable ex) {
            RecordLog.warn("[ParamFlowChecker] Request cluster token for parameter unexpected failed", ex);
            return fallbackToLocalOrPass(resourceWrapper, rule, count, value);
        }
    }

    private static boolean fallbackToLocalOrPass(ResourceWrapper resourceWrapper, ParamFlowRule rule, int count, Object value) {
        // 如果允许回退到local，那就执行本地检查
        if (rule.getClusterConfig().isFallbackToLocalWhenFail()) {
            return passLocalCheck(resourceWrapper, rule, count, value);
        } else {
            // 规则不会生效，直接通过
            return true;
        }
    }

    /**
     * @return 获取集群中的令牌服务
     */
    private static TokenService pickClusterService() {
        if (ClusterStateManager.isClient()) {
            return TokenClientProvider.getClient();
        }
        if (ClusterStateManager.isServer()) {
            return EmbeddedClusterTokenServerProvider.getServer();
        }
        return null;
    }

    private ParamFlowChecker() {
    }
}
