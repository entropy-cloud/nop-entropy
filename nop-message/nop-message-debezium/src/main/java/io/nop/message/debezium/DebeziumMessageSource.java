/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.ConsumeFailure;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancellable;
import io.nop.message.debezium.engine.DebeziumEngineWrapper;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;
import org.apache.kafka.connect.storage.OffsetBackingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * Debezium CDC 消息源
 * <p>
 * 将 Debezium CDC 事件推送给订阅者
 */
public class DebeziumMessageSource {
    private static final Logger LOG = LoggerFactory.getLogger(DebeziumMessageSource.class);

    private final DebeziumConfig config;
    private final OffsetBackingStore offsetStore;

    /**
     * C6-006 (plan 2503 Phase 2, WI23-aligned): subscriptions are held by SUBSCRIPTION
     * identity, never by the action reference. Pre-fix the action-keyed map made a second
     * subscribe with the same method reference orphan the first registration (put-overwrite),
     * and cancelling the stale handle deleted the CURRENT registration and stopped the live
     * engine. Identity keys give the WI23 Local semantics: every subscribe is an independent
     * subscription, cancelling one never touches its siblings.
     */
    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();

    private DebeziumEngineWrapper engineWrapper;
    private volatile boolean started = false;

    /**
     * MA-05 (C3-003 interleaving B, plan 2503 Phase 2): set FIRST in stop() under the source
     * monitor. A subscribe racing the teardown linearizes after it and is rejected typed — a
     * fresh engine is never silently resurrected against a closing source.
     */
    private boolean stopped = false;

    /**
     * D12 §3.3.3 (MA-02, plan 2503 Phase 2): the single poison-event endpoint — skip + count
     * + WARN + guarded ConsumeFailure dispatch. Serves BOTH barrier faces (禁分叉): the
     * wrapper's convert-stage barrier (fan-out to every subscription, attempt fixed 0) and
     * dispatchEvent's per-subscriber failure branch (single target, per-subscription attempt).
     */
    private final PoisonEventEndpoint poisonEndpoint = new PoisonEventEndpoint();

    public DebeziumMessageSource(DebeziumConfig config) {
        this(config, null);
    }

    /**
     * Constructor overload accepting a custom {@link OffsetBackingStore} (typically a
     * {@link NopStreamOffsetBackingStore}) so that CDC offsets participate in the nop-stream
     * checkpoint protocol. When {@code offsetStore} is non-null it is forwarded to
     * {@link DebeziumEngineWrapper}, which registers the store via the {@code offset.storage}
     * property so the embedded engine instantiates and binds it.
     *
     * @param config      the Debezium configuration
     * @param offsetStore custom offset backing store, or null to use Debezium defaults
     */
    public DebeziumMessageSource(DebeziumConfig config, OffsetBackingStore offsetStore) {
        if (config == null) {
            throw new NopException(DebeziumErrors.ERR_DEBEZIUM_CONFIG_INVALID)
                    .param("detail", "config must not be null");
        }
        this.config = config;
        this.offsetStore = offsetStore;
    }

    /**
     * Returns the offset backing store passed at construction (may be null).
     */
    public OffsetBackingStore getOffsetStore() {
        return offsetStore;
    }

    /** Test/wiring visibility for the construction-time config. */
    DebeziumConfig getConfig() {
        return config;
    }

    /**
     * MA-02 wiring seam (rule 23): the production {@link #createEngineWrapper()} passes these
     * accessor results; wiring tests construct a wrapper subtype through the SAME accessors,
     * so the production callback wiring is exercised verbatim (no mirrored private wiring).
     */
    protected Consumer<ConsumeFailure> poisonEventReporter() {
        return this::onPoisonEventReported;
    }

    /** MA-02 ③ wiring seam — see {@link #poisonEventReporter()}. */
    protected Consumer<Throwable> terminalDeathListener() {
        return this::onEngineTerminalDeath;
    }

    /**
     * 订阅 CDC 变更事件
     *
     * @param action 事件处理器
     * @return 可取消的订阅
     */
    public ICancellable subscribe(Consumer<ChangeEvent> action) {
        return subscribe(action, null);
    }

