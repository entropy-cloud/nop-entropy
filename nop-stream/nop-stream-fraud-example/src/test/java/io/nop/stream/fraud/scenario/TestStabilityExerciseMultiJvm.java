/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.nop.core.lang.sql.SQL;
import io.nop.stream.fraud.scenario.ChaosKillPlanner.KillStep;
import io.nop.stream.fraud.scenario.ExerciseSampler.SampleRecord;
import io.nop.stream.runtime.multijvm.MiniStreamCluster;

import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.COORDINATOR_LABEL;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.currentDurableManifestEpoch;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.readLatestFencingEpoch;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.readLogDelta;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitForDurableManifest;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitForEpochRotation;
import static io.nop.stream.fraud.scenario.MultiJvmTestSupport.waitForExactlyOnceOutput;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.readAlertRows;
import static io.nop.stream.fraud.scenario.ScenarioTestSupport.sameRows;
import static io.nop.stream.fraud.scenario.StabilityExerciseSupport.appendChaosEvent;
import static io.nop.stream.fraud.scenario.StabilityExerciseSupport.countS2OutputRows;
import static io.nop.stream.fraud.scenario.StabilityExerciseSupport.intParam;
import static io.nop.stream.fraud.scenario.StabilityExerciseSupport.longParam;
import static io.nop.stream.fraud.scenario.StabilityExerciseSupport.msLevelsParam;
import static io.nop.stream.fraud.scenario.StabilityExerciseSupport.outputDirOf;
import static io.nop.stream.fraud.scenario.StabilityExerciseSupport.readLeaseRow;
import static io.nop.stream.fraud.scenario.StabilityExerciseSupport.requireDrillArtifactPreservation;
import static io.nop.stream.fraud.scenario.StabilityExerciseSupport.s1SampleSources;
import static io.nop.stream.fraud.scenario.StabilityExerciseSupport.s2SampleSources;
import static io.nop.stream.fraud.scenario.StabilityExerciseSupport.samplesDirOf;
import static io.nop.stream.fraud.scenario.StabilityExerciseSupport.sendSignal;
import static io.nop.stream.fraud.scenario.StabilityExerciseSupport.writeRunSummary;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 15 (stability exercise): the PARAMETERIZED JOINT ENTRY for the whole drill
 * matrix (Phase 1 definitions in {@code ai-dev/analysis/2026-09/
 * 2026-09-03-distributed-stability-exercise-report.md}). Every cell — soak (S2
 * sustained+state-growth, S1 CDC sustained, S2 high-rate item-28 observation),
 * chaos (random TM kill loop incl. SIGSTOP/SIGCONT partition-equivalent rounds,
 * HA JC failover) and backpressure (stepped throttle) — runs through this class,
 * driving the Phase-2 devices ({@code ExerciseLoadGenerator} + {@code ChaosKillPlanner}
 * + {@code ExerciseSampler}); no cell assembles a one-off test.
 *
 * <p>Run any cell (preserve-artifacts is MANDATORY — the entry fails fast without it):
 * <pre>
 * ./mvnw test -pl nop-stream/nop-stream-fraud-example -am -T 1C \
 *   -Dtest=TestStabilityExerciseMultiJvm#soakS2 \
 *   -Dnop.stream.test.multi-jvm.enabled=true \
 *   -Dnop.stream.test.multi-jvm.preserve-artifacts=true \
 *   -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * <p>Verdict semantics: test green = cell {@code pass} (all Phase-1 criteria held,
 * artifacts + run-summary.json persisted under {@code _tmp/mini-stream-cluster/<runId>/});
 * test red = {@code fail} or a hang-signal hit (the report then classifies per the
 * hang rule: verify item-28 attribution FIRST, then judge new-defect vs boundary).
 */
@EnabledIfSystemProperty(named = "nop.stream.test.multi-jvm.enabled", matches = "true")
class TestStabilityExerciseMultiJvm {

    /** Convergence slack on top of the planned emission window. */
    private static final long CONVERGENCE_SLACK_MS = 300_000L;
    /** Soak pass criteria: no durable-epoch advance gap longer than this. */
    private static final long SOAK_MAX_EPOCH_GAP_MS = 60_000L;
    /** Chaos / high-rate pass criteria: looser gap (recovery windows included). */
    private static final long CHAOS_MAX_EPOCH_GAP_MS = 120_000L;
    /** Queue-depth threshold for the unbounded-growth leak signal. */
    private static final long QUEUE_LEAK_THRESHOLD = 100L;
    private static final long RECOVERY_TIMEOUT_MS = 120_000L;

    // ==================== SOAK-1: S2 sustained + state growth ====================

