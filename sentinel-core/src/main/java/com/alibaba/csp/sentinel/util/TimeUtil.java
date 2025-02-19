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
package com.alibaba.csp.sentinel.util;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slots.statistic.base.LeapArray;
import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;
import com.alibaba.csp.sentinel.util.function.Tuple2;

/**
 * 提供毫秒级的系统时间。
 *
 * <p>应该看到TimeUtil不应该一直保持每秒循环1000次（实际上由于一些性能损失，大约每秒800次）。
 * <ol>
 *     <li>在空闲条件下，TimeUtil的行为类似于{@link System#currentTimeMillis()}。</li>
 *     <li>在繁忙条件下（明显超过1000/s），TimeUtil会保持循环以减少成本。</li>
 * </ol>
 *
 * <p>对于详细的设计和方案请移步至
 * <a href="https://github.com/alibaba/Sentinel/issues/1702#issuecomment-692151160">https://github.com/alibaba/Sentinel/issues/1702</a>
 *
 * @author qinan.qn
 * @author jason
 */
public final class TimeUtil implements Runnable {
    /**
     * 检查间隔，3s一次
     */
    private static final long CHECK_INTERVAL = 3000;
    /**
     * 命中低的边界，800ms
     */
    private static final long HITS_LOWER_BOUNDARY = 800;
    /**
     * 命中高的边界，1200ms
     */
    private static final long HITS_UPPER_BOUNDARY = 1200;

    public static enum STATE {
        /**
         * 空闲
         */
        IDLE,
        /**
         * 准备
         */
        PREPARE,
        /**
         * 运行中
         */
        RUNNING;
    }

    /**
     * 统计信息
     */
    private static class Statistic {
        /**
         * 写总数
         */
        private final LongAdder writes = new LongAdder();
        /**
         * 读总数
         */
        private final LongAdder reads = new LongAdder();

        public LongAdder getWrites() {
            return writes;
        }

        public LongAdder getReads() {
            return reads;
        }
    }

    /**
     * 单例对象
     */
    private static TimeUtil INSTANCE;

    /**
     * 当前时间的毫秒表示
     */
    private volatile long currentTimeMillis;
    /**
     * TimeUtil的状态，初始为{@link STATE#IDLE}
     */
    private volatile STATE state = STATE.IDLE;

    /**
     * 统计数组
     *
     * <p>该设计类似于{@link com.alibaba.csp.sentinel.slots.statistic.metric.ArrayMetric}
     */
    private LeapArray<Statistic> statistics;

    /**
     * 上次检查时间，线程私有变量
     */
    private long lastCheck = 0;

    static {
        // 初始化，确保单实例
        INSTANCE = new TimeUtil();
    }

    public TimeUtil() {
        // 3s中采样3次，即每秒采样一次
        this.statistics = new LeapArray<TimeUtil.Statistic>(3, 3000) {

            @Override
            public Statistic newEmptyBucket(long timeMillis) {
                // 创建一个统计对象，作为一个空桶
                return new Statistic();
            }

            @Override
            protected WindowWrap<Statistic> resetWindowTo(WindowWrap<Statistic> windowWrap, long startTime) {
                // 重置窗口到指定时间，并清除内部的读写信息
                Statistic val = windowWrap.value();
                val.getReads().reset();
                val.getWrites().reset();
                windowWrap.resetTo(startTime);
                return windowWrap;
            }
        };
        // 记录当前时间戳
        this.currentTimeMillis = System.currentTimeMillis();
        // 更新最后检查时间
        this.lastCheck = this.currentTimeMillis;
        // 将该对象的执行封装到线程中
        Thread daemon = new Thread(this);
        // 设置为守护线程
        daemon.setDaemon(true);
        // 线程名称为：sentinel-time-tick-thread
        daemon.setName("sentinel-time-tick-thread");
        // 启动线程
        daemon.start();
    }

