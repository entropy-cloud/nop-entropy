/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.message.debezium.ChangeEvent;
import io.nop.stream.core.common.eventtime.TimestampAssigner;
import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.graph.PartitionedPlanGenerator;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.model.StreamModelFingerprint;
import io.nop.stream.fraud.model.TransactionEvent;
import io.nop.stream.flow.builder.InMemoryBeanFunctionResolver;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.runtime.execution.DeploymentPlanProviderImpl;
import io.nop.stream.runtime.launch.ClusterLaunchConfig;
import io.nop.stream.runtime.launch.ClusterPipelineFactory;

/**
 * Item 14 (composite-scenario distributed): shared plumbing for deploying the S1/S2
 * scenario pipelines in REAL multi-JVM mode via {@code MiniStreamCluster} +
 * {@code JobCoordinatorMain}'s {@code pipelineFactoryClass} seam.
 *
 * <p>The LOCAL scenario beans (plan 2) are constructed with live objects that cannot
 * cross the JVM boundary inside the {@code TaskDeploymentDescriptor}'s Java-serialized
 * JobGraph: the S1 JDBC sink holds a live {@link IJdbcTemplate} and a lambda record
 * mapper, and the watermark strategies carry non-serializable lambda timestamp
 * assigners. This class provides the serializable distributed variants:
 *
 * <ul>
 *   <li>watermark strategies with NAMED serializable timestamp assigners;</li>
 *   <li>a lazily-resolving serializable {@link IJdbcTemplate} dynamic proxy that
 *       rebuilds the JDBC plumbing from (url, user, password) on the TaskManager
 *       side after deserialization — the sink semantics stay exactly
 *       {@code JdbcTwoPhaseCommitSink}'s (same class, same ledger guard);</li>
 *   <li>a named serializable record mapper for the S1 sink.</li>
 * </ul>
 *
 * <p>Everything else (fixture events, expectations, XDSL files, window semantics)
 * is reused verbatim from the plan-2 LOCAL assets so the distributed matrix runs
 * the SAME scenarios with the SAME pre-computed expectations.
 */
public final class DistributedScenarioSupport {

    // ---- launch config keys consumed by the scenario pipeline factories ----

    public static final String KEY_STREAM_PATH = "scenarioStreamPath";
    public static final String KEY_CHECKPOINT_INTERVAL = "scenarioCheckpointIntervalMs";
    public static final String KEY_CHECKPOINT_TIMEOUT = "scenarioCheckpointTimeoutMs";
    public static final String KEY_MAX_RETAINED = "scenarioMaxRetainedCheckpoints";
    public static final String KEY_EXPECTED_NODES = "expectedNodeIds";

    public static final String KEY_S1_EVENTS_FILE = "s1EventsFile";
    public static final String KEY_S1_EMIT_DELAY = "s1EmitDelayMs";
    public static final String KEY_S1_LINGER = "s1FinishLingerMs";

    public static final String KEY_S2_INPUT_DIR = "s2InputDir";
    public static final String KEY_S2_OUTPUT_DIR = "s2OutputDir";
    public static final String KEY_S2_LINE_DELAY = "s2LineDelayMs";
    public static final String KEY_S2_LINGER = "s2FinishLingerMs";

    /**
     * Item 14 (matrix C3 backpressure): optional per-record throttle applied INSIDE
     * the 2PC sink beans (design §3.3 C3「限速 sink：sink bean 内节流」). While the
     * release-marker file is ABSENT every sink record first sleeps
     * {@code sinkThrottleMs}; once the marker exists records pass through at full
     * speed. Empty/zero = no throttle (C0/C1/C2 runs unaffected).
     */
    public static final String KEY_SINK_THROTTLE_MS = "sinkThrottleMs";
    public static final String KEY_THROTTLE_RELEASE_FILE = "throttleReleaseFile";

