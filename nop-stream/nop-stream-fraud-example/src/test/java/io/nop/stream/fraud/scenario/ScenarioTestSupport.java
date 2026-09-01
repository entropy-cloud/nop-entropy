/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.sql.DataSource;

import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink;
import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.runtime.execution.CheckpointExecutorFactoryImpl;
import io.nop.xlang.xdsl.DslModelParser;
import org.h2.jdbcx.JdbcDataSource;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Shared plumbing for the S1/S2 composite-scenario tests (roadmap item 13).
 * All scenario beans are assembled through an {@link InMemoryBeanFunctionResolver}
 * so every run gets fresh source/sink instances pointed at per-test temp storage.
 */
public final class ScenarioTestSupport {

    /** Scenario window size: 10-second tumbling event-time windows. */
    public static final long WINDOW_SIZE_MS = 10_000L;

    /** Bounded-out-of-orderness watermark delay shared by S1/S2 strategy beans. */
    public static final long WATERMARK_DELAY_MS = 2_000L;

    /** Fixed scenario epoch base: 2023-11-14T22:13:20Z. */
    public static final long T0 = 1_700_000_000_000L;

    public static final String S1_STREAM_PATH = "/nop/stream/demo/fraud-s1-cdc.stream.xml";
    public static final String S2_STREAM_PATH = "/nop/stream/demo/fraud-s2-file.stream.xml";
    public static final String S2_DELTA_STREAM_PATH = "/nop/stream/demo/fraud-s2-file-delta.stream.xml";

    public static final String ALERT_TABLE = "fraud_alerts_summary";

    public static final String PATTERN_RAPID = "RAPID_TRANSACTION";
    public static final String PATTERN_UNUSUAL = "UNUSUAL_AMOUNT";
    public static final String PATTERN_GEO = "GEOGRAPHIC_ANOMALY";
    public static final String PATTERN_TAKEOVER = "ACCOUNT_TAKEOVER";

    private ScenarioTestSupport() {
    }

    // ----------------------------------------------------------------
    // Execution entry registration (static global, must be cleaned up)
    // ----------------------------------------------------------------

    public static void registerCheckpointExecutorFactory() {
        StreamExecutionEnvironment.setCheckpointExecutorFactory(new CheckpointExecutorFactoryImpl());
    }

    public static void unregisterCheckpointExecutorFactory() {
        StreamExecutionEnvironment.setCheckpointExecutorFactory(null);
    }

    // ----------------------------------------------------------------
    // XDSL parsing and execution
    // ----------------------------------------------------------------

    public static StreamModel parseStreamXml(String vfsPath) {
        IResource resource = VirtualFileSystem.instance().getResource(vfsPath);
        return (StreamModel) new DslModelParser().parseFromResource(resource);
    }

    /**
     * Builds and executes a scenario pipeline. Parallelism and state backend are
     * overridden through the parsed model / env checkpoint config (temp-dir storage
     * cannot be expressed in the repo XDSL file).
     */
    public static StreamExecutionEnvironment buildEnv(StreamModel model,
                                                      InMemoryBeanFunctionResolver resolver,
                                                      String storagePath,
                                                      io.nop.stream.core.common.state.backend.IStateBackend backend) {
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        env.getCheckpointConfig().setStorageProperty("path", storagePath);
        if (backend != null) {
            env.getCheckpointConfig().setStateBackend(backend);
        }
        return env;
    }

    // ----------------------------------------------------------------
    // S1 beans
    // ----------------------------------------------------------------

    public static ReplayableCdcSourceFunction replaySource(String connectorName,
                                                           List<Map<String, Object>> eventSpecs,
                                                           long emitDelayMs, long finishLingerMs) {
        DebeziumConfig config = new DebeziumConfig();
        config.setName(connectorName);
        config.setConnectorType("mysql");
        return new ReplayableCdcSourceFunction(config, eventSpecs, emitDelayMs, finishLingerMs);
    }

    public static WatermarkStrategy<ChangeEvent> cdcWatermarks() {
        return WatermarkStrategy.<ChangeEvent>forBoundedOutOfOrderness(
                        Duration.ofMillis(WATERMARK_DELAY_MS))
                .withTimestampAssigner((event, ts) -> event.getTimestamp());
    }

