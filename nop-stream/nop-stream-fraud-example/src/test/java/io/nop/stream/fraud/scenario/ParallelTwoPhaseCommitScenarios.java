/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.stream.connector.file.FileTwoPhaseCommitSink;
import io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink;
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;

import static io.nop.stream.fraud.scenario.ScenarioTestSupport.execute;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CONN-01 successor (roadmap item 35): shared plumbing for the parallel-2PC scenario
 * tests ({@code fraud-parallel-2pc-jdbc*.stream.xml} / {@code fraud-parallel-2pc-file.stream.xml}).
 *
 * <p>The scenario records are plain text lines ({@code key-i}); the JDBC variant
 * writes them to a single-column data table (NO primary key — a duplicate slip must
 * surface in the multiset comparison, not in a PK violation), the file variant writes
 * them as per-subtask epoch files. Both variants reuse the SAME input fixture and
 * expected multiset so the LOCAL, P=3-rejection and multi-JVM forms assert identical
 * semantics.
 *
 * <p>All beans are named serializable classes (no lambdas) so the same resolver shape
 * works for the LOCAL runs and the distributed (multi-JVM) deployment path.
 */
public final class ParallelTwoPhaseCommitScenarios {

    public static final String JDBC_STREAM_PATH = "/nop/stream/test/fraud-parallel-2pc-jdbc.stream.xml";
    public static final String JDBC_P3_STREAM_PATH = "/nop/stream/test/fraud-parallel-2pc-jdbc-p3.stream.xml";
    public static final String FILE_STREAM_PATH = "/nop/stream/test/fraud-parallel-2pc-file.stream.xml";

    public static final String DATA_TABLE = "p2pc_out";
    public static final String LEDGER_TABLE = "p2pc_ledger";

    /** Total fixture cardinality: key-0 .. key-{@code TOTAL_KEYS-1}, each exactly once. */
    public static final int TOTAL_KEYS = 60;

    private ParallelTwoPhaseCommitScenarios() {
    }

    // ----------------------------------------------------------------
    // Serializable beans (LOCAL + distributed share the same classes)
    // ----------------------------------------------------------------

