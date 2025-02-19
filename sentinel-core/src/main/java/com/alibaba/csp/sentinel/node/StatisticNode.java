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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import com.alibaba.csp.sentinel.node.metric.MetricNode;
import com.alibaba.csp.sentinel.slots.statistic.metric.ArrayMetric;
import com.alibaba.csp.sentinel.slots.statistic.metric.Metric;
import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.util.function.Predicate;

/**
 * 统计节点保存三种类型的实时统计指标：
 * <ol>
 *     <li>秒级指标 {@link #rollingCounterInSecond}</li>
 *     <li>分钟级指标 {@link #rollingCounterInMinute}</li>
 *     <li>线程总数</li>
 * </ol>
 *
 * <p>Sentinel使用滑动窗口来实时记录和统计资源的统计信息。
 *
 * <p>场景1：当第一个请求到来时，Sentinel将创建一个特定的时间跨度的窗口桶
 * 来存储运行静态数据，例如总响应时间（RT），到来的请求（QPS），阻塞请求（bq）等。
 * 同时时间跨度由样本数定义。
 * <pre>
 * 	0      100ms
 *  +-------+--→ Sliding Windows
 * 	    ^
 * 	    |
 * 	  request
 * </pre>
 * <p>Sentinel使用合法通的静态数据来决定请求是否应该通过。例如：如果一个规则定义了仅能有100个请求通过，
 * 它将对有效存储桶中的所有QPS进行求和，并将其与规则中定义的阈值相比较。
 *
 * <p>场景2：持续请求
 * <pre>
 *  0    100ms    200ms    300ms
 *  +-------+-------+-------+-----→ Sliding Windows
 *                      ^
 *                      |
 *                   request
 * </pre>
 *
 * <p>场景3: 请求持续到来，之前的桶将变得非法</p>
 * <pre>
 *  0    100ms    200ms	  800ms	   900ms  1000ms    1300ms
 *  +-------+-------+ ...... +-------+-------+ ...... +-------+-----→ Sliding Windows
 *                                                      ^
 *                                                      |
 *                                                    request
 * </pre>
 *
 * <p>此时滑动窗口应该为：</p>
 * <pre>
 * 300ms     800ms  900ms  1000ms  1300ms
 *  + ...... +-------+ ...... +-------+-----→ Sliding Windows
 *                                                      ^
 *                                                      |
 *                                                    request
 * </pre>
 *
 * @author qinan.qn
 * @author jialiang.linjl
 */
public class StatisticNode implements Node {

    /**
     * 持有最近{@link IntervalProperty#INTERVAL}毫秒的统计信息。通过提供的{@link SampleCountProperty#SAMPLE_COUNT}
     * 将{@link IntervalProperty#INTERVAL}划分为时间跨度。
     */
    private transient volatile Metric rollingCounterInSecond = new ArrayMetric(SampleCountProperty.SAMPLE_COUNT, IntervalProperty.INTERVAL);

    /**
     * 持有最近60s的统计信息。其中 windowLengthInMs 被特意设置为1000ms，
     * 意思是每秒一个bucket，这样我们就可以得到每秒的精确统计数据。
     */
    private transient Metric rollingCounterInMinute = new ArrayMetric(60, 60 * 1000, false);

    /**
     * 统计线程总数
     */
    private LongAdder curThreadNum = new LongAdder();

    /**
     * 上次获取指标的时间戳
     */
    private long lastFetchTime = -1;

    @Override
    public Map<Long, MetricNode> metrics() {
        // The fetch operation is thread-safe under a single-thread scheduler pool.
        long currentTime = TimeUtil.currentTimeMillis();
        currentTime = currentTime - currentTime % 1000;
        Map<Long, MetricNode> metrics = new ConcurrentHashMap<>();
        List<MetricNode> nodesOfEverySecond = rollingCounterInMinute.details();
        long newLastFetchTime = lastFetchTime;
        // Iterate metrics of all resources, filter valid metrics (not-empty and up-to-date).
        for (MetricNode node : nodesOfEverySecond) {
            if (isNodeInTime(node, currentTime) && isValidMetricNode(node)) {
                metrics.put(node.getTimestamp(), node);
                newLastFetchTime = Math.max(newLastFetchTime, node.getTimestamp());
            }
        }
        lastFetchTime = newLastFetchTime;

        return metrics;
    }

    @Override
    public List<MetricNode> rawMetricsInMin(Predicate<Long> timePredicate) {
        return rollingCounterInMinute.detailsOnCondition(timePredicate);
    }

    private boolean isNodeInTime(MetricNode node, long currentTime) {
        return node.getTimestamp() > lastFetchTime && node.getTimestamp() < currentTime;
    }

    private boolean isValidMetricNode(MetricNode node) {
        return node.getPassQps() > 0 || node.getBlockQps() > 0 || node.getSuccessQps() > 0
            || node.getExceptionQps() > 0 || node.getRt() > 0 || node.getOccupiedPassQps() > 0;
    }

    @Override
    public void reset() {
        rollingCounterInSecond = new ArrayMetric(SampleCountProperty.SAMPLE_COUNT, IntervalProperty.INTERVAL);
    }

