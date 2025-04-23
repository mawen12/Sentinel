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
package com.alibaba.csp.sentinel.property;

/**
 * 当{@link SentinelProperty#updateValue(Object)}触发时，回调对应方法。
 *
 * @author jialiang.linjl
 */
public interface PropertyListener<T> {

    /**
     * 将{@link SentinelProperty#updateValue(Object)}触发时，会调用该方法
     *
     * @param value updated value.
     */
    void configUpdate(T value);

    /**
     * 首次加载时，触发通知
     *
     * @param value the value loaded.
     */
    void configLoad(T value);
}
