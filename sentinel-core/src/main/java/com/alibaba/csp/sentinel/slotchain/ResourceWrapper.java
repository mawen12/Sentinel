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
package com.alibaba.csp.sentinel.slotchain;

import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.util.AssertUtil;

/**
 * 资源名称和资源类型的包装器
 *
 * @author qinan.qn
 * @author jialiang.linjl
 * @author Eric Zhao
 */
public abstract class ResourceWrapper {

    /**
     * 资源名称
     */
    protected final String name;

    /**
     * 资源调用方向
     */
    protected final EntryType entryType;

    /**
     * 资源类型
     *
     * @see com.alibaba.csp.sentinel.ResourceTypeConstants
     */
    protected final int resourceType;

    public ResourceWrapper(String name, EntryType entryType, int resourceType) {
        AssertUtil.notEmpty(name, "resource name cannot be empty");
        AssertUtil.notNull(entryType, "entryType cannot be null");
        this.name = name;
        this.entryType = entryType;
        this.resourceType = resourceType;
    }

    /**
     * @return 返回资源名称
     */
    public String getName() {
        return name;
    }

    /**
     * @return 返回资源调用方向
     */
    public EntryType getEntryType() {
        return entryType;
    }

    /**
     * @return 返回资源的分类
     * @since 1.7.0
     */
    public int getResourceType() {
        return resourceType;
    }

    /**
     * @return 美化后的资源名称
     */
    public abstract String getShowName();

    /**
     * Only {@link #getName()} is considered.
     */
    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    /**
     * Only {@link #getName()} is considered.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ResourceWrapper) {
            ResourceWrapper rw = (ResourceWrapper)obj;
            return rw.getName().equals(getName());
        }
        return false;
    }
}
