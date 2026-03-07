/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancellable;
import io.nop.message.debezium.engine.DebeziumEngineWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Debezium CDC 消息源
 * <p>
 * 将 Debezium CDC 事件推送给订阅者
 */
public class DebeziumMessageSource {
    private static final Logger LOG = LoggerFactory.getLogger(DebeziumMessageSource.class);

    private final DebeziumConfig config;
    private final Map<Consumer<ChangeEvent>, Subscription> subscriptions = new ConcurrentHashMap<>();
    private DebeziumEngineWrapper engineWrapper;
    private volatile boolean started = false;

    public DebeziumMessageSource(DebeziumConfig config) {
        this.config = config;
    }

    /**
     * 订阅 CDC 变更事件
     *
     * @param action 事件处理器
     * @return 可取消的订阅
     */
    public ICancellable subscribe(Consumer<ChangeEvent> action) {
        Subscription subscription = new Subscription(action);
        subscriptions.put(action, subscription);

        // 首次订阅时启动引擎
        startEngineIfNeeded();

        return subscription;
    }

    /**
     * 订阅特定表的变更事件
     *
     * @param tableName 表名 (格式: database.table 或 schema.table)
     * @param action    事件处理器
     * @return 可取消的订阅
     */
    public ICancellable subscribeTable(String tableName, Consumer<ChangeEvent> action) {
        return subscribe(event -> {
            ChangeEventMetadata metadata = event.getMetadata();
            if (metadata != null) {
                String fullTableName = metadata.getDatabase() + "." + metadata.getTable();
                if (tableName.equals(fullTableName) || tableName.equals(metadata.getTable())) {
                    action.accept(event);
                }
            }
        });
    }

    /**
     * 订阅特定操作类型的事件
     *
     * @param operation 操作类型: c, u, d, r
     * @param action    事件处理器
     * @return 可取消的订阅
     */
    public ICancellable subscribeOperation(String operation, Consumer<ChangeEvent> action) {
        return subscribe(event -> {
            if (operation.equals(event.getOperation())) {
                action.accept(event);
            }
        });
    }

    /**
     * 订阅插入事件
     */
    public ICancellable subscribeInserts(Consumer<ChangeEvent> action) {
        return subscribeOperation("c", action);
    }

    /**
     * 订阅更新事件
     */
    public ICancellable subscribeUpdates(Consumer<ChangeEvent> action) {
        return subscribeOperation("u", action);
    }

    /**
     * 订阅删除事件
     */
    public ICancellable subscribeDeletes(Consumer<ChangeEvent> action) {
        return subscribeOperation("d", action);
    }

    private synchronized void startEngineIfNeeded() {
        if (started) {
            return;
        }

        try {
            engineWrapper = new DebeziumEngineWrapper(config, this::dispatchEvent);
            engineWrapper.start();
            started = true;
            LOG.info("Debezium message source started: {}", config.getName());
        } catch (Exception e) {
            LOG.error("Failed to start Debezium engine: {}", config.getName(), e);
            throw new NopException(DebeziumErrors.ERR_DEBEZIUM_ENGINE_START_FAILED)
                    .param("name", config.getName())
                    .cause(e);
        }
    }

    private void dispatchEvent(ChangeEvent event) {
        for (Consumer<ChangeEvent> consumer : subscriptions.keySet()) {
            try {
                consumer.accept(event);
            } catch (Exception e) {
                LOG.error("Error processing CDC event: {}", event, e);
            }
        }
    }

    /**
     * 停止消息源
     */
    public synchronized void stop() {
        if (engineWrapper != null) {
            try {
                engineWrapper.stop();
                LOG.info("Debezium message source stopped: {}", config.getName());
            } catch (Exception e) {
                LOG.error("Error stopping Debezium engine: {}", config.getName(), e);
            }
            engineWrapper = null;
        }
        started = false;
        subscriptions.clear();
    }

    /**
     * 是否已启动
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * 获取当前订阅数量
     */
    public int getSubscriptionCount() {
        return subscriptions.size();
    }

    /**
     * 订阅实现
     */
    private class Subscription implements ICancellable {
        private final Consumer<ChangeEvent> action;
        private volatile boolean cancelled = false;
        private String cancelReason;

        Subscription(Consumer<ChangeEvent> action) {
            this.action = action;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public String getCancelReason() {
            return cancelReason;
        }

        @Override
        public void cancel(String reason) {
            cancelled = true;
            cancelReason = reason;
            subscriptions.remove(action);
            LOG.debug("Subscription cancelled: {}", reason);

            // 如果没有订阅者了，停止引擎
            if (subscriptions.isEmpty()) {
                stop();
            }
        }

        @Override
        public void removeOnCancel(Consumer<String> task) {
            // Simple implementation: no-op since we don't track individual cancel callbacks
        }

        @Override
        public void appendOnCancel(Consumer<String> task) {
            // Simple implementation: no-op since we don't track individual cancel callbacks
        }
    }
}