    @Override
    public long totalRequest() {
        return rollingCounterInMinute.pass() + rollingCounterInMinute.block();
    }

    @Override
    public long blockRequest() {
        return rollingCounterInMinute.block();
    }

    @Override
    public double blockQps() {
        return rollingCounterInSecond.block() / rollingCounterInSecond.getWindowIntervalInSec();
    }

    @Override
    public double previousBlockQps() {
        return this.rollingCounterInMinute.previousWindowBlock();
    }

    @Override
    public double previousPassQps() {
        return this.rollingCounterInMinute.previousWindowPass();
    }

    @Override
    public double totalQps() {
        return passQps() + blockQps();
    }

    @Override
    public long totalSuccess() {
        return rollingCounterInMinute.success();
    }

    @Override
    public double exceptionQps() {
        return rollingCounterInSecond.exception() / rollingCounterInSecond.getWindowIntervalInSec();
    }

    @Override
    public long totalException() {
        return rollingCounterInMinute.exception();
    }

    @Override
    public double passQps() {
        return rollingCounterInSecond.pass() / rollingCounterInSecond.getWindowIntervalInSec();
    }

    @Override
    public long totalPass() {
        return rollingCounterInMinute.pass();
    }

    @Override
    public double successQps() {
        return rollingCounterInSecond.success() / rollingCounterInSecond.getWindowIntervalInSec();
    }

    @Override
    public double maxSuccessQps() {
        return (double) rollingCounterInSecond.maxSuccess() * rollingCounterInSecond.getSampleCount()
                / rollingCounterInSecond.getWindowIntervalInSec();
    }

    @Override
    public double occupiedPassQps() {
        return rollingCounterInSecond.occupiedPass() / rollingCounterInSecond.getWindowIntervalInSec();
    }

    @Override
    public double avgRt() {
        long successCount = rollingCounterInSecond.success();
        if (successCount == 0) {
            return 0;
        }

        return rollingCounterInSecond.rt() * 1.0 / successCount;
    }

    @Override
    public double minRt() {
        return rollingCounterInSecond.minRt();
    }

    @Override
    public int curThreadNum() {
        return (int)curThreadNum.sum();
    }

    @Override
    public void addPassRequest(int count) {
        rollingCounterInSecond.addPass(count);
        rollingCounterInMinute.addPass(count);
    }

    @Override
    public void addRtAndSuccess(long rt, int successCount) {
        rollingCounterInSecond.addSuccess(successCount);
        rollingCounterInSecond.addRT(rt);

        rollingCounterInMinute.addSuccess(successCount);
        rollingCounterInMinute.addRT(rt);
    }

    @Override
    public void increaseBlockQps(int count) {
        rollingCounterInSecond.addBlock(count);
        rollingCounterInMinute.addBlock(count);
    }

    @Override
    public void increaseExceptionQps(int count) {
        rollingCounterInSecond.addException(count);
        rollingCounterInMinute.addException(count);
    }

    @Override
    public void increaseThreadNum() {
        curThreadNum.increment();
    }

    @Override
    public void decreaseThreadNum() {
        curThreadNum.decrement();
    }

    @Override
    public void debug() {
        rollingCounterInSecond.debug();
    }

    @Override
    public long tryOccupyNext(long currentTime, int acquireCount, double threshold) {
        double maxCount = threshold * IntervalProperty.INTERVAL / 1000;
        long currentBorrow = rollingCounterInSecond.waiting();
        if (currentBorrow >= maxCount) {
            return OccupyTimeoutProperty.getOccupyTimeout();
        }

        int windowLength = IntervalProperty.INTERVAL / SampleCountProperty.SAMPLE_COUNT;
        long earliestTime = currentTime - currentTime % windowLength + windowLength - IntervalProperty.INTERVAL;

        int idx = 0;
        /*
         * Note: here {@code currentPass} may be less than it really is NOW, because time difference
         * since call rollingCounterInSecond.pass(). So in high concurrency, the following code may
         * lead more tokens be borrowed.
         */
        long currentPass = rollingCounterInSecond.pass();
        while (earliestTime < currentTime) {
            long waitInMs = idx * windowLength + windowLength - currentTime % windowLength;
            if (waitInMs >= OccupyTimeoutProperty.getOccupyTimeout()) {
                break;
            }
            long windowPass = rollingCounterInSecond.getWindowPass(earliestTime);
            if (currentPass + currentBorrow + acquireCount - windowPass <= maxCount) {
                return waitInMs;
            }
            earliestTime += windowLength;
            currentPass -= windowPass;
            idx++;
        }

        return OccupyTimeoutProperty.getOccupyTimeout();
    }

    @Override
    public long waiting() {
        return rollingCounterInSecond.waiting();
    }

    @Override
    public void addWaitingRequest(long futureTime, int acquireCount) {
        rollingCounterInSecond.addWaiting(futureTime, acquireCount);
    }

    @Override
    public void addOccupiedPass(int acquireCount) {
        rollingCounterInMinute.addOccupiedPass(acquireCount);
        rollingCounterInMinute.addPass(acquireCount);
    }
}
