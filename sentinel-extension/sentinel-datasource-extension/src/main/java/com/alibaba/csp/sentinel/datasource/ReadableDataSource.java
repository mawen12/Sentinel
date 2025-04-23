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
package com.alibaba.csp.sentinel.datasource;

import com.alibaba.csp.sentinel.property.SentinelProperty;

/**
 * 用于获取配置的可读数据源（只读）
 *
 * @param <S> 源数据类型
 * @param <T> 目标数据类型
 * @author leyou
 * @author Eric Zhao
 */
public interface ReadableDataSource<S, T> {

    /**
     * 加载数据
     *
     * @return the target data.
     * @throws Exception IO or other error occurs
     */
    T loadConfig() throws Exception;

    /**
     * 读取原始数据
     *
     * @return the original data.
     * @throws Exception IO or other error occurs
     */
    S readSource() throws Exception;

    /**
     * 返回数据源的{@link SentinelProperty}
     *
     * @return the property.
     */
    SentinelProperty<T> getProperty();

    /**
     * 关闭数据源
     *
     * @throws Exception IO or other error occurs
     */
    void close() throws Exception;
}