    /** Stateless pass-through keyed vertex bean (the P&gt;1 transform under test). */
    public static final class PassThroughMap implements MapFunction<String, String>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public String map(String value) {
            return value;
        }
    }

    /** Named serializable record mapper: a line {@code key-i} -> column map {@code {k: key-i}}. */
    public static final class LineRowMapper implements Function<String, Map<String, Object>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Map<String, Object> apply(String line) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("k", line);
            return values;
        }
    }

    // ----------------------------------------------------------------
    // Resolvers
    // ----------------------------------------------------------------

    public static InMemoryBeanFunctionResolver localJdbcResolver(
            IJdbcTemplate jdbcTemplate, String inputDir, long lineDelayMs, long finishLingerMs) {
        JdbcTwoPhaseCommitSink<String> sink = JdbcTwoPhaseCommitSink.<String>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName(DATA_TABLE)
                .ledgerTableName(LEDGER_TABLE)
                .columns("k")
                .recordMapper(new LineRowMapper())
                .build();
        try {
            sink.beginTransaction();
            sink.initializeLedgerTable();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to init " + LEDGER_TABLE, e);
        }
        return baseResolver(inputDir, lineDelayMs, finishLingerMs)
                .register("p2pcJdbcSink", sink);
    }

    public static InMemoryBeanFunctionResolver localFileResolver(
            String inputDir, String outputDir, long lineDelayMs, long finishLingerMs) {
        return baseResolver(inputDir, lineDelayMs, finishLingerMs)
                .register("p2pcFileSink", new FileTwoPhaseCommitSink<>(outputDir));
    }

    /**
     * Distributed resolver: the sink points at the SHARED cluster H2 (AUTO_SERVER)
     * via the lazy template so the ledger/data tables are shared across real JVM
     * boundaries (tables pre-created by the test harness on the same URL).
     */
    public static InMemoryBeanFunctionResolver distributedJdbcResolver(
            String inputDir, String jdbcUrl, String jdbcUser, String jdbcPassword,
            long lineDelayMs, long finishLingerMs) {
        JdbcTwoPhaseCommitSink<String> sink = JdbcTwoPhaseCommitSink.<String>builder()
                .jdbcTemplate(DistributedScenarioSupport.lazyJdbcTemplate(jdbcUrl, jdbcUser, jdbcPassword))
                .tableName(DATA_TABLE)
                .ledgerTableName(LEDGER_TABLE)
                .columns("k")
                .recordMapper(new LineRowMapper())
                .build();
        return baseResolver(inputDir, lineDelayMs, finishLingerMs)
                .register("p2pcJdbcSink", sink);
    }

    private static InMemoryBeanFunctionResolver baseResolver(
            String inputDir, long lineDelayMs, long finishLingerMs) {
        return new InMemoryBeanFunctionResolver()
                .register("p2pcSource", new DirectoryFileSourceFunction(inputDir, lineDelayMs, finishLingerMs))
                .register("p2pcMap", new PassThroughMap());
    }

    // ----------------------------------------------------------------
    // Table preparation / reading
    // ----------------------------------------------------------------

    /** Data table DDL: single column, NO primary key (duplicates must be observable). */
    public static void createDataTable(IJdbcTemplate jdbcTemplate) {
        execute(jdbcTemplate, "CREATE TABLE IF NOT EXISTS " + DATA_TABLE + " (k VARCHAR(64) NOT NULL)");
    }

    /** Ledger DDL comes from the sink's own getLedgerTableDDL (composite PK). */
    public static void createLedgerTable(IJdbcTemplate jdbcTemplate) {
        execute(jdbcTemplate, JdbcTwoPhaseCommitSink.<String>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName(DATA_TABLE)
                .ledgerTableName(LEDGER_TABLE)
                .columns("k")
                .recordMapper(new LineRowMapper())
                .build()
                .getLedgerTableDDL());
    }

    /** Multiset read of the data table: value -> occurrence count (exactly-once comparator). */
    public static Map<String, Integer> readValueCounts(IJdbcTemplate jdbcTemplate) {
        Map<String, Integer> counts = new TreeMap<>();
        try (Connection conn = jdbcTemplate.openConnection("");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT k FROM " + DATA_TABLE)) {
            while (rs.next()) {
                counts.merge(rs.getString(1), 1, Integer::sum);
            }
        } catch (SQLException e) {
            fail("Failed to read " + DATA_TABLE, e);
        }
        return counts;
    }

    /** Expected multiset: every fixture key exactly once. */
    public static Map<String, Integer> expectedValueCounts() {
        Map<String, Integer> expected = new TreeMap<>();
        for (int i = 0; i < TOTAL_KEYS; i++) {
            expected.put("key-" + i, 1);
        }
        return expected;
    }

    /**
     * Ledger read keyed by epoch: epoch -> set of subtask ids that committed that
     * epoch. The per-subtask commit-key evidence: an epoch with 2+ distinct subtask
     * ids proves both independent subtask copies committed their own batch under the
     * composite key (epoch_id, subtask_id).
     */
    public static Map<Long, Set<Integer>> readLedgerSubtasks(IJdbcTemplate jdbcTemplate) {
        Map<Long, Set<Integer>> byEpoch = new TreeMap<>();
        try (Connection conn = jdbcTemplate.openConnection("");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT epoch_id, subtask_id FROM " + LEDGER_TABLE
                     + " ORDER BY epoch_id, subtask_id")) {
            while (rs.next()) {
                byEpoch.computeIfAbsent(rs.getLong(1), e -> new LinkedHashSet<>()).add(rs.getInt(2));
            }
        } catch (SQLException e) {
            fail("Failed to read " + LEDGER_TABLE, e);
        }
        return byEpoch;
    }

    /** @return true when some epoch carries commits from 2+ distinct subtasks. */
    public static boolean hasEpochWithMultipleSubtasks(IJdbcTemplate jdbcTemplate) {
        for (Set<Integer> subtasks : readLedgerSubtasks(jdbcTemplate).values()) {
            if (subtasks.size() >= 2) {
                return true;
            }
        }
        return false;
    }
}