    /**
     * D12 §3.3.3 (M-1, plan 2503 Phase 2): the CDC failure callback is PER-SUBSCRIPTION and
     * carried on the subscribe overload (never injected at source level — a source-level
     * single callback would lose the "which subscription failed" attribution). A null
     * handler is a declared degradation: the poison-event endpoint then provides WARN +
     * counter visibility only, and says so on every dispatch.
     *
     * @param action         事件处理器
     * @param failureHandler 失败回调（毒事件 endpoint 第三通道；null = 显式两通道退化）
     * @return 可取消的订阅
     */
    public ICancellable subscribe(Consumer<ChangeEvent> action,
                                  Consumer<ConsumeFailure> failureHandler) {
        Guard.notNull(action, "action");
        Subscription subscription = new Subscription(action, failureHandler);
        // MA-05 三面归一 (C6-002 / C3-003 交错 A/B / C1-006, plan 2503 Phase 2): the compound
        // operation "reject-if-stopped + start engine + register" is atomic under the source
        // monitor. Pre-fix the registration happened BEFORE the start (a failed start left a
        // ghost entry: the caller got an exception but its handler kept receiving events and
        // could never cancel), and the lock-free put raced stop()'s clear (subscription
        // silently lost).
        synchronized (this) {
            if (stopped) {
                // C3-003 interleaving B: a stopped source is never resurrected by a late
                // subscriber — typed rejection instead of a fresh engine.
                throw new NopException(DebeziumErrors.ERR_DEBEZIUM_ENGINE_START_FAILED)
                        .param("name", config.getName())
                        .param("detail",
                                "message source already stopped: subscribe is rejected; create a new source");
            }
            // C6-002 face ①: registration happens only AFTER the engine start succeeded.
            startEngineIfNeeded();
            subscriptions.add(subscription);
        }
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

    protected synchronized void startEngineIfNeeded() {
        if (started) {
            return;
        }

        try {
            engineWrapper = createEngineWrapper();
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

    /**
     * Single construction point of the engine wrapper (rule 23 wiring seam): production
     * callers always get the fully-wired wrapper below; tests may override to inject a
     * wrapper subtype while keeping this callback wiring verbatim.
     */
    protected DebeziumEngineWrapper createEngineWrapper() {
        return new DebeziumEngineWrapper(config, this::dispatchEvent, offsetStore,
                this::onPoisonEventReported, this::onEngineTerminalDeath);
    }

    /**
     * Poison-event endpoint, convert stage (MA-02 / D12 §3.3.3): the wrapper barrier hands
     * the failure over (attempt fixed 0 per the M-1 convert-attribution ruling); the endpoint
     * owns WARN + counter + fan-out to every subscription's failure handler. Never rethrows
     * into the engine thread.
     */
    private void onPoisonEventReported(ConsumeFailure failure) {
        poisonEndpoint.report(failure, null);
    }

    /**
     * MA-02 ③ (D12 §3.3.3): a terminal engine death flips the started flag — isStarted no
     * longer lies about a silently dead CDC stream. Wired as an INSTANCE-level wrapper
     * callback on purpose: the static FAILURE_LISTENERS registry is single-slot per connector
     * name and is owned by the embedding task (DebeziumCdcSourceFunction registers its
     * R5-CON-03 listener before this source exists) — a source-side static registration would
     * clobber it. Protocol change (listener-key upgrade) is R4-24/MA-10 scope.
     */
    private void onEngineTerminalDeath(Throwable error) {
        if (started) {
            LOG.error("nop.message.debezium.engine-terminal-failure:connector={} "
                    + "- CDC stream died terminally, started flag flipped", config.getName(), error);
        }
        started = false;
    }

    /**
     * C3-009 ⑤ (plan 2496 Phase 4): the per-subscriber guard must cover Throwable.
     * Pre-fix an Error thrown by one subscriber escaped the Exception-only catch, killed
     * {@code engine.run()} on the Debezium worker thread and (before the C3-009 ④ worker
     * guard) bypassed the terminal-failure notification. Post-fix a failing subscriber
     * only costs its own event log; the remaining subscribers still receive the event
     * (visibility widened to protected as the Error-injection test seam).
     */
    /**
     * C3-009 ⑤ (plan 2496 Phase 4): the per-subscriber guard must cover Throwable — one
     * failing subscriber never kills event delivery to the remaining subscribers (Error
     * included).
     *
     * <p>MA-02 (C8-004 delivery polarity, plan 2503 Phase 2): a subscriber failure is no
     * longer a bare LOG-and-return — pre-fix the engine then treated the event as delivered
     * and advanced the offset (permanent silent loss for that subscriber). The failure now
     * enters the SAME poison-event endpoint as the wrapper's convert-stage barrier: skip +
     * WARN + counter + guarded ConsumeFailure dispatch with the subscription's own attempt
     * counter. Semantics: the delivery terminal point acks AT the endpoint — the endpoint
     * carries the full evidence obligation (at-most-once, declared), so the engine's offset
     * advance is no longer backed by silence.
     */
    protected void dispatchEvent(ChangeEvent event) {
        for (Subscription subscription : subscriptions) {
            subscription.deliver(event);
        }
    }

    /**
     * 停止消息源
     */
    public synchronized void stop() {
        // MA-05 (C3-003 interleaving B): the stopped latch is set FIRST under the source
        // monitor — a subscribe racing the teardown linearizes after it and hits the typed
        // rejection instead of resurrecting a fresh engine.
        stopped = true;
        if (engineWrapper != null) {
            try {
                engineWrapper.stop();
                LOG.info("Debezium message source stopped: {}", config.getName());
                engineWrapper = null;
            } catch (Exception e) {
                // C3-014 ② support (plan 2503 Phase 3): keep the wrapper reference on a
                // failed stop so engine terminality stays queryable by the stop faces'
                // verification — nulling it would hide a possible ghost.
                LOG.error("Error stopping Debezium engine: {}", config.getName(), e);
            }
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
     * D12 §3.3.3: the poison-event failure counter ({@code nop.message.debezium.poison-event}
     * observation key) — every event dropped by the poison endpoint (convert stage or
     * per-subscriber stage) increments it. Metric-surface visibility for the at-most-once
     * failure semantics declared by the endpoint.
     */
    public long getPoisonEventCount() {
        return poisonEndpoint.count();
    }

    /**
     * C3-014 ② (plan 2503 Phase 3): whether the bound engine body has TERMINATED (delegates
     * to the wrapper's termination latch). A stopped or never-started source is vacuously
     * terminated. This is the signal the nop-stream stop faces poll after the stop chain
     * returns, upgrading the stop contract from「已发出」to「已终止」.
     */
    public boolean isEngineTerminated() {
        DebeziumEngineWrapper wrapper = this.engineWrapper;
        return wrapper == null || wrapper.isTerminated();
    }

    /**
     * D12 §3.3.3 poison-event endpoint (MA-02): skip + count + WARN + guarded ConsumeFailure
     * dispatch — one implementation for both barrier faces (禁分叉). The counter is the
     * {@code nop.message.debezium.poison-event} metric surface (visible via
     * {@link #getPoisonEventCount()}).
     */
    private class PoisonEventEndpoint {
        private final LongAdder poisonEventCount = new LongAdder();

        long count() {
            return poisonEventCount.sum();
        }

        /**
         * @param origin the failing subscription; {@code null} = convert-stage failure before
         *               any subscriber (fan-out to every subscription's handler, attempt
         *               fixed 0 per the M-1 ruling)
         */
        void report(ConsumeFailure failure, Subscription origin) {
            poisonEventCount.increment();
            LOG.warn("nop.message.debezium.poison-event:topic={},location={},attempt={},total={}",
                    failure.getTopic(), failure.getLocation(), failure.getAttempt(),
                    poisonEventCount.sum(), failure.getThrowable());
            if (origin == null) {
                for (Subscription subscription : subscriptions) {
                    dispatch(subscription, failure);
                }
            } else {
                dispatch(origin, failure);
            }
        }

        private void dispatch(Subscription subscription, ConsumeFailure failure) {
            Consumer<ConsumeFailure> handler = subscription.failureHandler;
            if (handler == null) {
                // D12 §3.3.3 (M-1): the callback channel is optional; its absence is
                // DECLARED per failure — visibility degrades to the WARN + counter channels.
                LOG.warn("nop.message.debezium.poison-event.no-failure-handler:topic={},location={} "
                        + "- no failure handler injected at subscribe; visibility degrades to WARN + counter",
                        failure.getTopic(), failure.getLocation());
                return;
            }
            try {
                handler.accept(failure);
            } catch (Throwable callbackError) {
                // guarded dispatch (D12 §3.3 WI16 alignment): a throwing user callback must
                // not kill the engine thread or the sibling dispatch loop
                LOG.warn("nop.message.debezium.poison-event.failure-callback-throw:topic={}",
                        failure.getTopic(), callbackError);
            }
        }
    }

    /**
     * 订阅实现
     */
    private class Subscription implements ICancellable {
        private final Consumer<ChangeEvent> action;
        private final Consumer<ConsumeFailure> failureHandler;
        /**
         * D12 §3.3.3: per-subscription consecutive failure count on the poison-event endpoint
         * (success resets; memory-scoped, no cross-restart semantics).
         */
        private final AtomicInteger failureAttempt = new AtomicInteger();
        private volatile boolean cancelled = false;
        private String cancelReason;

        Subscription(Consumer<ChangeEvent> action, Consumer<ConsumeFailure> failureHandler) {
            this.action = action;
            this.failureHandler = failureHandler;
        }

        /**
         * Per-subscriber delivery with the poison-event endpoint as its failure branch
         * (MA-02): the 2496 ⑤ Throwable containment width is preserved, but the failure is
         * no longer swallowed silently — see {@link #dispatchEvent(ChangeEvent)}.
         */
        void deliver(ChangeEvent event) {
            try {
                action.accept(event);
                failureAttempt.set(0);
            } catch (Throwable e) {
                poisonEndpoint.report(asConsumeFailure(event, e), this);
            }
        }

        private ConsumeFailure asConsumeFailure(ChangeEvent event, Throwable e) {
            ChangeEventMetadata metadata = event.getMetadata();
            String connector = (metadata != null && metadata.getConnector() != null)
                    ? metadata.getConnector() : config.getName();
            String serverName = (metadata != null && metadata.getServerName() != null)
                    ? metadata.getServerName() : connector;
            String table = metadata == null ? null : metadata.getTable();
            // D12 §3.3.3 field mapping: topic = event destination, location = connector +
            // source offset key, message = the event itself, attempt = subscription-scoped
            String topic = table != null ? serverName + "." + table : serverName;
            return new ConsumeFailure(topic, locationOf(connector, metadata), event,
                    failureAttempt.incrementAndGet(), asReportedException(e));
        }

        private String locationOf(String connector, ChangeEventMetadata metadata) {
            if (metadata == null) {
                return connector;
            }
            String position;
            if (metadata.getBinlogFile() != null) {
                position = metadata.getBinlogFile() + ":" + metadata.getBinlogPosition();
            } else if (metadata.getLsn() != null) {
                position = "lsn:" + metadata.getLsn();
            } else if (metadata.getCommitLsn() != null) {
                position = "commitLsn:" + metadata.getCommitLsn();
            } else {
                position = String.valueOf(metadata.getTable());
            }
            return connector + "@" + position;
        }

        private Exception asReportedException(Throwable e) {
            if (e instanceof Exception) {
                return (Exception) e;
            }
            return new IllegalStateException("non-exception throwable: " + e, e);
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
            // MA-05 face ② (C3-003 interleavings) + C6-006: identity-based deregistration and
            // the emptiness check are ONE atomic compound under the same monitor as
            // subscribe/stop (stop() reenters it). Pre-fix the action-keyed remove deleted
            // the CURRENT registration when a stale handle was cancelled (orphaning the live
            // subscription and stopping the engine under it), and the lock-free
            // isEmpty()->stop() raced a concurrent subscribe.
            synchronized (DebeziumMessageSource.this) {
                cancelled = true;
                cancelReason = reason;
                // C6-006 (WI23 同法): remove by SUBSCRIPTION identity — never by action key.
                boolean removed = subscriptions.remove(this);
                if (removed && subscriptions.isEmpty()) {
                    stop();
                }
            }
            LOG.debug("Subscription cancelled: {}", reason);
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