    @Test
    void soakS2() throws Exception {
        requireDrillArtifactPreservation();
        long durationSec = longParam("exercise.soak.durationSec", 600L);
        long lineDelayMs = longParam("exercise.soak.lineDelayMs", 50L);
        int linesPerUser = intParam("exercise.soak.linesPerUser", 100);
        long checkpointIntervalMs = longParam("exercise.soak.checkpointIntervalMs", 2000L);
        long sampleIntervalMs = longParam("exercise.sample.intervalMs", 5000L);

        ExerciseLoadGenerator.S2LoadPlan plan = ExerciseLoadGenerator.s2LoadPlan(durationSec, lineDelayMs, linesPerUser);
        try (MiniStreamCluster cluster = StabilityExerciseSupport.startS2ExerciseCluster(
                plan, lineDelayMs, checkpointIntervalMs, false)) {
            String jobId = "job-" + cluster.getRunId();
            try (ExerciseSampler sampler = new ExerciseSampler(
                    samplesDirOf(cluster).resolve("samples.jsonl"), sampleIntervalMs,
                    s2SampleSources(cluster, jobId))) {
                sampler.start();
                try {
                    waitForExactlyOnceOutput(outputDirOf(cluster), plan.getExpectedRows(),
                            plan.getPlannedEmissionMs() + CONVERGENCE_SLACK_MS, "SOAK-1 soakS2");
                    assertSoakHealth(sampler, cluster, SOAK_MAX_EPOCH_GAP_MS, "SOAK-1");
                    writeRunSummary(cluster, "SOAK-1-soakS2",
                            soakParams(durationSec, lineDelayMs, linesPerUser, checkpointIntervalMs, sampleIntervalMs),
                            "pass", "samples.jsonl + output dir + checkpoints preserved under " + cluster.getRunDir(),
                            soakObservations(sampler, plan, "S2"));
                } catch (AssertionError e) {
                    writeCellCriteriaViolation(cluster, "SOAK-1-soakS2",
                            soakParams(durationSec, lineDelayMs, linesPerUser, checkpointIntervalMs, sampleIntervalMs),
                            sampler, plan, e);
                    throw e;
                }
            }
        }
    }

    // ==================== SOAK-2: S1 CDC sustained + keyed state growth ====================

    @Test
    void soakS1() throws Exception {
        requireDrillArtifactPreservation();
        long durationSec = longParam("exercise.soak.durationSec", 600L);
        long emitDelayMs = longParam("exercise.soak.emitDelayMs", 100L);
        long checkpointIntervalMs = longParam("exercise.soak.checkpointIntervalMs", 2000L);
        long sampleIntervalMs = longParam("exercise.sample.intervalMs", 5000L);

        ExerciseLoadGenerator.S1LoadPlan plan = ExerciseLoadGenerator.s1LoadPlan(durationSec, emitDelayMs);
        try (MiniStreamCluster cluster = StabilityExerciseSupport.startS1ExerciseCluster(
                plan, emitDelayMs, checkpointIntervalMs)) {
            String jobId = "job-" + cluster.getRunId();
            try (ExerciseSampler sampler = new ExerciseSampler(
                    samplesDirOf(cluster).resolve("samples.jsonl"), sampleIntervalMs,
                    s1SampleSources(cluster, jobId))) {
                sampler.start();
                try {
                    long timeoutMs = plan.getPlannedEmissionMs() + CONVERGENCE_SLACK_MS;
                    MultiJvmTestSupport.waitFor(() -> sameRows(
                            readAlertRows(cluster.getHarnessJdbcTemplate()), plan.getExpectedAlerts()),
                            timeoutMs, "SOAK-2 soakS1 must converge to the exact expected alert set (expected "
                                    + plan.getExpectedAlerts().size() + " rows). Coordinator log tail: "
                                    + MultiJvmTestSupport.readLogTail(cluster, COORDINATOR_LABEL));
                    Long alertCount = cluster.getHarnessJdbcTemplate().executeQuery(SQL.begin()
                            .sql("SELECT COUNT(*) FROM " + ScenarioTestSupport.ALERT_TABLE).end(),
                            ds -> ds.next().getLong(0));
                    assertEquals(plan.getExpectedAlerts().size(), alertCount.longValue(),
                            "alert table COUNT must equal the expected count (Set equality hides duplicates)");
                    assertSoakHealth(sampler, cluster, SOAK_MAX_EPOCH_GAP_MS, "SOAK-2");
                    writeRunSummary(cluster, "SOAK-2-soakS1",
                            soakParams(durationSec, emitDelayMs, 0, checkpointIntervalMs, sampleIntervalMs),
                            "pass", "samples.jsonl + alert table + ledgers + checkpoints preserved under "
                                    + cluster.getRunDir(),
                            soakObservations(sampler, plan, "S1"));
                } catch (AssertionError e) {
                    writeCellCriteriaViolation(cluster, "SOAK-2-soakS1",
                            soakParams(durationSec, emitDelayMs, 0, checkpointIntervalMs, sampleIntervalMs),
                            sampler, plan, e);
                    throw e;
                }
            }
        }
    }

