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
 * 持有配置的值，Sentinel中全局的属性对象。
 *
 * <p>在配置更新时，通知所有的{@link PropertyListener}。
 *
 * <p>仅当配置的新值与旧值不同时，才会触发通知，如果相同，则不会触发。
 *
 * @param <T> the target type.
 * @author Carpenter Lee
 */
public interface SentinelProperty<T> {

    /**
     * 注册监听器
     *
     * @param listener listener to add.
     */
    void addListener(PropertyListener<T> listener);

    /**
     * 移除监听器
     *
     * @param listener the listener to remove.
     */
    void removeListener(PropertyListener<T> listener);

    /**
     * 更新配置值，并通知监听器。
     *
     * <p>仅当配置的新值与旧值不同时，才会触发通知，如果相同，则不会触发。
     *
     * @param newValue the new value.
     * @return true if the value in property has been updated, otherwise false
     */
    boolean updateValue(T newValue);
}
