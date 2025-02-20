/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;

/**
 * 基本的断路器接口。
 *
 * @author Eric Zhao
 * @see <a href="https://martinfowler.com/bliki/CircuitBreaker.html">circuit breaker</a>
 */
public interface CircuitBreaker {

    /**
     * @return 返回关联的断路器规则
     */
    DegradeRule getRule();

    /**
     * 仅当调用可用时才获取调用的权限
     *
     * @param context 当前调用的上下文
     * @return {@code true}权限申请成功，{@code false}权限申请失败
     */
    boolean tryPass(Context context);

    /**
     * @return 返回断路器的当前状态
     */
    State currentState();

    /**
     * 记录一个已完成的请求以及上下文，并处理断路器状态的转换。
     *
     * <p>仅当{@code passed}调用完成时才触发该方法
     *
     * @param context 当前调用的上下文
     */
    void onRequestComplete(Context context);

    /**
     * 断路器状态
     *
     * <p>断路器状态转换流程如下：
     * <ol>
     *     <li>从{@link #CLOSED}到{@link #OPEN}</li>
     *     <li>从{@link #OPEN}到{@link #HALF_OPEN}</li>
     *     <li>从{@link #OPEN}到{@link #CLOSED}</li>
     *     <li>从{@link #HALF_OPEN}到{@link #CLOSED}</li>
     * </ol>
     */
    enum State {
        /**
         * 开启状态，所有请求将被拒绝，直到下次恢复时间点
         */
        OPEN,
        /**
         * 半开状态，断路器将允许探测调用。
         * 如果调用异常并根据策略（或者缓慢），断路器将重新变为打开装填，直到下次恢复时间点。
         * 否则资源将被视为已恢复，且断路器将停止阻塞请求，并转换为关闭状态。
         */
        HALF_OPEN,
        /**
         * 关闭状态，所有请求都被允许。当当前指标值超过了阈值，断路器会变为打开状态。
         */
        CLOSED
    }
}
