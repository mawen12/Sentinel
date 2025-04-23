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
package com.alibaba.csp.sentinel.datasource;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.alibaba.csp.sentinel.concurrent.NamedThreadFactory;
import com.alibaba.csp.sentinel.log.RecordLog;

/**
 * 支持自动从后端获取数据的能力。
 *
 * <p>使用{@link ScheduledExecutorService}定时从后端获取数据，每次间隔3s。
 *
 * @param <S> source data type
 * @param <T> target data type
 * @author Carpenter Lee
 */
public abstract class AutoRefreshDataSource<S, T> extends AbstractDataSource<S, T> {

    /**
     * 定时调度
     */
    private ScheduledExecutorService service;
    /**
     * 3s后启动，之后每隔3s执行一次
     */
    protected long recommendRefreshMs = 3000;

    public AutoRefreshDataSource(Converter<S, T> configParser) {
        super(configParser);
        startTimerService();
    }

    public AutoRefreshDataSource(Converter<S, T> configParser, final long recommendRefreshMs) {
        super(configParser);
        if (recommendRefreshMs <= 0) {
            throw new IllegalArgumentException("recommendRefreshMs must > 0, but " + recommendRefreshMs + " get");
        }
        this.recommendRefreshMs = recommendRefreshMs;
        startTimerService();
    }

    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    private void startTimerService() {
        // 创建单线程的调度服务
        service = Executors.newScheduledThreadPool(1, new NamedThreadFactory("sentinel-datasource-auto-refresh-task", true));
        service.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isModified()) {
                        return;
                    }
                    // 加载配置
                    T newValue = loadConfig();
                    // 更新配置，并由属性触发对应监听器
                    getProperty().updateValue(newValue);
                } catch (Throwable e) {
                    RecordLog.info("loadConfig exception", e);
                }
            }
        }, recommendRefreshMs, recommendRefreshMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() throws Exception {
        if (service != null) {
            // 停止调度服务
            service.shutdownNow();
            service = null;
        }
    }

    protected boolean isModified() {
        return true;
    }
}
