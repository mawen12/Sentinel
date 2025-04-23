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
package com.alibaba.csp.sentinel.cluster.server;

/**
 * 代表令牌服务器，负责管理其生命周期
 *
 * @author Eric Zhao
 * @since 1.4.0
 */
public interface ClusterTokenServer {

    /**
     * 启动
     *
     * @throws Exception if any error occurs
     */
    void start() throws Exception;

    /**
     * 关闭
     *
     * @throws Exception if any error occurs
     */
    void stop() throws Exception;
}
