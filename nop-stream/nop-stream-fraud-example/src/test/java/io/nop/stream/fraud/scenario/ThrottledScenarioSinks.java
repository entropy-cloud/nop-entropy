/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.stream.connector.file.FileTwoPhaseCommitSink;
import io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink;

/**
 * Item 14 (composite-scenario distributed, matrix C3 backpressure trigger):
 * throttled variants of the production 2PC sinks. The design's C3 cell is
 * 「限速 sink：sink bean 内节流」— the throttle lives INSIDE the sink bean while the
 * commit semantics stay exactly the production classes' (ledger/manifest guarded
 * 2PC; only the data path gains a bounded per-record delay).
 *
 * <p>Semantics: while the release-marker file is ABSENT, every consumed record
 * first sleeps {@code throttleMs} (a rate-limited drain, NOT an unbounded block —
 * a checkpoint barrier queued behind throttled records still passes within a
 * bounded window, which is the C3 acceptance point「背压期间 checkpoint 仍推进,
 * 无死锁」). Once the marker exists, records pass through at full speed.
 *
 * <p>The scenario pipelines run at effective parallelism 1 (engine hard gate
 * {@code ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED}), so
 * {@link #copyForSubtask(int)} rebuilds throttled copies with the same
 * configuration.
 */
public final class ThrottledScenarioSinks {

    private ThrottledScenarioSinks() {
    }

    private static void awaitThrottleStep(long throttleMs, String releaseMarkerPath)
            throws InterruptedException {
        Path marker = Paths.get(releaseMarkerPath);
        if (!Files.exists(marker)) {
            Thread.sleep(throttleMs);
        }
    }

    /** Throttled {@link FileTwoPhaseCommitSink} (S2). */
    public static final class ThrottledFileTwoPhaseCommitSink<IN>
            extends FileTwoPhaseCommitSink<IN> {
        private static final long serialVersionUID = 1L;

        private final String outputDirCopy;
        private final long throttleMs;
        private final String releaseMarkerPath;

        public ThrottledFileTwoPhaseCommitSink(String outputDir, long throttleMs,
                                               String releaseMarkerPath) {
            super(outputDir);
            if (throttleMs <= 0) {
                throw new IllegalArgumentException("throttleMs must be positive (got " + throttleMs + ")");
            }
            if (releaseMarkerPath == null || releaseMarkerPath.isBlank()) {
                throw new IllegalArgumentException("releaseMarkerPath must not be blank");
            }
            this.outputDirCopy = outputDir;
            this.throttleMs = throttleMs;
            this.releaseMarkerPath = releaseMarkerPath;
        }

        @Override
        public void invoke(IN value) throws Exception {
            awaitThrottleStep(throttleMs, releaseMarkerPath);
            super.invoke(value);
        }

        @Override
        public ThrottledFileTwoPhaseCommitSink<IN> copyForSubtask(int subtaskIndex) {
            // Scenario sinks run at P=1; the copy keeps the throttle configuration.
            return new ThrottledFileTwoPhaseCommitSink<>(outputDirCopy, throttleMs, releaseMarkerPath);
        }
    }

    /** Throttled {@link JdbcTwoPhaseCommitSink} (S1). */
    public static final class ThrottledJdbcTwoPhaseCommitSink<IN>
            extends JdbcTwoPhaseCommitSink<IN> {
        private static final long serialVersionUID = 1L;

        private final IJdbcTemplate templateCopy;
        private final String querySpaceCopy;
        private final String tableNameCopy;
        private final String ledgerTableNameCopy;
        private final List<String> columnNamesCopy;
        private final Function<IN, Map<String, Object>> recordMapperCopy;
        private final long throttleMs;
        private final String releaseMarkerPath;

        public ThrottledJdbcTwoPhaseCommitSink(IJdbcTemplate jdbcTemplate, String querySpace,
                                               String tableName, String ledgerTableName,
                                               List<String> columnNames,
                                               Function<IN, Map<String, Object>> recordMapper,
                                               long throttleMs, String releaseMarkerPath) {
            super(jdbcTemplate, querySpace, tableName, ledgerTableName, columnNames, recordMapper);
            if (throttleMs <= 0) {
                throw new IllegalArgumentException("throttleMs must be positive (got " + throttleMs + ")");
            }
            if (releaseMarkerPath == null || releaseMarkerPath.isBlank()) {
                throw new IllegalArgumentException("releaseMarkerPath must not be blank");
            }
            this.templateCopy = jdbcTemplate;
            this.querySpaceCopy = querySpace;
            this.tableNameCopy = tableName;
            this.ledgerTableNameCopy = ledgerTableName;
            this.columnNamesCopy = columnNames;
            this.recordMapperCopy = recordMapper;
            this.throttleMs = throttleMs;
            this.releaseMarkerPath = releaseMarkerPath;
        }

        @Override
        public void invoke(IN value) throws Exception {
            awaitThrottleStep(throttleMs, releaseMarkerPath);
            super.invoke(value);
        }

        @Override
        public ThrottledJdbcTwoPhaseCommitSink<IN> copyForSubtask(int subtaskIndex) {
            return new ThrottledJdbcTwoPhaseCommitSink<>(templateCopy, querySpaceCopy,
                    tableNameCopy, ledgerTableNameCopy, columnNamesCopy, recordMapperCopy,
                    throttleMs, releaseMarkerPath);
        }
    }
}
