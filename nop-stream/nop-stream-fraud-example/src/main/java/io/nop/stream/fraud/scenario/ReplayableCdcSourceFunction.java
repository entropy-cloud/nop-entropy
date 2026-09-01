/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.nop.api.core.util.ICancellable;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.ChangeEventMetadata;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;
import io.nop.stream.connector.debezium.DebeziumCdcSourceFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * S1 scenario CDC source: a deterministic replayable CDC event source built on the
 * production {@link DebeziumCdcSourceFunction} (composite-scenario-design.md §3.4.1 D1).
 *
 * <p>Only the message engine is a test double — injected through the documented
 * {@code createMessageSource} factory seam. Everything else is the production code
 * path: the replay position is tracked in {@link NopStreamOffsetBackingStore},
 * checkpointed into operator state under {@value DebeziumCdcSourceFunction#CDC_OFFSETS_KEY}
 * by the production {@code snapshotState}, and restored by the production
 * {@code initializeState}, so a recovered run resumes from the checkpointed replay
 * position (exactly-once source semantics, consistency REPLAYABLE).
 *
 * <p>Swapping this bean for a real {@code DebeziumCdcSourceFunction} (engine-backed)
 * is the "change one bean to get real CDC" upgrade path: the record shape
 * ({@link ChangeEvent}) and the source base class are identical.
 */
public class ReplayableCdcSourceFunction extends DebeziumCdcSourceFunction {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(ReplayableCdcSourceFunction.class);

    /**
     * Offset-store partition key holding the next-to-replay index (Debezium
     * next-to-consume semantics: the stored value is the index of the next event a
     * fresh engine would read).
     */
    static final ByteBuffer REPLAY_INDEX_KEY =
            ByteBuffer.wrap("fraud-s1-replay-idx".getBytes(StandardCharsets.UTF_8));

    private final List<Map<String, Object>> eventSpecs;
    private final long emitDelayMs;
    /**
     * Park time after the last event before signalling completion: gives the
     * engine's periodic checkpoint scheduler time to durably commit the rows
     * fired by the final watermarks (bounded-run determinism for scenario tests).
     */
    private final long finishLingerMs;

    /**
     * @param config     Debezium config; {@code name} must be set (connector-name
     *                   fail-fast is inherited)
     * @param eventSpecs the deterministic event fixture: each spec map holds
     *                   {@code op} ("c"/"u"/"d"), {@code ts} (event-time millis) and
     *                   {@code after} (column map with transactionId/userId/amount/
     *                   city/eventType). Serializable so subtask copies survive.
     * @param emitDelayMs pacing between emitted events (0 for maximal speed); lets
     *                   periodic checkpoints interleave with emission
     */
    public ReplayableCdcSourceFunction(DebeziumConfig config,
                                       List<Map<String, Object>> eventSpecs, long emitDelayMs) {
        this(config, eventSpecs, emitDelayMs, 0L);
    }

    public ReplayableCdcSourceFunction(DebeziumConfig config,
                                       List<Map<String, Object>> eventSpecs,
                                       long emitDelayMs, long finishLingerMs) {
        super(config);
        this.eventSpecs = new ArrayList<>(eventSpecs);
        this.emitDelayMs = emitDelayMs;
        this.finishLingerMs = finishLingerMs;
    }

    public List<Map<String, Object>> getEventSpecs() {
        return Collections.unmodifiableList(eventSpecs);
    }

    @Override
    protected DebeziumMessageSource createMessageSource(DebeziumConfig config,
                                                        NopStreamOffsetBackingStore offsetStore) {
        List<ChangeEvent> events = new ArrayList<>(eventSpecs.size());
        for (Map<String, Object> spec : eventSpecs) {
            events.add(CdcEventFixtures.toChangeEvent(spec));
        }
        return new ReplayEngine(config, offsetStore, events);
    }

    /**
     * Deterministic replay engine: emits the fixture events from the offset-store
     * replay position, advancing the offset after every event, then signals source
     * completion by cancelling the outer source function (bounded run semantics).
     */
    private final class ReplayEngine extends DebeziumMessageSource {

        private final NopStreamOffsetBackingStore offsetStore;
        private final List<ChangeEvent> events;
        private volatile boolean cancelled;

        ReplayEngine(DebeziumConfig config, NopStreamOffsetBackingStore offsetStore,
                     List<ChangeEvent> events) {
            super(config, offsetStore);
            this.offsetStore = offsetStore;
            this.events = events;
        }

        @Override
        public ICancellable subscribe(Consumer<ChangeEvent> action) {
            Thread emitter = new Thread(() -> runReplay(action), "fraud-s1-replay-cdc");
            emitter.setDaemon(true);
            emitter.start();
            return new ReplaySubscription();
        }

        private final class ReplaySubscription implements ICancellable {
            private final List<Consumer<String>> onCancelTasks = new ArrayList<>();

            @Override
            public boolean isCancelled() {
                return cancelled;
            }

            @Override
            public String getCancelReason() {
                return cancelled ? ICancellable.CANCEL_REASON_STOP : null;
            }

            @Override
            public void cancel(String reason) {
                ReplayEngine.this.cancelled = true;
                for (Consumer<String> task : onCancelTasks) {
                    task.accept(reason);
                }
            }

            @Override
            public void appendOnCancel(Consumer<String> task) {
                onCancelTasks.add(task);
            }

            @Override
            public void removeOnCancel(Consumer<String> task) {
                onCancelTasks.remove(task);
            }
        }

        private void runReplay(Consumer<ChangeEvent> action) {
            try {
                long resumeIndex = readReplayIndex();
                for (int i = (int) resumeIndex; i < events.size(); i++) {
                    if (cancelled) {
                        break;
                    }
                    action.accept(events.get(i));
                    writeReplayIndex(i + 1L);
                    if (emitDelayMs > 0) {
                        Thread.sleep(emitDelayMs);
                    }
                }
                if (finishLingerMs > 0 && !cancelled) {
                    Thread.sleep(finishLingerMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.error("CDC replay engine failed; cancelling source so the job fails loudly", e);
            } finally {
                // Bounded source completion: the production run() loop only exits on
                // cancel/drain, so a completed replay must cancel its own source.
                ReplayableCdcSourceFunction.this.cancel();
            }
        }

        private long readReplayIndex() {
            if (offsetStore == null) {
                return 0L;
            }
            ByteBuffer value = offsetStore.getOffsets().get(REPLAY_INDEX_KEY);
            if (value == null) {
                return 0L;
            }
            String text = StandardCharsets.UTF_8.decode(
                    ByteBuffer.wrap(value.array(), value.position(), value.remaining())).toString();
            return Long.parseLong(text.trim());
        }

        private void writeReplayIndex(long nextIndex) {
            if (offsetStore == null) {
                return;
            }
            byte[] bytes = String.valueOf(nextIndex).getBytes(StandardCharsets.UTF_8);
            offsetStore.set(Collections.singletonMap(REPLAY_INDEX_KEY, ByteBuffer.wrap(bytes)), null);
        }

        @Override
        public synchronized void stop() {
            cancelled = true;
        }
    }

    /**
     * Fixture helpers shared by the source and the scenario tests: converts a
     * serializable event spec map into a {@link ChangeEvent} with the same record
     * shape a real Debezium engine produces.
     */
    public static final class CdcEventFixtures {

        private CdcEventFixtures() {
        }

        public static Map<String, Object> spec(String op, long ts, String transactionId, String userId,
                                               String amount, String city, String eventType) {
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("transactionId", transactionId);
            after.put("userId", userId);
            after.put("amount", amount);
            after.put("city", city);
            after.put("eventType", eventType);

            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("op", op);
            spec.put("ts", ts);
            spec.put("after", after);
            return spec;
        }

        public static ChangeEvent toChangeEvent(Map<String, Object> spec) {
            String op = String.valueOf(spec.get("op"));
            long ts = ((Number) spec.get("ts")).longValue();

            @SuppressWarnings("unchecked")
            Map<String, Object> after = (Map<String, Object>) spec.get("after");

            ChangeEventMetadata metadata = new ChangeEventMetadata(
                    null, null, "fraud", null, "transactions", "data");
            Map<String, Object> key = new LinkedHashMap<>();
            key.put("transactionId", after.get("transactionId"));
            return new ChangeEvent(metadata, op, null, after, key, ts);
        }
    }
}
