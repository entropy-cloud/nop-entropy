package io.nop.lint.core.cli;

import io.nop.api.core.exceptions.NopException;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.node.SourceRange;
import io.nop.lint.core.suppress.BaselineFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cache write-face concurrency contract (plan 2531 W-03+W-10, red③ —
 * M-P2-5): a shared {@code --cache} file must not let two writers stomp one
 * fixed-name temp file (red③①, constructive probabilistic — the bounded
 * loop below captures any stomp round), a failing save must be explicitly
 * attributed and leave no temp residue (red③② deterministic — pre-fix the
 * catch had no cleanup), and the baseline write face meets the same
 * standard (W-10 activated with W-03 at the same site). The torn-write load
 * contract of the baseline stays fail-closed (zero-change anchor).
 */
class TestLintCacheWriteContract_W_03 {

    private static final String FP = "fp-w03";
    private static final byte[] CONTENT = "class Warn {\n}\n".getBytes(StandardCharsets.UTF_8);

    private static Diagnostic diag() {
        return new Diagnostic("demo/w03", "warning", "msg", new SourceRange(0, 5));
    }

    private static boolean isExplicitLockFail(Throwable t) {
        // NopException's message carries the error code verbatim; the
        // same-JVM second-grant face names the held lock explicitly
        String message = t.getMessage();
        return message != null
                && (message.contains("nop.err.commons.file.acquire-lock")
                || message.contains("holds the lint cache file lock"));
    }

