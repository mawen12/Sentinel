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
package com.alibaba.csp.sentinel.cluster.client;

import com.alibaba.csp.sentinel.cluster.TokenServerDescriptor;
import com.alibaba.csp.sentinel.cluster.TokenService;

/**
 * 负责与令牌服务端通信的客户端
 *
 * @author Eric Zhao
 * @since 1.4.0
 */
public interface ClusterTokenClient extends TokenService {

    /**
     * 获取当前令牌服务器的描述符
     *
     * @return 当前已连接的令牌服务器，否则为null
     */
    TokenServerDescriptor currentServer();

    /**
     * 开始令牌客户端
     *
     * @throws Exception some error occurs
     */
    void start() throws Exception;

    /**
     * 停止令牌客户端
     *
     * @throws Exception some error occurs
     */
    void stop() throws Exception;

    /**
     * 获取集群令牌客户端状态
     *
     * @return state of the cluster token client
     */
    int getState();
}