    // ==================== SOAK-3: S2 high-rate burst (item 28 observation) ====================

    @Test
    void soakHighRateItem28Observation() throws Exception {
        requireDrillArtifactPreservation();
        long durationSec = longParam("exercise.soak.highRate.durationSec", 180L);
        long lineDelayMs = longParam("exercise.soak.highRate.lineDelayMs", 5L);
        int linesPerUser = intParam("exercise.soak.highRate.linesPerUser", 200);
        long checkpointIntervalMs = longParam("exercise.soak.checkpointIntervalMs", 2000L);
        long sampleIntervalMs = longParam("exercise.sample.intervalMs", 5000L);

        ExerciseLoadGenerator.S2LoadPlan plan = ExerciseLoadGenerator.s2LoadPlan(durationSec, lineDelayMs, linesPerUser);
        try (MiniStreamCluster cluster = StabilityExerciseSupport.startS2ExerciseCluster(
                plan, lineDelayMs, checkpointIntervalMs, false)) {
            String jobId = "job-" + cluster.getRunId();
            try (ExerciseSampler sampler = new ExerciseSampler(
                    samplesDirOf(cluster).resolve("samples.jsonl"), sampleIntervalMs,
                    s2SampleSources(cluster, jobId))) {
                sampler.start();
                try {
                    waitForExactlyOnceOutput(outputDirOf(cluster), plan.getExpectedRows(),
                            plan.getPlannedEmissionMs() + CONVERGENCE_SLACK_MS, "SOAK-3 high-rate");
                    assertSoakHealth(sampler, cluster, CHAOS_MAX_EPOCH_GAP_MS, "SOAK-3");
                    writeRunSummary(cluster, "SOAK-3-soakHighRateItem28Observation",
                            soakParams(durationSec, lineDelayMs, linesPerUser, checkpointIntervalMs, sampleIntervalMs),
                            "pass", "samples.jsonl (queue-depth/epoch/output series under sustained ~"
                                    + (1000L / lineDelayMs) + " lines/s) preserved under " + cluster.getRunDir(),
                            soakObservations(sampler, plan, "S2-high-rate"));
                } catch (AssertionError e) {
                    writeCellCriteriaViolation(cluster, "SOAK-3-soakHighRateItem28Observation",
                            soakParams(durationSec, lineDelayMs, linesPerUser, checkpointIntervalMs, sampleIntervalMs),
                            sampler, plan, e);
                    throw e;
                }
            }
        }
    }

    // ==================== CHAOS-1: S2 random kill TM loop (+ partition rounds) ====================

