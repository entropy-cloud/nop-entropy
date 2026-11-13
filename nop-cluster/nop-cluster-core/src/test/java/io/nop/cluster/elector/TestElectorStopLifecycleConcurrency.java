package io.nop.cluster.elector;

import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C1-004 (plan 2521): stop/election-completion race — a stop that fully returns while a
 * round is still in flight must not be undone by the round's completion (no leaderEpoch
 * revival, no post-stop listener callbacks, no re-arm). Covers the C10-003 protection
 * set (suggestions (1)-(4)) and the C10-004 MA-01 half (post-stop dispatch guard).
 */
@Timeout(60)
public class TestElectorStopLifecycleConcurrency {

    static class RecordingListener implements ILeaderElectionListener {
        final List<LeaderEpoch> leaderEpochs = new CopyOnWriteArrayList<>();
        final List<LeaderEpoch> followerEpochs = new CopyOnWriteArrayList<>();
        final AtomicInteger stopCount = new AtomicInteger();

        @Override
        public void becomeLeader(LeaderEpoch leaderEpoch) {
            leaderEpochs.add(leaderEpoch);
        }

        @Override
        public void becomeFollower(LeaderEpoch leaderEpoch) {
            followerEpochs.add(leaderEpoch);
        }

        @Override
        public void onStop() {
            stopCount.incrementAndGet();
        }
    }

    /** Round body blocks on a latch, mirroring the subclass DB-I/O wide window. */
    static class LatchElector extends AbstractPollingLeaderElector {
        final CountDownLatch roundEntered = new CountDownLatch(1);
        volatile CountDownLatch roundGate = new CountDownLatch(1);
        final AtomicInteger rounds = new AtomicInteger();

        @Override
        protected Void checkElection() {
            rounds.incrementAndGet();
            roundEntered.countDown();
            try {
                roundGate.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // the in-flight election completes during the stop window (the subclass
            // DB-I/O face: the lease check finished and the node won)
            complete(epoch("node-inflight", 1));
            return null;
        }

        @Override
        public void restartElection() {
            onRestartElection();
        }

        public void complete(LeaderEpoch epoch) {
            onElectionCompleted(epoch);
        }

        public void fireBecomeLeader(LeaderEpoch epoch) {
            onBecomeLeader(epoch);
        }
    }

    /**
     * Fast-loop elector for the double-chain growth pin (suggestion (2)). The tail
     * schedule mirrors SysDaoLeaderElector/JdbcLeaderElector (re-arm inside the body) —
     * without it the loop runs on the 2496 breakdown-backoff channel instead of the
     * check interval.
     */
    static class FastElector extends AbstractPollingLeaderElector {
        final AtomicInteger rounds = new AtomicInteger();

        FastElector() {
            setCheckIntervalMs(100);
        }

        @Override
        protected Void checkElection() {
            rounds.incrementAndGet();
            scheduleCheck();
            return null;
        }

        @Override
        public void restartElection() {
            onRestartElection();
        }

        public void complete(LeaderEpoch epoch) {
            onElectionCompleted(epoch);
        }
    }

    private static final AtomicInteger TIMER_SEQ = new AtomicInteger();

    private DefaultScheduledExecutor timer;

    @BeforeEach
    public void setUp() {
        timer = DefaultScheduledExecutor.newSingleThreadTimer("test-elector-stop-timer-" + TIMER_SEQ.incrementAndGet());
    }

    @AfterEach
    public void tearDown() {
        timer.destroy();
    }

    private static LeaderEpoch epoch(String leaderId, long epochNo) {
        return new LeaderEpoch(leaderId, epochNo, new Timestamp(System.currentTimeMillis() + 30_000));
    }

    /**
     * 红① (C10-003 suggestion (1) + C10-004 MA-01 half): stop returns while a round is
     * blocked in checkElection; the round's completion must not revive the epoch, must
     * not notify listeners, and must not complete a promise the stop already cancelled.
     * Defect state: onElectionCompleted writes leaderEpoch after stop (red); fixed state:
     * entry guard rejects (green).
     */
    @Test
    public void stopMidRoundThenCompletionMustNotRevive() throws Exception {
        LatchElector elector = new LatchElector();
        elector.setScheduledExecutor(timer);
        RecordingListener listener = new RecordingListener();
        elector.addElectionListener(listener);
        elector.start();

        assertTrue(elector.roundEntered.await(5, TimeUnit.SECONDS), "round must enter checkElection");

        Thread stopper = new Thread(elector::stop);
        stopper.start();
        stopper.join(10_000);
        assertFalse(stopper.isAlive(), "stop must fully return (post-stop window)");
        assertEquals(1, listener.stopCount.get(), "stop must fire listener.onStop exactly once");
        assertNull(elector.getLeaderEpoch(), "stop must reset the leader epoch");

        // release the in-flight round — its completion lands in the post-stop window
        elector.roundGate.countDown();
        Thread.sleep(300);

        assertNull(elector.getLeaderEpoch(),
                "C1-004: post-stop election completion must not revive the leader epoch");
        assertTrue(listener.leaderEpochs.isEmpty(),
                "C10-004 half: becomeLeader must not reach listeners after onStop");
        assertTrue(listener.followerEpochs.isEmpty(),
                "C10-004 half: becomeFollower must not reach listeners after onStop");
        CompletableFuture<?> postStopPromise = elector.whenElectionCompleted().toCompletableFuture();
        assertFalse(postStopPromise.isDone() && !postStopPromise.isCompletedExceptionally(),
                "the promise cancelled by stop must not complete normally after stop");

        // dispatch guard: a late fireBecomeLeader after stop must not reach listeners
        elector.fireBecomeLeader(epoch("late", 99));
        assertTrue(listener.leaderEpochs.isEmpty(),
                "post-stop becomeLeader dispatch must be rejected at the entry");
    }

