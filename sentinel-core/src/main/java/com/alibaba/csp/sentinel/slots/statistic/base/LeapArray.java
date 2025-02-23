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
package com.alibaba.csp.sentinel.slots.statistic.base;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.TimeUtil;

/**
 * Sentinel中由于统计信息指标的基础数据结构。
 *
 * <p>Leap 数组使用滑动窗口算法来统计数据。每个bucket涵盖{@link #windowLengthInMs}时间跨度，并且总的时间跨度为{@link #intervalInMs}。
 * 因此总的bucket容量为{@code sampleCount = intervalMs / windowLengthInMs}。
 *
 * @param <T> type of statistic data
 * @author jialiang.linjl
 * @author Eric Zhao
 * @author Carpenter Lee
 */
public abstract class LeapArray<T> {

    /**
     * 滑动窗口的长度，单位为毫秒
     */
    protected int windowLengthInMs;
    /**
     * 采样数，即总的bucket容量
     */
    protected int sampleCount;
    /**
     * 总的时间跨度，单位为毫秒
     */
    protected int intervalInMs;
    /**
     * 总的时间跨度，单位为秒
     */
    private double intervalInSecond;

    /**
     * 支持原子更新的数组引用
     */
    protected final AtomicReferenceArray<WindowWrap<T>> array;

    /**
     * 仅在当前bucket过期时才会被使用的条件更新锁
     */
    private final ReentrantLock updateLock = new ReentrantLock();

    /**
     * 总的存储桶个数为: {@code sampleCount = intervalInMs / windowLengthInMs}
     *
     * @param sampleCount  滑动窗口的桶数
     * @param intervalInMs 此{@link LeapArray}的总时间间隔（以毫秒为单位）
     */
    public LeapArray(int sampleCount, int intervalInMs) {
        AssertUtil.isTrue(sampleCount > 0, "bucket count is invalid: " + sampleCount);
        AssertUtil.isTrue(intervalInMs > 0, "total time interval of the sliding window should be positive");
        AssertUtil.isTrue(intervalInMs % sampleCount == 0, "time span needs to be evenly divided");

        // 毫秒内的窗口长度 = 毫秒间隔 / 滑动窗口的桶数
        this.windowLengthInMs = intervalInMs / sampleCount;
        this.intervalInMs = intervalInMs;
        // 转换为秒间隔
        this.intervalInSecond = intervalInMs / 1000.0;
        this.sampleCount = sampleCount;

        // 保存了桶数量的
        this.array = new AtomicReferenceArray<>(sampleCount);
    }

    /**
     * @return 获取当前时间戳的存储桶
     */
    public WindowWrap<T> currentWindow() {
        return currentWindow(TimeUtil.currentTimeMillis());
    }

    /**
     * 为存储桶创建一个新的统计值
     *
     * @param timeMillis 以毫秒表示的当前时间
     * @return 新的空存储桶
     */
    public abstract T newEmptyBucket(long timeMillis);

    /**
     * 将给定的桶重置为给定的开始时间，并重置桶中的统计数据。
     *
     * @param startTime  以毫秒表示的桶的开始时间
     * @param windowWrap 当前存储桶
     * @return 在给定的开始时间新的干净存储桶
     */
    protected abstract WindowWrap<T> resetWindowTo(WindowWrap<T> windowWrap, long startTime);

    /**
     * 根据给定时间戳计算时间索引
     *
     * @param timeMillis 时间戳
     * @return leap数组的索引
     */
    private int calculateTimeIdx(/*@Valid*/ long timeMillis) {
        long timeId = timeMillis / windowLengthInMs;
        // 计算当前索引，以便我们可以将时间戳映射到leap数组中
        return (int)(timeId % array.length());
    }

    /**
     * @param timeMillis 时间戳
     * @return 计算窗口开始时间
     */
    protected long calculateWindowStart(/*@Valid*/ long timeMillis) {
        return timeMillis - timeMillis % windowLengthInMs;
    }

