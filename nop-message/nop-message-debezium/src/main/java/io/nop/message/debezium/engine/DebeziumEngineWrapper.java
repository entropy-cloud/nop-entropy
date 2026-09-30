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
import org.apache.kafka.connect.storage.OffsetBackingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Debezium 嵌入式引擎包装类
 */
public class DebeziumEngineWrapper {
    private static final Logger LOG = LoggerFactory.getLogger(DebeziumEngineWrapper.class);

    /**
     * Engine-terminal-failure listeners keyed by connector name (plan 368 Phase 4,
     * audit R5-CON-03). The engine runs on a global worker thread; without a listener
     * its terminal death (run() throwing or CompletionCallback(success=false)) was only
     * visible as ERROR log lines, leaving the owning task RUNNING with a silently dead
     * CDC stream. The registry is keyed by connector name because the wrapper instances
     * are created internally by {@code DebeziumMessageSource} — the name is the same
     * identity the offset registry ({@code NopStreamOffsetBackingStore.forConnector})
     * already keys by, so consumers of this class register/deregister around their run
     * lifecycle.
     */
    private static final Map<String, Consumer<Throwable>> FAILURE_LISTENERS = new ConcurrentHashMap<>();

    /**
     * Registers the terminal-failure listener for the given connector name (replaces any
     * previous listener for that name). The listener is invoked for EVERY
     * engine-terminal failure notification while registered; owners must deregister in
     * their cleanup path.
     */
    public static void registerFailureListener(String connectorName, Consumer<Throwable> listener) {
        if (connectorName == null || connectorName.isEmpty()) {
            throw new NopException(DebeziumErrors.ERR_DEBEZIUM_CONFIG_INVALID)
                    .param("detail", "connectorName must not be empty");
        }
        if (listener == null) {
            throw new NopException(DebeziumErrors.ERR_DEBEZIUM_CONFIG_INVALID)
                    .param("detail", "listener must not be null");
        }
        FAILURE_LISTENERS.put(connectorName, listener);
    }

    /**
     * Removes the failure listener for the given connector name.
     */
    public static void unregisterFailureListener(String connectorName) {
        if (connectorName != null) {
            FAILURE_LISTENERS.remove(connectorName);
        }
    }

    /**
     * Delivers an engine-terminal failure to the listener registered for the connector
     * name (no-op when nothing is registered). Production callers are the engine worker
     * catch block and the completion callback below; embedders/tests may use it to
     * report engine-terminal failures observed out of band.
     */
    public static void notifyEngineFailure(String connectorName, Throwable error) {
        Consumer<Throwable> listener = connectorName == null ? null : FAILURE_LISTENERS.get(connectorName);
        if (listener == null) {
            return;
        }
        try {
            listener.accept(error);
        } catch (Exception listenerError) {
            // never let a listener failure kill the engine worker thread
            LOG.error("Debezium engine failure listener threw for connector {}", connectorName, listenerError);
        }
    }

    private final DebeziumConfig config;
    private final Consumer<ChangeEvent> changeEventConsumer;
    private final OffsetBackingStore offsetStore;

    private DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engine;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public DebeziumEngineWrapper(DebeziumConfig config, Consumer<ChangeEvent> changeEventConsumer) {
        this(config, changeEventConsumer, null);
    }

    /**
     * Constructor overload accepting a custom {@link OffsetBackingStore} (typically a
     * {@link NopStreamOffsetBackingStore}). When {@code offsetStore} is non-null,
     * {@link DebeziumEngineConfig#buildProperties(DebeziumConfig, boolean)} is called with
     * {@code useCustomOffsetStore=true} so the {@code offset.storage} property points at the
     * store's class name. The embedded engine then instantiates the store via reflection and
     * binds it to the same connector-name registry entry as the source function's instance.
     *
     * <p>This bridges the gap created by Debezium 2.4.0 not exposing
     * {@code DebeziumEngine.Builder.using(OffsetBackingStore)}.
     */
    public DebeziumEngineWrapper(DebeziumConfig config, Consumer<ChangeEvent> changeEventConsumer,
                                 OffsetBackingStore offsetStore) {
        this.config = config;
        this.changeEventConsumer = changeEventConsumer;
        this.offsetStore = offsetStore;
    }

    /**
     * Returns the offset store passed at construction (may be null).
     */
    public OffsetBackingStore getOffsetStore() {
        return offsetStore;
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
            boolean useCustomOffsetStore = offsetStore != null;
            Properties props = DebeziumEngineConfig.buildProperties(config, useCustomOffsetStore);

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
                    // R5-CON-03: a terminal engine death must be observable by the
                    // owning task, not only in the log.
                    notifyEngineFailure(config.getName(), e);
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
                // R5-CON-03: CompletionCallback(success=false) is an engine-terminal
                // failure — surface it the same way as a thrown engine error.
                notifyEngineFailure(config.getName(),
                        error != null ? error : new DebeziumEngineFailure(message));
            } else {
                LOG.info("Debezium engine completed successfully: {}", config.getName());
            }
            running.set(false);
        }
    }

    /** Placeholder throwable for failure completions that carry only a message. */
    private static final class DebeziumEngineFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        DebeziumEngineFailure(String message) {
            super(message == null ? "Debezium engine failed" : message);
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
