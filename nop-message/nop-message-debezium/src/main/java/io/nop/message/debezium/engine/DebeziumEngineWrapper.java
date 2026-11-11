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
import io.nop.api.core.message.ConsumeFailure;
import io.nop.commons.concurrent.thread.NamedThreadFactory;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumErrors;
import org.apache.kafka.connect.storage.OffsetBackingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Debezium 嵌入式引擎包装类
 */
public class DebeziumEngineWrapper {
    private static final Logger LOG = LoggerFactory.getLogger(DebeziumEngineWrapper.class);

    /**
     * Engine-terminal-failure listeners keyed by connector name (plan 368 Phase 4,
     * audit R5-CON-03). The engine runs on its dedicated carrier thread (see
     * {@link #engineExecutor}); without a listener its terminal death (run() throwing or
     * CompletionCallback(success=false)) was only visible as ERROR log lines, leaving the
     * owning task RUNNING with a silently dead CDC stream. The registry is keyed by
     * connector name because the wrapper instances are created internally by
     * {@code DebeziumMessageSource} — the name is the same identity the offset registry
     * ({@code NopStreamOffsetBackingStore.forConnector}) already keys by, so consumers of
     * this class register/deregister around their run lifecycle.
     */
    private static final Map<String, Consumer<Throwable>> FAILURE_LISTENERS = new ConcurrentHashMap<>();

    /**
     * Registers the terminal-failure listener for the given connector name (replaces any
     * previous listener for that name). The listener is invoked for EVERY
     * engine-terminal failure notification while registered; owners must deregister in
     * their cleanup path.
     *
     * <p>MA-10 (plan 2558 裁定 F): replacing a DIFFERENT live listener (previous instance
     * still registered) emits a WARN — the generation observation face of the overwrite.
     * The replace-takes-effect semantics is unchanged (R404: the double-registration guard
     * upgrade belongs to the embedding task, zero API change).
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
        Consumer<Throwable> previous = FAILURE_LISTENERS.put(connectorName, listener);
        if (previous != null && previous != listener) {
            LOG.warn("nop.message.debezium.failure-listener-replace:connector={} - a DIFFERENT "
                    + "live listener was replaced (generation observation; the new listener "
                    + "takes effect)", connectorName);
        }
    }

    /**
     * Removes the failure listener for the given connector name (remove-by-name). The
     * unconditionally-removing form: used by owners clearing their own slot and as the
     * wrapper's stop-path fallback (MA-10: the wrapper cannot see the external owner's
     * handle, so the stop fallback clears the connector's slot by name).
     */
    public static void unregisterFailureListener(String connectorName) {
        if (connectorName != null) {
            FAILURE_LISTENERS.remove(connectorName);
        }
    }

