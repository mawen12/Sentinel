/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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
package com.alibaba.csp.sentinel.annotation;

import com.alibaba.csp.sentinel.EntryType;

import java.lang.annotation.*;

/**
 * 定义资源的注解
 *
 * @author Eric Zhao
 * @author zhaoyuguang
 * @since 0.1.1
 *
 * @see
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface SentinelResource {

    /**
     * @return Sentinel资源名称
     */
    String value() default "";

    /**
     * @return 入口类型（入站还是出站），默认为出站
     */
    EntryType entryType() default EntryType.OUT;

    /**
     * @return 资源的分类（类型）
     * @since 1.7.0
     */
    int resourceType() default 0;

    /**
     * @return 阻塞异常函数的名称，默认为空
     */
    String blockHandler() default "";

    /**
     * {@link #blockHandler()}默认与原始方法位于同一个类中。
     * 然而，如果某些方法共享相同的签名（重载）并打算设置相同的{@link #blockHandler()}，
     * 那么用户可以设置{@link #blockHandler()}所在的类。
     *
     * <p>请注意：{@link #blockHandler()}方法必须是静态的。
     *
     * @return the class where the block handler exists, should not provide more than one classes
     */
    Class<?>[] blockHandlerClass() default {};

    /**
     * @return 回退函数的名称，默认为空
     */
    String fallback() default "";

    /**
     * 该方法被用作默认统一的回退方法。它不应该允许任何参数，且返回值应与原方法适配。
     *
     * @return 默认回退方法的名称，默认为空
     * @since 1.6.0
     */
    String defaultFallback() default "";

    /**
     * {@link #fallback()}默认与原始方法位于同一个类中。
     * 然而，如果某些方法共享相同的签名（重载）并打算设置相同的{@link #blockHandler()}，
     * 那么用户可以设置{@link #blockHandler()}所在的类。
     *
     * <p>请注意：{@link #fallback()}方法必须是静态的。
     *
     * @return fallback方法所在的类（仅限单个类）
     * @since 1.6.0
     */
    Class<?>[] fallbackClass() default {};

    /**
     * @return 跟踪的异常列表，默认为{@link Throwable}
     * @since 1.5.1
     */
    Class<? extends Throwable>[] exceptionsToTrace() default {Throwable.class};
    
    /**
     * 被忽略的异常。注意{@link #exceptionsToTrace()}不应出现在该方法中。
     * 否则该方法的优先级会更高。
     *
     * @return 忽略的异常类列表，默认为空
     * @since 1.6.0
     */
    Class<? extends Throwable>[] exceptionsToIgnore() default {};
}