    private static boolean isStompSignature(Throwable t) {
        Throwable cause = t.getCause();
        while (cause != null) {
            if (cause instanceof java.nio.file.NoSuchFileException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * red③① (M-P2-5 ①): two independent cache instances on one shared
     * cache file, barrier-aligned saves, bounded rounds. Pre-fix the fixed
     * name {@code cache.json.tmp} is shared: one writer's move empties the
     * other's temp and the run dies with a false NoSuchFileException (or a
     * last-writer-wins silently drops results). Post-fix the temp name is
     * unique and the load→save critical section is serialized, so no round
     * shows a stomp signature.
     */
    @Test
    void testW_03_concurrentSavesOnSharedCacheFileNeverStomp(@TempDir Path dir) throws Exception {
        Path cache = dir.resolve("cache.json");
        // seed one valid artifact so the loads read a real file
        RuleResultCache seed = RuleResultCache.load(cache, FP);
        seed.put("seed.java", CONTENT, List.of(diag()));
        seed.save();

        int rounds = 20;
        List<String> violations = new ArrayList<>();
        for (int round = 0; round < rounds; round++) {
            // driver shape: pre-place the shared fixed-name temp (the stomp
            // target); content marks it apart from real residue
            Path marker = dir.resolve("cache.json.tmp");
            Files.writeString(marker, "stale-marker");
            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicReference<Throwable> failureA = new AtomicReference<>();
            AtomicReference<Throwable> failureB = new AtomicReference<>();
            Runnable run = () -> {
                AtomicReference<Throwable> failure = failureA;
                RuleResultCache c = null;
                AtomicReference<Throwable> loadFailure = new AtomicReference<>();
                try {
                    c = RuleResultCache.load(cache, FP);
                } catch (Throwable t) {
                    loadFailure.set(t);
                }
                try {
                    // both threads reach the barrier even when the load
                    // already failed — the round is judged from the slots
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    failure.set(e);
                    return;
                }
                try {
                    if (loadFailure.get() != null) {
                        throw loadFailure.get();
                    }
                    c.put("A.java", CONTENT, List.of(diag()));
                    c.save();
                } catch (Throwable t) {
                    if (Thread.currentThread().getName().endsWith("b")) {
                        failure = failureB;
                    }
                    failure.set(t);
                }
            };
            Thread a = new Thread(run, "w03-save-a");
            Thread b = new Thread(run, "w03-save-b");
            a.start();
            b.start();
            a.join(20_000);
            b.join(20_000);

            for (AtomicReference<Throwable> slot : List.of(failureA, failureB)) {
                Throwable t = slot.get();
                if (t == null) {
                    continue;
                }
                if (isStompSignature(t)) {
                    violations.add("round " + round + ": stomp signature — " + t);
                } else if (isExplicitLockFail(t)) {
                    // the same-JVM second acquisition is the documented
                    // explicit fail-closed face, not a stomp (plan 2531
                    // adjudication 3: same-JVM concurrent access fails
                    // closed instead of silently crossing writes)
                } else {
                    violations.add("round " + round + ": unexpected failure — " + t);
                }
            }
            // residue check: the marker content must be untouched — a real
            // temp reused the fixed name and stayed behind
            if (Files.exists(marker)
                    && !"stale-marker".equals(Files.readString(marker))) {
                violations.add("round " + round + ": fixed-name temp residue after a failed save");
            }
            Files.deleteIfExists(marker);
        }
        assertTrue(violations.isEmpty(), () -> "stomp rounds: " + violations);
        // the artifact stays loadable and replays its entry
        RuleResultCache after = RuleResultCache.load(cache, FP);
        assertEquals(1, after.get("A.java", CONTENT).diagnostics().size(),
                "the surviving artifact replays the winner's entry");
        after.save();
    }

    /**
     * red③② (M-P2-5 ②, deterministic — the W-10 activation assertion): a
     * move failure after a successful temp write must leave NO temp residue.
     * Pre-fix the catch (no cleanup) leaves {@code cache.json.tmp} behind;
     * post-fix the moved/finally form deletes it.
     */
    @Test
    void testW_03_moveFailureInjectionLeavesNoTempResidue(@TempDir Path dir) throws Exception {
        Path cache = dir.resolve("cache.json");
        RuleResultCache cache0 = RuleResultCache.load(cache, FP);
        cache0.put("A.java", CONTENT, List.of(diag()));
        cache0.save();

        // inject: the target path becomes a non-empty directory —
        // REPLACE_EXISTING cannot replace it, so the move fails after the
        // temp write succeeded
        Files.delete(cache);
        Path asDir = Files.createDirectory(cache);
        Files.writeString(asDir.resolve("occupied.txt"), "x");

        NopLintException ex = assertThrows(NopLintException.class, cache0::save,
                "the move failure must fail closed with an explicit error");
        assertTrue(ex.getMessage().contains("cache.json"),
                "the error names the cache file: " + ex.getMessage());

        try (var stream = Files.list(dir)) {
            List<Path> temps = stream.filter(p -> p.getFileName().toString().endsWith(".tmp")).toList();
            assertTrue(temps.isEmpty(),
                    "no temp residue after the failed save: " + temps);
        }
    }

    /**
     * red③② attribution face (M-P2-5 ②): a save into a read-only directory
     * fails with an explicit, file-naming error and no residue. The lock
     * sibling is pre-created (R1 Minor-6) so the failure lands in the
     * temp/move segment, not the lock-acquisition segment.
     */
    @Test
    void testW_03_readOnlyDirectoryInjectionIsExplicitlyAttributed(@TempDir Path dir) throws Exception {
        Path cache = dir.resolve("cache.json");
        RuleResultCache seed = RuleResultCache.load(cache, FP);
        seed.put("A.java", CONTENT, List.of(diag()));
        seed.save();

        // pre-create the lock sibling so (with the fix in place) the lock
        // acquisition succeeds on the existing file and the failure lands in
        // the temp/move segment
        Files.writeString(dir.resolve("cache.json.lock"), "");

        Set<PosixFilePermission> original = Files.getPosixFilePermissions(dir);
        Set<PosixFilePermission> readOnly = Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(dir, readOnly);
        try {
            RuleResultCache cache0 = RuleResultCache.load(cache, FP);
            cache0.put("A.java", CONTENT, List.of(diag()));
            NopLintException ex = assertThrows(NopLintException.class, cache0::save,
                    "the save must fail closed with an explicit error");
            assertTrue(ex.getMessage().contains("cache.json"),
                    "the error names the cache file: " + ex.getMessage());
            try (var stream = Files.list(dir)) {
                List<Path> temps = stream.filter(p -> p.getFileName().toString().endsWith(".tmp")).toList();
                assertTrue(temps.isEmpty(), "no temp residue: " + temps);
            }
        } finally {
            Files.setPosixFilePermissions(dir, original);
        }
    }

    /**
     * The lock-lifecycle poison face (R1 Major-3): a failed load must not
     * hold the file lock — the next generation on the same file must load
     * and save cleanly.
     */
    @Test
    void testW_03_failedLoadReleasesTheLockForLaterRuns(@TempDir Path dir) throws Exception {
        Path cache = dir.resolve("cache.json");
        RuleResultCache seed = RuleResultCache.load(cache, FP);
        seed.put("A.java", CONTENT, List.of(diag()));
        seed.save();

        // corrupt the artifact: the load fails closed...
        Files.writeString(cache, "{not-json");
        assertThrows(NopLintException.class, () -> RuleResultCache.load(cache, FP));

        // restore a valid artifact: the next run on the same file still works
        // (no lock poison from the failed load)
        Files.writeString(cache, "{\"version\":1,\"runFingerprint\":\"fp-w03\","
                + "\"files\":{}}");
        RuleResultCache fresh = RuleResultCache.load(cache, FP);
        fresh.put("B.java", CONTENT, List.of(diag()));
        fresh.save();
        RuleResultCache reloaded = RuleResultCache.load(cache, FP);
        assertEquals(1, reloaded.get("B.java", CONTENT).diagnostics().size());
        reloaded.save();
    }

    // ==================== W-10: the baseline write face ====================

    /**
     * W-10 zero-change anchor: a torn baseline write still fails closed on
     * load (pre/post both green — the load contract does not move).
     */
    @Test
    void testW_10_baselineTornWriteStillFailsClosedOnLoad(@TempDir Path dir) throws Exception {
        Path baseline = dir.resolve("baseline.yaml");
        new BaselineFile(BaselineFile.VERSION_1,
                List.of(new BaselineFile.Entry("demo/r", "A.java", "fp", 1))).writeTo(baseline);
        String yaml = Files.readString(baseline);
        Files.writeString(baseline, yaml.substring(0, yaml.length() / 2));

        NopLintException ex = assertThrows(NopLintException.class, () -> BaselineFile.load(baseline));
        assertTrue(ex.getMessage().contains("baseline"), ex.getMessage());
    }

    /**
     * W-10 activation face: a baseline write failure leaves no temp residue
     * (the pre-fix direct write leaves none either — the face pins the fix's
     * temp+move form against leaving junk).
     */
    @Test
    void testW_10_baselineWriteFailureLeavesNoTempResidue(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("baseline.yaml");
        Files.createDirectories(target);
        Files.writeString(target.resolve("occupied.txt"), "x");

        BaselineFile baseline = new BaselineFile(BaselineFile.VERSION_1,
                List.of(new BaselineFile.Entry("demo/r", "A.java", "fp", 1)));
        NopLintException ex = assertThrows(NopLintException.class, () -> baseline.writeTo(target));
        assertTrue(ex.getMessage().contains("baseline"), ex.getMessage());

        try (var stream = Files.list(dir)) {
            List<Path> temps = stream.filter(p -> p.getFileName().toString().endsWith(".tmp")).toList();
            assertTrue(temps.isEmpty(), "no temp residue: " + temps);
        }
        assertFalse(Files.exists(dir.resolve("baseline.yaml.tmp")));
    }

    /**
     * W-10 warm anchor: a successful baseline write still round-trips
     * through load (the atomic form preserves the byte-level contract).
     */
    @Test
    void testW_10_baselineWriteStillRoundTrips(@TempDir Path dir) throws Exception {
        Path baseline = dir.resolve("baseline.yaml");
        BaselineFile model = new BaselineFile(BaselineFile.VERSION_1,
                List.of(new BaselineFile.Entry("demo/r", "A.java", "fp", 1)));
        model.writeTo(baseline);
        BaselineFile loaded = BaselineFile.load(baseline);
        assertEquals(1, loaded.entries().size());
        assertEquals("demo/r", loaded.entries().get(0).rule());
        assertInstanceOf(BaselineFile.class, loaded);
    }
}
