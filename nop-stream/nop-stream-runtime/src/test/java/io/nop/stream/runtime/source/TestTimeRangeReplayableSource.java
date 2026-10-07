/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.source;

import io.nop.stream.core.common.eventtime.TimestampAssigner;
import io.nop.stream.core.common.functions.source.ParallelismCheckable;
import io.nop.stream.core.common.functions.source.ReplayableSourceFunction;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.connector.ConnectivityCheckable;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI10 (plan 2334): unit coverage for the {@link TimeRangeReplayableSource} wrapper —
 * range filtering on both emission branches, typed rejections, offset delegation and
 * capability-face forwarding (the deployment-time guards must survive wrapping).
 */
class TestTimeRangeReplayableSource {

    static final class Ev implements Serializable {
        private static final long serialVersionUID = 1L;

        final String key;
        final long ts;

        Ev(String key, long ts) {
            this.key = key;
            this.ts = ts;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Ev)) {
                return false;
            }
            Ev ev = (Ev) o;
            return ts == ev.ts && java.util.Objects.equals(key, ev.key);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(key, ts);
        }
    }

    private static final TimestampAssigner<Ev> EV_TS = (e, recTs) -> e.ts;

    /** Records collectWithTimestamp emissions; bare collects are tracked separately. */
    private static final class RecordingContext implements SourceFunction.SourceContext<Ev> {
        final List<Ev> timestamped = new ArrayList<>();
        final List<Long> timestamps = new ArrayList<>();
        final List<Ev> bareCollects = new ArrayList<>();
        final List<Long> watermarks = new ArrayList<>();

        @Override
        public void collect(Ev element) {
            bareCollects.add(element);
        }

        @Override
        public void collectWithTimestamp(Ev element, long timestamp) {
            timestamped.add(element);
            timestamps.add(timestamp);
        }

        @Override
        public void emitWatermark(long mark) {
            watermarks.add(mark);
        }

        @Override
        public void markAsTemporarilyIdle() {
        }

        @Override
        public long getProcessingTime() {
            return 0L;
        }
    }

    /**
     * The bounded RECALCULATE recipe depends on watermarks surviving the wrapper: the
     * inner bounded source emits the terminal watermark that fires downstream windows,
     * and the wrapper must forward it untouched (never swallowed, never filtered).
     */
    @Test
    void forwardsTerminalWatermarkFromInnerSource() throws Exception {
        SourceFunction<Ev> inner = new ReplayableSourceFunction<Ev>() {
            private static final long serialVersionUID = 1L;
            private long offset;

            @Override
            public void seek(long offset) {
                this.offset = offset;
            }

            @Override
            public long getCurrentOffset() {
                return offset;
            }

            @Override
            public void run(SourceContext<Ev> ctx) {
                ctx.collectWithTimestamp(new Ev("a", 1000), 1000L);
                ctx.emitWatermark(60_000L);
            }

            @Override
            public void cancel() {
            }
        };
        TimeRangeReplayableSource<Ev> source = new TimeRangeReplayableSource<>(inner, EV_TS, 0L, 5000L);

        RecordingContext ctx = new RecordingContext();
        source.run(ctx);

        assertEquals(List.of(60_000L), ctx.watermarks,
                "the terminal watermark must pass through the wrapper untouched");
    }

    @Test
    void filtersCollectBranchByEventTimeRange() throws Exception {
        List<Ev> data = Arrays.asList(new Ev("a", 1000), new Ev("a", 2000), new Ev("b", 3000),
                new Ev("b", 4000), new Ev("c", 5000));
        TimeRangeReplayableSource<Ev> source = new TimeRangeReplayableSource<>(
                new CollectionReplayableSource<>(data), EV_TS, 2000L, 4000L);

        RecordingContext ctx = new RecordingContext();
        source.run(ctx);

        assertEquals(Arrays.asList(new Ev("a", 2000), new Ev("b", 3000)), ctx.timestamped,
                "only records with ts in [2000, 4000) survive");
        assertEquals(Arrays.asList(2000L, 3000L), ctx.timestamps,
                "in-range records from the bare collect branch must carry the extracted event time");
        assertTrue(ctx.bareCollects.isEmpty(),
                "in-range records must be re-emitted via collectWithTimestamp, not bare collect");
    }

    @Test
    void filtersCollectWithTimestampBranchPreservingOriginalTimestamp() throws Exception {
        SourceFunction<Ev> rawSource = new ReplayableSourceFunction<Ev>() {
            private static final long serialVersionUID = 1L;
            private long offset;

            @Override
            public void seek(long offset) {
                this.offset = offset;
            }

            @Override
            public long getCurrentOffset() {
                return offset;
            }

            @Override
            public void run(SourceContext<Ev> ctx) {
                ctx.collectWithTimestamp(new Ev("a", 1000), 1000L);
                ctx.collectWithTimestamp(new Ev("a", 2000), 2000L);
                ctx.collectWithTimestamp(new Ev("b", 4000), 4000L);
            }

            @Override
            public void cancel() {
            }
        };
        TimeRangeReplayableSource<Ev> source = new TimeRangeReplayableSource<>(rawSource, EV_TS, 2000L, 4000L);

        RecordingContext ctx = new RecordingContext();
        source.run(ctx);

        assertEquals(List.of(new Ev("a", 2000)), ctx.timestamped,
                "collectWithTimestamp records outside [2000, 4000) are dropped");
        assertEquals(List.of(2000L), ctx.timestamps,
                "the original event timestamp must pass through unchanged");
    }

    @Test
    void rejectsNonReplayableDelegateTyped() {
        SourceFunction<Ev> plain = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<Ev> ctx) {
            }

            @Override
            public void cancel() {
            }
        };
        StreamException ex = assertThrows(StreamException.class,
                () -> new TimeRangeReplayableSource<>(plain, EV_TS, 0L, 1000L));
        assertEquals(ERR_STREAM_INVALID_ARG.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex).contains(plain.getClass().getName()),
                "rejection must name the offending source type: " + ex);
    }

    @Test
    void rejectsEmptyOrInvertedRangeTyped() {
        CollectionReplayableSource<Ev> inner = new CollectionReplayableSource<>(List.of(new Ev("a", 1)));
        assertEquals(ERR_STREAM_INVALID_ARG.getErrorCode(),
                assertThrows(StreamException.class, () -> new TimeRangeReplayableSource<>(inner, EV_TS, 5L, 5L))
                        .getErrorCode());
        assertEquals(ERR_STREAM_INVALID_ARG.getErrorCode(),
                assertThrows(StreamException.class, () -> new TimeRangeReplayableSource<>(inner, EV_TS, 10L, 5L))
                        .getErrorCode());
    }

    @Test
    void forwardsSeekAndOffsetToDelegate() {
        CollectionReplayableSource<Ev> inner = new CollectionReplayableSource<>(
                Arrays.asList(new Ev("a", 1), new Ev("b", 2), new Ev("c", 3)));
        TimeRangeReplayableSource<Ev> source = new TimeRangeReplayableSource<>(inner, EV_TS, 0L, 10L);

        assertEquals(0L, source.getCurrentOffset());
        source.seek(2L);
        assertEquals(2L, source.getCurrentOffset(), "offset semantics stay with the delegate");
        assertEquals(2L, inner.getCurrentOffset());
    }

    @Test
    void forwardsSnapshotAndInitializeStateToDelegate() throws Exception {
        StatefulDelegate delegate = new StatefulDelegate();
        TimeRangeReplayableSource<Ev> source = new TimeRangeReplayableSource<>(delegate, EV_TS, 0L, 10L);

        OperatorSnapshotResult result = source.snapshotState(7L);
        assertNotNull(result, "snapshot forwarding must return the delegate's snapshot result");
        assertEquals(1, delegate.snapshotCalls);

        source.initializeState(null);
        assertEquals(1, delegate.initializeCalls);
    }

    /**
     * Capability faces must survive wrapping: consistency declaration forwards, and a
     * checkable delegate's parallelism guard + connectivity probe are still reached
     * through the wrapper (plan Major-3 pin).
     */
    @Test
    void forwardsCapabilityFacesOfCheckableDelegate() throws Exception {
        CheckableDelegate delegate = new CheckableDelegate();
        TimeRangeReplayableSource<Ev> source = new TimeRangeReplayableSource<>(delegate, EV_TS, 0L, 10L);

        assertEquals(SourceConsistencyCapability.REPLAYABLE, source.getSourceConsistency(),
                "the wrapper must not downgrade the delegate's consistency declaration");

        StreamException parEx = assertThrows(StreamException.class, () -> source.validateParallelism(2),
                "the delegate's single-writer guard must still trigger through the wrapper");
        assertEquals("parallelism", String.valueOf(parEx.getParams().get("argName")),
                "the rejection must come from the delegate's own guard");

        source.checkConnection();
        assertEquals(1, delegate.connectionChecks, "the connectivity probe must reach the delegate");
    }

    /**
     * A delegate with no capability contract: no parallelism constraint (no-op, same as
     * unwrapped) and NO fake connectivity pass — checkConnection fails typed.
     */
    @Test
    void nonCheckableDelegateHasNoFakeProbe() {
        CollectionReplayableSource<Ev> inner = new CollectionReplayableSource<>(List.of(new Ev("a", 1)));
        TimeRangeReplayableSource<Ev> source = new TimeRangeReplayableSource<>(inner, EV_TS, 0L, 10L);

        assertEquals(SourceConsistencyCapability.BEST_EFFORT, source.getSourceConsistency(),
                "the wrapper forwards the delegate's own declaration verbatim");

        source.validateParallelism(8);
        source.validateParallelism(1);

        StreamException ex = assertThrows(StreamException.class, source::checkConnection,
                "a delegate without a probe contract must fail typed, never fake a pass");
        assertEquals(ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex).contains(CollectionReplayableSource.class.getSimpleName()),
                "error must name the delegate type: " + ex);
    }

    static class StatefulDelegate implements ReplayableSourceFunction<Ev> {
        private static final long serialVersionUID = 1L;

        int snapshotCalls;
        int initializeCalls;
        private long offset;

        @Override
        public void seek(long offset) {
            this.offset = offset;
        }

        @Override
        public long getCurrentOffset() {
            return offset;
        }

        @Override
        public OperatorSnapshotResult snapshotState(long checkpointId) {
            snapshotCalls++;
            return OperatorSnapshotResult.empty();
        }

        @Override
        public void initializeState(TaskStateSnapshot state) {
            initializeCalls++;
        }

        @Override
        public void run(SourceContext<Ev> ctx) {
        }

        @Override
        public void cancel() {
        }
    }

    static final class CheckableDelegate extends StatefulDelegate
            implements ParallelismCheckable, ConnectivityCheckable {
        private static final long serialVersionUID = 1L;

        int connectionChecks;

        @Override
        public SourceConsistencyCapability getSourceConsistency() {
            return SourceConsistencyCapability.REPLAYABLE;
        }

        @Override
        public void validateParallelism(int parallelism) {
            if (parallelism > 1) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param("argName", "parallelism")
                        .param("detail", "single-writer delegate rejects parallelism=" + parallelism);
            }
        }

        @Override
        public void checkConnection() {
            connectionChecks++;
        }
    }
}