    /**
     * C10-003 suggestion (2): stop→start cycles must not double the election chain —
     * the round counter growth rate stays single-chain.
     */
    @Test
    public void stopStartCyclesKeepSingleElectionLoop() throws Exception {
        FastElector elector = new FastElector();
        elector.setScheduledExecutor(timer);
        elector.start();

        // let the loop settle into its steady rate
        Thread.sleep(500);
        int before = elector.rounds.get();
        Thread.sleep(1000);
        int singleChainGrowth = elector.rounds.get() - before;
        assertTrue(singleChainGrowth > 0, "loop must be running");

        // full stop→start ×2: the defective face resurrects the un-cancelled pending
        // chain alongside the freshly scheduled one
        elector.stop();
        elector.start();
        elector.stop();
        elector.start();
        Thread.sleep(500);
        int base = elector.rounds.get();
        Thread.sleep(1000);
        int growth = elector.rounds.get() - base;

        // single chain at 100ms interval ≈ 10 rounds/s; a doubled chain ≈ 20
        // single chain ≈ one round per checkIntervalMs; a resurrected stale chain doubles it.
        // Timing-noise hardening (2026-11-13): a wall-clock 1s window can jitter (GC/scheduler),
        // so a violation is confirmed by re-measuring BOTH windows once before failing —
        // a genuinely doubled chain reproduces on the second sample, jitter does not.
        if (growth > singleChainGrowth + 6) {
            Thread.sleep(500);
            int refBefore = elector.rounds.get();
            Thread.sleep(1000);
            singleChainGrowth = elector.rounds.get() - refBefore;
            Thread.sleep(500);
            int base2 = elector.rounds.get();
            Thread.sleep(1000);
            growth = elector.rounds.get() - base2;
        }
        assertTrue(growth <= singleChainGrowth + 6,
                "C10-003(2): stop→start must keep a single election loop, growth=" + growth
                        + " vs single-chain reference=" + singleChainGrowth);
        elector.stop();
    }

    /**
     * C10-003 suggestion (3) / C3-004 waiter contract: a waiter attached before
     * restartElection must complete with the next election result, not receive a
     * spurious CancellationException (defect: restart cancels the old promise).
     */
    @Test
    public void waiterAttachedBeforeRestartCompletesWithNextResult() throws Exception {
        // never started: the loop stays off and completion is driven manually
        FastElector manual = new FastElector();
        CompletableFuture<LeaderEpoch> waiter1 = manual.whenElectionCompleted().toCompletableFuture();

        manual.restartElection();
        CompletableFuture<LeaderEpoch> waiter2 = manual.whenElectionCompleted().toCompletableFuture();
        assertTrue(waiter1 != waiter2, "restart must install a fresh promise");

        manual.complete(epoch("node-2", 2));

        assertEquals(2, waiter1.get(5, TimeUnit.SECONDS).getEpoch(),
                "C3-004: pre-restart waiter must drain to the next winning epoch, not CancellationException");
        assertEquals(2, waiter2.get(5, TimeUnit.SECONDS).getEpoch(),
                "post-restart waiter completes with the same winning epoch");
    }

    /**
     * C10-003 suggestion (4) / C2-005 structural pin: while a synchronous waiter
     * continuation is blocked inside the completion dispatch, another thread must still
     * be able to enter a state-lock-guarded method. Defect state: complete() runs inside
     * the synchronized method, so the blocked continuation holds the monitor and the
     * probe cannot enter (red, structural — not a timing threshold).
     */
    @Test
    public void completionMustNotHoldStateLockDuringWaiterDispatch() throws Exception {
        FastElector elector = new FastElector();
        CountDownLatch waiterEntered = new CountDownLatch(1);
        CountDownLatch releaseWaiter = new CountDownLatch(1);
        elector.whenElectionCompleted().toCompletableFuture().thenRun(() -> {
            waiterEntered.countDown();
            try {
                releaseWaiter.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread completer = new Thread(() -> elector.complete(epoch("node-1", 1)));
        completer.start();
        assertTrue(waiterEntered.await(5, TimeUnit.SECONDS),
                "waiter continuation must be running inside the completion dispatch");

        CountDownLatch probeEntered = new CountDownLatch(1);
        Thread probe = new Thread(() -> {
            elector.restartElection();
            probeEntered.countDown();
        });
        probe.start();
        assertTrue(probeEntered.await(5, TimeUnit.SECONDS),
                "C2-005: completion dispatch must not hold the state lock while waiter "
                        + "continuations run — probe thread could not enter restartElection");

        releaseWaiter.countDown();
        completer.join(10_000);
        probe.join(10_000);
    }
}
