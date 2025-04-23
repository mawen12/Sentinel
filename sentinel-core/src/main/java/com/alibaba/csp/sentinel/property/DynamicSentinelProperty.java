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

import com.alibaba.csp.sentinel.log.RecordLog;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 代表可变的属性。即值可以被修改。当值发生变化时，通知监听器。
 *
 * @param <T>
 */
public class DynamicSentinelProperty<T> implements SentinelProperty<T> {

    /**
     * 监听器，当值发生变化时，会被通知
     */
    protected Set<PropertyListener<T>> listeners = new CopyOnWriteArraySet<>();
    /**
     * 属性值
     */
    private T value = null;

    public DynamicSentinelProperty() {
    }

    public DynamicSentinelProperty(T value) {
        super();
        this.value = value;
    }

    @Override
    public void addListener(PropertyListener<T> listener) {
        listeners.add(listener);
        // 初次注册时，便会通知
        listener.configLoad(value);
    }

    @Override
    public void removeListener(PropertyListener<T> listener) {
        listeners.remove(listener);
    }

    @Override
    public boolean updateValue(T newValue) {
        if (isEqual(value, newValue)) {
            // 值相等，不被视作更新
            return false;
        }
        RecordLog.info("[DynamicSentinelProperty] Config will be updated to: {}", newValue);

        // 更新值
        value = newValue;
        for (PropertyListener<T> listener : listeners) {
            // 通知监听器
            listener.configUpdate(newValue);
        }
        return true;
    }

    private boolean isEqual(T oldValue, T newValue) {
        if (oldValue == null && newValue == null) {
            return true;
        }

        if (oldValue == null) {
            return false;
        }

        return oldValue.equals(newValue);
    }

    public void close() {
        listeners.clear();
    }
}
