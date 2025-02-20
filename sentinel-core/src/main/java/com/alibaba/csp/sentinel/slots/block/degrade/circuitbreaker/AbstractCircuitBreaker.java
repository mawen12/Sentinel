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
package com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker;

import java.util.concurrent.atomic.AtomicReference;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.util.function.BiConsumer;

/**
 * 抽象的{@link CircuitBreaker}
 *
 * @author Eric Zhao
 * @since 1.8.0
 */
public abstract class AbstractCircuitBreaker implements CircuitBreaker {

    /**
     * 降级规则
     */
    protected final DegradeRule rule;

    /**
     * 恢复的超时时间，单位为毫秒
     *
     * <p>从{@link State#OPEN}转换到{@link State#HALF_OPEN}的间隔时间
     */
    protected final int recoveryTimeoutMs;

    /**
     * 事件观察者注册器，当断路器状态发生变化时，通知到对应的监听器
     */
    private final EventObserverRegistry observerRegistry;

    /**
     * 断路器状态的原子引用
     */
    protected final AtomicReference<State> currentState = new AtomicReference<>(State.CLOSED);

    /**
     * 下次尝试的时间戳，基于当前时间和{@link #recoveryTimeoutMs}计算出的
     */
    protected volatile long nextRetryTimestamp;

    public AbstractCircuitBreaker(DegradeRule rule) {
        this(rule, EventObserverRegistry.getInstance());
    }

    AbstractCircuitBreaker(DegradeRule rule, EventObserverRegistry observerRegistry) {
        AssertUtil.notNull(observerRegistry, "observerRegistry cannot be null");
        // 检查绑定的降级规则是否合法
        if (!DegradeRuleManager.isValidRule(rule)) {
            throw new IllegalArgumentException("Invalid DegradeRule: " + rule);
        }
        this.observerRegistry = observerRegistry;
        this.rule = rule;
        // 以降级规则的时间窗口作为恢复的间隔时间
        this.recoveryTimeoutMs = rule.getTimeWindow() * 1000;
    }

    @Override
    public DegradeRule getRule() {
        return rule;
    }

    @Override
    public State currentState() {
        return currentState.get();
    }

    /**
     * 模板方法实现
     *
     * @param context 当前调用的上下文
     * @return
     */
    @Override
    public boolean tryPass(Context context) {
        // 对于关闭的断路器，无需拦截
        if (currentState.get() == State.CLOSED) {
            return true;
        }
        // 对于打开状态，检查
        if (currentState.get() == State.OPEN) {
            // 当达到重试超时间隔之后，代表状态要被切换为HALF_OPEN
            // 对于HALF_OPEN，允许发送一个请求执行探测
            return retryTimeoutArrived() && fromOpenToHalfOpen(context);
        }
        return false;
    }

    /**
     * Reset the statistic data.
     */
    abstract void resetStat();

    /**
     * @return {@code true}达到了重试时间间隔，{@code false}还在重试时间间隔内
     */
    protected boolean retryTimeoutArrived() {
        return TimeUtil.currentTimeMillis() >= nextRetryTimestamp;
    }

    protected void updateNextRetryTimestamp() {
        this.nextRetryTimestamp = TimeUtil.currentTimeMillis() + recoveryTimeoutMs;
    }

    protected boolean fromCloseToOpen(double snapshotValue) {
        State prev = State.CLOSED;
        if (currentState.compareAndSet(prev, State.OPEN)) {
            updateNextRetryTimestamp();

            notifyObservers(prev, State.OPEN, snapshotValue);
            return true;
        }
        return false;
    }

    protected boolean fromOpenToHalfOpen(Context context) {
        // 将状态从打开切换到半开，需要注意，此处使用了AtomicBoolean，代表只允许一个请求通过，执行探测
        if (currentState.compareAndSet(State.OPEN, State.HALF_OPEN)) {
            // 观察者通知断路器状态发生变化，从OPEN -> HALF_OPEN
            notifyObservers(State.OPEN, State.HALF_OPEN, null);
            // 获取当前entry
            Entry entry = context.getCurEntry();
            entry.whenTerminate(new BiConsumer<Context, Entry>() {
                @Override
                public void accept(Context context, Entry entry) {// 添加回调，在请求结束后修改断路器状态
                    // 这是https://github.com/alibaba/Sentinel/issues/1638临时的解决方案，如果没有hook，
                    // 当请求实际上被即将到来的规则阻止（不仅是降级规则）时，断路器在某些情况下将无法从HALF_OPEN恢复。
                    if (entry.getBlockError() != null) {
                        // Fallback to OPEN due to detecting request is blocked
                        // 当检测到请求被阻塞时，断路器状态从HALF_OPEN切换到OPEN
                        currentState.compareAndSet(State.HALF_OPEN, State.OPEN);
                        ///  观察者通知断路器状态发生变化，从HALF_OPEN -> OPEN
                        notifyObservers(State.HALF_OPEN, State.OPEN, 1.0d);
                    }
                }
            });
            // 对于HALF_OPEN，允许发送一个请求执行探测
            return true;
        }
        // 同一时间内的其他请求，均被拒绝
        return false;
    }
    
    private void notifyObservers(CircuitBreaker.State prevState, CircuitBreaker.State newState, Double snapshotValue) {
        for (CircuitBreakerStateChangeObserver observer : observerRegistry.getStateChangeObservers()) {
            observer.onStateChange(prevState, newState, rule, snapshotValue);
        }
    }

    protected boolean fromHalfOpenToOpen(double snapshotValue) {
        if (currentState.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            updateNextRetryTimestamp();
            notifyObservers(State.HALF_OPEN, State.OPEN, snapshotValue);
            return true;
        }
        return false;
    }

    protected boolean fromHalfOpenToClose() {
        if (currentState.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
            resetStat();
            notifyObservers(State.HALF_OPEN, State.CLOSED, null);
            return true;
        }
        return false;
    }

    protected void transformToOpen(double triggerValue) {
        State cs = currentState.get();
        switch (cs) {
            case CLOSED:
                fromCloseToOpen(triggerValue);
                break;
            case HALF_OPEN:
                fromHalfOpenToOpen(triggerValue);
                break;
            default:
                break;
        }
    }
}
