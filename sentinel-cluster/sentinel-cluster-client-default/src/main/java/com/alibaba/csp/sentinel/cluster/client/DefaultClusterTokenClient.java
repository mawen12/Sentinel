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

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.csp.sentinel.cluster.ClusterConstants;
import com.alibaba.csp.sentinel.cluster.ClusterErrorMessages;
import com.alibaba.csp.sentinel.cluster.ClusterTransportClient;
import com.alibaba.csp.sentinel.cluster.TokenResult;
import com.alibaba.csp.sentinel.cluster.TokenResultStatus;
import com.alibaba.csp.sentinel.cluster.TokenServerDescriptor;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientAssignConfig;
import com.alibaba.csp.sentinel.cluster.client.config.ClusterClientConfigManager;
import com.alibaba.csp.sentinel.cluster.client.config.ServerChangeObserver;
import com.alibaba.csp.sentinel.cluster.log.ClusterClientStatLogUtil;
import com.alibaba.csp.sentinel.cluster.request.ClusterRequest;
import com.alibaba.csp.sentinel.cluster.request.data.FlowRequestData;
import com.alibaba.csp.sentinel.cluster.request.data.ParamFlowRequestData;
import com.alibaba.csp.sentinel.cluster.response.ClusterResponse;
import com.alibaba.csp.sentinel.cluster.response.data.FlowTokenResponseData;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.util.StringUtil;

/**
 * 默认的{@link ClusterTokenClient}实现
 *
 * @author Eric Zhao
 * @since 1.4.0
 */
public class DefaultClusterTokenClient implements ClusterTokenClient {

    /**
     * Sentinel分布式的同步传输客户端
     */
    private ClusterTransportClient transportClient;
    /**
     * Sentinel令牌服务器的描述符
     */
    private TokenServerDescriptor serverDescriptor;

    /**
     * 线程安全的启动状态标识
     */
    private final AtomicBoolean shouldStart = new AtomicBoolean(false);

    public DefaultClusterTokenClient() {
        ClusterClientConfigManager.addServerChangeObserver(new ServerChangeObserver() {
            @Override
            public void onRemoteServerChange(ClusterClientAssignConfig assignConfig) {
                changeServer(assignConfig);
            }
        });
        initNewConnection();
    }

    private boolean serverEqual(TokenServerDescriptor descriptor, ClusterClientAssignConfig config) {
        if (descriptor == null || config == null) {
            return false;
        }
        return descriptor.getHost().equals(config.getServerHost()) && descriptor.getPort() == config.getServerPort();
    }

    private void initNewConnection() {
        if (transportClient != null) {
            return;
        }
        String host = ClusterClientConfigManager.getServerHost();
        int port = ClusterClientConfigManager.getServerPort();
        if (StringUtil.isBlank(host) || port <= 0) {
            return;
        }

        try {
            this.transportClient = new NettyTransportClient(host, port);
            this.serverDescriptor = new TokenServerDescriptor(host, port);
            RecordLog.info("[DefaultClusterTokenClient] New client created: {}", serverDescriptor);
        } catch (Exception ex) {
            RecordLog.warn("[DefaultClusterTokenClient] Failed to initialize new token client", ex);
        }
    }

    private void changeServer(/*@Valid*/ ClusterClientAssignConfig config) {
        if (serverEqual(serverDescriptor, config)) {
            return;
        }
        try {
            // 停止当前客户端
            if (transportClient != null) {
                transportClient.stop();
            }
            // 替换成创建新的客户端
            this.transportClient = new NettyTransportClient(config.getServerHost(), config.getServerPort());
            this.serverDescriptor = new TokenServerDescriptor(config.getServerHost(), config.getServerPort());
            // 启动新客户端
            startClientIfScheduled();
            RecordLog.info("[DefaultClusterTokenClient] New client created: {}", serverDescriptor);
        } catch (Exception ex) {
            RecordLog.warn("[DefaultClusterTokenClient] Failed to change remote token server", ex);
        }
    }

