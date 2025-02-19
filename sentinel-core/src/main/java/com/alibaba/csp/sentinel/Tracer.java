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
package com.alibaba.csp.sentinel;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.context.NullContext;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.function.Predicate;

/**
 * 用于记录除{@link BlockException}外的其他异常
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 */
public class Tracer {

    /**
     * 跟踪的异常
     *
     * @see SentinelResource#exceptionsToTrace()
     */
    protected static Class<? extends Throwable>[] traceClasses;

    /**
     * 忽略的异常
     *
     * @see SentinelResource#exceptionsToIgnore()
     */
    protected static Class<? extends Throwable>[] ignoreClasses;

    protected static Predicate<Throwable> exceptionPredicate;

    protected Tracer() {}

    /**
     * 跟踪当前上下文中提供的{@link Throwable}到资源条目
     *
     * @param e exception to record
     */
    public static void trace(Throwable e) {
        traceContext(e, ContextUtil.getContext());
    }

    /**
     * 跟踪当前上下文中提供的{@link Throwable}到资源条目
     *
     * @param e     exception to record
     * @param count exception count to add
     */
    @Deprecated
    public static void trace(Throwable e, int count) {
        traceContext(e, count, ContextUtil.getContext());
    }

    /**
     * 跟踪给定入口上下文中提供的{@link Throwable}到资源条目
     *
     * @param e     exception to record
     * @param context target entrance context
     * @since 1.8.0
     */
    public static void traceContext(Throwable e, Context context) {
        if (!shouldTrace(e)) {
            return;
        }

        if (context == null || context instanceof NullContext) {
            return;
        }
        traceEntryInternal(e, context.getCurEntry());
    }

    /**
     * 跟踪给定入口上下文中提供的{@link Throwable}和增加异常数到资源条目
     *
     * @param e     exception to record
     * @param count exception count to add
     * @since 1.4.2
     */
    @Deprecated
    public static void traceContext(Throwable e, int count, Context context) {
        if (!shouldTrace(e)) {
            return;
        }

        if (context == null || context instanceof NullContext) {
            return;
        }
        traceEntryInternal(e, context.getCurEntry());
    }

    /**
     * 跟踪提供的{@link Throwable}到给定资源条目
     *
     * @param e exception to record
     * @since 1.4.2
     */
    public static void traceEntry(Throwable e, Entry entry) {
        if (!shouldTrace(e)) {
            return;
        }
        traceEntryInternal(e, entry);
    }

    private static void traceEntryInternal(/*@NeedToTrace*/ Throwable e, Entry entry) {
        if (entry == null) {
            return;
        }

        entry.setError(e);
    }

    /**
     * 设置跟踪的异常。如果未设置，便跟踪除{@link BlockException}外的所有异常
     *
     * <p>如果该方法和{@link #setExceptionsToIgnore(Class[])}均设置了相同的异常，
     * 那么{@link #setExceptionsToIgnore(Class[])}具有更高优先级
     *
     * @param traceClasses the list of exception classes to trace.
     * @since 1.6.1
     */
    @SafeVarargs
    public static void setExceptionsToTrace(Class<? extends Throwable>... traceClasses) {
        checkNotNull(traceClasses);
        Tracer.traceClasses = traceClasses;
    }

    /**
     * 获取跟踪的异常类
     *
     * @return an array of exception classes to trace.
     * @since 1.6.1
     */
    public static Class<? extends Throwable>[] getExceptionsToTrace() {
        return traceClasses;
    }

    /**
     * Set exceptions to ignore. if not set, all Exception except for {@link BlockException} will be traced.
     * <p>
     * Note that if both {@link #setExceptionsToTrace(Class[])} and this method is set,
     * the ExceptionsToIgnore will be of higher precedence.
     * </p>
     *
     * @param ignoreClasses the list of exception classes to ignore.
     * @since 1.6.1
     */
    @SafeVarargs
    public static void setExceptionsToIgnore(Class<? extends Throwable>... ignoreClasses) {
        checkNotNull(ignoreClasses);
        Tracer.ignoreClasses = ignoreClasses;
    }

    /**
     * Get exception classes to ignore.
     *
     * @return an array of exception classes to ignore.
     * @since 1.6.1
     */
    public static Class<? extends Throwable>[] getExceptionsToIgnore() {
        return ignoreClasses;
    }

    /**
     * Get exception predicate
     * @return the exception predicate.
     */
    public static Predicate<? extends Throwable> getExceptionPredicate() {
        return exceptionPredicate;
    }

    /**
     * set an exception predicate which indicates the exception should be traced(return true) or ignored(return false)
     * except for {@link BlockException}
     * @param exceptionPredicate the exception predicate
     */
    public static void setExceptionPredicate(Predicate<Throwable> exceptionPredicate) {
        AssertUtil.notNull(exceptionPredicate, "exception predicate must not be null");
        Tracer.exceptionPredicate = exceptionPredicate;
    }

    private static void checkNotNull(Class<? extends Throwable>[] classes) {
        AssertUtil.notNull(classes, "trace or ignore classes must not be null");
        for (Class<? extends Throwable> clazz : classes) {
            AssertUtil.notNull(clazz, "trace or ignore classes must not be null");
        }
    }

    /**
     * 检查是否跟踪给定异常
     *
     * @param t the throwable to check.
     * @return true if the throwable should be traced, else return false.
     */
    protected static boolean shouldTrace(Throwable t) {
        if (t == null || t instanceof BlockException) {
            return false;
        }
        if (exceptionPredicate != null) {
            return exceptionPredicate.test(t);
        }

        if (ignoreClasses != null) {
            for (Class<? extends Throwable> clazz : ignoreClasses) {
                if (clazz != null && clazz.isAssignableFrom(t.getClass())) {
                    return false;
                }
            }
        }
        if (traceClasses != null) {
            for (Class<? extends Throwable> clazz : traceClasses) {
                if (clazz != null && clazz.isAssignableFrom(t.getClass())) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }
}