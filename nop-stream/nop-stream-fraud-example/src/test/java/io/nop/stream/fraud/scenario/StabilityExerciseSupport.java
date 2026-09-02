/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.stream.runtime.multijvm.MiniStreamCluster;

import static io.nop.stream.fraud.scenario.ScenarioTestSupport.S2_STREAM_PATH;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 15 (stability exercise): shared driver plumbing for the parameterized
 * joint entry {@code TestStabilityExerciseMultiJvm} — exercise parameter reading
 * (fail-fast on illegal values), S1/S2 exercise cluster launch (ops HTTP port on
 * the coordinator for direct metrics sampling), cluster-bound sample sources,
 * the chaos kill loop (TM kill/restart + SIGSTOP/SIGCONT partition-equivalent +
 * HA lease helpers), and run-summary persistence.
 *
 * <p>Everything here is TEST-domain tooling around the existing
 * {@code MiniStreamCluster} / scenario-package assets — no engine capability is
 * added or changed.
 */
final class StabilityExerciseSupport {

    /** Drill artifacts must be preserved (Phase 1 artifact rule) — fail fast otherwise. */
    static final String PRESERVE_PROPERTY = "nop.stream.test.multi-jvm.preserve-artifacts";

    /**
     * Post-input linger so the final data windows' commits complete before the
     * bounded source ends (wall-clock margin; the pump tail itself is volume-minimal).
     */
    static final long FINISH_LINGER_MS = 5000L;

    static final String DEFAULT_OPS_HTTP_PORT = "8931";

    private static final System.Logger LOG = System.getLogger(StabilityExerciseSupport.class.getName());

    private StabilityExerciseSupport() {
    }

    // ----------------------------------------------------------------
    // Exercise parameters (system properties, fail-fast validation)
    // ----------------------------------------------------------------

    static long longParam(String name, long defaultValue) {
        String raw = System.getProperty(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        long value;
        try {
            value = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("exercise parameter " + name + " is not a long: " + raw);
        }
        if (value < 1L) {
            throw new IllegalArgumentException("exercise parameter " + name + " must be >= 1 (got " + value + ")");
        }
        return value;
    }

    static int intParam(String name, int defaultValue) {
        long value = longParam(name, defaultValue);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("exercise parameter " + name + " exceeds int range: " + value);
        }
        return (int) value;
    }

