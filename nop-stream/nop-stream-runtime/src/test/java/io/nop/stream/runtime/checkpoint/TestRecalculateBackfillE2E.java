/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.commons.util.StringHelper;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink;
import io.nop.stream.connector.batch.BatchLoaderSourceFunction;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.datastream.WindowedStreamImpl;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.execution.CheckpointExecutorFactoryImpl;
import io.nop.stream.runtime.operators.windowing.WindowOperatorFactoryImpl;
import io.nop.stream.runtime.source.TimeRangeReplayableSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI10 (plan 2334): the two recompute job shapes end-to-end.
 *
 * <ul>
 *   <li><b>RECALCULATE</b>: a time-range replay over a bounded collection source
 *       ({@link TimeRangeReplayableSource}) feeding a tumbling event-time window whose
 *       result rows are written through the jdbc-2pc UPSERT sink. Re-running the SAME
 *       range as a FRESH job (new jobId + fresh checkpoint storage dir — resubmitting
 *       the same jobId would auto-restore the seek'ed offset and replay nothing) must
 *       leave the H2 table unchanged: same-key upserts overwrite with identical values
 *       (idempotent recompute).</li>
 *   <li><b>FILL_HISTORY</b>: a batch-loader source + UPSERT sink backfill job terminates
 *       naturally on the loader's empty batch (EOS), and re-running it re-upserts the
 *       same rows without duplication.</li>
 * </ul>
 *
 * <p><b>Bounded-source commit contract (engine semantic pinned by this test)</b>: rows
 * that only reach a 2PC sink at the job-end MAX_WATERMARK fire are never committed —
 * no checkpoint barrier follows the terminal fire, so the open epoch never preCommits
 * (verified empirically: pendingCommits stays empty and the table stays empty for ANY
 * source shape, wrapped or not). A bounded RECALCULATE source therefore MUST (a) emit
 * an explicit terminal watermark past the last window end so windows fire DURING the
 * run, and (b) linger at least one checkpoint interval so the periodic barrier
 * preCommits + commits the fired windows. The wrapper stays neutral: the watermark is
 * the inner source's responsibility (the wrapper only forwards it).
 */
class TestRecalculateBackfillE2E {

    private static final long WINDOW_SIZE = 60_000L;

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private IJdbcTemplate jdbcTemplate;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initialize();
        StreamExecutionEnvironment.setCheckpointExecutorFactory(new CheckpointExecutorFactoryImpl());
    }

    @AfterAll
    static void destroyCore() {
        StreamExecutionEnvironment.setCheckpointExecutorFactory(null);
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + getClass().getSimpleName()
                + StringHelper.generateUUID() + ";MODE=MySQL");
        jdbcTemplate = JdbcFactory.newJdbcTemplateFor(dataSource);

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE TABLE recalc_target (k VARCHAR(100), win_start BIGINT, win_end BIGINT, "
                            + "total BIGINT, PRIMARY KEY(k, win_end))")) {
                ps.execute();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE TABLE fill_target (id BIGINT PRIMARY KEY, name VARCHAR(100))")) {
                ps.execute();
            }
        }
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    // ==================== domain ====================

    static final class RecEvent implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        final String key;
        final long ts;
        final long value;

        RecEvent(String key, long ts, long value) {
            this.key = key;
            this.ts = ts;
            this.value = value;
        }
    }

    static final class RecRow implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        final String k;
        final long winStart;
        final long winEnd;
        final long total;

        RecRow(String k, long winStart, long winEnd, long total) {
            this.k = k;
            this.winStart = winStart;
            this.winEnd = winEnd;
            this.total = total;
        }
    }

    /** Two windows of history: [0,60s) holds a=1+2, [60s,120s) holds b=3 and a=4. */
    private static List<RecEvent> history() {
        return Arrays.asList(
                new RecEvent("a", 10_000L, 1),
                new RecEvent("a", 20_000L, 2),
                new RecEvent("b", 70_000L, 3),
                new RecEvent("a", 80_000L, 4));
    }

    /**
     * Bounded replay source per the RECALCULATE commit contract: emits the history with
     * event timestamps, then an explicit terminal watermark past the last window end,
     * then lingers so a periodic checkpoint commits the fired windows before EOS.
     */
    static final class BoundedHistorySource implements
            io.nop.stream.core.common.functions.source.ReplayableSourceFunction<RecEvent> {
        private static final long serialVersionUID = 1L;

        private final List<RecEvent> data;
        private final long terminalWatermark;
        private final long lingerMs;
        private long offset;

        BoundedHistorySource(List<RecEvent> data, long terminalWatermark, long lingerMs) {
            this.data = data;
            this.terminalWatermark = terminalWatermark;
            this.lingerMs = lingerMs;
        }

        @Override
        public void seek(long offset) {
            this.offset = offset;
        }

        @Override
        public long getCurrentOffset() {
            return offset;
        }

        @Override
        public void run(io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<RecEvent> ctx) throws Exception {
            while (offset < data.size()) {
                RecEvent e = data.get((int) offset++);
                ctx.collectWithTimestamp(e, e.ts);
            }
            ctx.emitWatermark(terminalWatermark);
            Thread.sleep(lingerMs);
        }

        @Override
        public void cancel() {
        }
    }

    // ==================== RECALCULATE ====================

    private JdbcTwoPhaseCommitSink<RecRow> recalcUpsertSink() {
        JdbcTwoPhaseCommitSink<RecRow> sink = JdbcTwoPhaseCommitSink.<RecRow>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("recalc_target")
                .ledgerTableName("recalc_ledger")
                .dmlMode(JdbcTwoPhaseCommitSink.DmlMode.UPSERT)
                .keyColumns("k", "win_end")
                .columns("k", "win_start", "win_end", "total")
                .recordMapper(row -> {
                    Map<String, Object> values = new LinkedHashMap<>();
                    values.put("k", row.k);
                    values.put("win_start", row.winStart);
                    values.put("win_end", row.winEnd);
                    values.put("total", row.total);
                    return values;
                })
                // generic (non-ChangeEvent) records in a keyed mode need an op code;
                // a recompute only ever rewrites current state, so every record is "c"
                .opMapper(row -> "c")
                .build();
        // fraud-example contract: the first epoch transaction is begun at construction;
        // with parallelism=1 the operator keeps this instance (copyForSubtask returns this)
        try {
            sink.beginTransaction();
        } catch (Exception e) {
            throw new IllegalStateException("failed to begin the first 2PC transaction", e);
        }
        sink.initializeLedgerTable();
        return sink;
    }

    private void runRecalculateJob(String jobId, Path storageDir, long from, long to,
                                   List<RecEvent> events) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        CheckpointConfig cfg = env.getCheckpointConfig();
        cfg.setJobId(jobId);
        cfg.setCheckpointInterval(200L);
        cfg.setCheckpointTimeout(5000L);
        cfg.setMinPause(100L);
        cfg.setStorageProperty("path", storageDir.toString());

        TimeRangeReplayableSource<RecEvent> source = new TimeRangeReplayableSource<>(
                // terminal watermark 120s (past both window ends) + 800ms linger (> 200ms
                // checkpoint interval): windows fire mid-run and a periodic barrier commits
                // the 2PC epoch — see the commit-contract note in the class javadoc
                new BoundedHistorySource(events, 120_000L, 800L), (e, recTs) -> e.ts, from, to);

        KeyedStream<RecEvent, String> keyed = env
                .addSource(source, "recalc-src")
                .keyBy((KeySelector<RecEvent, String>) e -> e.key);

        StreamComponents components = new StreamComponents();
        components.setWindowOperatorFactory(new WindowOperatorFactoryImpl());

        new WindowedStreamImpl<>(keyed, TumblingEventTimeWindows.of(WINDOW_SIZE))
                .withComponents(components)
                .process(new WindowSumFunction())
                .sink(recalcUpsertSink());

        env.execute(jobId);
    }

    static final class WindowSumFunction
            implements ProcessWindowFunction<RecEvent, RecRow, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, TimeWindow window, Iterable<RecEvent> input,
                            Context ctx, Collector<RecRow> out) {
            long total = 0;
            for (RecEvent e : input) {
                total += e.value;
            }
            out.collect(new RecRow(key, window.getStart(), window.getEnd(), total));
        }
    }

    private List<Map<String, Object>> snapshotRecalcTable() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT k, win_start, win_end, total FROM recalc_target "
                             + "ORDER BY k, win_end");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("k", rs.getString(1));
                row.put("win_start", rs.getLong(2));
                row.put("win_end", rs.getLong(3));
                row.put("total", rs.getLong(4));
                rows.add(row);
            }
        }
        return rows;
    }

    private Map<String, Object> recRow(String k, long winStart, long winEnd, long total) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("k", k);
        row.put("win_start", winStart);
        row.put("win_end", winEnd);
        row.put("total", total);
        return row;
    }

    /**
     * The idempotent-recompute contract: replaying the SAME history through a fresh job
     * leaves the upsert table byte-identical (same rows, same values, no duplicates).
     */
    @Test
    void recalculateReplayIsIdempotentThroughUpsert() throws Exception {
        List<Map<String, Object>> expected = Arrays.asList(
                recRow("a", 0L, 60_000L, 3L),
                recRow("a", 60_000L, 120_000L, 4L),
                recRow("b", 60_000L, 120_000L, 3L));

        runRecalculateJob("recalc-run-1", tempDir.resolve("st-run-1"), 0L, Long.MAX_VALUE, history());
        assertEquals(expected, snapshotRecalcTable(), "run 1 materializes the full history");

        // fresh jobId + fresh storage dir: a genuine second recompute, not an offset resume
        runRecalculateJob("recalc-run-2", tempDir.resolve("st-run-2"), 0L, Long.MAX_VALUE, history());
        assertEquals(expected, snapshotRecalcTable(),
                "replaying the same range must be idempotent: same-key upserts rewrite identical values, "
                        + "never duplicate rows");
    }

    /** A subrange recompute only materializes windows inside [from, to). */
    @Test
    void recalculateSubrangeMaterializesOnlyWindowsInRange() throws Exception {
        runRecalculateJob("recalc-sub", tempDir.resolve("st-sub"), 60_000L, 120_000L, history());

        List<Map<String, Object>> rows = snapshotRecalcTable();
        assertEquals(Arrays.asList(
                recRow("a", 60_000L, 120_000L, 4L),
                recRow("b", 60_000L, 120_000L, 3L)), rows,
                "window [0,60s) is outside the replay range and must not be materialized");
    }

    // ==================== FILL_HISTORY ====================

    private JdbcTwoPhaseCommitSink<Map<String, Object>> fillUpsertSink() {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = JdbcTwoPhaseCommitSink
                .<Map<String, Object>>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("fill_target")
                .ledgerTableName("fill_ledger")
                .dmlMode(JdbcTwoPhaseCommitSink.DmlMode.UPSERT)
                .keyColumns("id")
                .columns("id", "name")
                .recordMapper(row -> {
                    Map<String, Object> values = new LinkedHashMap<>();
                    values.put("id", row.get("id"));
                    values.put("name", row.get("name"));
                    return values;
                })
                .opMapper(row -> "c")
                .build();
        try {
            sink.beginTransaction();
        } catch (Exception e) {
            throw new IllegalStateException("failed to begin the first 2PC transaction", e);
        }
        sink.initializeLedgerTable();
        return sink;
    }

    /**
     * A loader that yields one bounded batch then an empty batch — the natural EOS the
     * FILL_HISTORY job shape terminates on (no DRAIN, no cancel).
     */
    private static IBatchLoaderProvider<Map<String, Object>> oneShotLoader() {
        return new IBatchLoaderProvider<>() {
            private static final long serialVersionUID = 1L;

            boolean drained;

            @Override
            public IBatchLoader<Map<String, Object>> setup(IBatchTaskContext taskContext) {
                return new IBatchLoader<>() {
                    @Override
                    public List<Map<String, Object>> load(int batchSize, IBatchChunkContext chunkCtx) {
                        if (drained) {
                            return List.of();
                        }
                        drained = true;
                        List<Map<String, Object>> batch = new ArrayList<>();
                        for (long id = 1; id <= 3; id++) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("id", id);
                            row.put("name", "hist-" + id);
                            batch.add(row);
                        }
                        return batch;
                    }
                };
            }
        };
    }

    private void runFillHistoryJob(String jobId, Path storageDir) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        CheckpointConfig cfg = env.getCheckpointConfig();
        cfg.setJobId(jobId);
        cfg.setCheckpointInterval(200L);
        cfg.setCheckpointTimeout(5000L);
        cfg.setMinPause(100L);
        cfg.setStorageProperty("path", storageDir.toString());

        BatchLoaderSourceFunction<Map<String, Object>> loaderSource =
                new BatchLoaderSourceFunction<>(oneShotLoader(), 10);

        env.addSource(loaderSource, "fill-src")
                .sink(fillUpsertSink());

        env.execute(jobId);
    }

    private List<Map<String, Object>> snapshotFillTable() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, name FROM fill_target ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong(1));
                row.put("name", rs.getString(2));
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * The backfill job terminates naturally on the loader's empty batch and upserts the
     * full history; re-running the backfill re-upserts the same rows (idempotent, no
     * duplication).
     */
    @Test
    void fillHistoryTerminatesOnNaturalEosAndBackfillsIdempotently() throws Exception {
        runFillHistoryJob("fill-run-1", tempDir.resolve("st-fill-1"));

        List<Map<String, Object>> expected = new ArrayList<>();
        for (long id = 1; id <= 3; id++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("name", "hist-" + id);
            expected.add(row);
        }
        List<Map<String, Object>> afterRun1 = snapshotFillTable();
        assertEquals(expected, afterRun1, "the backfill must load the full batch history");

        runFillHistoryJob("fill-run-2", tempDir.resolve("st-fill-2"));
        assertEquals(expected, snapshotFillTable(),
                "re-running the backfill must re-upsert identical rows, not duplicate them");
        assertTrue(afterRun1.size() == 3, "no duplicate rows after the first run");
    }
}
