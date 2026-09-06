/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.fraud.scenario.ReplayableCdcSourceFunction.CdcEventFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.nop.stream.fraud.scenario.ReplayableCdcSourceFunction.CDC_OFFSETS_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the S1 replayable CDC source: bounded completion semantics,
 * offset checkpoint/resume through the PRODUCTION offset-store path, and
 * connector-name fail-fast inheritance.
 */
public class TestReplayableCdcSourceFunction {

    private static final String CONNECTOR = "test-replay-cdc";

    @AfterEach
    public void clearConnectorRegistry() {
        NopStreamOffsetBackingStore.clearConnector(CONNECTOR);
    }

    private static List<Map<String, Object>> fixture(int count) {
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(CdcEventFixtures.spec("c", 1000L + i, "tx-" + i, "user-" + i,
                    "100", "NYC", "PURCHASE"));
        }
        return events;
    }

    private static ReplayableCdcSourceFunction newSource(List<Map<String, Object>> specs) {
        DebeziumConfig config = new DebeziumConfig();
        config.setName(CONNECTOR);
        config.setConnectorType("mysql");
        return new ReplayableCdcSourceFunction(config, specs, 5L, 50L);
    }

    private static final class CollectingContext implements SourceFunction.SourceContext<ChangeEvent> {
        final List<ChangeEvent> collected = new ArrayList<>();

        @Override
        public void collect(ChangeEvent element) {
            collected.add(element);
        }

        @Override
        public void collectWithTimestamp(ChangeEvent element, long timestamp) {
            collected.add(element);
        }

        @Override
        public void emitWatermark(long mark) {
        }

        @Override
        public void markAsTemporarilyIdle() {
        }

        @Override
        public long getProcessingTime() {
            return System.currentTimeMillis();
        }
    }

    /**
     * Runs the source until it completes (the replay engine self-cancels the
     * bounded source) and returns the collected events.
     */
    private static List<ChangeEvent> runToCompletion(ReplayableCdcSourceFunction source) throws Exception {
        CollectingContext ctx = new CollectingContext();
        source.initializeState(null);
        source.run(ctx);
        return ctx.collected;
    }

    @Test
    public void emitsAllEventsThenCompletesBoundedRun() throws Exception {
        List<Map<String, Object>> specs = fixture(5);
        List<ChangeEvent> events = runToCompletion(newSource(specs));
        assertEquals(5, events.size());
        assertEquals("tx-0", events.get(0).getAfter().get("transactionId"));
        assertEquals("tx-4", events.get(4).getAfter().get("transactionId"));
    }

    @Test
    public void offsetCheckpointAndResumeSkipConsumedPrefix() throws Exception {
        List<Map<String, Object>> specs = fixture(6);
        ReplayableCdcSourceFunction first = newSource(specs);
        first.initializeState(null);
        CollectingContext firstCtx = new CollectingContext();

        // run on a watcher thread and stop after 3 events (simulated interruption)
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread runner = new Thread(() -> {
            try {
                first.run(firstCtx);
            } catch (Throwable t) {
                error.set(t);
            }
        });
        runner.start();
        while (firstCtx.collected.size() < 3 && runner.isAlive()) {
            Thread.sleep(5);
        }
        first.cancel();
        runner.join(3000);
        assertNull(error.get(), "interrupted run must not throw");
        // the cancel races the emitter's 5ms pacing: capture whatever was
        // actually consumed (>= 3) and derive the resume expectation from it.
        // consumed == specs.size() 也是合法竞态结果——cancel 无法撤销已 accept 的
        // 事件（cancel 检查在循环顶部，最后一条 accept 后才生效），此时 resumed
        // run 期望 0 条剩余。此前断言 consumed < specs.size() 会把这条合法路径
        // 判为失败（负载下 cancel 恰落在最后一条 5ms pacing 期间时随机触发）。
        int consumed = firstCtx.collected.size();
        assertTrue(consumed >= 3 && consumed <= specs.size(),
                "interrupted run must consume a prefix, got " + consumed);

        // snapshot the offsets via the PRODUCTION checkpoint path
        OperatorSnapshotResult snapshot = first.snapshotState(1L);
        assertTrue(snapshot.getOperatorStates().containsKey(CDC_OFFSETS_KEY));

        // restore into a fresh source: the replay engine resumes from the offset
        TaskStateSnapshot state = new TaskStateSnapshot(null, 1L);
        state.putOperatorState(CDC_OFFSETS_KEY, snapshot.getOperatorState(CDC_OFFSETS_KEY));
        ReplayableCdcSourceFunction second = newSource(specs);
        NopStreamOffsetBackingStore.clearConnector(CONNECTOR);
        second.initializeState(state);
        CollectingContext secondCtx = new CollectingContext();
        second.run(secondCtx);
        assertEquals(specs.size() - consumed, secondCtx.collected.size(),
                "resumed run must emit exactly the remaining events (offset resume)");
        if (consumed < specs.size()) {
            assertEquals("tx-" + consumed, secondCtx.collected.get(0).getAfter().get("transactionId"));
        }
        if (!secondCtx.collected.isEmpty()) {
            assertEquals("tx-" + (specs.size() - 1),
                    secondCtx.collected.get(secondCtx.collected.size() - 1).getAfter().get("transactionId"));
        }
    }

    @Test
    public void freshStartNeverInheritsStaleRegistryOffsets() throws Exception {
        // seed a stale registry offset for the connector
        ReplayableCdcSourceFunction stale = newSource(fixture(4));
        runToCompletion(stale);
        // AR-03: a fresh first run must clear and start from the beginning
        List<ChangeEvent> again = runToCompletion(newSource(fixture(4)));
        assertEquals(4, again.size(), "fresh run must replay from index 0");
        assertEquals("tx-0", again.get(0).getAfter().get("transactionId"));
    }

    @Test
    public void unnamedConnectorFailsFast() {
        DebeziumConfig config = new DebeziumConfig();
        ReplayableCdcSourceFunction source =
                new ReplayableCdcSourceFunction(config, fixture(2), 0L, 0L);
        io.nop.stream.core.exceptions.StreamException ex = assertThrows(
                io.nop.stream.core.exceptions.StreamException.class,
                () -> source.initializeState(null));
        assertTrue(ex.getMessage().contains("Debezium connector name is required"));
    }

    @Test
    public void declaresReplayableConsistency() {
        assertEquals(io.nop.stream.core.common.functions.source.SourceConsistencyCapability.REPLAYABLE,
                newSource(fixture(1)).getSourceConsistency());
    }
}