    public static WatermarkStrategy<io.nop.stream.fraud.model.TransactionEvent> txWatermarks() {
        return WatermarkStrategy.<io.nop.stream.fraud.model.TransactionEvent>forBoundedOutOfOrderness(
                        Duration.ofMillis(WATERMARK_DELAY_MS))
                .withTimestampAssigner((event, ts) -> event.getTimestamp());
    }

    public static TumblingEventTimeWindows windowAssigner() {
        return TumblingEventTimeWindows.of(WINDOW_SIZE_MS);
    }

    /**
     * Registers the full S1 bean set: replay CDC source, watermark strategy,
     * decoder, enricher, 4 CEP selects, window assigner/aggregator and 4
     * per-chain 2PC JDBC sinks (one ledger table per chain — the ledger PK
     * (epoch_id, subtask_id) has no vertex dimension, so chains must not share
     * a ledger table).
     */
    public static InMemoryBeanFunctionResolver s1Resolver(ReplayableCdcSourceFunction source,
                                                          IJdbcTemplate jdbcTemplate) {
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("cdcSource", source);
        resolver.register("cdcWatermarks", cdcWatermarks());
        resolver.register("cdcDecoder", new CdcChangeDecoder());
        resolver.register("userHistoryEnricher", new UserHistoryEnricher());
        resolver.register("selectRapid", new FraudAlertPatternFunction(PATTERN_RAPID));
        resolver.register("selectUnusual", new FraudAlertPatternFunction(PATTERN_UNUSUAL));
        resolver.register("selectGeo", new FraudAlertPatternFunction(PATTERN_GEO));
        resolver.register("selectTakeover", new FraudAlertPatternFunction(PATTERN_TAKEOVER));
        resolver.register("alertWindowAssigner", windowAssigner());
        resolver.register("alertAggregator", new AlertCountAggregate(WINDOW_SIZE_MS));
        resolver.register("jdbcSinkRapid", jdbcSink(jdbcTemplate, "fraud_ledger_rapid"));
        resolver.register("jdbcSinkUnusual", jdbcSink(jdbcTemplate, "fraud_ledger_unusual"));
        resolver.register("jdbcSinkGeo", jdbcSink(jdbcTemplate, "fraud_ledger_geo"));
        resolver.register("jdbcSinkTakeover", jdbcSink(jdbcTemplate, "fraud_ledger_takeover"));
        return resolver;
    }

    public static JdbcTwoPhaseCommitSink<AlertSummaryRow> jdbcSink(IJdbcTemplate jdbcTemplate,
                                                                   String ledgerTable) {
        JdbcTwoPhaseCommitSink<AlertSummaryRow> sink = JdbcTwoPhaseCommitSink.<AlertSummaryRow>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName(ALERT_TABLE)
                .ledgerTableName(ledgerTable)
                .columns("window_start", "window_end", "user_id", "pattern", "alert_count", "total_amount")
                .recordMapper(row -> {
                    Map<String, Object> values = new java.util.LinkedHashMap<>();
                    values.put("window_start", row.getWindowStart());
                    values.put("window_end", row.getWindowEnd());
                    values.put("user_id", row.getUserId());
                    values.put("pattern", row.getPattern());
                    values.put("alert_count", row.getAlertCount());
                    values.put("total_amount", row.getTotalAmount());
                    return values;
                })
                .build();
        try {
            sink.beginTransaction();
            sink.initializeLedgerTable();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to init ledger table " + ledgerTable, e);
        }
        return sink;
    }

    // ----------------------------------------------------------------
    // S2 beans
    // ----------------------------------------------------------------

    public static InMemoryBeanFunctionResolver s2Resolver(String inputDir, String outputDir) {
        return s2Resolver(inputDir, outputDir, 0L, 0L);
    }

    public static InMemoryBeanFunctionResolver s2Resolver(String inputDir, String outputDir,
                                                          long lineDelayMs, long finishLingerMs) {
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("fileSource",
                new DirectoryFileSourceFunction(inputDir, lineDelayMs, finishLingerMs));
        resolver.register("lineParser", new TransactionLineParser());
        resolver.register("txWatermarks", txWatermarks());
        resolver.register("txWindowAssigner", windowAssigner());
        resolver.register("txAggregator", new TransactionWindowAggregate(WINDOW_SIZE_MS));
        resolver.register("fileSink", new io.nop.stream.connector.file.FileTwoPhaseCommitSink<>(outputDir));
        return resolver;
    }