    /**
     * 获取提供的时间戳的存储桶元素
     *
     * @param timeMillis 以毫秒表示的合法时间戳
     * @return 获取提供的时间戳的存储桶元素，如果时间非法则返回空
     */
    public WindowWrap<T> currentWindow(long timeMillis) {
        if (timeMillis < 0) {
            return null;
        }

        // 计算时间戳在leap数组中的索引
        int idx = calculateTimeIdx(timeMillis);
        // 计算当前存储桶的开始时间
        long windowStart = calculateWindowStart(timeMillis);

        /**
         * 从数组中获取给定时间的存储桶元素
         * <ol>
         *     <li>如果存储桶不存在，创建一个新的存储桶，并使用CAS更新到圆形数组</li>
         *     <li>存储桶是最新的，则直接返回</li>
         *     <li>存储桶已过期，则重置当前存储桶</li>
         * </ol>
         */
        while (true) {
            // 获取当前索引的存储桶
            WindowWrap<T> old = array.get(idx);
            if (old == null) {// 出现第一种情况，存储桶不存在，则需要创建新的桶，并使用CAS更新到leap数组
                /*
                 *     B0       B1      B2    NULL      B4
                 * ||_______|_______|_______|_______|_______||___
                 * 200     400     600     800     1000    1200  timestamp
                 *                             ^
                 *                          time=888
                 *            bucket is empty, so create new and update
                 *
                 * 如果老的存储桶不存在，则在{@code windowStart}位置创建新的存储桶，
                 * 然后尝试通过CAS操作更新循环数组。由于CAS的原子性操作，只有一个线程能够更新成功，
                 * 而其他线程则让出时间片。
                 */
                WindowWrap<T> window = new WindowWrap<T>(windowLengthInMs, windowStart, newEmptyBucket(timeMillis));
                if (array.compareAndSet(idx, null, window)) {
                    // 成功更新，返回创建后的存储桶
                    return window;
                } else {
                    // CAS线程争用失败，该线程将放弃其时间片以等待存储桶可用
                    Thread.yield();
                }
            } else if (windowStart == old.windowStart()) {// 出现第二种情况，存储中是最新的，直接返回
                /*
                 *     B0       B1      B2     B3      B4
                 * ||_______|_______|_______|_______|_______||___
                 * 200     400     600     800     1000    1200  timestamp
                 *                             ^
                 *                          time=888
                 *            startTime of Bucket 3: 800, so it's up-to-date
                 *
                 * 当前的{@code windowStart}等于已有存储同的开始时间点，
                 * 这说明时间在存储桶内，所以直接返回存储桶
                 */
                return old;
            } else if (windowStart > old.windowStart()) {// 出现第三种情况，存储同已过期，需要重置计数
                /*
                 *   (old)
                 *             B0       B1      B2    NULL      B4
                 * |_______||_______|_______|_______|_______|_______||___
                 * ...    1200     1400    1600    1800    2000    2200  timestamp
                 *                              ^
                 *                           time=1676
                 *          startTime of Bucket 2: 400, deprecated, should be reset
                 *
                 * 如果旧的存储桶的开始时间落后于提供的时间，这意味着该存储桶已过期。
                 * 我们必须将该桶重置到当前{@code windowStart}，需要注意重置和清理操作很难变成原子操作，
                 * 因此需要一个更新锁来保证存储桶的正确更新。
                 *
                 * 更新锁是有有条件的（微小范围），并且只有当存储同被弃用时才会生效，
                 * 因此绝大多数情况下不会导致性能损失。
                 */
                if (updateLock.tryLock()) {// 加上更新锁
                    try {
                        // 成功获取到更新锁后，此时重置存储桶的开始时间
                        return resetWindowTo(old, windowStart);
                    } finally {
                        // 释放更新锁
                        updateLock.unlock();
                    }
                } else {
                    // 线程争用失败，该线程将放弃其时间片以等待存储桶可用
                    Thread.yield();
                }
            } else if (windowStart < old.windowStart()) {// 不应存在的情况，老的存储桶比最新的还要早
                return new WindowWrap<T>(windowLengthInMs, windowStart, newEmptyBucket(timeMillis));
            }
        }
    }

    /**
     * 获取在提供的时间戳之前的存储桶元素
     *
     * @param timeMillis 已毫秒表示的合法时间戳
     * @return 提供时间戳之前的前一个存储桶元素
     */
    public WindowWrap<T> getPreviousWindow(long timeMillis) {
        // 非法时间戳，直接返回空
        if (timeMillis < 0) {
            return null;
        }
        // 减去一个窗口长度，获取前一个存储桶的索引
        int idx = calculateTimeIdx(timeMillis - windowLengthInMs);
        // 计算数组桶的开始时间
        timeMillis = timeMillis - windowLengthInMs;
        // 获取索引对应的存储桶元素
        WindowWrap<T> wrap = array.get(idx);

        // 对于不存在的桶或过期的桶，直接返回空
        if (wrap == null || isWindowDeprecated(wrap)) {
            return null;
        }

        if (wrap.windowStart() + windowLengthInMs < (timeMillis)) {
            return null;
        }

        return wrap;
    }

    /**
     * @return 当前时间戳的前一个存储桶元素
     */
    public WindowWrap<T> getPreviousWindow() {
        return getPreviousWindow(TimeUtil.currentTimeMillis());
    }

    /**
     * Get statistic value from bucket for provided timestamp.
     *
     * @param timeMillis a valid timestamp in milliseconds
     * @return 如果提供的时间戳对应的存储桶是最新的，则返回其统计数据，否则返回空
     */
    public T getWindowValue(long timeMillis) {
        // 非法时间戳，直接返回空
        if (timeMillis < 0) {
            return null;
        }
        // 计算在存储桶数组的索引
        int idx = calculateTimeIdx(timeMillis);

        // 获取对应索引的元素
        WindowWrap<T> bucket = array.get(idx);

        // 存储桶不存在，或者存储桶已经过期，直接返回空
        if (bucket == null || !bucket.isTimeInWindow(timeMillis)) {
            return null;
        }

        // 获取存储桶中的统计统计数据
        return bucket.value();
    }

