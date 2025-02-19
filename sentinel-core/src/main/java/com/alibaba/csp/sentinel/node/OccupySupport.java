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
package com.alibaba.csp.sentinel.node;

/**
 * 支持占领的接口
 *
 * @author Eric Zhao
 * @since 1.5.0
 */
public interface OccupySupport {

    /**
     * 尝试占领后面的时间窗口的Token。如果占领成功，将返回一个小于
     * {@link OccupyTimeoutProperty#occupyTimeout}的值。
     *
     * <p>每当在未来窗口成功占领Token后，当前线程应该休眠相应的时间以平滑QPS。
     * 我们不能无限制的占领未来的Token，睡眠时间限制为{@link OccupyTimeoutProperty#occupyTimeout}。
     *
     * @param currentTime  当前时间的毫秒格式
     * @param acquireCount 要申请的Token数量
     * @param threshold    QPS阈值
     * @return 当前线程应该睡眠的时间。返回值超过{@link OccupyTimeoutProperty#occupyTimeout}意味着申请失败。
     * 在这种场景下，请求应该立刻被拒绝。
     */
    long tryOccupyNext(long currentTime, int acquireCount, double threshold);

    /**
     * @return 返回当前等待总时长，用于debug。
     */
    long waiting();

    /**
     * 添加占用的请求。
     *
     * @param futureTime   应当添加令牌总数的未来时间戳
     * @param acquireCount 令牌总数
     */
    void addWaitingRequest(long futureTime, int acquireCount);

    /**
     * 增加已占用通行证请求，代表借用后一个窗口的令牌的通行证请求。
     *
     * @param acquireCount 令牌总数
     */
    void addOccupiedPass(int acquireCount);

    /**
     * @return 获取当前已占用通信证QPS
     */
    double occupiedPassQps();
}