    private void startClientIfScheduled() throws Exception {
        if (shouldStart.get()) {
            if (transportClient != null) {
                transportClient.start();
            } else {
                RecordLog.warn("[DefaultClusterTokenClient] Cannot start transport client: client not created");
            }
        }
    }

    private void stopClientIfStarted() throws Exception {
        if (shouldStart.compareAndSet(true, false)) {
            if (transportClient != null) {
                transportClient.stop();
            }
        }
    }

    @Override
    public void start() throws Exception {
        if (shouldStart.compareAndSet(false, true)) {
            startClientIfScheduled();
        }
    }

    @Override
    public void stop() throws Exception {
        stopClientIfStarted();
    }

    @Override
    public int getState() {
        if (transportClient == null) {
            return ClientConstants.CLIENT_STATUS_OFF;
        }
        return transportClient.isReady() ? ClientConstants.CLIENT_STATUS_STARTED : ClientConstants.CLIENT_STATUS_OFF;
    }

    @Override
    public TokenServerDescriptor currentServer() {
        return serverDescriptor;
    }

    @Override
    public TokenResult requestToken(Long flowId, int acquireCount, boolean prioritized) {
        if (notValidRequest(flowId, acquireCount)) {
            return badRequest();
        }
        FlowRequestData data = new FlowRequestData().setCount(acquireCount)
            .setFlowId(flowId).setPriority(prioritized);
        ClusterRequest<FlowRequestData> request = new ClusterRequest<>(ClusterConstants.MSG_TYPE_FLOW, data);
        try {
            TokenResult result = sendTokenRequest(request);
            logForResult(result);
            return result;
        } catch (Exception ex) {
            ClusterClientStatLogUtil.log(ex.getMessage());
            return new TokenResult(TokenResultStatus.FAIL);
        }
    }

    @Override
    public TokenResult requestParamToken(Long flowId, int acquireCount, Collection<Object> params) {
        if (notValidRequest(flowId, acquireCount) || params == null || params.isEmpty()) {
            return badRequest();
        }
        ParamFlowRequestData data = new ParamFlowRequestData().setCount(acquireCount)
            .setFlowId(flowId).setParams(params);
        ClusterRequest<ParamFlowRequestData> request = new ClusterRequest<>(ClusterConstants.MSG_TYPE_PARAM_FLOW, data);
        try {
            TokenResult result = sendTokenRequest(request);
            logForResult(result);
            return result;
        } catch (Exception ex) {
            ClusterClientStatLogUtil.log(ex.getMessage());
            return new TokenResult(TokenResultStatus.FAIL);
        }
    }

    @Override
    public TokenResult requestConcurrentToken(String clientAddress, Long ruleId, int acquireCount) {
        return null;
    }

    @Override
    public void releaseConcurrentToken(Long tokenId) {
    }

    private void logForResult(TokenResult result) {
        switch (result.getStatus()) {
            case TokenResultStatus.NO_RULE_EXISTS:
                ClusterClientStatLogUtil.log(ClusterErrorMessages.NO_RULES_IN_SERVER);
                break;
            case TokenResultStatus.TOO_MANY_REQUEST:
                ClusterClientStatLogUtil.log(ClusterErrorMessages.TOO_MANY_REQUESTS);
                break;
            default:
        }
    }

    private TokenResult sendTokenRequest(ClusterRequest request) throws Exception {
        if (transportClient == null) {
            RecordLog.warn(
                "[DefaultClusterTokenClient] Client not created, please check your config for cluster client");
            return clientFail();
        }
        ClusterResponse response = transportClient.sendRequest(request);
        TokenResult result = new TokenResult(response.getStatus());
        if (response.getData() != null) {
            FlowTokenResponseData responseData = (FlowTokenResponseData)response.getData();
            result.setRemaining(responseData.getRemainingCount())
                .setWaitInMs(responseData.getWaitInMs());
        }
        return result;
    }

    private boolean notValidRequest(Long id, int count) {
        return id == null || id <= 0 || count <= 0;
    }

    private TokenResult badRequest() {
        return new TokenResult(TokenResultStatus.BAD_REQUEST);
    }

    private TokenResult clientFail() {
        return new TokenResult(TokenResultStatus.FAIL);
    }
}
