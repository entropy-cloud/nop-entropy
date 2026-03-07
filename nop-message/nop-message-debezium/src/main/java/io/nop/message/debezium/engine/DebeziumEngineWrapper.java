/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium.engine;

import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.JsonByteArray;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Debezium 嵌入式引擎包装类
 */
public class DebeziumEngineWrapper {
    private static final Logger LOG = LoggerFactory.getLogger(DebeziumEngineWrapper.class);

    private final DebeziumConfig config;
    private final Consumer<ChangeEvent> changeEventConsumer;

    private DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engine;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public DebeziumEngineWrapper(DebeziumConfig config, Consumer<ChangeEvent> changeEventConsumer) {
        this.config = config;
        this.changeEventConsumer = changeEventConsumer;
    }

    /**
     * 启动引擎
     */
    public synchronized void start() {
        if (running.get()) {
            LOG.warn("Debezium engine is already running");
            return;
        }

        try {
            // 构建配置
            Properties props = DebeziumEngineConfig.buildProperties(config);

            // 创建引擎
            engine = DebeziumEngine.create(JsonByteArray.class)
                    .using(props)
                    .using(new EngineCompletionCallback())
                    .notifying(this::handleDebeziumEvent)
                    .using(new EngineConnectorCallback())
                    .build();

            // 使用全局线程池运行引擎
            GlobalExecutors.globalWorker().execute(() -> {
                try {
                    running.set(true);
                    LOG.info("Starting Debezium engine: {}", config.getName());
                    engine.run();
                } catch (Exception e) {
                    LOG.error("Debezium engine error: {}", config.getName(), e);
                } finally {
                    running.set(false);
                    LOG.info("Debezium engine stopped: {}", config.getName());
                }
            });

        } catch (Exception e) {
            throw new NopException(DebeziumErrors.ERR_DEBEZIUM_ENGINE_START_FAILED)
                    .param("connector", config.getName())
                    .cause(e);
        }
    }

    /**
     * 处理 Debezium 事件并转换为 ChangeEvent
     */
    private void handleDebeziumEvent(io.debezium.engine.ChangeEvent<byte[], byte[]> event) {
        ChangeEvent changeEvent = DebeziumEventConverter.convert(event);
        if (changeEvent != null) {
            changeEventConsumer.accept(changeEvent);
        }
    }

    /**
     * 停止引擎
     */
    public synchronized void stop() {
        if (!running.get() || stopped.getAndSet(true)) {
            return;
        }

        LOG.info("Stopping Debezium engine: {}", config.getName());

        try {
            if (engine != null) {
                engine.close();
            }
        } catch (Exception e) {
            LOG.error("Error closing Debezium engine: {}", config.getName(), e);
        }

        running.set(false);
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 引擎完成回调
     */
    private class EngineCompletionCallback implements DebeziumEngine.CompletionCallback {
        @Override
        public void handle(boolean success, String message, Throwable error) {
            if (!success) {
                LOG.error("Debezium engine completed with error: {} - {}", config.getName(), message, error);
            } else {
                LOG.info("Debezium engine completed successfully: {}", config.getName());
            }
            running.set(false);
        }
    }

    /**
     * 引擎连接器回调
     */
    private class EngineConnectorCallback implements DebeziumEngine.ConnectorCallback {
        @Override
        public void connectorStarted() {
            LOG.info("Debezium connector started: {}", config.getName());
        }

        @Override
        public void connectorStopped() {
            LOG.info("Debezium connector stopped: {}", config.getName());
        }

        @Override
        public void taskStarted() {
            LOG.debug("Debezium task started: {}", config.getName());
        }

        @Override
        public void taskStopped() {
            LOG.debug("Debezium task stopped: {}", config.getName());
        }
    }
}
