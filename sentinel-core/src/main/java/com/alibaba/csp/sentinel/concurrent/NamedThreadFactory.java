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
package com.alibaba.csp.sentinel.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 为了更方便使用而包装的线程工厂
 */
public class NamedThreadFactory implements ThreadFactory {

    /**
     * 线程分组
     */
    private final ThreadGroup group;

    /**
     * 线程安全的线程数量
     */
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    /**
     * 线程名称前缀
     */
    private final String namePrefix;

    /**
     * 是否为守护线程
     */
    private final boolean daemon;

    public NamedThreadFactory(String namePrefix, boolean daemon) {
        this.daemon = daemon;
        SecurityManager s = System.getSecurityManager();
        // 如果指定了安全管理器，则使用其线程分组，否则继承当前线程分组
        group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        this.namePrefix = namePrefix;
    }

    public NamedThreadFactory(String namePrefix) {
        this(namePrefix, false);
    }

    @Override
    public Thread newThread(Runnable r) {
        // 在创建线程的时候，指定该分组，格式为<namePrefix>-thread-<threadNumber>
        Thread t = new Thread(group, r, namePrefix + "-thread-" + threadNumber.getAndIncrement(), 0);
        t.setDaemon(daemon);
        return t;
    }
}