    /**
     * Item 15 (stability exercise BP-1): alternative live-stepped throttle — the
     * per-record delay is polled from a level file (numeric ms ≥ 0; 0 = release),
     * so the exercise driver can raise/lower the backpressure level mid-run
     * without redeploy. Mutually exclusive with the C3 static throttle above.
     */
    public static final String KEY_THROTTLE_LEVEL_FILE = "throttleLevelFile";

    /** Multi-JVM checkpoint cadence default (JDBC-polled data plane is slower than LOCAL). */
    public static final long DEFAULT_DISTRIBUTED_CHECKPOINT_INTERVAL_MS = 400L;

    private DistributedScenarioSupport() {
    }

    // ----------------------------------------------------------------
    // Serializable watermark strategies (named assigners, no lambdas)
    // ----------------------------------------------------------------

    /** Event time of a CDC {@link ChangeEvent} shell (S1). */
    public static final class CdcEventTimestampAssigner
            implements TimestampAssigner<ChangeEvent>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public long extractTimestamp(ChangeEvent event, long recordTimestamp) {
            return event.getTimestamp();
        }
    }

    /** Event time of a parsed {@link TransactionEvent} (S2). */
    public static final class TransactionEventTimestampAssigner
            implements TimestampAssigner<TransactionEvent>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public long extractTimestamp(TransactionEvent event, long recordTimestamp) {
            return event.getTimestamp();
        }
    }

    public static WatermarkStrategy<ChangeEvent> distributedCdcWatermarks() {
        return WatermarkStrategy.<ChangeEvent>forBoundedOutOfOrderness(
                        Duration.ofMillis(ScenarioTestSupport.WATERMARK_DELAY_MS))
                .withTimestampAssigner(new CdcEventTimestampAssigner());
    }

    public static WatermarkStrategy<TransactionEvent> distributedTxWatermarks() {
        return WatermarkStrategy.<TransactionEvent>forBoundedOutOfOrderness(
                        Duration.ofMillis(ScenarioTestSupport.WATERMARK_DELAY_MS))
                .withTimestampAssigner(new TransactionEventTimestampAssigner());
    }

    // ----------------------------------------------------------------
    // Serializable JDBC plumbing for the S1 2PC sink
    // ----------------------------------------------------------------

    /**
     * Named serializable mapper replacing the LOCAL lambda: maps an
     * {@link AlertSummaryRow} to the sink's column map.
     */
    public static final class AlertRowMapper
            implements Function<AlertSummaryRow, Map<String, Object>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public Map<String, Object> apply(AlertSummaryRow row) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("window_start", row.getWindowStart());
            values.put("window_end", row.getWindowEnd());
            values.put("user_id", row.getUserId());
            values.put("pattern", row.getPattern());
            values.put("alert_count", row.getAlertCount());
            values.put("total_amount", row.getTotalAmount());
            return values;
        }
    }

    /**
     * Builds a lazily-resolving serializable {@link IJdbcTemplate}. The returned proxy
     * serializes as (url, user, password) and rebuilds a pooled {@link IJdbcTemplate}
     * on first use after deserialization — so the S1
     * {@link io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink} crosses the JVM
     * boundary inside the deployment descriptor while keeping its production
     * implementation (ledger-guarded 2PC commit) untouched.
     */
    public static IJdbcTemplate lazyJdbcTemplate(String jdbcUrl, String user, String password) {
        return (IJdbcTemplate) Proxy.newProxyInstance(
                IJdbcTemplate.class.getClassLoader(),
                new Class<?>[]{IJdbcTemplate.class},
                new LazyJdbcHandler(jdbcUrl, user, password));
    }

    /**
     * Serializable invocation handler behind {@link #lazyJdbcTemplate}. The delegate
     * is rebuilt from the serializable connection info on first use (volatile +
     * double-checked so concurrent first uses share one pool).
     */
    static final class LazyJdbcHandler implements InvocationHandler, Serializable {
        private static final long serialVersionUID = 1L;

        private final String jdbcUrl;
        private final String user;
        private final String password;
        private transient volatile IJdbcTemplate delegate;

        LazyJdbcHandler(String jdbcUrl, String user, String password) {
            this.jdbcUrl = jdbcUrl;
            this.user = user;
            this.password = password;
        }

        IJdbcTemplate delegate() {
            IJdbcTemplate local = this.delegate;
            if (local != null) {
                return local;
            }
            synchronized (this) {
                if (this.delegate == null) {
                    // The sink runs inside a spawned TaskManager JVM whose main()
                    // already ran CoreInitialization; the test JVM did too. Calling
                    // initialize() again is idempotent and guards the direct-wiring
                    // paths (in-process harness).
                    CoreInitialization.initialize();
                    HikariDataSource ds = new HikariDataSource();
                    ds.setDriverClassName("org.h2.Driver");
                    ds.setJdbcUrl(jdbcUrl);
                    ds.setUsername(user);
                    ds.setPassword(password);
                    ds.setMaximumPoolSize(4);
                    JdbcFactory factory = new JdbcFactory();
                    this.delegate = factory.newJdbcTemplate(factory.newTransactionTemplate(ds));
                }
                return this.delegate;
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            try {
                return method.invoke(delegate(), args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    // ----------------------------------------------------------------
    // Distributed bean resolvers (serializable variants of the LOCAL sets)
    // ----------------------------------------------------------------

    /**
     * S1 distributed resolver: same bean ids as {@link ScenarioTestSupport#s1Resolver}
     * (the XDSL file is identical) but every bean is Java-serializable so the JobGraph
     * survives the deployTask RPC. The 4 per-chain 2PC JDBC sinks point at the SHARED
     * cluster H2 (AUTO_SERVER) via the lazy template; ledger/data tables must be
     * pre-created by the test harness on the same URL. {@code throttleMs <= 0} keeps
     * the plain production sinks (C0/C1 runs); a positive value wraps every chain sink
     * in the C3 throttled variant.
     */
    public static InMemoryBeanFunctionResolver s1DistributedResolver(
            List<Map<String, Object>> eventSpecs, long emitDelayMs, long finishLingerMs,
            String jdbcUrl, String jdbcUser, String jdbcPassword) {
        return s1DistributedResolver(eventSpecs, emitDelayMs, finishLingerMs,
                jdbcUrl, jdbcUser, jdbcPassword, 0L, null);
    }

    public static InMemoryBeanFunctionResolver s1DistributedResolver(
            List<Map<String, Object>> eventSpecs, long emitDelayMs, long finishLingerMs,
            String jdbcUrl, String jdbcUser, String jdbcPassword,
            long sinkThrottleMs, String throttleReleaseFile) {
        io.nop.message.debezium.DebeziumConfig config = new io.nop.message.debezium.DebeziumConfig();
        config.setName("fraud-s1-distributed");
        config.setConnectorType("mysql");
        ReplayableCdcSourceFunction source = new ReplayableCdcSourceFunction(
                config, eventSpecs, emitDelayMs, finishLingerMs);

        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("cdcSource", source);
        resolver.register("cdcWatermarks", distributedCdcWatermarks());
        resolver.register("cdcDecoder", new CdcChangeDecoder());
        resolver.register("userHistoryEnricher", new UserHistoryEnricher());
        resolver.register("selectRapid", new FraudAlertPatternFunction(ScenarioTestSupport.PATTERN_RAPID));
        resolver.register("selectUnusual", new FraudAlertPatternFunction(ScenarioTestSupport.PATTERN_UNUSUAL));
        resolver.register("selectGeo", new FraudAlertPatternFunction(ScenarioTestSupport.PATTERN_GEO));
        resolver.register("selectTakeover", new FraudAlertPatternFunction(ScenarioTestSupport.PATTERN_TAKEOVER));
        resolver.register("alertWindowAssigner", ScenarioTestSupport.windowAssigner());
        resolver.register("alertAggregator", new AlertCountAggregate(ScenarioTestSupport.WINDOW_SIZE_MS));
        resolver.register("jdbcSinkRapid", alertSink(jdbcUrl, jdbcUser, jdbcPassword,
                "fraud_ledger_rapid", sinkThrottleMs, throttleReleaseFile));
        resolver.register("jdbcSinkUnusual", alertSink(jdbcUrl, jdbcUser, jdbcPassword,
                "fraud_ledger_unusual", sinkThrottleMs, throttleReleaseFile));
        resolver.register("jdbcSinkGeo", alertSink(jdbcUrl, jdbcUser, jdbcPassword,
                "fraud_ledger_geo", sinkThrottleMs, throttleReleaseFile));
        resolver.register("jdbcSinkTakeover", alertSink(jdbcUrl, jdbcUser, jdbcPassword,
                "fraud_ledger_takeover", sinkThrottleMs, throttleReleaseFile));
        return resolver;
    }

    /**
     * S1 sink built on the production {@link io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink}
     * with the lazy template + named mapper (serializable across JVMs). No eager ledger
     * DDL: the test harness pre-creates the tables on the shared URL.
     */
    public static io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink<AlertSummaryRow> alertSink(
            String jdbcUrl, String jdbcUser, String jdbcPassword, String ledgerTable) {
        return io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink.<AlertSummaryRow>builder()
                .jdbcTemplate(lazyJdbcTemplate(jdbcUrl, jdbcUser, jdbcPassword))
                .tableName(ScenarioTestSupport.ALERT_TABLE)
                .ledgerTableName(ledgerTable)
                .columns("window_start", "window_end", "user_id", "pattern", "alert_count", "total_amount")
                .recordMapper(new AlertRowMapper())
                .build();
    }

    /**
     * Item 14 (matrix C3): throttled variant of the S1 JDBC 2PC sink — same
     * production commit semantics (ledger-guarded 2PC), with a bounded per-record
     * delay while the release marker is absent. {@code throttleMs <= 0} returns the
     * plain production sink (no wrapper overhead in the C0/C1/C2 runs).
     */
    public static io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink<AlertSummaryRow> alertSink(
            String jdbcUrl, String jdbcUser, String jdbcPassword, String ledgerTable,
            long throttleMs, String throttleReleaseFile) {
        if (throttleMs <= 0) {
            return alertSink(jdbcUrl, jdbcUser, jdbcPassword, ledgerTable);
        }
        return new ThrottledScenarioSinks.ThrottledJdbcTwoPhaseCommitSink<>(
                lazyJdbcTemplate(jdbcUrl, jdbcUser, jdbcPassword),
                "", ScenarioTestSupport.ALERT_TABLE, ledgerTable,
                java.util.Arrays.asList("window_start", "window_end", "user_id",
                        "pattern", "alert_count", "total_amount"),
                new AlertRowMapper(), throttleMs, throttleReleaseFile);
    }

    /**
     * Item 14 (matrix C3): throttled variant of the S2 file 2PC sink — same
     * production commit semantics (temp + atomic rename + manifest), with a bounded
     * per-record delay while the release marker is absent. {@code throttleMs <= 0}
     * returns the plain production sink.
     */
    public static io.nop.stream.connector.file.FileTwoPhaseCommitSink<TxSummaryRow> fileSink(
            String outputDir, long throttleMs, String throttleReleaseFile) {
        if (throttleMs <= 0) {
            return new io.nop.stream.connector.file.FileTwoPhaseCommitSink<>(outputDir);
        }
        return new ThrottledScenarioSinks.ThrottledFileTwoPhaseCommitSink<>(
                outputDir, throttleMs, throttleReleaseFile);
    }

    /**
     * Item 15 (BP-1): live-stepped throttle variant — the delay comes from the
     * level file polled per record (see {@link ThrottledScenarioSinks#currentThrottleLevel}).
     */
    public static io.nop.stream.connector.file.FileTwoPhaseCommitSink<TxSummaryRow> steppedThrottleFileSink(
            String outputDir, String levelFilePath) {
        return new ThrottledScenarioSinks.SteppedThrottleFileTwoPhaseCommitSink<>(
                outputDir, levelFilePath);
    }

    /**
     * S2 distributed resolver: file source/sink beans are path/delay-configured and
     * serializable; the delta variant reuses the same beans (the delta XDSL only adds
     * the blacklist filter operator inline). {@code throttleMs <= 0} keeps the plain
     * production file sink; a positive value wraps it in the C3 throttled variant.
     */
    public static InMemoryBeanFunctionResolver s2DistributedResolver(
            String inputDir, String outputDir, long lineDelayMs, long finishLingerMs) {
        return s2DistributedResolver(inputDir, outputDir, lineDelayMs, finishLingerMs, 0L, null);
    }

    public static InMemoryBeanFunctionResolver s2DistributedResolver(
            String inputDir, String outputDir, long lineDelayMs, long finishLingerMs,
            long sinkThrottleMs, String throttleReleaseFile) {
        return s2DistributedResolver(inputDir, outputDir, lineDelayMs, finishLingerMs,
                sinkThrottleMs, throttleReleaseFile, null);
    }

    /**
     * Item 15 (BP-1): additional {@code throttleLevelFile} variant — when set, the
     * file sink uses the live-stepped throttle (mutually exclusive with the C3
     * static {@code sinkThrottleMs}).
     */
    public static InMemoryBeanFunctionResolver s2DistributedResolver(
            String inputDir, String outputDir, long lineDelayMs, long finishLingerMs,
            long sinkThrottleMs, String throttleReleaseFile, String throttleLevelFile) {
        if (throttleLevelFile != null && !throttleLevelFile.isBlank()) {
            if (sinkThrottleMs > 0) {
                throw new IllegalArgumentException(
                        "throttleLevelFile and sinkThrottleMs are mutually exclusive (C3 static"
                                + " throttle vs item-15 stepped throttle)");
            }
        }
        InMemoryBeanFunctionResolver resolver = new InMemoryBeanFunctionResolver();
        resolver.register("fileSource",
                new DirectoryFileSourceFunction(inputDir, lineDelayMs, finishLingerMs));
        resolver.register("lineParser", new TransactionLineParser());
        resolver.register("txWatermarks", distributedTxWatermarks());
        resolver.register("txWindowAssigner", ScenarioTestSupport.windowAssigner());
        resolver.register("txAggregator", new TransactionWindowAggregate(ScenarioTestSupport.WINDOW_SIZE_MS));
        resolver.register("fileSink", throttleLevelFile != null && !throttleLevelFile.isBlank()
                ? steppedThrottleFileSink(outputDir, throttleLevelFile)
                : fileSink(outputDir, sinkThrottleMs, throttleReleaseFile));
        return resolver;
    }

    // ----------------------------------------------------------------
    // XDSL -> deployable artifacts (D9: declaration builds the graph,
    // the runtime launch orchestrates execution)
    // ----------------------------------------------------------------

    /**
     * Builds the deployable pipeline artifacts from a parsed scenario XDSL model:
     * env (via the DSL builder) → JobGraph ({@link StreamExecutionEnvironment#buildJobGraph},
     * stable ids + populated StreamModel fingerprint) → PartitionedPlan →
     * DeploymentPlan (materialized assignment over the launch's expected nodes when
     * provided; otherwise local, letting the coordinator assign round-robin).
     *
     * <p>The returned artifacts ALSO carry a {@link io.nop.stream.runtime.rpc.RemotePipelineSpec}
     * (declaration + serializable beans): the compiled graph embeds non-serializable
     * expression objects, so the launch ships the spec and every TaskManager rebuilds
     * an identical graph locally via the registered
     * {@link io.nop.stream.fraud.scenario.ScenarioXdslPipelineResolver}.
     */
    public static ClusterPipelineFactory.PipelineArtifacts xdslArtifacts(
            String streamVfsPath, StreamModel model, InMemoryBeanFunctionResolver resolver,
            ClusterLaunchConfig config) {
        StreamExecutionEnvironment env = StreamModelDslBuilder.of(model, resolver).build();
        String jobId = config.require(ClusterLaunchConfig.KEY_JOB_ID);
        JobGraph jobGraph = env.buildJobGraph(jobId);

        StreamModelFingerprint fingerprint = jobGraph.getStreamModel() != null
                ? jobGraph.getStreamModel().computeFingerprint() : null;
        PartitionedPlanGenerator partitionedPlanGenerator = new PartitionedPlanGenerator();
        io.nop.stream.core.execution.plan.PartitionedPlan partitionedPlan =
                partitionedPlanGenerator.generate(jobGraph, fingerprint);

        DeploymentPlan deploymentPlan;
        String expectedNodes = config.get(KEY_EXPECTED_NODES, "");
        if (!expectedNodes.isBlank()) {
            deploymentPlan = new DeploymentPlanProviderImpl().generateDistributed(
                    partitionedPlan, java.util.Arrays.asList(expectedNodes.split(",")));
        } else {
            deploymentPlan = new DeploymentPlanProviderImpl().generateLocal(partitionedPlan);
        }

        Long interval = getLongOrNull(config, KEY_CHECKPOINT_INTERVAL);
        Long timeout = getLongOrNull(config, KEY_CHECKPOINT_TIMEOUT);
        Integer retained = getIntOrNull(config, KEY_MAX_RETAINED);
        io.nop.stream.runtime.rpc.RemotePipelineSpec spec =
                new io.nop.stream.runtime.rpc.RemotePipelineSpec(streamVfsPath, resolver.getBeans());
        return new ClusterPipelineFactory.PipelineArtifacts(
                jobGraph, deploymentPlan,
                interval != null ? interval : DEFAULT_DISTRIBUTED_CHECKPOINT_INTERVAL_MS,
                timeout, retained, spec);
    }

    // ----------------------------------------------------------------
    // S1 fixture transport (test JVM -> coordinator child process)
    // ----------------------------------------------------------------

    /** Serializes the deterministic S1 event fixture to a JSON file for the JC child. */
    public static void writeS1EventsFile(Path file, List<Map<String, Object>> eventSpecs)
            throws IOException {
        Files.write(file, JsonTool.serialize(eventSpecs, false).getBytes(StandardCharsets.UTF_8));
    }

    /** Reads back the S1 event fixture written by {@link #writeS1EventsFile}. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> readS1EventsFile(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Object parsed = JsonTool.parse(text);
        if (!(parsed instanceof List)) {
            throw new IllegalStateException("s1 events file is not a JSON list: " + file);
        }
        return (List<Map<String, Object>>) parsed;
    }

    // ----------------------------------------------------------------
    // Serialization helpers (wiring proof for the cross-JVM bean set)
    // ----------------------------------------------------------------

    /** Java round-trip (serialize + deserialize) — the deployTask RPC transport contract. */
    public static <T> T roundTrip(T value) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(value);
            }
            try (ObjectInputStream ois = new ObjectInputStream(
                    new ByteArrayInputStream(baos.toByteArray()))) {
                return (T) ois.readObject();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Java round-trip failed for " + value
                    + " (the deployment descriptor Java-serializes the JobGraph): " + e, e);
        }
    }

    private static Long getLongOrNull(ClusterLaunchConfig config, String key) {
        String v = config.raw().get(key);
        if (v == null || v.isBlank()) {
            return null;
        }
        return Long.parseLong(v.trim());
    }

    private static Integer getIntOrNull(ClusterLaunchConfig config, String key) {
        String v = config.raw().get(key);
        if (v == null || v.isBlank()) {
            return null;
        }
        return Integer.parseInt(v.trim());
    }
}
