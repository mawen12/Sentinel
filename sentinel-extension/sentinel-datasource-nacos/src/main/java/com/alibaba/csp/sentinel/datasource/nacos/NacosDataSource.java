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
package com.alibaba.csp.sentinel.datasource.nacos;

import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.alibaba.csp.sentinel.concurrent.NamedThreadFactory;
import com.alibaba.csp.sentinel.datasource.AbstractDataSource;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;

/**
 * 基于Nacos的只读数据源。当Nacos上的数据被编辑时，
 * Nacos会自动推送新值，因此可以实现动态配置刷新。
 *
 * @author Eric Zhao
 */
public class NacosDataSource<T> extends AbstractDataSource<String, T> {

    private static final int DEFAULT_TIMEOUT = 3000;

    /**
     * 只有单个线程的线程池，一旦线程池被阻塞，将丢弃最旧的任务，
     */
    private final ExecutorService pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<Runnable>(1), new NamedThreadFactory("sentinel-nacos-ds-update", true),
        new ThreadPoolExecutor.DiscardOldestPolicy());

    /**
     * Nacos的配置监听器
     */
    private final Listener configListener;
    /**
     * 数据的分组ID
     */
    private final String groupId;
    /**
     * 数据的ID
     */
    private final String dataId;
    /**
     *
     */
    private final Properties properties;

    private ConfigService configService = null;

    /**
     * Constructs an read-only DataSource with Nacos backend.
     *
     * @param serverAddr server address of Nacos, cannot be empty
     * @param groupId    group ID, cannot be empty
     * @param dataId     data ID, cannot be empty
     * @param parser     customized data parser, cannot be empty
     */
    public NacosDataSource(final String serverAddr, final String groupId, final String dataId,
                           Converter<String, T> parser) {
        this(NacosDataSource.buildProperties(serverAddr), groupId, dataId, parser);
    }

    /**
     *
     * @param properties properties for construct {@link ConfigService} using {@link NacosFactory#createConfigService(Properties)}
     * @param groupId    group ID, cannot be empty
     * @param dataId     data ID, cannot be empty
     * @param parser     customized data parser, cannot be empty
     */
    public NacosDataSource(final Properties properties, final String groupId, final String dataId,
                           Converter<String, T> parser) {
        super(parser);
        if (StringUtil.isBlank(groupId) || StringUtil.isBlank(dataId)) {
            throw new IllegalArgumentException(String.format("Bad argument: groupId=[%s], dataId=[%s]",
                groupId, dataId));
        }
        AssertUtil.notNull(properties, "Nacos properties must not be null, you could put some keys from PropertyKeyConst");
        this.groupId = groupId;
        this.dataId = dataId;
        this.properties = properties;
        this.configListener = new Listener() {
            @Override
            public Executor getExecutor() {
                return pool;
            }

            @Override
            public void receiveConfigInfo(final String configInfo) {
                RecordLog.info("[NacosDataSource] New property value received for (properties: {}) (dataId: {}, groupId: {}): {}",
                    properties, dataId, groupId, configInfo);
                // 解析配置
                T newValue = NacosDataSource.this.parser.convert(configInfo);
                // 更新配置
                getProperty().updateValue(newValue);
            }
        };
        // 初始化Nacos，并注册监听器
        initNacosListener();
        // 加载配置
        loadInitialConfig();
    }

    private void loadInitialConfig() {
        try {
            T newValue = loadConfig();
            if (newValue == null) {
                RecordLog.warn("[NacosDataSource] WARN: initial config is null, you may have to check your data source");
            }
            getProperty().updateValue(newValue);
        } catch (Exception ex) {
            RecordLog.warn("[NacosDataSource] Error when loading initial config", ex);
        }
    }

    private void initNacosListener() {
        try {
            this.configService = NacosFactory.createConfigService(this.properties);
            // Add config listener.
            configService.addListener(dataId, groupId, configListener);
        } catch (Exception e) {
            RecordLog.warn("[NacosDataSource] Error occurred when initializing Nacos data source", e);
            e.printStackTrace();
        }
    }

    @Override
    public String readSource() throws Exception {
        if (configService == null) {
            throw new IllegalStateException("Nacos config service has not been initialized or error occurred");
        }
        return configService.getConfig(dataId, groupId, DEFAULT_TIMEOUT);
    }

    @Override
    public void close() {
        if (configService != null) {
            configService.removeListener(dataId, groupId, configListener);
            try {
                configService.shutDown();
            } catch (Exception e) {
                RecordLog.warn("[NacosDataSource] Error occurred when closing Nacos data source", e);
                e.printStackTrace();
            }
        }
        pool.shutdownNow();
    }

    private static Properties buildProperties(String serverAddr) {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, serverAddr);
        return properties;
    }
}
