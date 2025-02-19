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

import java.util.Collection;

/**
 * 流控服务接口
 *
 * @author Eric Zhao
 * @since 1.4.0
 */
public interface TokenService {

    /**
     * 从远程令牌服务器请求令牌
     *
     * @param ruleId 唯一规则ID
     * @param acquireCount 申请的令牌总数
     * @param prioritized 该请求是否优先
     * @return 令牌请求的结果
     */
    TokenResult requestToken(Long ruleId, int acquireCount, boolean prioritized);

    /**
     * 从远程令牌服务器为特定参数请求令牌
     *
     * @param ruleId 唯一规则ID
     * @param acquireCount 申请的令牌总数
     * @param params 参数列表
     * @return 令牌请求的结果
     */
    TokenResult requestParamToken(Long ruleId, int acquireCount, Collection<Object> params);

    /**
     * 从远程令牌服务器申请并发令牌
     *
     * @param clientAddress 请求所属的地址
     * @param ruleId 唯一规则ID
     * @param acquireCount 申请的令牌总数
     * @return 令牌请求的结果
     */
    TokenResult requestConcurrentToken(String clientAddress,Long ruleId,int acquireCount);
    /**
     * 从远程令牌服务器异步释放并发令牌
     *
     * @param tokenId 唯一令牌ID
     */
    void releaseConcurrentToken(Long tokenId);
}
