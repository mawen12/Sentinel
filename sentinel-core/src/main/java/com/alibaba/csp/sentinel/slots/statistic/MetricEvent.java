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
package com.alibaba.csp.sentinel.slots.statistic;

/**
 * 指标事件
 *
 * @author Eric Zhao
 */
public enum MetricEvent {

    /**
     * 正常通过
     */
    PASS,

    /**
     * 正常阻塞
     */
    BLOCK,

    /**
     * 发生异常
     */
    EXCEPTION,

    /**
     * 请求成功
     */
    SUCCESS,

    /**
     * 响应事件
     */
    RT,

    /**
     * 已通过未来配额（已预先占用，自1.5.0起）
     */
    OCCUPIED_PASS
}
