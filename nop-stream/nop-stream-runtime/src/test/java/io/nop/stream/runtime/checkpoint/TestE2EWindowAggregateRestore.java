package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.runtime.execution.CheckpointExecutorFactoryImpl;
import io.nop.stream.runtime.operators.windowing.WindowOperatorFactoryImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P1-01 end-to-end proof (Decision: 方案 1 = live function reuse) — a window
 * aggregate/reduce job with a CAPTURING aggregate function (no no-arg
 * constructor) runs with checkpoints, is restarted at JOB level against the same
 * checkpoint storage, restores its in-flight window accumulation, and continues
 * processing to produce the combined result. Runs on BOTH the Memory and the
 * RocksDB state backend.
 *
 * <p>Restart semantics (per plan Phase 3 ruling): "重启" = job-level restart
 * (second {@code execute()} against the same storage) — NOT a supervision
 * region restart, which would rebuild the chain via deepCopy and silently fall
 * back to the Memory backend for a RocksDB-configured job.
 *
 * <p>Timing contract (engine semantics): the job-end MAX_WATERMARK fires every
 * window and the terminating final checkpoint captures POST-fire state, so the
 * restore source is the last PERIODIC checkpoint that completed BEFORE the
 * fire. The source therefore bursts all values immediately (full accumulation
 * 55 in the window state) and then parks ~800ms so several periodic checkpoints
 * complete with the PRE-fire state. The window fires at job end of run 2 with
 * the RESTORED + new accumulation (a silently-empty restore would fire 100).
 *
 * <p>Checkpointing must be enabled end-to-end: the env-based execute path
 * requires the checkpoint executor factory to be registered (the runtime SPI is
 * not auto-discovered), otherwise the job silently runs without checkpoints.
 */
class TestE2EWindowAggregateRestore {

    @TempDir
    Path tempDir;

    private static final String JOB_NAME = "window-aggregate-restore-e2e";
    private static final long WINDOW_SIZE = 60000L;

    @BeforeAll
    static void registerCheckpointExecutorFactory() {
        StreamExecutionEnvironment.setCheckpointExecutorFactory(new CheckpointExecutorFactoryImpl());
    }

    @AfterAll
    static void unregisterCheckpointExecutorFactory() {
        StreamExecutionEnvironment.setCheckpointExecutorFactory(null);
    }

    static final class IntEvent implements Serializable {
        private static final long serialVersionUID = 1L;

        final String key;
        final long ts;
        final int value;

        IntEvent(String key, long ts, int value) {
            this.key = key;
            this.ts = ts;
            this.value = value;
        }
    }

