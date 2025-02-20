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
package com.alibaba.csp.sentinel.slots.system;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.csp.sentinel.Constants;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.concurrent.NamedThreadFactory;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.property.DynamicSentinelProperty;
import com.alibaba.csp.sentinel.property.SentinelProperty;
import com.alibaba.csp.sentinel.property.SimplePropertyListener;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;

/**
 * Sentinel 系统规则使入站流量和容量满足。它包含平均响应时间、QPS、入站请求的线程总数。
 * 它同时提供系统负载的策略，但仅在Linux系统上提供。
 *
 * <p>响应时间、QPS、线程数量很容易理解。如果入站请求的RT、QPS、线程总数超过了阈值，该请求
 * 将被拒绝。然而，我们使用不同的方法来计算负载。
 *
 * <p>将系统视作管道，约束之间的转换会导致三个不同区域（流量受限、容量受限、危险区域），
 * 具有性质上不同的行为。当飞行中没有足够的请求来填充管道时，RPprop决定行为；否则，系统容量
 * 占主导地位。约束线再飞行中=容量*RTprop处相交。由于管道在此点之后已满，飞行中容量过剩会
 * 创建一个队列，从而导致RTT对飞行中流量的线性依赖以及系统负载的增加，在危险区域，系统将停止
 * 响应。
 *
 * <p>更多内容可参考BBR算法。
 *
 * <p>需要注意{@link SystemRule}仅影响入站请求，出站流量将不会被{@link SystemRule}限制。
 *
 * @author jialiang.linjl
 * @author leyou
 */
public final class SystemRuleManager {

    private static volatile double highestSystemLoad = Double.MAX_VALUE;
    /**
     * cpu usage, between [0, 1]
     */
    private static volatile double highestCpuUsage = Double.MAX_VALUE;
    private static volatile double qps = Double.MAX_VALUE;
    private static volatile long maxRt = Long.MAX_VALUE;
    private static volatile long maxThread = Long.MAX_VALUE;
    /**
     * mark whether the threshold are set by user.
     */
    private static volatile boolean highestSystemLoadIsSet = false;
    private static volatile boolean highestCpuUsageIsSet = false;
    private static volatile boolean qpsIsSet = false;
    private static volatile boolean maxRtIsSet = false;
    private static volatile boolean maxThreadIsSet = false;

    private static AtomicBoolean checkSystemStatus = new AtomicBoolean(false);