    /**
     * @param windowWrap 非空的存储桶
     * @return 检查一个存储桶是否过期，意味着存储桶至少落后了整个窗口时间跨度。
     */
    public boolean isWindowDeprecated(/*@NonNull*/ WindowWrap<T> windowWrap) {
        return isWindowDeprecated(TimeUtil.currentTimeMillis(), windowWrap);
    }

    public boolean isWindowDeprecated(long time, WindowWrap<T> windowWrap) {
        return time - windowWrap.windowStart() > intervalInMs;
    }

    /**
     * @return 返回整个滑动时间窗口的合法的存储桶集合
     */
    public List<WindowWrap<T>> list() {
        return list(TimeUtil.currentTimeMillis());
    }

    public List<WindowWrap<T>> list(long validTime) {
        int size = array.length();
        // TODO by mawen 棱形语法
        List<WindowWrap<T>> result = new ArrayList<WindowWrap<T>>(size);

        for (int i = 0; i < size; i++) {
            WindowWrap<T> windowWrap = array.get(i);
            // 仅保留合法的存储桶
            if (windowWrap == null || isWindowDeprecated(validTime, windowWrap)) {
                continue;
            }
            result.add(windowWrap);
        }

        return result;
    }

    /**
     * @return 返回所有的存储桶，包含过期的存储桶
     */
    public List<WindowWrap<T>> listAll() {
        int size = array.length();
        // TODO by mawen 棱形语法
        List<WindowWrap<T>> result = new ArrayList<WindowWrap<T>>(size);

        for (int i = 0; i < size; i++) {
            WindowWrap<T> windowWrap = array.get(i);
            // 仅保留非空的存储桶
            if (windowWrap == null) {
                continue;
            }
            result.add(windowWrap);
        }

        return result;
    }

    /**
     * @return 返回整个滑动时间窗口中合法存储桶的聚合统计数据
     */
    public List<T> values() {
        return values(TimeUtil.currentTimeMillis());
    }

    public List<T> values(long timeMillis) {
        // 非法时间，直接返回空集合
        if (timeMillis < 0) {
            return new ArrayList<T>();
        }
        int size = array.length();
        // TODO by mawen 棱形语法
        List<T> result = new ArrayList<T>(size);

        for (int i = 0; i < size; i++) {
            WindowWrap<T> windowWrap = array.get(i);
            // 仅保留合法的存储桶
            if (windowWrap == null || isWindowDeprecated(timeMillis, windowWrap)) {
                continue;
            }
            result.add(windowWrap.value());
        }
        return result;
    }

    /**
     * 获取所提供时间戳的滑动窗口的有效"头"桶。测试专用包。
     *
     * @param timeMillis 以毫秒表示的时间戳
     * @return 获取所提供时间戳的滑动窗口的有效"头"桶
     */
    WindowWrap<T> getValidHead(long timeMillis) {
        // Calculate index for expected head time.
        // 根据期待的头时间计算索引
        int idx = calculateTimeIdx(timeMillis + windowLengthInMs);

        // 获取索引对应的存储桶元素
        WindowWrap<T> wrap = array.get(idx);
        // 对于非法的存储桶，直接返回空
        if (wrap == null || isWindowDeprecated(wrap)) {
            return null;
        }

        return wrap;
    }

    /**
     *
     * @return 滑动窗口中当前时间戳的合法的头部存储桶
     */
    public WindowWrap<T> getValidHead() {
        return getValidHead(TimeUtil.currentTimeMillis());
    }

    /**
     * @return 样本总数（即存储桶总数）
     */
    public int getSampleCount() {
        return sampleCount;
    }

    /**
     * TODO by mawen milliseconds
     * @return 以毫秒表示的间隔
     */
    public int getIntervalInMs() {
        return intervalInMs;
    }

    /**
     * @return 以秒表示的间隔
     */
    public double getIntervalInSecond() {
        return intervalInSecond;
    }

    public void debug(long time) {
        StringBuilder sb = new StringBuilder();
        List<WindowWrap<T>> lists = list(time);
        sb.append("Thread_").append(Thread.currentThread().getId()).append("_");
        for (WindowWrap<T> window : lists) {
            sb.append(window.windowStart()).append(":").append(window.value().toString());
        }
        System.out.println(sb.toString());
    }

    public long currentWaiting() {
        // TODO: default method. Should remove this later.
        return 0;
    }

    public void addWaiting(long time, int acquireCount) {
        // Do nothing by default.
        throw new UnsupportedOperationException();
    }
}
