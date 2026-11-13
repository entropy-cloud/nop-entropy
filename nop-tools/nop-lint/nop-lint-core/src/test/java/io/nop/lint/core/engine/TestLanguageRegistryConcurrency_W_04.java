package io.nop.lint.core.engine;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The embedded-registration concurrency contract (plan 2531 W-04, red④ —
 * M-P2-4): two providers claiming one id is a discovery conflict, never a
 * winner-takes-all race, and {@code registeredIds()} iteration overlaps
 * concurrent registration without a {@code ConcurrentModificationException}.
 * The conflict-detection foot is the probabilistic face (the check-then-act
 * window is narrow — bounded rounds with barrier alignment), the CME foot is
 * the deterministic face (HashMap iteration against live registration). The
 * sequential conflict contract keeps its coverage in
 * {@code TestLanguageRegistry} unchanged.
 */
public class TestLanguageRegistryConcurrency_W_04 {

    /**
     * A minimal binding carrier: only the id is real — the registry's
     * register/registeredIds paths never touch the parse machinery.
     */
    private static final class IdOnlyLanguage implements LintLanguage {
        private final String id;

        IdOnlyLanguage(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Language treeSitter() {
            throw new UnsupportedOperationException("id-only concurrency carrier");
        }

        @Override
        public io.nop.lint.core.node.LintTree parse(String source) {
            throw new UnsupportedOperationException("id-only concurrency carrier");
        }

        @Override
        public LintTree parse(byte[] source) {
            throw new UnsupportedOperationException("id-only concurrency carrier");
        }

        @Override
        public LintTree parseIncremental(LintTree oldTree, byte[] newSource) {
            throw new UnsupportedOperationException("id-only concurrency carrier");
        }

        @Override
        public String preprocessPattern(String patternText) {
            throw new UnsupportedOperationException("id-only concurrency carrier");
        }

        @Override
        public int kindId(String kindName) {
            throw new UnsupportedOperationException("id-only concurrency carrier");
        }
    }

    /**
     * red④ race foot (probabilistic, bounded rounds): two threads aligned at
     * a barrier register different instances under the same id — exactly one
     * conflict detection per round, never a silent winner-takes-all.
     */
    @Test
    public void testW_04_concurrentConflictingRegistrationsAreAlwaysDetected() throws Exception {
        int rounds = 1000;
        AtomicInteger detected = new AtomicInteger();
        List<String> undetected = new java.util.concurrent.CopyOnWriteArrayList<>();
        for (int round = 0; round < rounds; round++) {
            LanguageRegistry registry = LanguageRegistry.empty();
            String id = "dup-" + round;
            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicReference<Throwable> failureA = new AtomicReference<>();
            AtomicReference<Throwable> failureB = new AtomicReference<>();
            Runnable register = () -> {
                AtomicReference<Throwable> failure = failureA;
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    registry.register(new IdOnlyLanguage(id));
                } catch (Throwable t) {
                    if (Thread.currentThread().getName().endsWith("w04-b")) {
                        failure = failureB;
                    }
                    failure.set(t);
                }
            };
            Thread a = new Thread(register, "w04-a");
            Thread b = new Thread(register, "w04-b");
            a.start();
            b.start();
            a.join(10_000);
            b.join(10_000);
            int conflicts = 0;
            for (AtomicReference<Throwable> slot : List.of(failureA, failureB)) {
                Throwable t = slot.get();
                if (t == null) {
                    continue;
                }
                if (t instanceof NopLintException && t.getMessage().contains("already bound")) {
                    conflicts++;
                } else {
                    undetected.add("round " + round + ": unexpected — " + t);
                }
            }
            detected.addAndGet(conflicts);
            if (conflicts == 0) {
                undetected.add("round " + round + ": winner-takes-all — both registrations passed");
            }
        }
        assertTrue(undetected.isEmpty(), () -> "undetected rounds: "
                + undetected.stream().limit(5).toList() + " (total " + undetected.size() + ")");
        assertEquals(rounds, detected.get(),
                "every round must detect exactly one conflict (fail-closed, zero winner-takes-all)");
    }

    /**
     * red④ CME foot (deterministic): {@code registeredIds()} iteration runs
     * against a registry that keeps receiving new ids — pre-fix the HashMap
     * keySet iteration throws ConcurrentModificationException; post-fix the
     * snapshot is weakly consistent and never throws.
     */
    @Test
    public void testW_04_registeredIdsIterationOverlapsRegistrationWithoutCme() throws Exception {
        LanguageRegistry registry = LanguageRegistry.empty();
        for (int i = 0; i < 64; i++) {
            registry.register(new IdOnlyLanguage("base-" + i));
        }
        AtomicReference<Throwable> writerFailure = new AtomicReference<>();
        java.util.concurrent.CountDownLatch firstScanDone = new java.util.concurrent.CountDownLatch(1);
        int registrations = 20_000;
        Thread writer = new Thread(() -> {
            try {
                // 确定性握手（合并修订 2026-11-13）：等待主线程完成首次迭代扫描后再开始
                // 注册——否则压力下 writer 可先于主线程首个循环检查跑完全部注册
                //（scans=0 假红）。注册与迭代的重叠由该屏障保证。
                firstScanDone.await();
                for (int i = 0; i < registrations; i++) {
                    registry.register(new IdOnlyLanguage("new-" + i));
                }
            } catch (Throwable t) {
                writerFailure.set(t);
            }
        }, "w04-ids-writer");
        writer.start();

        // 主线程先完成一次迭代扫描，再放行 writer（保证重叠）
        registry.registeredIds();
        firstScanDone.countDown();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        long scans = 0;
        try {
            while (writer.isAlive() && System.nanoTime() < deadline) {
                // pre-fix: HashMap keySet iteration under live structural
                // modification — CME surfaces here
                registry.registeredIds();
                scans++;
            }
        } finally {
            writer.join(20_000);
        }
        assertEquals(null, writerFailure.get(),
                "the concurrent registrant must succeed: " + writerFailure.get());
        assertTrue(scans > 0, "the iteration must have run at least once");
        assertEquals(registrations + 64, registry.registeredIds().size(),
                "every registration lands (no silent loss)");
    }
}
