package io.nop.ai.agent.team;

import io.nop.ai.agent.NopAiAgentException;
import io.nop.ai.agent.contribution.Contribution;
import io.nop.ai.agent.contribution.ContributionType;
import io.nop.ai.agent.contribution.InMemoryContributionRegistry;
import io.nop.ai.agent.engine.CalibratedTokenEstimator;
import io.nop.ai.agent.engine.ITokenEstimator;
import io.nop.ai.agent.quota.DefaultResourceGuard;
import io.nop.ai.agent.quota.QuotaConfig;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.dialect.ILlmDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.1-MA-14 (plan 2555 裁定 D) carrier — compound-operation atomicity:
 * <ul>
 *   <li>N concurrent {@code bindMemberSession} calls never exceed
 *       {@code maxParallelMembers} (the quota decision runs inside the
 *       {@code members.compute} critical section);</li>
 *   <li>{@code InMemoryContributionRegistry.unregisterSource} keeps the
 *       bySource index and the per-type registry in one global order — a
 *       concurrently-completing registration is never silently rolled back
 *       by a stale key snapshot;</li>
 *   <li>{@code CalibratedTokenEstimator} EMA updates are atomic RMW —
 *       concurrent {@code record} calls converge to the true sequential EMA
 *       (no lost updates).</li>
 * </ul>
 */
public class TestTeamQuotaAtomicity {

    private final List<ExecutorService> pools = new ArrayList<>();

    private ExecutorService newPool(int n) {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        pools.add(pool);
        return pool;
    }

    @AfterEach
    void shutdownPools() {
        for (ExecutorService pool : pools) {
            pool.shutdownNow();
        }
    }

    // ========================================================================
    // bindMemberSession: quota inside compute — N concurrent binders never
    // exceed maxParallelMembers
    // ========================================================================

    @Test
    void concurrentBindMemberSessionNeverExceedsQuota() throws Exception {
        // quota config: TEAM_PARALLEL_BOUND_MEMBERS enforced via the team's
        // maxParallelMembers override (= 2). DefaultResourceGuard with a
        // QuotaConfig allows any overrideLimit > 0 to be enforced.
        InMemoryTeamManager mgr = new InMemoryTeamManager(new DefaultResourceGuard(new QuotaConfig(100, 100)));
        int limit = 2;
        Team team = mgr.createTeam(new TeamSpec("t", null, "m0",
                List.of(new TeamMemberSpec("m0", "coder", MemberRole.LEAD),
                        new TeamMemberSpec("m1", "coder", MemberRole.MEMBER),
                        new TeamMemberSpec("m2", "coder", MemberRole.MEMBER),
                        new TeamMemberSpec("m3", "coder", MemberRole.MEMBER),
                        new TeamMemberSpec("m4", "coder", MemberRole.MEMBER),
                        new TeamMemberSpec("m5", "coder", MemberRole.MEMBER)),
                limit));

        int binders = 6;
        ExecutorService pool = newPool(binders);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger denied = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < binders; i++) {
            final String memberName = "m" + i;
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    mgr.bindMemberSession(team.getTeamId(), memberName,
                            "sess-" + memberName, "actor-" + memberName);
                } catch (NopAiAgentException e) {
                    denied.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(20, TimeUnit.SECONDS);
        }

        int boundCount = 0;
        for (Object m : team.getMembers().values()) {
            if (((TeamMember) m).isBound()) {
                boundCount++;
            }
        }
        assertTrue(boundCount <= limit,
                "concurrent binders must never exceed maxParallelMembers=" + limit
                        + " (bound=" + boundCount + ")");
        assertTrue(denied.get() > 0,
                "with 6 binders against a limit of 2, at least one bind must be denied");
    }

    // ========================================================================
    // unregisterSource: no silently-rolled-back completed registration
    // ========================================================================

    @Test
    void unregisterSourceKeepsGlobalOrderingWithRegister() throws Exception {
        InMemoryContributionRegistry registry = new InMemoryContributionRegistry();
        ContributionType type = ContributionType.TOOL;
        String source = "order-src";
        ExecutorService pool = newPool(2);

        for (int round = 0; round < 500; round++) {
            final String id = "c" + round;
            Contribution c = new Contribution(type, id, source, 1, new Object());
            registry.register(c);
            CountDownLatch start = new CountDownLatch(1);
            // T1: unregister the whole source; T2: re-register one contribution.
            Future<?> unreg = pool.submit(() -> {
                start.await();
                registry.unregisterSource(source);
                return null;
            });
            Future<?> reg = pool.submit(() -> {
                start.await();
                registry.register(new Contribution(type, id + "-r", source, 1, new Object()));
                return null;
            });
            start.countDown();
            unreg.get(10, TimeUnit.SECONDS);
            reg.get(10, TimeUnit.SECONDS);

            // Invariant: if the source still exists in the reverse index, its
            // registered contributions MUST be discoverable in the registry.
            // The pre-fix interleave (bySource.remove outside the per-type
            // lock) produced "source in index but registry entry deleted" —
            // a completed registration silently rolled back.
            if (registry.getSources().contains(source)) {
                List<Contribution> remaining = registry.getContributions(type, source);
                assertTrue(!remaining.isEmpty(),
                        "round " + round + ": source present in the index but its "
                                + "registrations were rolled back (C1-025 ordering bug)");
            }
            // And the registry never keeps a source-less orphan either way.
            registry.unregisterSource(source);
        }
    }

    // ========================================================================
    // CalibratedTokenEstimator: EMA RMW atomicity
    // ========================================================================

    @Test
    void concurrentEmaUpdatesMatchSequentialComputation() throws Exception {
        ILlmDialect dialect = io.nop.ai.core.dialect.LlmDialectFactory.getDialect(
                io.nop.ai.core.model.ApiStyle.openai);
        CalibratedTokenEstimator estimator = new CalibratedTokenEstimator(dialect);
        List<ChatMessage> messages = List.of(new ChatUserMessage("hello"));
        long base = dialect.estimateTokens(messages);
        int actualPromptTokens = (int) (base + base / 2); // observedRatio = 1.5 always
        double observedRatio = (double) actualPromptTokens / (double) base;

        int threads = 4;
        int updatesPerThread = 500;
        ExecutorService pool = newPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int u = 0; u < updatesPerThread; u++) {
                    estimator.record(messages, actualPromptTokens);
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(20, TimeUnit.SECONDS);
        }

        int totalUpdates = threads * updatesPerThread;
        double expected = 1.0;
        for (int u = 0; u < totalUpdates; u++) {
            expected = expected * (1.0 - CalibratedTokenEstimator.SMOOTHING_ALPHA)
                    + observedRatio * CalibratedTokenEstimator.SMOOTHING_ALPHA;
            expected = Math.max(CalibratedTokenEstimator.MIN_FACTOR,
                    Math.min(CalibratedTokenEstimator.MAX_FACTOR, expected));
        }
        assertEquals(expected, estimator.getFactor(), 1e-9,
                "concurrent EMA updates must equal the sequential computation "
                        + "(no lost updates — atomic RMW required)");
    }
}