    @Test
    void chaosKillLoop() throws Exception {
        requireDrillArtifactPreservation();
        int rounds = intParam("exercise.chaos.rounds", 8);
        long minDelayMs = longParam("exercise.chaos.killDelayMinMs", 20_000L);
        long maxDelayMs = longParam("exercise.chaos.killDelayMaxMs", 60_000L);
        int partitionFromRound = intParam("exercise.chaos.partitionFromRound", 7);
        long seed = longParam("exercise.chaos.seed", 15L);
        long partitionPauseMs = longParam("exercise.chaos.partitionPauseMs", 25_000L);
        long lineDelayMs = longParam("exercise.chaos.lineDelayMs", 100L);
        int linesPerUser = intParam("exercise.soak.linesPerUser", 100);
        long checkpointIntervalMs = longParam("exercise.chaos.checkpointIntervalMs", 400L);
        long sampleIntervalMs = longParam("exercise.sample.intervalMs", 5000L);

        List<KillStep> steps = ChaosKillPlanner.plan(rounds, minDelayMs, maxDelayMs, partitionFromRound, seed);
        long killSpanMs = ChaosKillPlanner.totalPlannedDelayMs(steps) + rounds * 30_000L;
        long durationSec = Math.max(120L, (killSpanMs + 240_000L) / 1000L);
        Long overrideSec = StabilityExerciseSupport.optionalLongParam("exercise.chaos.durationSec");
        if (overrideSec != null) {
            if (overrideSec * 1000L < killSpanMs + 20_000L) {
                throw new IllegalArgumentException("exercise.chaos.durationSec=" + overrideSec
                        + " is too small for the kill schedule (needs >= " + (killSpanMs + 20_000L) / 1000L + "s)");
            }
            durationSec = overrideSec;
        }

        ExerciseLoadGenerator.S2LoadPlan plan = ExerciseLoadGenerator.s2LoadPlan(durationSec, lineDelayMs, linesPerUser);
        try (MiniStreamCluster cluster = StabilityExerciseSupport.startS2ExerciseCluster(
                plan, lineDelayMs, checkpointIntervalMs, false)) {
            String jobId = "job-" + cluster.getRunId();
            Path chaosEvents = samplesDirOf(cluster).resolve("chaos-events.jsonl");
            try (ExerciseSampler sampler = new ExerciseSampler(
                    samplesDirOf(cluster).resolve("samples.jsonl"), sampleIntervalMs,
                    s2SampleSources(cluster, jobId))) {
                sampler.start();
                waitForDurableManifest(cluster, jobId, 120_000L);

                long emissionEndWallMs = sampler.getStartWallMs() + plan.getPlannedEmissionMs();
                long lastFencingEpoch = readLatestFencingEpoch(cluster);
                for (KillStep step : steps) {
                    sleepUntilKillTime(step, emissionEndWallMs);
                    lastFencingEpoch = executeChaosStep(cluster, chaosEvents, step, lastFencingEpoch, partitionPauseMs);
                }

                try {
                    waitForExactlyOnceOutput(outputDirOf(cluster), plan.getExpectedRows(),
                            plan.getPlannedEmissionMs() + CONVERGENCE_SLACK_MS, "CHAOS-1 kill loop");
                    assertSoakHealth(sampler, cluster, CHAOS_MAX_EPOCH_GAP_MS, "CHAOS-1");
                    Map<String, Object> params = chaosParams(durationSec, lineDelayMs, linesPerUser,
                            checkpointIntervalMs, sampleIntervalMs, rounds, minDelayMs, maxDelayMs,
                            (long) partitionFromRound, seed);
                    writeRunSummary(cluster, "CHAOS-1-chaosKillLoop", params, "pass",
                            "chaos-events.jsonl (per-round fencing epochs) + samples.jsonl preserved under "
                                    + cluster.getRunDir(), soakObservations(sampler, plan, "S2-chaos"));
                } catch (AssertionError e) {
                    Map<String, Object> params = chaosParams(durationSec, lineDelayMs, linesPerUser,
                            checkpointIntervalMs, sampleIntervalMs, rounds, minDelayMs, maxDelayMs,
                            (long) partitionFromRound, seed);
                    writeCellCriteriaViolation(cluster, "CHAOS-1-chaosKillLoop", params, sampler, plan, e);
                    throw e;
                }
            }
        }
    }

    private static void sleepUntilKillTime(KillStep step, long emissionEndWallMs) throws InterruptedException {
        long killWallMs = System.currentTimeMillis() + step.getDelayMsBefore();
        assertTrue(killWallMs < emissionEndWallMs - 20_000L,
                "kill schedule must land inside the emission window (step=" + step
                        + ") — fixture sizing regression");
        while (System.currentTimeMillis() < killWallMs) {
            TimeUnit.MILLISECONDS.sleep(Math.min(1000L, killWallMs - System.currentTimeMillis()));
        }
    }