    // ----------------------------------------------------------------
    // H2 helpers
    // ----------------------------------------------------------------

    public static DataSource newH2DataSource(String name) {
        JdbcDataSource ds = new JdbcDataSource();
        // DB_CLOSE_DELAY=-1: the 2PC sink opens independent connections per commit,
        // so the in-memory database must survive between connections.
        ds.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    public static IJdbcTemplate newJdbcTemplate(DataSource dataSource) {
        return JdbcFactory.newJdbcTemplateFor(dataSource);
    }

    public static void createS1Tables(IJdbcTemplate jdbcTemplate) {
        execute(jdbcTemplate, "CREATE TABLE IF NOT EXISTS " + ALERT_TABLE + " ("
                + "window_start BIGINT NOT NULL, "
                + "window_end BIGINT NOT NULL, "
                + "user_id VARCHAR(64) NOT NULL, "
                + "pattern VARCHAR(64) NOT NULL, "
                + "alert_count BIGINT NOT NULL, "
                + "total_amount DECIMAL(19,2) NOT NULL, "
                + "PRIMARY KEY (window_start, window_end, user_id, pattern))");
    }

    public static void execute(IJdbcTemplate jdbcTemplate, String sql) {
        try (Connection conn = jdbcTemplate.openConnection("");
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            fail("Failed to execute SQL: " + sql, e);
        }
    }

    public static Set<AlertSummaryRow> readAlertRows(IJdbcTemplate jdbcTemplate) {
        Set<AlertSummaryRow> rows = new LinkedHashSet<>();
        try (Connection conn = jdbcTemplate.openConnection("");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT window_start, window_end, user_id, pattern, "
                     + "alert_count, total_amount FROM " + ALERT_TABLE)) {
            while (rs.next()) {
                rows.add(new AlertSummaryRow(rs.getLong(1), rs.getLong(2), rs.getString(3),
                        rs.getString(4), rs.getLong(5), rs.getBigDecimal(6)));
            }
        } catch (SQLException e) {
            fail("Failed to read alert rows", e);
        }
        return rows;
    }

    public static TreeMap<Long, Integer> readLedgerRows(IJdbcTemplate jdbcTemplate, String ledgerTable) {
        TreeMap<Long, Integer> epochs = new TreeMap<>();
        try (Connection conn = jdbcTemplate.openConnection("");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT epoch_id, subtask_id FROM " + ledgerTable
                     + " ORDER BY epoch_id, subtask_id")) {
            while (rs.next()) {
                epochs.put(rs.getLong(1), rs.getInt(2));
            }
        } catch (SQLException e) {
            fail("Failed to read ledger rows", e);
        }
        return epochs;
    }

    // ----------------------------------------------------------------
    // Expectation builders
    // ----------------------------------------------------------------

    public static long windowStart(long eventTime) {
        return Math.floorDiv(eventTime, WINDOW_SIZE_MS) * WINDOW_SIZE_MS;
    }

    public static AlertSummaryRow alertRow(long anyEventTime, String userId, String pattern,
                                           long count, String totalAmount) {
        long start = windowStart(anyEventTime);
        return new AlertSummaryRow(start, start + WINDOW_SIZE_MS, userId, pattern,
                count, new BigDecimal(totalAmount));
    }

    /** Multi-set sum equality for BigDecimal rows ignores scale differences (2700 == 2700.00). */
    public static boolean sameRows(Set<AlertSummaryRow> actual, Set<AlertSummaryRow> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        outer:
        for (AlertSummaryRow exp : expected) {
            for (AlertSummaryRow act : actual) {
                if (act.getUserId().equals(exp.getUserId())
                        && act.getPattern().equals(exp.getPattern())
                        && act.getWindowStart() == exp.getWindowStart()
                        && act.getWindowEnd() == exp.getWindowEnd()
                        && act.getAlertCount() == exp.getAlertCount()
                        && act.getTotalAmount().compareTo(exp.getTotalAmount()) == 0) {
                    continue outer;
                }
            }
            return false;
        }
        return true;
    }
}