    private static SystemStatusListener statusListener = null;
    private final static SystemPropertyListener listener = new SystemPropertyListener();
    private static SentinelProperty<List<SystemRule>> currentProperty = new DynamicSentinelProperty<List<SystemRule>>();

    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    private final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1,
        new NamedThreadFactory("sentinel-system-status-record-task", true));

    static {
        checkSystemStatus.set(false);
        statusListener = new SystemStatusListener();
        scheduler.scheduleAtFixedRate(statusListener, 0, 1, TimeUnit.SECONDS);
        currentProperty.addListener(listener);
    }

    /**
     * Listen to the {@link SentinelProperty} for {@link SystemRule}s. The property is the source
     * of {@link SystemRule}s. System rules can also be set by {@link #loadRules(List)} directly.
     *
     * @param property the property to listen.
     */
    public static void register2Property(SentinelProperty<List<SystemRule>> property) {
        synchronized (listener) {
            RecordLog.info("[SystemRuleManager] Registering new property to system rule manager");
            currentProperty.removeListener(listener);
            property.addListener(listener);
            currentProperty = property;
        }
    }

    /**
     * Load {@link SystemRule}s, former rules will be replaced.
     *
     * @param rules new rules to load.
     */
    public static void loadRules(List<SystemRule> rules) {
        currentProperty.updateValue(rules);
    }

    /**
     * Get a copy of the rules.
     *
     * @return a new copy of the rules.
     */
    public static List<SystemRule> getRules() {
        // TODO by mawen jdk7 棱形语法
        List<SystemRule> result = new ArrayList<SystemRule>();
        // 确保开了状态检查
        if (!checkSystemStatus.get()) {
            return result;
        }

        // 最高系统使用率
        if (highestSystemLoadIsSet) {
            SystemRule loadRule = new SystemRule();
            loadRule.setHighestSystemLoad(highestSystemLoad);
            result.add(loadRule);
        }

        // 最高CPU使用率
        if (highestCpuUsageIsSet) {
            SystemRule rule = new SystemRule();
            rule.setHighestCpuUsage(highestCpuUsage);
            result.add(rule);
        }

        // 最大系统响应时间
        if (maxRtIsSet) {
            SystemRule rtRule = new SystemRule();
            rtRule.setAvgRt(maxRt);
            result.add(rtRule);
        }

        // 最大线程数
        if (maxThreadIsSet) {
            SystemRule threadRule = new SystemRule();
            threadRule.setMaxThread(maxThread);
            result.add(threadRule);
        }

        // QPS
        if (qpsIsSet) {
            SystemRule qpsRule = new SystemRule();
            qpsRule.setQps(qps);
            result.add(qpsRule);
        }

        return result;
    }

    public static double getInboundQpsThreshold() {
        return qps;
    }

    public static long getRtThreshold() {
        return maxRt;
    }

    public static long getMaxThreadThreshold() {
        return maxThread;
    }

    static class SystemPropertyListener extends SimplePropertyListener<List<SystemRule>> {

        @Override
        public synchronized void configUpdate(List<SystemRule> rules) {
            restoreSetting();
            // systemRules = rules;
            if (rules != null && rules.size() >= 1) {
                for (SystemRule rule : rules) {
                    loadSystemConf(rule);
                }
            } else {
                checkSystemStatus.set(false);
            }

            RecordLog.info(String.format("[SystemRuleManager] Current system check status: %s, "
                    + "highestSystemLoad: %e, "
                    + "highestCpuUsage: %e, "
                    + "maxRt: %d, "
                    + "maxThread: %d, "
                    + "maxQps: %e",
                checkSystemStatus.get(),
                highestSystemLoad,
                highestCpuUsage,
                maxRt,
                maxThread,
                qps));
        }

        protected void restoreSetting() {
            checkSystemStatus.set(false);

            // should restore changes
            highestSystemLoad = Double.MAX_VALUE;
            highestCpuUsage = Double.MAX_VALUE;
            maxRt = Long.MAX_VALUE;
            maxThread = Long.MAX_VALUE;
            qps = Double.MAX_VALUE;

            highestSystemLoadIsSet = false;
            highestCpuUsageIsSet = false;
            maxRtIsSet = false;
            maxThreadIsSet = false;
            qpsIsSet = false;
        }

    }

    public static Boolean getCheckSystemStatus() {
        return checkSystemStatus.get();
    }

    public static double getSystemLoadThreshold() {
        return highestSystemLoad;
    }

    public static double getCpuUsageThreshold() {
        return highestCpuUsage;
    }

    public static void loadSystemConf(SystemRule rule) {
        boolean checkStatus = false;
        // Check if it's valid.

        if (rule.getHighestSystemLoad() >= 0) {
            highestSystemLoad = Math.min(highestSystemLoad, rule.getHighestSystemLoad());
            highestSystemLoadIsSet = true;
            checkStatus = true;
        }

        if (rule.getHighestCpuUsage() >= 0) {
            if (rule.getHighestCpuUsage() > 1) {
                RecordLog.warn(String.format("[SystemRuleManager] Ignoring invalid SystemRule: "
                    + "highestCpuUsage %.3f > 1", rule.getHighestCpuUsage()));
            } else {
                highestCpuUsage = Math.min(highestCpuUsage, rule.getHighestCpuUsage());
                highestCpuUsageIsSet = true;
                checkStatus = true;
            }
        }

        if (rule.getAvgRt() >= 0) {
            maxRt = Math.min(maxRt, rule.getAvgRt());
            maxRtIsSet = true;
            checkStatus = true;
        }
        if (rule.getMaxThread() >= 0) {
            maxThread = Math.min(maxThread, rule.getMaxThread());
            maxThreadIsSet = true;
            checkStatus = true;
        }

        if (rule.getQps() >= 0) {
            qps = Math.min(qps, rule.getQps());
            qpsIsSet = true;
            checkStatus = true;
        }

        checkSystemStatus.set(checkStatus);

    }

    /**
     * 将{@link SystemRule}应用到资源上，仅处理入站流量。
     *
     * @param resourceWrapper 资源包装器
     * @throws BlockException 当达到任何系统规则的阈值时，将抛出该异常
     */
    public static void checkSystem(ResourceWrapper resourceWrapper, int count) throws BlockException {
        // 对于空的资源包装器，不处理
        if (resourceWrapper == null) {
            return;
        }
        // 确保开启了检查开关
        if (!checkSystemStatus.get()) {
            return;
        }

        // 仅处理入站流量
        if (resourceWrapper.getEntryType() != EntryType.IN) {
            return;
        }

        // 获取当前的QPS
        double currentQps = Constants.ENTRY_NODE.passQps();
        // 确保总的QPS<=阈值
        if (currentQps + count > qps) {
            throw new SystemBlockException(resourceWrapper.getName(), "qps");
        }

        // 获取当前的线程数
        int currentThread = Constants.ENTRY_NODE.curThreadNum();
        // 确保当前线程数<=最大线程数
        if (currentThread > maxThread) {
            throw new SystemBlockException(resourceWrapper.getName(), "thread");
        }

        // 获取平均响应时间
        double rt = Constants.ENTRY_NODE.avgRt();
        // 确保当前响应时间<=最大响应时间
        if (rt > maxRt) {
            throw new SystemBlockException(resourceWrapper.getName(), "rt");
        }

        // 加载BBR算法
        if (highestSystemLoadIsSet && getCurrentSystemAvgLoad() > highestSystemLoad) {
            if (!checkBbr(currentThread)) {
                throw new SystemBlockException(resourceWrapper.getName(), "load");
            }
        }

        // 计算CPU使用率
        if (highestCpuUsageIsSet && getCurrentCpuUsage() > highestCpuUsage) {
            throw new SystemBlockException(resourceWrapper.getName(), "cpu");
        }
    }

    private static boolean checkBbr(int currentThread) {
        // 确保当前线程数<=successQps*minRT/1000
        if (currentThread > 1 && currentThread > Constants.ENTRY_NODE.maxSuccessQps() * Constants.ENTRY_NODE.minRt() / 1000) {
            return false;
        }
        return true;
    }

    public static double getCurrentSystemAvgLoad() {
        return statusListener.getSystemAverageLoad();
    }

    public static double getCurrentCpuUsage() {
        return statusListener.getCpuUsage();
    }
}