    @Override
    public void run() {
        // 一直循环，因为执行任务的线程是守护线程，因此无需担心停止的问题
        while (true) {
            // 自1.8.2版本以来机制优化
            this.check();
            if (this.state == STATE.RUNNING) {
                // 更新当前时间戳
                this.currentTimeMillis = System.currentTimeMillis();
                // 定位到当前时间戳的bucket，并将写操作增加一次
                this.statistics.currentWindow(this.currentTimeMillis).value().getWrites().increment();
                try {
                    // 睡1ms
                    TimeUnit.MILLISECONDS.sleep(1);
                } catch (Throwable e) {
                }
                continue;
            }
            if (this.state == STATE.IDLE) {
                try {
                    TimeUnit.MILLISECONDS.sleep(300);
                } catch (Throwable e) {
                }
                continue;
            }
            if (this.state == STATE.PREPARE) {
                RecordLog.debug("TimeUtil switches to RUNNING");
                this.currentTimeMillis = System.currentTimeMillis();
                this.state = STATE.RUNNING;
                continue;
            }
        }
    }

    /**
     * Current running state
     *
     * @return
     */
    public STATE getState() {
        return state;
    }

    /**
     * Current qps statistics (including reads and writes request)
     * excluding current working time window for accurate result.
     *
     * @param now
     * @return
     */
    public Tuple2<Long, Long> currentQps(long now) {
        List<WindowWrap<Statistic>> list = this.statistics.listAll();
        long reads = 0;
        long writes = 0;
        int cnt = 0;
        for (WindowWrap<Statistic> windowWrap : list) {
            if (windowWrap.isTimeInWindow(now)) {
                continue;
            }
            cnt++;
            reads += windowWrap.value().getReads().longValue();
            writes += windowWrap.value().getWrites().longValue();
        }
        if (cnt < 1) {
            return new Tuple2<Long, Long>(0L, 0L);
        }
        return new Tuple2<Long, Long>(reads / cnt, writes / cnt);
    }

    /**
     * Check and operate the state if necessary.
     * ATTENTION: It's called in daemon thread.
     */
    private void check() {
        // 获取真实的当前时间
        long now = currentTime(true);
        // every period
        if (now - this.lastCheck < CHECK_INTERVAL) {
            return;
        }
        this.lastCheck = now;
        Tuple2<Long, Long> qps = currentQps(now);
        if (this.state == STATE.IDLE && qps.r1 > HITS_UPPER_BOUNDARY) {
            RecordLog.info("TimeUtil switches to PREPARE for better performance, reads={}/s, writes={}/s", qps.r1, qps.r2);
            this.state = STATE.PREPARE;
        } else if (this.state == STATE.RUNNING && qps.r1 < HITS_LOWER_BOUNDARY) {
            RecordLog.info("TimeUtil switches to IDLE due to not enough load, reads={}/s, writes={}/s", qps.r1, qps.r2);
            this.state = STATE.IDLE;
        }
    }

    private long currentTime(boolean innerCall) {
        // 从变量中获取当前记录的时间
        long now = this.currentTimeMillis;
        // 获取当前记录的时间的统计信息
        Statistic val = this.statistics.currentWindow(now).value();
        if (!innerCall) {
            // 对于外部调用，读操作增加一次
            val.getReads().increment();
        }
        if (this.state == STATE.IDLE || this.state == STATE.PREPARE) {// 对于尚未运行的TimeUtil，需要修正当前时间
            // 修正当前时间
            now = System.currentTimeMillis();
            // 修正当前时间
            this.currentTimeMillis = now;
            if (!innerCall) {
                // 对于外部调用，写操作增加一次
                val.getWrites().increment();
            }
        }
        return now;
    }

    /**
     * Current timestamp in milliseconds.
     *
     * @return
     */
    public long getTime() {
        return this.currentTime(false);
    }

    public static TimeUtil instance() {
        return INSTANCE;
    }

    public static long currentTimeMillis() {
        return INSTANCE.getTime();
    }
}