    /**
     * MA-10 (plan 2558 裁定 F): two-arg conditional removal. The slot is only cleared when it
     * still holds the CALLER's listener instance; removing a foreign handle (a different live
     * listener registered by another owner) is rejected typed and the foreign listener stays
     * registered. Removing from an empty slot is an idempotent no-op.
     */
    public static void unregisterFailureListener(String connectorName, Consumer<Throwable> listener) {
        if (connectorName == null || connectorName.isEmpty()) {
            throw new NopException(DebeziumErrors.ERR_DEBEZIUM_CONFIG_INVALID)
                    .param("detail", "connectorName must not be empty");
        }
        if (listener == null) {
            throw new NopException(DebeziumErrors.ERR_DEBEZIUM_CONFIG_INVALID)
                    .param("detail", "listener must not be null");
        }
        Consumer<Throwable> current = FAILURE_LISTENERS.get(connectorName);
        if (current == null) {
            return;
        }
        if (current != listener) {
            throw new NopException(DebeziumErrors.ERR_DEBEZIUM_LISTENER_FOREIGN_HANDLE)
                    .param("connector", connectorName);
        }
        FAILURE_LISTENERS.remove(connectorName, listener);
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
        } catch (Throwable listenerError) {
            // C3-009 ⑦ (plan 2496 Phase 4): the listener guard must cover Throwable —
            // never let a listener failure (incl. Error) kill the engine worker thread.
            LOG.error("Debezium engine failure listener threw for connector {}", connectorName, listenerError);
        }
    }

    private final DebeziumConfig config;
    private final Consumer<ChangeEvent> changeEventConsumer;
    private final OffsetBackingStore offsetStore;

    /**
     * MA-02 (plan 2503 Phase 2, D12 §3.3.3): receives a {@link ConsumeFailure} for every
     * event the {@link #handleDebeziumEvent} barrier contains (convert stage — attempt is
     * fixed 0 per the M-1 attribution ruling). The owning DebeziumMessageSource fans the
     * failure out to every subscription's failure handler. Null (legacy constructors) = the
     * endpoint degrades to a declared WARN-only fallback inside the wrapper.
     */
    private final Consumer<ConsumeFailure> poisonEventReporter;

    /**
     * MA-02 ③ (plan 2503 Phase 2): instance-level engine-terminal-death listener, invoked
     * alongside the static FAILURE_LISTENERS registry. Instance-level ON PURPOSE: the static
     * registry is single-slot per connector name and is owned by the embedding task (e.g.
     * DebeziumCdcSourceFunction's R5-CON-03 listener registers before this source even
     * exists) — a source-side static registration would clobber it. The registry protocol is
     * unchanged (key upgrade belongs to R4-24/MA-10).
     */
    private final Consumer<Throwable> terminalFailureListener;

    /**
     * Bounded termination budget for the dedicated engine carrier (plan 2498 Phase 1,
     * finding 1.2-C4-004). stop() awaits the carrier at most this long, aligned with the
     * reader close chain's existing join(5s); the wait must never be unbounded.
     */
    static final int STOP_TERMINATION_BUDGET_SECONDS = 5;

    /**
     * C3-014 ① (plan 2503 Phase 3): bounded retry budget for {@code engine.close()}
     * rejections during the STARTING_TASKS window (debezium-embedded state-machine
     * contract), sliced at {@link #STARTING_TASKS_RETRY_SLICE_MILLIS}.
     */
    static final long STARTING_TASKS_RETRY_BUDGET_MILLIS = 2000L;
    static final long STARTING_TASKS_RETRY_SLICE_MILLIS = 200L;

    /**
     * C3-014 ⑤/④ (plan 2503 Phase 3): engine-body termination wait and stuck-engine
     * escalation budgets. Defaults align with the carrier budget of plan 2498; instance
     * fields (not constants) so the stuck-engine paths stay unit-testable without paying
     * the full default budget.
     */
    volatile long terminationWaitMillis = STOP_TERMINATION_BUDGET_SECONDS * 1000L;
    volatile long escalationWaitMillis = 1000L;

    private DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engine;

    /**
     * C3-014 ⑤ (plan 2503 Phase 3): engine-body termination latch — the queryable
     * terminality signal of the upgraded stop contract ("stop issued" → "engine
     * terminated"). Created at start(); counted down by the carrier lambda's finally, so
     * overrides of {@link #runEngineWorker} cannot lose it. Polled by the four nop-stream
     * stop faces via {@link #isTerminated()}.
     */
    private volatile CountDownLatch terminationLatch;

    /**
     * C3-014 ③ (C4-006 worker guard): set at stop() entry — a worker lambda that has not
     * entered yet must not drive the engine during or after the stop contract.
     */
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    /**
     * Dedicated per-engine single-thread carrier (plan 2498 Phase 1, finding 1.2-C4-004).
     * The embedded engine body ({@code engine.run()}) blocks for the whole engine lifetime
     * (day/month scale); it must never occupy a shared {@code nop-global-worker} thread.
     * The carrier has an independent capacity budget (one daemon thread) and its own
     * shutdown ownership, and is named {@code debezium-engine-<connector>-<n>-<n>} so
     * engine threads are distinguishable from shared workers in thread dumps.
     */
    private volatile ExecutorService engineExecutor;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** MA-09: set when the stop contract closed the engine — the worker-death quiet close is skipped then. */
    private final AtomicBoolean engineClosedByStopContract = new AtomicBoolean(false);

    /** R5 CON-02: set synchronously in start(); stop() gates on this, not on the worker-observed flag. */
    private final AtomicBoolean startRequested = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public DebeziumEngineWrapper(DebeziumConfig config, Consumer<ChangeEvent> changeEventConsumer) {
        this(config, changeEventConsumer, null, null, null);
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
        this(config, changeEventConsumer, offsetStore, null, null);
    }

    /**
     * Full constructor (MA-02, plan 2503 Phase 2, D12 §3.3.3).
     *
     * @param poisonEventReporter    receives a {@link ConsumeFailure} for every event the
     *                               {@link #handleDebeziumEvent} barrier contains (convert
     *                               stage, attempt fixed 0 per the M-1 ruling); null = the
     *                               endpoint degrades to a declared WARN-only fallback
     * @param terminalFailureListener invoked on every engine-terminal death notification
     *                               alongside the static FAILURE_LISTENERS registry; the
     *                               owning source flips its started flag with it (isStarted
     *                               must not lie about a dead CDC stream)
     */
    public DebeziumEngineWrapper(DebeziumConfig config, Consumer<ChangeEvent> changeEventConsumer,
                                 OffsetBackingStore offsetStore,
                                 Consumer<ConsumeFailure> poisonEventReporter,
                                 Consumer<Throwable> terminalFailureListener) {
        this.config = config;
        this.changeEventConsumer = changeEventConsumer;
        this.offsetStore = offsetStore;
        this.poisonEventReporter = poisonEventReporter;
        this.terminalFailureListener = terminalFailureListener;
    }

    /**
     * Returns the offset store passed at construction (may be null).
     */
    public OffsetBackingStore getOffsetStore() {
        return offsetStore;
    }

    /**
     * 启动引擎
     *
     * <p>MA-09 P3 (plan 2558 裁定 F 同文件顺带): a FAILED start resets {@code startRequested}
     * so the wrapper is retryable (pre-fix the request flag stayed set and every later start()
     * no-op'd behind the「already running」gate). A SUCCESSFUL start keeps the flag set:
     * start-after-stop is a one-shot contract — once the stop contract completed
     * ({@code stopped} latched), start() permanently no-ops; a restart requires a new wrapper
     * (and, per the offset registry lifecycle contract, a fresh binding generation).
     */
    public synchronized void start() {
        if (running.get() || startRequested.get()) {
            LOG.warn("Debezium engine is already running");
            return;
        }
        // R5 CON-02: mark the start request synchronously so a stop() arriving
        // inside the async startup window (worker lambda queued but not yet
        // past running.set(true)) does not early-exit and leak the engine.
        startRequested.set(true);
        // C3-014 ⑤: a fresh termination latch per start — the terminality signal is
        // defined from the moment the engine could run.
        this.terminationLatch = new CountDownLatch(1);

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

            // 1.2-C4-004 (plan 2498 Phase 1): run the engine on a dedicated per-engine carrier
            // instead of the platform-shared globalWorker pool. The engine body is an
            // unbounded long-running task (one CDC subtask must not permanently pin a shared
            // worker thread), and the dedicated naming makes engine threads observable.
            ExecutorService carrier = this.engineExecutor;
            if (carrier == null || carrier.isShutdown()) {
                carrier = Executors.newSingleThreadExecutor(new NamedThreadFactory(
                        "debezium-engine-" + config.getName(), true));
                this.engineExecutor = carrier;
            }
            final DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engineRef = engine;
            carrier.execute(() -> {
                try {
                    runEngineWorker(engineRef);
                } finally {
                    // C3-014 ⑤: the termination signal fires when the carrier task exits —
                    // independent of how the body ended (natural, failure, or worker skip).
                    CountDownLatch latch = terminationLatch;
                    if (latch != null) {
                        latch.countDown();
                    }
                }
            });

        } catch (Exception e) {
            // nothing will run: the engine is vacuously terminated
            CountDownLatch latch = terminationLatch;
            if (latch != null) {
                latch.countDown();
            }
            // MA-09 P3: a failed start resets the request flag so the next start() is a
            // retry, not a permanent no-op behind the「already running」gate
            startRequested.set(false);
            throw new NopException(DebeziumErrors.ERR_DEBEZIUM_ENGINE_START_FAILED)
                    .param("connector", config.getName())
                    .cause(e);
        }
    }

    /**
     * 处理 Debezium 事件并转换为 ChangeEvent
     *
     * <p>D12 §3.3.3 (MA-02 同 diff 配对 C3-002+C8-004, plan 2503 Phase 2): per-event barrier
     * over BOTH the convert and the consumer call. Pre-fix an exception here (CCE on a
     * malformed payload, NPE on a null key, numeric-width CCE, ...) escaped into
     * {@code engine.run()} and killed the CDC stream terminally with {@code isStarted()}
     * still lying (survival polarity / C3-002). Post-fix the failure enters the poison-event
     * endpoint — skip + WARN + counter + guarded {@code ConsumeFailure} dispatch (convert
     * stage attempt fixed 0 per the M-1 ruling) — and the engine moves on to the next event;
     * the endpoint acks the delivery with full evidence obligations (delivery polarity /
     * C8-004: no silent swallow-and-advance).
     */
    protected void handleDebeziumEvent(io.debezium.engine.ChangeEvent<byte[], byte[]> event) {
        try {
            ChangeEvent changeEvent = DebeziumEventConverter.convert(event);
            if (changeEvent != null) {
                changeEventConsumer.accept(changeEvent);
            }
        } catch (Throwable e) {
            reportPoisonEvent(event, e);
        }
    }

    /**
     * Poison-event endpoint, convert stage (D12 §3.3.3): skip (never rethrow into the engine
     * thread) + WARN + counter + guarded {@code ConsumeFailure} dispatch. The WARN/counter/
     * fan-out live in the source-owned endpoint; this fallback covers wrapper-standalone use
     * without a wired reporter — the missing channels are DECLARED, never silent (M-1).
     */
    private void reportPoisonEvent(io.debezium.engine.ChangeEvent<byte[], byte[]> event, Throwable error) {
        String topic = (event == null || event.destination() == null)
                ? config.getName() : event.destination();
        String location = config.getName() + "@" + topic;
        if (poisonEventReporter == null) {
            LOG.warn("nop.message.debezium.poison-event:topic={},location={},attempt=0 "
                    + "(no failure reporter wired; WARN-only visibility)", topic, location, error);
            return;
        }
        try {
            poisonEventReporter.accept(new ConsumeFailure(topic, location, event, 0,
                    asReportedException(error)));
        } catch (Throwable reporterError) {
            LOG.error("Debezium poison-event reporter threw: connector={}", config.getName(),
                    reporterError);
        }
    }

    /** {@link ConsumeFailure} carries an Exception (nop-api-core contract, zero-touch). */
    static Exception asReportedException(Throwable e) {
        if (e instanceof Exception) {
            return (Exception) e;
        }
        return new IllegalStateException("non-exception throwable: " + e, e);
    }

    /**
     * Engine-terminal failure notification: the static FAILURE_LISTENERS registry
     * (R5-CON-03, protocol unchanged) plus the instance-level listener wired by the owning
     * {@code DebeziumMessageSource} (MA-02 ③ — started-flag honesty).
     */
    private void notifyTerminalFailure(Throwable error) {
        notifyEngineFailure(config.getName(), error);
        Consumer<Throwable> listener = this.terminalFailureListener;
        if (listener == null) {
            return;
        }
        try {
            listener.accept(error);
        } catch (Throwable listenerError) {
            // same guard width as the static path (C3-009 ⑦): a listener failure must not
            // kill the engine worker thread
            LOG.error("Debezium engine terminal-failure listener threw for connector {}",
                    config.getName(), listenerError);
        }
    }

    /**
     * 引擎 worker 体（protected 供 C3-009 Error 注入回归测试直接驱动；生产调用点 =
     * {@code start()} 提交到专用引擎载体线程的 lambda）。
     *
     * <p>C3-009 ④ (plan 2496 Phase 4): the worker guard must cover Throwable — pre-fix
     * an Error escaping {@code engine.run()} skipped the Exception-only catch and the
     * terminal-failure notification ({@code notifyEngineFailure}) never fired, so the
     * registered FAILURE_LISTENERS never learned the CDC stream had died (nop-stream
     * side tasks waited on the latch forever). Post-fix the Error branch runs the same
     * notification path as Exception, then resets the running flag.
     *
     * <p>1.2-C4-004 (plan 2498 Phase 1): when the engine body exits (naturally or via
     * failure), the dedicated carrier is shut down so its daemon thread does not idle
     * forever; stop() additionally performs a bounded {@code awaitTermination}.
     */
    protected void runEngineWorker(
            DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engineRef) {
        // C3-014 ③ (C4-006, plan 2503 Phase 3): a worker lambda entering after the stop
        // contract began must not drive the engine — the queued task would otherwise open a
        // fresh engine AFTER stop completed (pre-fix ghost face). Orderly cancel: no failure
        // notification; the termination latch is counted by the carrier lambda's finally.
        if (stopRequested.get()) {
            LOG.warn("Debezium engine worker skipped after stop request: connector={}",
                    config.getName());
            return;
        }
        try {
            running.set(true);
            LOG.info("Starting Debezium engine: {}", config.getName());
            engineRef.run();
        } catch (Throwable e) {
            LOG.error("Debezium engine error: {}", config.getName(), e);
            // R5-CON-03: a terminal engine death must be observable by the
            // owning task, not only in the log.
            notifyTerminalFailure(e);
        } finally {
            running.set(false);
            shutdownEngineCarrier();
            // MA-09 P3 (plan 2558 裁定 F 同文件顺带): worker-death idempotent close — a
            // best-effort, repeat-tolerant close of the engine body's resources on the
            // natural/failure death paths (skipped when the stop contract already closed
            // the engine, so the close-attempt pins see exactly the stop-contract attempts)
            // 2558 集成修正：stopRequested 时由 stop 契约独占关闭（quiet close 与 STARTING_TASKS
            // 重试窗并发会在契约两次尝试之间夹入第三次 close，破坏 close-attempt 钉）——
            // death-path quiet close 仅承载「无 stop 契约时的资源回收」语义
            if (!engineClosedByStopContract.get() && !stopRequested.get()) {
                closeEngineQuietly(engineRef);
            }
            LOG.info("Debezium engine stopped: {}", config.getName());
        }
    }

    /**
     * MA-09 P3: single-attempt, fully-swallowed engine close for the worker-death path.
     * Idempotent by tolerance: every rejection (STARTING_TASKS IllegalStateException, I/O
     * failure, close on an already-closed engine) degrades to a WARN line — the terminal
     * stop contract keeps its own typed retry path, this seam only guarantees that nobody
     * dies BECAUSE of a close attempt on the death path.
     */
    private void closeEngineQuietly(
            DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engineRef) {
        if (engineRef == null) {
            return;
        }
        try {
            engineRef.close();
        } catch (Throwable closeError) {
            LOG.warn("Debezium engine quiet close rejected on the worker-death path "
                    + "(idempotent tolerance): connector={}", config.getName(), closeError);
        }
    }

    /**
     * 停止引擎
     *
     * <p>C3-014 (plan 2503 Phase 3): the stop contract is upgraded from「已发出」to「已终止」.
     * ① STARTING_TASKS close rejections are retried within a bounded budget and fail typed
     * on exhaustion (never swallowed); ④ a stuck engine is escalated to
     * {@code carrier.shutdownNow()}; ⑤ the engine-body termination latch is awaited and a
     * residual ghost is declared by explicit ERROR. The one-shot {@code stopped} latch
     * engages only AFTER the contract completed — a typed stop failure leaves the wrapper
     * retryable (a later stop() retries the close), closing the pre-fix permanent-no-op
     * ghost window. The 2498 carrier shape (dedicated carrier + bounded
     * {@code awaitTermination}) is kept verbatim (zero-regression pin).
     */
    public synchronized void stop() {
        // C3-014 ③: worker-entry guard signal — set BEFORE the gate so even a gated stop
        // (never-started wrapper) arms the worker skip.
        stopRequested.set(true);
        if (!startRequested.get() || stopped.get()) {
            return;
        }

        LOG.info("Stopping Debezium engine: {}", config.getName());

        // MA-03 (plan 2558 裁定 B): the stop contract body is wrapped so the offset-registry
        // paired release and the failure-listener stop fallback run in the SINGLE release
        // point — this method's finally — even when the termination verification failed or
        // the close contract failed typed (anti-overwrite takes priority over conservative
        // residency, registry-lifecycle-contract.md §3.5①). DebeziumMessageSource.stop()
        // deliberately does NOT release (no double counting).
        try {
            // C3-014 ①: engine.close() may be rejected with IllegalStateException while the
            // engine is still starting up (STARTING_TASKS window, debezium-embedded contract).
            // Bounded retry until the window clears; exhaustion fails typed — never swallowed.
            // The waits below still run (the engine may yet die); the typed failure surfaces
            // after them and does NOT latch the one-shot.
            NopException closeFailure = null;
            try {
                closeEngineWithRetry();
            } catch (NopException e) {
                closeFailure = e;
            }

            // 1.2-C4-004 (plan 2498 Phase 1): bounded termination budget on the dedicated
            // carrier. engine.close() is only a cooperative async signal; awaiting the carrier
            // with a bounded budget makes stop() wait for the engine body to actually finish,
            // with no unbounded wait and no silent timeout.
            ExecutorService carrier = this.engineExecutor;
            if (carrier != null) {
                carrier.shutdown();
                try {
                    if (!carrier.awaitTermination(STOP_TERMINATION_BUDGET_SECONDS, TimeUnit.SECONDS)) {
                        // MA-09 P3: quiesce join refinement — when the engine body already
                        // exited (its finally shut the carrier down), a stuck join is only a
                        // pool quiesce lag, not a live-engine hazard; the WARN wording
                        // distinguishes the two situations.
                        if (running.get()) {
                            LOG.warn("Debezium engine carrier did not terminate within {}s and the "
                                    + "engine body is still running: connector={}",
                                    STOP_TERMINATION_BUDGET_SECONDS, config.getName());
                        } else {
                            LOG.warn("Debezium engine carrier join exceeded {}s after the engine "
                                    + "body already exited (quiesce lag, no live-engine hazard): "
                                    + "connector={}", STOP_TERMINATION_BUDGET_SECONDS, config.getName());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.error("Interrupted while awaiting Debezium engine carrier termination: connector={}",
                            config.getName(), e);
                }
            }

            // C3-014 ⑤: bounded wait on the engine-body termination latch.
            if (!awaitEngineTermination(terminationWaitMillis, TimeUnit.MILLISECONDS)) {
                LOG.warn("Debezium engine body did not terminate within {}ms after close; escalating "
                        + "to carrier shutdownNow: connector={}", terminationWaitMillis, config.getName());
                // C3-014 ④: stuck-engine escalation (reserved by plan 2498 errata 4)
                ExecutorService carrierNow = this.engineExecutor;
                if (carrierNow != null) {
                    carrierNow.shutdownNow();
                }
                if (!awaitEngineTermination(escalationWaitMillis, TimeUnit.MILLISECONDS)) {
                    // rule 24: the residual ghost is DECLARED, never silent
                    LOG.error("nop.message.debezium.engine-ghost-risk:connector={} - engine body did not "
                            + "terminate within the stop budget; a ghost engine (live connector thread, "
                            + "continuing offset writes) may persist until JVM exit", config.getName());
                }
            }

            running.set(false);
            if (closeFailure != null) {
                // C3-014 ①: typed stop failure — NOT latched, so a later stop() may retry the
                // close (retry coordination with the one-shot guard)
                throw closeFailure;
            }
            stopped.set(true);
        } finally {
            // MA-03 (plan 2558 裁定 B): stop-contract paired release of the offset registry
            // bucket — the SINGLE release point (finally: termination-verification failure
            // and typed close failure still release; a stuck ghost's old-generation bucket
            // is split away so its writes land in an orphan map, never overwriting a new
            // generation's recovery offsets).
            releaseOffsetRegistryBucket();
            // MA-10 (plan 2558 裁定 F): stop-path unregister fallback — the wrapper cannot
            // see the external owner's listener handle, so the stop clears this connector's
            // slot by name (owner-side later unregister is an idempotent no-op).
            FAILURE_LISTENERS.remove(config.getName());
        }
    }

    /**
     * MA-03 paired release single point: resolves the connector identity of this wrapper's
     * offset store (the registry key) and releases its registry bucket. A file-based or
     * custom store without a registry entry releases nothing (no-op).
     */
    private void releaseOffsetRegistryBucket() {
        OffsetBackingStore store = this.offsetStore;
        if (store instanceof NopStreamOffsetBackingStore) {
            NopStreamOffsetBackingStore.releaseConnector(
                    ((NopStreamOffsetBackingStore) store).getConnectorName());
        } else {
            // non-registry offset storage: releasing by the connector name is a no-op
            // unless a bucket for that name exists (then it is the generation's bucket)
            NopStreamOffsetBackingStore.releaseConnector(config.getName());
        }
    }

    /**
     * C3-014 ①: close with bounded retry over the STARTING_TASKS rejection window. Budget
     * exhaustion fails typed ({@code ERR_DEBEZIUM_ENGINE_STOP_FAILED}) — the pre-fix shape
     * (single attempt, Exception swallowed into a log line, one-shot latch engaged) is the
     * audited permanent-ghost mechanism. Package-private as a focused test seam (same
     * visibility discipline as {@link #getEngineExecutor()}).
     */
    void closeEngineWithRetry() {
        closeEngineWithRetry(this.engine);
    }

    void closeEngineWithRetry(DebeziumEngine<io.debezium.engine.ChangeEvent<byte[], byte[]>> engineRef) {
        if (engineRef == null) {
            return;
        }
        long deadline = System.currentTimeMillis() + STARTING_TASKS_RETRY_BUDGET_MILLIS;
        while (true) {
            try {
                engineRef.close();
                // MA-09: the stop contract closed the engine — the worker-death quiet close
                // must not fire a second attempt on the same engine (idempotency + the
                // existing close-attempt pins see exactly the stop-contract attempts)
                engineClosedByStopContract.set(true);
                return;
            } catch (IllegalStateException e) {
                if (System.currentTimeMillis() >= deadline) {
                    throw new NopException(DebeziumErrors.ERR_DEBEZIUM_ENGINE_STOP_FAILED)
                            .param("connector", config.getName())
                            .param("detail", "engine.close() rejected with IllegalStateException "
                                    + "past the STARTING_TASKS retry budget; the engine may be a ghost")
                            .cause(e);
                }
                LOG.warn("Debezium engine close rejected while starting up (STARTING_TASKS window); "
                        + "retrying within budget: connector={}", config.getName());
                try {
                    Thread.sleep(STARTING_TASKS_RETRY_SLICE_MILLIS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new NopException(DebeziumErrors.ERR_DEBEZIUM_ENGINE_STOP_FAILED)
                            .param("connector", config.getName())
                            .param("detail", "interrupted while retrying engine.close() during "
                                    + "the STARTING_TASKS window")
                            .cause(ie);
                }
            } catch (IOException e) {
                // I/O failure while closing = a real stop-contract failure: typed, never
                // swallowed into a log line (rule 24)
                throw new NopException(DebeziumErrors.ERR_DEBEZIUM_ENGINE_STOP_FAILED)
                        .param("connector", config.getName())
                        .param("detail", "engine.close() failed with IOException")
                        .cause(e);
            }
        }
    }

    /**
     * C3-014 ⑤: whether the engine body has terminated. A never-started wrapper is vacuously
     * terminated. This is the queryable terminality signal the nop-stream stop faces poll
     * after the stop chain returns.
     */
    public boolean isTerminated() {
        CountDownLatch latch = this.terminationLatch;
        return latch == null || latch.getCount() == 0;
    }

    /** C3-014 ⑤: bounded wait for the engine-body termination signal. */
    public boolean awaitEngineTermination(long timeout, TimeUnit unit) {
        CountDownLatch latch = this.terminationLatch;
        if (latch == null) {
            return true;
        }
        try {
            return latch.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return latch.getCount() == 0;
        }
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Dedicated engine carrier (visible for wiring assertions, plan 2498 Phase 1
     * rule 23): the engine body must actually run on this carrier's thread, not on a
     * shared pool. Package-private on purpose — production callers have no business
     * with the carrier's lifecycle.
     */
    ExecutorService getEngineExecutor() {
        return engineExecutor;
    }

    /**
     * Releases the dedicated carrier after the engine body has exited. {@code shutdown()}
     * (not {@code shutdownNow()}): the shutdownNow escalation on a stuck engine belongs to
     * 1.2-C3-014 (R1-08); this batch only owns the carrier itself.
     */
    private void shutdownEngineCarrier() {
        ExecutorService carrier = this.engineExecutor;
        if (carrier != null) {
            carrier.shutdown();
        }
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
                notifyTerminalFailure(error != null ? error : new DebeziumEngineFailure(message));
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
