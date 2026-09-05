/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Item 15 (stability exercise): the chaos kill schedule. Seeded RNG so every drill
 * run's kill timing/victim sequence is reproducible from the archived seed; the
 * schedule itself is unit-tested independently of any cluster.
 *
 * <p>Step kinds (Phase 1 matrix):
 * <ul>
 *   <li>{@code KILL_RESTART_TM} — SIGTERM a random TaskManager, restart, wait for
 *       fencing rotation (C1 semantics, randomized timing/rounds);</li>
 *   <li>{@code PARTITION_PAUSE_RESUME} — SIGSTOP the TM (JDBC-polling backend:
 *       paused poller ≡ network partition on the observation surface — no
 *       heartbeat, no message consumption, lease expiry), then SIGCONT so the
 *       stale-view resurrection is fenced at the RPC boundary.</li>
 * </ul>
 */
public final class ChaosKillPlanner {

    public enum StepKind {
        KILL_RESTART_TM,
        PARTITION_PAUSE_RESUME
    }

    public static final class KillStep {
        final int round;
        final StepKind kind;
        final String victim;
        final long delayMsBefore;

        KillStep(int round, StepKind kind, String victim, long delayMsBefore) {
            this.round = round;
            this.kind = kind;
            this.victim = victim;
            this.delayMsBefore = delayMsBefore;
        }

        public int getRound() {
            return round;
        }

        public StepKind getKind() {
            return kind;
        }

        public String getVictim() {
            return victim;
        }

        public long getDelayMsBefore() {
            return delayMsBefore;
        }

        @Override
        public String toString() {
            return "round=" + round + " kind=" + kind + " victim=" + victim
                    + " delayMs=" + delayMsBefore;
        }
    }

    private ChaosKillPlanner() {
    }

    /**
     * Plans {@code rounds} kill steps with uniform-random delay in
     * {@code [minDelayMs, maxDelayMs]} before each step and a uniform-random TM
     * victim per step. Rounds {@code >= partitionFromRound} are partition-
     * equivalent (SIGSTOP/SIGCONT); {@code partitionFromRound = rounds + 1} means
     * none.
     */
    public static List<KillStep> plan(int rounds, long minDelayMs, long maxDelayMs,
                                      int partitionFromRound, long seed) {
        if (rounds < 1) {
            throw new IllegalArgumentException("rounds must be >= 1 (got " + rounds + ")");
        }
        if (minDelayMs < 1L || maxDelayMs < minDelayMs) {
            throw new IllegalArgumentException("delay range invalid: need 1 <= min <= max, got min="
                    + minDelayMs + " max=" + maxDelayMs);
        }
        if (partitionFromRound < 1 || partitionFromRound > rounds + 1) {
            throw new IllegalArgumentException("partitionFromRound must be in [1, rounds+1]"
                    + " (got " + partitionFromRound + ", rounds=" + rounds + ")");
        }
        Random random = new Random(seed);
        List<KillStep> steps = new ArrayList<>(rounds);
        for (int round = 1; round <= rounds; round++) {
            long delay = minDelayMs + (long) (random.nextDouble() * (maxDelayMs - minDelayMs + 1));
            String victim = random.nextBoolean() ? "tm-0" : "tm-1";
            StepKind kind = round >= partitionFromRound
                    ? StepKind.PARTITION_PAUSE_RESUME : StepKind.KILL_RESTART_TM;
            steps.add(new KillStep(round, kind, victim, delay));
        }
        return steps;
    }

    /** Sum of the planned pre-step delays (fixture sizing input). */
    public static long totalPlannedDelayMs(List<KillStep> steps) {
        long total = 0L;
        for (KillStep step : steps) {
            total += step.getDelayMsBefore();
        }
        return total;
    }
}