    static List<Long> msLevelsParam(String name, String defaultValue) {
        String raw = Optional.ofNullable(System.getProperty(name)).orElse(defaultValue);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("exercise parameter " + name + " must not be blank");
        }
        List<Long> levels = new java.util.ArrayList<>();
        for (String part : raw.split(",")) {
            long value;
            try {
                value = Long.parseLong(part.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("exercise parameter " + name
                        + " contains a non-numeric level: '" + part + "' in " + raw);
            }
            if (value < 0L) {
                throw new IllegalArgumentException("exercise parameter " + name
                        + " levels must be >= 0 (got " + value + ")");
            }
            levels.add(value);
        }
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("exercise parameter " + name + " parsed to zero levels: " + raw);
        }
        return levels;
    }

    /** Optional override: null when unset; validated (>= 1) when set — never silently defaulted. */
    static Long optionalLongParam(String name) {
        String raw = System.getProperty(name);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return longParam(name, 1L);
    }

    static void requireDrillArtifactPreservation() {
        assertTrue(Boolean.getBoolean(PRESERVE_PROPERTY),
                "stability exercises MUST run with -D" + PRESERVE_PROPERTY + "=true"
                        + " (drill artifacts under _tmp/ are the closure evidence; MiniStreamCluster"
                        + " deletes the runDir otherwise)");
    }

    // ----------------------------------------------------------------
    // Exercise cluster launch
    // ----------------------------------------------------------------

    /**
     * Spawns an S2 exercise cluster: writes the generated fixture (data lines +
     * watermark-pump tail) to {@code <runDir>/input/part-001.txt} BEFORE start,
     * registers the scenario factory + ops HTTP metrics port on the coordinator.
     */
    static MiniStreamCluster startS2ExerciseCluster(ExerciseLoadGenerator.S2LoadPlan plan,
                                                    long lineDelayMs, long checkpointIntervalMs,
                                                    boolean haMode)
            throws Exception {
        return prepareS2ExerciseCluster(plan, lineDelayMs, checkpointIntervalMs, haMode, null).getCluster();
    }

    /**
     * Item 15 (BP-1): prepared S2 exercise cluster handle. When
     * {@code initialThrottleLevelMs} is non-null, a throttle level file is created
     * under the runDir with that value BEFORE start and registered on the
     * coordinator — the test JVM steps the level live via
     * {@link S2ExerciseClusterHandle#getThrottleLevelFile()}.
     */
    static S2ExerciseClusterHandle prepareS2ExerciseCluster(ExerciseLoadGenerator.S2LoadPlan plan,
                                                            long lineDelayMs, long checkpointIntervalMs,
                                                            boolean haMode, Long initialThrottleLevelMs)
            throws Exception {
        CoreInitialization.initialize();
        MiniStreamCluster cluster = new MiniStreamCluster(2,
                120_000L, 5_000L, 50L);
        Path runDir = cluster.getCheckpointDir().getParent();
        Path inputDir = runDir.resolve("input");
        Path outputDir = runDir.resolve("output");
        Files.createDirectories(inputDir);

        List<String> lines = new java.util.ArrayList<>(plan.getDataLines());
        lines.addAll(plan.getPumpLines());
        Files.write(inputDir.resolve("part-001.txt"), String.join("\n", lines).getBytes(StandardCharsets.UTF_8));

        Path levelFile = null;
        if (initialThrottleLevelMs != null) {
            if (initialThrottleLevelMs < 0L) {
                throw new IllegalArgumentException("initialThrottleLevelMs must be >= 0 (got "
                        + initialThrottleLevelMs + ")");
            }
            levelFile = runDir.resolve("bp-throttle-level.txt");
            Files.writeString(levelFile, String.valueOf(initialThrottleLevelMs), StandardCharsets.UTF_8);
        }

        cluster.withCoordinatorArg("pipelineFactoryClass=" + S2ScenarioPipelineFactory.class.getName());
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_STREAM_PATH + "=" + S2_STREAM_PATH);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S2_INPUT_DIR + "=" + inputDir);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S2_OUTPUT_DIR + "=" + outputDir);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S2_LINE_DELAY + "=" + lineDelayMs);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S2_LINGER + "=" + FINISH_LINGER_MS);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_CHECKPOINT_INTERVAL + "=" + checkpointIntervalMs);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_CHECKPOINT_TIMEOUT + "=30000");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_MAX_RETAINED + "=5");
        cluster.withCoordinatorArg("opsHttpPort=" + System.getProperty("exercise.opsHttpPort", DEFAULT_OPS_HTTP_PORT));
        if (levelFile != null) {
            cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_THROTTLE_LEVEL_FILE + "=" + levelFile);
        }
        cluster.start(haMode);
        return new S2ExerciseClusterHandle(cluster, levelFile);
    }

    /** Cluster + the live-stepped throttle level file (BP-1; null when unused). */
    static final class S2ExerciseClusterHandle {
        private final MiniStreamCluster cluster;
        private final Path throttleLevelFile;

        S2ExerciseClusterHandle(MiniStreamCluster cluster, Path throttleLevelFile) {
            this.cluster = cluster;
            this.throttleLevelFile = throttleLevelFile;
        }

        public MiniStreamCluster getCluster() {
            return cluster;
        }

        public Path getThrottleLevelFile() {
            if (throttleLevelFile == null) {
                throw new IllegalStateException("this cluster was not prepared with a throttle level file");
            }
            return throttleLevelFile;
        }
    }

    /** Spawns an S1 exercise cluster against the generated CDC fixture. */
    static MiniStreamCluster startS1ExerciseCluster(ExerciseLoadGenerator.S1LoadPlan plan,
                                                    long emitDelayMs, long checkpointIntervalMs)
            throws Exception {
        CoreInitialization.initialize();
        MiniStreamCluster cluster = new MiniStreamCluster(2,
                120_000L, 5_000L, 50L);
        Path runDir = cluster.getCheckpointDir().getParent();
        Files.createDirectories(runDir);
        Path eventsFile = runDir.resolve("s1-exercise-events.json");
        DistributedScenarioSupport.writeS1EventsFile(eventsFile, plan.getEvents());

        cluster.withCoordinatorArg("pipelineFactoryClass=" + S1ScenarioPipelineFactory.class.getName());
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S1_EVENTS_FILE + "=" + eventsFile);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S1_EMIT_DELAY + "=" + emitDelayMs);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_S1_LINGER + "=" + FINISH_LINGER_MS);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_CHECKPOINT_INTERVAL + "=" + checkpointIntervalMs);
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_CHECKPOINT_TIMEOUT + "=30000");
        cluster.withCoordinatorArg(DistributedScenarioSupport.KEY_MAX_RETAINED + "=5");
        cluster.withCoordinatorArg("opsHttpPort=" + System.getProperty("exercise.opsHttpPort", DEFAULT_OPS_HTTP_PORT));

        MultiJvmTestSupport.resetS1Tables(cluster);
        cluster.start();
        return cluster;
    }

    static Path samplesDirOf(MiniStreamCluster cluster) {
        return cluster.getRunDir().resolve("samples");
    }

    static Path outputDirOf(MiniStreamCluster cluster) {
        return cluster.getRunDir().resolve("output");
    }

    // ----------------------------------------------------------------
    // Cluster-bound sample sources
    // ----------------------------------------------------------------

    /** S2 flavor: output progress = committed output rows in the epoch files. */
    static ExerciseSampler.SampleSources s2SampleSources(MiniStreamCluster cluster, String jobId) {
        return new ClusterSampleSources(cluster, jobId, true);
    }

    /** S1 flavor: output progress = committed alert rows (SQL COUNT). */
    static ExerciseSampler.SampleSources s1SampleSources(MiniStreamCluster cluster, String jobId) {
        return new ClusterSampleSources(cluster, jobId, false);
    }

    static final class ClusterSampleSources implements ExerciseSampler.SampleSources {

        private final MiniStreamCluster cluster;
        private final String jobId;
        private final boolean s2Mode;
        private final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build();
        private volatile String lastMetricsNote;

        ClusterSampleSources(MiniStreamCluster cluster, String jobId, boolean s2Mode) {
            this.cluster = cluster;
            this.jobId = jobId;
            this.s2Mode = s2Mode;
        }

        @Override
        public Map<String, Double> fetchMetrics() {
            String port = System.getProperty("exercise.opsHttpPort", DEFAULT_OPS_HTTP_PORT);
            try {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/metrics")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    lastMetricsNote = "metrics-http-" + response.statusCode();
                    return Map.of();
                }
                lastMetricsNote = null;
                return ExerciseSampler.parsePrometheusText(response.body());
            } catch (IOException e) {
                lastMetricsNote = "metrics-fetch-failed: " + e.getClass().getSimpleName();
                return Map.of();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                lastMetricsNote = "metrics-interrupted";
                return Map.of();
            }
        }

        String lastMetricsNote() {
            return lastMetricsNote;
        }

        @Override
        public long fetchQueueDepth() {
            try {
                Long count = cluster.getHarnessJdbcTemplate().executeQuery(SQL.begin()
                        .sql("SELECT COUNT(*) FROM nop_stream_msg_queue").end(), ds -> {
                    if (!ds.hasNext()) {
                        return 0L;
                    }
                    return ds.next().getLong(0);
                });
                return count == null ? -1L : count;
            } catch (Exception e) {
                return -1L;
            }
        }

        @Override
        public long fetchDurableEpoch() {
            return MultiJvmTestSupport.currentDurableManifestEpoch(cluster, jobId);
        }

        @Override
        public long fetchRetainedManifestCount() {
            Path pipelineDir = cluster.getCheckpointDir().resolve(jobId).resolve("pipeline-0");
            if (!Files.isDirectory(pipelineDir)) {
                return 0L;
            }
            try (var stream = Files.list(pipelineDir)) {
                return stream.filter(p -> p.toString().endsWith(".epoch")).count();
            } catch (IOException e) {
                return -1L;
            }
        }

        @Override
        public long fetchOutputProgress() {
            if (s2Mode) {
                return countS2OutputRows(outputDirOf(cluster));
            }
            try {
                Long count = cluster.getHarnessJdbcTemplate().executeQuery(SQL.begin()
                        .sql("SELECT COUNT(*) FROM " + ScenarioTestSupport.ALERT_TABLE).end(), ds -> {
                    if (!ds.hasNext()) {
                        return 0L;
                    }
                    return ds.next().getLong(0);
                });
                return count == null ? 0L : count;
            } catch (Exception e) {
                return 0L;
            }
        }

        @Override
        public Map<String, Boolean> fetchAlive() {
            Map<String, Boolean> alive = new LinkedHashMap<>();
            alive.put("coordinator-0", cluster.coordinatorAlive());
            alive.put("coordinator-1", cluster.coordinatorAlive(1));
            alive.put("tm-0", cluster.taskManagerAlive("tm-0"));
            alive.put("tm-1", cluster.taskManagerAlive("tm-1"));
            return alive;
        }
    }

    static long countS2OutputRows(Path outputDir) {
        if (!Files.isDirectory(outputDir)) {
            return 0L;
        }
        long rows = 0L;
        try (var files = Files.list(outputDir)) {
            for (Path file : files.sorted().toList()) {
                if (!file.getFileName().toString().startsWith("epoch-")) {
                    continue;
                }
                try {
                    rows += Files.readAllLines(file).stream().filter(l -> !l.isBlank()).count();
                } catch (IOException ignored) {
                    // epoch file being atomically renamed mid-read; next tick re-counts
                }
            }
        } catch (IOException e) {
            return -1L;
        }
        return rows;
    }

    // ----------------------------------------------------------------
    // Chaos loop + partition-equivalent rounds + HA lease helpers
    // ----------------------------------------------------------------

    /** Sends a POSIX signal to a child process (SIGSTOP/SIGCONT for partition rounds). */
    static void sendSignal(long pid, String signal) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("/bin/kill", "-" + signal, String.valueOf(pid))
                .redirectErrorStream(true).start();
        boolean finished = p.waitFor(10, TimeUnit.SECONDS);
        if (!finished || p.exitValue() != 0) {
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException("kill -" + signal + " " + pid + " failed (exit="
                    + (finished ? p.exitValue() : "timeout") + "): " + output);
        }
    }

    static final class LeaseRow {
        final String leaderId;
        final long leaderEpoch;

        LeaseRow(String leaderId, long leaderEpoch) {
            this.leaderId = leaderId;
            this.leaderEpoch = leaderEpoch;
        }
    }

    static LeaseRow readLeaseRow(MiniStreamCluster cluster) {
        try {
            return cluster.getHarnessJdbcTemplate().executeQuery(SQL.begin()
                    .sql("SELECT leader_id, leader_epoch FROM nop_stream_leader WHERE cluster_id = ?",
                            "job-" + cluster.getRunId())
                    .end(), ds -> {
                if (!ds.hasNext()) {
                    return null;
                }
                var row = ds.next();
                return new LeaseRow(row.getString(0), row.getLong(1));
            });
        } catch (Exception e) {
            return null;
        }
    }

    /** Appends one chaos event (kill round outcome) to the drill event log (JSONL). */
    static synchronized void appendChaosEvent(Path chaosEventsFile, Map<String, Object> event)
            throws IOException {
        Files.createDirectories(chaosEventsFile.getParent());
        Files.writeString(chaosEventsFile, JsonTool.serialize(event, false) + "\n",
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    // ----------------------------------------------------------------
    // Run summary
    // ----------------------------------------------------------------

    static void writeRunSummary(MiniStreamCluster cluster, String cellName,
                                Map<String, Object> parameters, String verdict,
                                String evidence, Map<String, Object> observations) throws IOException {
        Path dir = samplesDirOf(cluster);
        Files.createDirectories(dir);
        Map<String, Object> summary = new TreeMap<>();
        summary.put("cell", cellName);
        summary.put("runId", cluster.getRunId());
        summary.put("runDir", cluster.getRunDir().toString());
        summary.put("parameters", parameters);
        summary.put("verdict", verdict);
        summary.put("evidence", evidence);
        summary.put("observations", observations);
        Files.write(dir.resolve("run-summary.json"),
                JsonTool.serialize(summary, true).getBytes(StandardCharsets.UTF_8));
        LOG.log(System.Logger.Level.INFO, "exercise {0} verdict={1} summary={2}",
                cellName, verdict, dir.resolve("run-summary.json"));
    }

}
