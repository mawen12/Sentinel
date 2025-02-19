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
package com.alibaba.csp.sentinel.cluster;

import com.alibaba.csp.sentinel.cluster.request.ClusterRequest;
import com.alibaba.csp.sentinel.cluster.response.ClusterResponse;

/**
 * 用于分布式流控的同步传输客户端
 *
 * @author Eric Zhao
 * @since 1.4.0
 */
public interface ClusterTransportClient {

    /**
     * 开始客户端
     *
     * @throws Exception some error occurred (e.g. initialization failed)
     */
    void start() throws Exception;

    /**
     * 停止客户端
     *
     * @throws Exception some error occurred (e.g. shutdown failed)
     */
    void stop() throws Exception;

    /**
     * 发送请求到远程服务器并接受响应
     *
     * @param request Sentinel集群请求
     * @return response from remote server
     * @throws Exception some error occurs
     */
    ClusterResponse sendRequest(ClusterRequest request) throws Exception;

    /**
     * 检查客户端是否已启动并准备好发送请求
     *
     * @return true if the client is ready to send requests, otherwise false
     */
    boolean isReady();
}
