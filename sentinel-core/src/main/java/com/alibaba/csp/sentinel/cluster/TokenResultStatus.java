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

/**
 * 令牌结果状态
 *
 * @author Eric Zhao
 * @since 1.4.0
 */
public final class TokenResultStatus {

    /**
     * 错误的客户端请求
     */
    public static final int BAD_REQUEST = -4;

    /**
     * 服务器存在太多请求
     */
    public static final int TOO_MANY_REQUEST = -2;

    /**
     * 服务器或客户端未预期错误（由于传输或序列化错误）
     */
    public static final int FAIL = -1;

    /**
     * 令牌申请成功
     */
    public static final int OK = 0;

    /**
     * 令牌申请失败（阻塞）
     */
    public static final int BLOCKED = 1;

    /**
     * 应等待下一个存储桶
     */
    public static final int SHOULD_WAIT = 2;

    /**
     * 令牌申请失败（规则不存在）
     */
    public static final int NO_RULE_EXISTS = 3;

    /**
     * 令牌申请失败（引用资源不可用）
     */
    public static final int NO_REF_RULE_EXISTS = 4;

    /**
     * 令牌申请失败（策略不可用）
     */
    public static final int NOT_AVAILABLE = 5;

    /**
     * 令牌成功被释放
     */
    public static final int RELEASE_OK = 6;

    /**
     * 在请求到达之前令牌已被释放
     */
    public static final int ALREADY_RELEASE = 7;

    private TokenResultStatus() {
    }
}