    private static long executeChaosStep(MiniStreamCluster cluster, Path chaosEvents, KillStep step,
                                         long lastFencingEpoch, long partitionPauseMs) throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("wallMs", System.currentTimeMillis());
        event.put("round", (long) step.getRound());
        event.put("kind", step.getKind().name());
        event.put("victim", step.getVictim());
        event.put("delayMsBefore", step.getDelayMsBefore());
        event.put("fencingEpochBefore", lastFencingEpoch);
        long rotated;
        if (step.getKind() == ChaosKillPlanner.StepKind.KILL_RESTART_TM) {
            assertTrue(cluster.killTaskManager(step.getVictim()), "kill must succeed: " + step);
            cluster.restartTaskManager(step.getVictim());
            rotated = waitForEpochRotation(cluster, lastFencingEpoch, RECOVERY_TIMEOUT_MS);
        } else {
            long pid = cluster.taskManagerPid(step.getVictim());
            assertTrue(pid > 0, "partition round needs a live TM pid: " + step);
            java.nio.file.Path log = cluster.logFileFor(step.getVictim());
            long logSizeBefore = java.nio.file.Files.size(log);
            sendSignal(pid, "STOP");
            try {
                long deadline = System.currentTimeMillis() + partitionPauseMs + 30_000L;
                rotated = lastFencingEpoch;
                while (System.currentTimeMillis() < deadline) {
                    rotated = readLatestFencingEpoch(cluster);
                    if (rotated > lastFencingEpoch) {
                        break;
                    }
                    TimeUnit.MILLISECONDS.sleep(1000L);
                }
                assertTrue(rotated > lastFencingEpoch,
                        "partition round: lease expiry must trigger recovery while the TM is paused: " + step);
                sendSignal(pid, "CONT");
                TimeUnit.MILLISECONDS.sleep(5000L);
                String delta = readLogDelta(log, logSizeBefore);
                event.put("staleViewRejectedOnResume", delta.contains("ERR_STREAM_FENCING_TOKEN_MISMATCH")
                        || delta.contains("fencing-token-mismatch")
                        || delta.contains("process-request-fail"));
            } finally {
                sendSignal(pid, "CONT");
            }
            cluster.restartTaskManager(step.getVictim());
        }
        long fencingAfter = readLatestFencingEpoch(cluster);
        event.put("fencingEpochAfter", fencingAfter);
        assertTrue(fencingAfter > lastFencingEpoch,
                "fencing epoch must strictly increase per round: " + event);
        appendChaosEvent(chaosEvents, event);
        return fencingAfter;
    }

    // ==================== CHAOS-2: S2 HA JC failover loop ====================

    @Test
    void chaosJcHaFailover() throws Exception {
        requireDrillArtifactPreservation();
        int rounds = intParam("exercise.chaos.jc.rounds", 2);
        long minDelayMs = longParam("exercise.chaos.jc.killDelayMinMs", 20_000L);
        long maxDelayMs = longParam("exercise.chaos.jc.killDelayMaxMs", 40_000L);
        long seed = longParam("exercise.chaos.jc.seed", 16L);
        long lineDelayMs = longParam("exercise.chaos.lineDelayMs", 100L);
        int linesPerUser = intParam("exercise.soak.linesPerUser", 100);
        long checkpointIntervalMs = longParam("exercise.chaos.checkpointIntervalMs", 400L);
        long sampleIntervalMs = longParam("exercise.sample.intervalMs", 5000L);

        List<KillStep> steps = ChaosKillPlanner.plan(rounds, minDelayMs, maxDelayMs, rounds + 1, seed);
        long durationSec = Math.max(120L,
                (ChaosKillPlanner.totalPlannedDelayMs(steps) + rounds * 60_000L + 240_000L) / 1000L);

        ExerciseLoadGenerator.S2LoadPlan plan = ExerciseLoadGenerator.s2LoadPlan(durationSec, lineDelayMs, linesPerUser);
        try (MiniStreamCluster cluster = StabilityExerciseSupport.startS2ExerciseCluster(
                plan, lineDelayMs, checkpointIntervalMs, true)) {
            String jobId = "job-" + cluster.getRunId();
            Path chaosEvents = samplesDirOf(cluster).resolve("chaos-events.jsonl");
            try (ExerciseSampler sampler = new ExerciseSampler(
                    samplesDirOf(cluster).resolve("samples.jsonl"), sampleIntervalMs,
                    s2SampleSources(cluster, jobId))) {
                sampler.start();
                waitForLeaseLeaderId(cluster, "coordinator-0", 60_000L);
                waitForDurableManifest(cluster, jobId, 120_000L);

                int nextSpawnIndex = 1;
                cluster.spawnJobCoordinator(nextSpawnIndex);
                for (KillStep step : steps) {
                    sleepFixed(step.getDelayMsBefore());
                    StabilityExerciseSupport.LeaseRow before = readLeaseRow(cluster);
                    assertTrue(before != null, "lease row must exist before the kill");
                    int leaderIndex = parseCoordinatorIndex(before.leaderId);
                    long fencingBefore = readLatestFencingEpoch(cluster);

                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("wallMs", System.currentTimeMillis());
                    event.put("round", (long) step.getRound());
                    event.put("kind", "KILL_LEADER");
                    event.put("victim", "coordinator-" + leaderIndex);
                    event.put("leaseBefore", before.leaderId + ":" + before.leaderEpoch);
                    event.put("fencingEpochBefore", fencingBefore);

                    assertTrue(cluster.killCoordinator(leaderIndex), "leader kill must succeed");
                    nextSpawnIndex++;
                    cluster.spawnJobCoordinator(nextSpawnIndex);
                    StabilityExerciseSupport.LeaseRow after = waitForLeaseAwayFrom(cluster,
                            "coordinator-" + leaderIndex, 90_000L);
                    long fencingAfter = waitForEpochRotation(cluster, fencingBefore, RECOVERY_TIMEOUT_MS);
                    assertTrue(after.leaderEpoch > before.leaderEpoch,
                            "lease epoch must strictly rotate on failover: before=" + before.leaderId + ":"
                                    + before.leaderEpoch + " after=" + after.leaderId + ":" + after.leaderEpoch);
                    assertTrue(fencingAfter > fencingBefore,
                            "assignment fencing epoch must strictly rotate on failover");
                    event.put("leaseAfter", after.leaderId + ":" + after.leaderEpoch);
                    event.put("fencingEpochAfter", fencingAfter);
                    appendChaosEvent(chaosEvents, event);
                }

                try {
                    waitForExactlyOnceOutput(outputDirOf(cluster), plan.getExpectedRows(),
                            plan.getPlannedEmissionMs() + CONVERGENCE_SLACK_MS, "CHAOS-2 JC failover");
                    assertSoakHealth(sampler, cluster, CHAOS_MAX_EPOCH_GAP_MS, "CHAOS-2");
                    Map<String, Object> params = chaosParams(durationSec, lineDelayMs, linesPerUser,
                            checkpointIntervalMs, sampleIntervalMs, rounds, minDelayMs, maxDelayMs,
                            rounds + 1L, seed);
                    writeRunSummary(cluster, "CHAOS-2-chaosJcHaFailover", params, "pass",
                            "chaos-events.jsonl (per-round lease flip + epoch rotation) + samples.jsonl preserved under "
                                    + cluster.getRunDir(), soakObservations(sampler, plan, "S2-jc-failover"));
                } catch (AssertionError e) {
                    Map<String, Object> params = chaosParams(durationSec, lineDelayMs, linesPerUser,
                            checkpointIntervalMs, sampleIntervalMs, rounds, minDelayMs, maxDelayMs,
                            rounds + 1L, seed);
                    writeCellCriteriaViolation(cluster, "CHAOS-2-chaosJcHaFailover", params, sampler, plan, e);
                    throw e;
                }
            }
        }
    }

    private static void sleepFixed(long delayMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + delayMs;
        while (System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(Math.min(1000L, deadline - System.currentTimeMillis()));
        }
    }

    private static int parseCoordinatorIndex(String leaderId) {
        String prefix = "coordinator-";
        if (leaderId == null || !leaderId.startsWith(prefix)) {
            throw new IllegalStateException("unexpected lease leaderId: " + leaderId);
        }
        String suffix = leaderId.substring(prefix.length());
        int dash = suffix.indexOf('-');
        if (dash >= 0) {
            suffix = suffix.substring(0, dash);
        }
        return Integer.parseInt(suffix.trim());
    }

    private static void waitForLeaseLeaderId(MiniStreamCluster cluster, String contains, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            StabilityExerciseSupport.LeaseRow row = readLeaseRow(cluster);
            if (row != null && row.leaderId.contains(contains) && row.leaderEpoch >= 1) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(500L);
        }
        throw new AssertionError("lease leader '" + contains + "' not observed within " + timeoutMs
                + "ms; lease=" + readLeaseRow(cluster));
    }

    private static StabilityExerciseSupport.LeaseRow waitForLeaseAwayFrom(MiniStreamCluster cluster,
                                                                          String killedLeaderId, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            StabilityExerciseSupport.LeaseRow row = readLeaseRow(cluster);
            if (row != null && !row.leaderId.contains(killedLeaderId) && row.leaderEpoch >= 1) {
                return row;
            }
            TimeUnit.MILLISECONDS.sleep(500L);
        }
        throw new AssertionError("standby takeover not observed within " + timeoutMs + "ms after killing "
                + killedLeaderId + "; lease=" + readLeaseRow(cluster));
    }

    // ==================== BP-1: S2 stepped throttle backpressure ====================

    @Test
    void backpressureSteppedThrottle() throws Exception {
        requireDrillArtifactPreservation();
        List<Long> levels = msLevelsParam("exercise.bp.levels", "50,200,500");
        long perLevelSec = longParam("exercise.bp.perLevelSec", 60L);
        long lineDelayMs = longParam("exercise.bp.lineDelayMs", 50L);
        int linesPerUser = intParam("exercise.soak.linesPerUser", 100);
        long checkpointIntervalMs = longParam("exercise.bp.checkpointIntervalMs", 2000L);
        long sampleIntervalMs = longParam("exercise.sample.intervalMs", 5000L);
        long durationSec = longParam("exercise.bp.durationSec", levels.size() * perLevelSec + 240L);

        ExerciseLoadGenerator.S2LoadPlan plan = ExerciseLoadGenerator.s2LoadPlan(durationSec, lineDelayMs, linesPerUser);
        StabilityExerciseSupport.S2ExerciseClusterHandle handle =
                StabilityExerciseSupport.prepareS2ExerciseCluster(plan, lineDelayMs, checkpointIntervalMs,
                        false, levels.get(0));
        try (MiniStreamCluster cluster = handle.getCluster()) {
            String jobId = "job-" + cluster.getRunId();
            try (ExerciseSampler sampler = new ExerciseSampler(
                    samplesDirOf(cluster).resolve("samples.jsonl"), sampleIntervalMs,
                    s2SampleSources(cluster, jobId))) {
                sampler.start();
                waitForDurableManifest(cluster, jobId, 120_000L);

                List<Map<String, Object>> levelObservations = new ArrayList<>();
                for (int i = 0; i < levels.size(); i++) {
                    long level = levels.get(i);
                    java.nio.file.Files.writeString(handle.getThrottleLevelFile(),
                            String.valueOf(level), java.nio.charset.StandardCharsets.UTF_8);
                    long epochStart = currentDurableManifestEpoch(cluster, jobId);
                    long outputStart = countS2OutputRows(outputDirOf(cluster));
                    long windowStartWall = System.currentTimeMillis();
                    sleepFixed(perLevelSec * 1000L);
                    long epochEnd = currentDurableManifestEpoch(cluster, jobId);
                    long outputEnd = countS2OutputRows(outputDirOf(cluster));
                    assertTrue(cluster.coordinatorAlive(),
                            "coordinator must stay alive under throttle level " + level);
                    assertTrue(epochEnd > epochStart, "checkpoint must advance under throttle level "
                            + level + "ms (C3 no-deadlock semantics)");
                    Map<String, Object> observation = new LinkedHashMap<>();
                    observation.put("levelMs", level);
                    observation.put("windowWallMs", System.currentTimeMillis() - windowStartWall);
                    observation.put("epochAdvance", epochEnd - epochStart);
                    observation.put("outputRowDelta", outputEnd - outputStart);
                    levelObservations.add(observation);
                }
                java.nio.file.Files.writeString(handle.getThrottleLevelFile(), "0",
                        java.nio.charset.StandardCharsets.UTF_8);

                try {
                    waitForExactlyOnceOutput(outputDirOf(cluster), plan.getExpectedRows(),
                            plan.getPlannedEmissionMs() + CONVERGENCE_SLACK_MS, "BP-1 stepped throttle release");
                    assertSoakHealth(sampler, cluster, CHAOS_MAX_EPOCH_GAP_MS, "BP-1");
                    Map<String, Object> params = soakParams(durationSec, lineDelayMs, linesPerUser,
                            checkpointIntervalMs, sampleIntervalMs);
                    params.put("levels", levels.toString());
                    params.put("perLevelSec", perLevelSec);
                    Map<String, Object> observations = soakObservations(sampler, plan, "S2-backpressure");
                    observations.put("levelObservations", levelObservations);
                    writeRunSummary(cluster, "BP-1-backpressureSteppedThrottle", params, "pass",
                            "samples.jsonl + per-level output/epoch deltas + output dir preserved under "
                                    + cluster.getRunDir(), observations);
                } catch (AssertionError e) {
                    Map<String, Object> params = soakParams(durationSec, lineDelayMs, linesPerUser,
                            checkpointIntervalMs, sampleIntervalMs);
                    params.put("levels", levels.toString());
                    params.put("perLevelSec", perLevelSec);
                    writeCellCriteriaViolation(cluster, "BP-1-backpressureSteppedThrottle", params, sampler, plan, e);
                    throw e;
                }
            }
        }
    }

    // ==================== shared assertions / summaries ====================

    /** Failed cells still persist a run-summary (evidence-first: classify from artifacts). */
    private static void writeCellCriteriaViolation(MiniStreamCluster cluster, String cell,
                                                   Map<String, Object> params, ExerciseSampler sampler,
                                                   ExerciseLoadGenerator.S2LoadPlan plan, AssertionError e) {
        try {
            Map<String, Object> observations = soakObservations(sampler, plan, "criteria-violated");
            observations.put("violation", String.valueOf(e.getMessage() != null ? e.getMessage() : e));
            writeRunSummary(cluster, cell, params, "criteria-violated",
                    "samples.jsonl preserved under " + cluster.getRunDir()
                            + " — classify per the hang rule (item-28 attribution first), see report",
                    observations);
        } catch (Exception suppressed) {
            System.getLogger(TestStabilityExerciseMultiJvm.class.getName())
                    .log(System.Logger.Level.WARNING, "failed to write cell criteria-violation summary", suppressed);
        }
    }

    /** S1 flavor of the failure summary. */
    private static void writeCellCriteriaViolation(MiniStreamCluster cluster, String cell,
                                                   Map<String, Object> params, ExerciseSampler sampler,
                                                   ExerciseLoadGenerator.S1LoadPlan plan, AssertionError e) {
        try {
            Map<String, Object> observations = soakObservations(sampler, plan, "criteria-violated");
            observations.put("violation", String.valueOf(e.getMessage() != null ? e.getMessage() : e));
            writeRunSummary(cluster, cell, params, "criteria-violated",
                    "samples.jsonl preserved under " + cluster.getRunDir()
                            + " — classify per the hang rule (item-28 attribution first), see report",
                    observations);
        } catch (Exception suppressed) {
            System.getLogger(TestStabilityExerciseMultiJvm.class.getName())
                    .log(System.Logger.Level.WARNING, "failed to write cell criteria-violation summary", suppressed);
        }
    }

    private static Map<String, Object> chaosParams(long durationSec, long paceMs, int linesPerUser,
                                                   long checkpointIntervalMs, long sampleIntervalMs,
                                                   int rounds, long minDelayMs, long maxDelayMs,
                                                   long partitionFromRound, long seed) {
        Map<String, Object> params = soakParams(durationSec, paceMs, linesPerUser,
                checkpointIntervalMs, sampleIntervalMs);
        params.put("rounds", (long) rounds);
        params.put("killDelayMinMs", minDelayMs);
        params.put("killDelayMaxMs", maxDelayMs);
        params.put("partitionFromRound", partitionFromRound);
        params.put("seed", seed);
        return params;
    }

    private static void assertSoakHealth(ExerciseSampler sampler, MiniStreamCluster cluster,
                                         long maxEpochGapMs, String cell) {
        List<SampleRecord> records = sampler.records();
        assertFalse(records.isEmpty(), cell + ": sampler captured no records");
        assertFalse(sampler.failed(), cell + ": sampler itself failed mid-run");
        long gap = ExerciseSampler.maxEpochAdvanceGapMs(records);
        long advances = ExerciseSampler.epochAdvanceCount(records);
        assertTrue(gap <= maxEpochGapMs, cell + ": durable-epoch advance gap " + gap
                + "ms exceeded " + maxEpochGapMs + "ms (stall/hang signal — classify per the hang rule,"
                + " item-28 attribution first)");
        assertTrue(advances >= 5L, cell + ": too few durable-epoch advances (" + advances + ")");
        assertFalse(ExerciseSampler.queueDepthUnboundedGrowth(records, QUEUE_LEAK_THRESHOLD),
                cell + ": msg-queue depth shows unbounded monotonic growth (leak signal)");
        assertTrue(cluster.coordinatorAlive(), cell + ": coordinator must stay alive");
    }

    private static Map<String, Object> soakParams(long durationSec, long paceMs, int linesPerUser,
                                                  long checkpointIntervalMs, long sampleIntervalMs) {
        Map<String, Object> params = new TreeMap<>();
        params.put("durationSec", durationSec);
        params.put("paceMs", paceMs);
        params.put("linesPerUser", (long) linesPerUser);
        params.put("checkpointIntervalMs", checkpointIntervalMs);
        params.put("sampleIntervalMs", sampleIntervalMs);
        params.put("opsHttpPort", System.getProperty("exercise.opsHttpPort",
                StabilityExerciseSupport.DEFAULT_OPS_HTTP_PORT));
        return params;
    }

    private static Map<String, Object> soakObservations(ExerciseSampler sampler,
                                                        ExerciseLoadGenerator.S2LoadPlan plan, String flavor) {
        List<SampleRecord> records = sampler.records();
        Map<String, Object> observations = new TreeMap<>();
        observations.put("flavor", flavor);
        observations.put("sampleCount", (long) records.size());
        observations.put("epochAdvanceCount", ExerciseSampler.epochAdvanceCount(records));
        observations.put("maxEpochAdvanceGapMs", ExerciseSampler.maxEpochAdvanceGapMs(records));
        observations.put("plannedEmissionMs", plan.getPlannedEmissionMs());
        observations.put("dataLines", (long) plan.getDataLines().size());
        observations.put("distinctUsers", (long) plan.getDistinctUsers());
        observations.put("expectedRows", (long) plan.getExpectedRows().size());
        observations.put("queueDepthMax", records.stream().mapToLong(r -> r.queueDepth).max().orElse(-1L));
        observations.put("queueDepthLast", records.isEmpty() ? -1L
                : records.get(records.size() - 1).queueDepth);
        observations.put("retainedManifestsMax", records.stream().mapToLong(r -> r.retainedManifests).max().orElse(-1L));
        return observations;
    }

    private static Map<String, Object> soakObservations(ExerciseSampler sampler,
                                                        ExerciseLoadGenerator.S1LoadPlan plan, String flavor) {
        List<SampleRecord> records = sampler.records();
        Map<String, Object> observations = new TreeMap<>();
        observations.put("flavor", flavor);
        observations.put("sampleCount", (long) records.size());
        observations.put("epochAdvanceCount", ExerciseSampler.epochAdvanceCount(records));
        observations.put("maxEpochAdvanceGapMs", ExerciseSampler.maxEpochAdvanceGapMs(records));
        observations.put("plannedEmissionMs", plan.getPlannedEmissionMs());
        observations.put("events", plan.getTotalEventCount());
        observations.put("burstWindows", (long) plan.getBurstWindows());
        observations.put("expectedAlerts", (long) plan.getExpectedAlerts().size());
        observations.put("queueDepthMax", records.stream().mapToLong(r -> r.queueDepth).max().orElse(-1L));
        observations.put("queueDepthLast", records.isEmpty() ? -1L
                : records.get(records.size() - 1).queueDepth);
        observations.put("retainedManifestsMax", records.stream().mapToLong(r -> r.retainedManifests).max().orElse(-1L));
        return observations;
    }
}
