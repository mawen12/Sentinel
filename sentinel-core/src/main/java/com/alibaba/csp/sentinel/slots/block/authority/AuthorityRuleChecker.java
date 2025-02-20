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
package com.alibaba.csp.sentinel.slots.block.authority;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.util.StringUtil;

/**
 * 用于黑白名单列表权限的规则检查器
 *
 * @author Eric Zhao
 * @since 0.2.0
 */
final class AuthorityRuleChecker {

    static boolean passCheck(AuthorityRule rule, Context context) {
        // 获取原始的请求
        String requester = context.getOrigin();

        // 原始为空或者空的限制app允许通过
        if (StringUtil.isEmpty(requester) || StringUtil.isEmpty(rule.getLimitApp())) {
            return true;
        }

        // 检查是否与原始名称匹配
        int pos = rule.getLimitApp().indexOf(requester);
        boolean contain = pos > -1;

        if (contain) {
            boolean exactlyMatch = false;
            String[] appArray = rule.getLimitApp().split(",");
            for (String app : appArray) {
                // 如果匹配app
                if (requester.equals(app)) {
                    exactlyMatch = true;
                    break;
                }
            }

            contain = exactlyMatch;
        }

        // 获取策略
        int strategy = rule.getStrategy();
        // 黑名单策略，且存在
        if (strategy == RuleConstant.AUTHORITY_BLACK && contain) {
            return false;
        }

        // 白名单策略，且不存在
        if (strategy == RuleConstant.AUTHORITY_WHITE && !contain) {
            return false;
        }

        return true;
    }

    private AuthorityRuleChecker() {}
}