    /**
     * Capturing aggregate function: the anonymous class holds a captured
     * {@code marker} that the class-name reflection path can never reproduce
     * (and, being an anonymous class, has no no-arg constructor — the
     * pre-fix restore threw NoSuchMethodException). {@code getResult} adds the
     * marker so the assertions also pin live-instance reuse.
     */
    private static AggregateFunction<IntEvent, long[], Long> capturingSumAggregate(long marker) {
        return new AggregateFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public long[] createAccumulator() {
                return new long[]{0};
            }

            @Override
            public long[] add(IntEvent value, long[] accumulator) {
                accumulator[0] += value.value;
                return accumulator;
            }

            @Override
            public Long getResult(long[] accumulator) {
                return accumulator[0] + marker;
            }

            @Override
            public long[] merge(long[] a, long[] b) {
                a[0] += b[0];
                return a;
            }
        };
    }

    private void configureCheckpoints(StreamExecutionEnvironment env, String storagePath,
                                      IStateBackend backend) {
        CheckpointConfig cfg = env.getCheckpointConfig();
        cfg.setCheckpointInterval(200L);
        cfg.setCheckpointTimeout(5000L);
        cfg.setMinPause(100L);
        cfg.setStorageProperty("path", storagePath);
        cfg.setStateBackend(backend);
    }

    /**
     * Bounded source that bursts all values immediately (so every periodic
     * checkpoint captures the FULL pre-fire accumulation) and then parks so
     * multiple periodic checkpoints complete before the job-end MAX_WATERMARK
     * fires the window.
     */
    private static SourceFunction<IntEvent> burstSource(int startValue, int count) {
        return new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<IntEvent> ctx) {
                for (int i = 0; i < count; i++) {
                    int value = startValue + i;
                    long ts = (value - startValue + 1) * 1000L;
                    ctx.collectWithTimestamp(new IntEvent("k1", ts, value), ts);
                }
                try {
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public void cancel() {
            }
        };
    }

    private List<Long> runAggregateJob(Path storageDir, IStateBackend backend,
                                       int startValue, int count, long marker) throws Exception {
        List<Long> results = Collections.synchronizedList(new ArrayList<>());
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        configureCheckpoints(env, storageDir.toString(), backend);

        KeyedStream<IntEvent, String> keyed = env
                .addSource(burstSource(startValue, count), "src")
                .keyBy((KeySelector<IntEvent, String>) e -> e.key);

        StreamComponents components = new StreamComponents();
        components.setWindowOperatorFactory(new WindowOperatorFactoryImpl());

        new io.nop.stream.core.datastream.WindowedStreamImpl<>(keyed,
                TumblingEventTimeWindows.of(WINDOW_SIZE))
                .withComponents(components)
                .aggregate(capturingSumAggregate(marker))
                .sink((SinkFunction<Long>) results::add);

        env.execute(JOB_NAME);
        return results;
    }

    /**
     * P1-01 reduce-path variant: {@code reduce()} wraps the user
     * {@link ReduceFunction} into the capturing anonymous aggregate wrapper
     * ({@code WindowOperatorBuilder.reduceFunctionAsAggregate} — no no-arg
     * constructor), so restore must reuse the live descriptor function rather
     * than reflectively recreate it (pre-fix: NoSuchMethodException). The
     * window element type is String so the accumulated contents are JSON-safe
     * in the checkpoint persist path.
     */
    private List<String> runReduceJob(Path storageDir, IStateBackend backend,
                                      int startValue, int count) throws Exception {
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        configureCheckpoints(env, storageDir.toString(), backend);

        KeyedStream<String, String> keyed = env
                .addSource(new SourceFunction<String>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void run(SourceContext<String> ctx) {
                        for (int i = 0; i < count; i++) {
                            int value = startValue + i;
                            ctx.collectWithTimestamp(String.valueOf(value), (value - startValue + 1) * 1000L);
                        }
                        try {
                            Thread.sleep(800);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    @Override
                    public void cancel() {
                    }
                }, "src")
                .keyBy((KeySelector<String, String>) e -> "k1");

        StreamComponents components = new StreamComponents();
        components.setWindowOperatorFactory(new WindowOperatorFactoryImpl());

        new io.nop.stream.core.datastream.WindowedStreamImpl<>(keyed,
                TumblingEventTimeWindows.of(WINDOW_SIZE))
                .withComponents(components)
                .reduce(new ReduceFunction<String>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public String reduce(String value1, String value2) {
                        // value1 = new value, value2 = accumulated contents.
                        return value2 + "|" + value1;
                    }
                })
                .sink((SinkFunction<String>) results::add);

        env.execute(JOB_NAME);
        return results;
    }

    private static String joined(int start, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(start + i);
        }
        return sb.toString();
    }

    private void assertCombinedAggregateRestore(Path storageDir, IStateBackend backend, String backendName) throws Exception {
        // Run 1: values 1..10 accumulate to 55; the window fires at job end with 55.
        List<Long> run1 = runAggregateJob(storageDir, backend, 1, 10, 0L);
        assertEquals(List.of(55L), run1,
                backendName + " run 1 must fire the window with the accumulated sum 55: " + run1);

        // Run 2: restore from the manifest (in-flight window accumulation 55),
        // add values 11..20 (sum 155), fire with the COMBINED 210.
        List<Long> run2 = runAggregateJob(storageDir, backend, 11, 10, 0L);
        assertNotNull(run2);
        assertEquals(List.of(210L), run2,
                backendName + " run 2 must fire with the RESTORED + new accumulation (55+155=210). "
                        + "A silently-empty restore would fire 100: " + run2);
    }

    private void assertCombinedReduceRestore(Path storageDir, IStateBackend backend, String backendName) throws Exception {
        // Run 1: values 1..10 concatenated; the window fires at job end.
        List<String> run1 = runReduceJob(storageDir, backend, 1, 10);
        assertEquals(List.of(joined(1, 10)), run1,
                backendName + " run 1 must fire the window with the concatenated contents: " + run1);

        // Run 2: restore the accumulated contents and append values 11..20.
        List<String> run2 = runReduceJob(storageDir, backend, 11, 10);
        assertNotNull(run2);
        assertEquals(List.of(joined(1, 10) + "|" + joined(11, 10)), run2,
                backendName + " run 2 must fire with the RESTORED contents + new values. "
                        + "A silently-empty restore would fire " + joined(11, 10) + ": " + run2);
    }

    @Test
    void testWindowAggregateRestoreAcrossJobRestart_memoryBackend() throws Exception {
        assertCombinedAggregateRestore(tempDir, new MemoryStateBackend(), "Memory");
    }

    @Test
    void testWindowAggregateRestoreAcrossJobRestart_rocksdbBackend() throws Exception {
        // Fresh RocksDB scratch dir per run: the DB is scratch storage re-created
        // from the JSON checkpoint snapshot on restore; the SHARED checkpoint
        // storage (tempDir) is what carries state across the restart.
        io.nop.stream.core.common.state.backend.rocksdb.RocksDBStateBackend backend =
                new io.nop.stream.core.common.state.backend.rocksdb.RocksDBStateBackend(
                        tempDir.resolve("rocksdb-run").toString());
        assertCombinedAggregateRestore(tempDir, backend, "RocksDB");
    }

    @Test
    void testWindowReduceRestoreAcrossJobRestart_memoryBackend() throws Exception {
        assertCombinedReduceRestore(tempDir, new MemoryStateBackend(), "Memory");
    }

    @Test
    void testWindowReduceRestoreAcrossJobRestart_rocksdbBackend() throws Exception {
        io.nop.stream.core.common.state.backend.rocksdb.RocksDBStateBackend backend =
                new io.nop.stream.core.common.state.backend.rocksdb.RocksDBStateBackend(
                        tempDir.resolve("rocksdb-reduce-run").toString());
        assertCombinedReduceRestore(tempDir, backend, "RocksDB");
    }
}
