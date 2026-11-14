package io.nop.ib.lock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBuildLock {
    @Test
    void testExclusiveAndCrossThread(@TempDir Path tmp) throws Exception {
        try (BuildLock outer = BuildLock.acquire(tmp, Duration.ofSeconds(5))) {
            assertNotNull(outer);
            AtomicReference<BuildLock> inner = new AtomicReference<>();
            CountDownLatch tried = new CountDownLatch(1);
            Thread t = new Thread(() -> {
                inner.set(BuildLock.acquire(tmp, Duration.ofMillis(800)));
                tried.countDown();
            });
            t.start();
            assertTrue(tried.await(5, TimeUnit.SECONDS));
            t.join();
            assertNull(inner.get(), "second holder must time out while outer holds the lock");
        }
        // after release the lock is acquirable again
        try (BuildLock again = BuildLock.acquire(tmp, Duration.ofSeconds(5))) {
            assertNotNull(again);
        }
    }

    @Test
    void testTimeoutSemantics(@TempDir Path tmp) {
        try (BuildLock ignored = BuildLock.acquire(tmp, Duration.ofSeconds(5))) {
            assertNotNull(ignored);
            long start = System.currentTimeMillis();
            BuildLock timedOut = BuildLock.acquire(tmp, Duration.ofMillis(300));
            long waited = System.currentTimeMillis() - start;
            assertNull(timedOut, "expected timeout result, not exception");
            assertTrue(waited >= 250, "waited " + waited + "ms");
        }
    }

    @Test
    void testMvnqCrossProcessCompat(@TempDir Path tmp) throws Exception {
        // hold the lock in-process, then verify mvnq's MvnqLock helper (the same
        // FileChannel protocol on the same file) queues and reports timeout exit 75
        Path lockFile = tmp.resolve(".mvn/build.lock");
        try (BuildLock outer = BuildLock.acquire(tmp, Duration.ofSeconds(5))) {
            assertNotNull(outer);
            Process p = new ProcessBuilder("java", lockHelper(), lockFile.toString(), "700",
                    "sleep", "5")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            int code = p.waitFor();
            assertEquals(75, code, "mvnq helper must report timeout 75; output: " + out);
            assertTrue(out.contains("waiting for build lock"), out);
        }
        // and the reverse order: mvnq holds, nop-ib times out
        Process holder = new ProcessBuilder("java", lockHelper(), lockFile.toString(), "60000",
                "sleep", "3")
                .redirectErrorStream(true).start();
        // wait until the holder actually owns the lock: a probe acquisition must
        // FAIL (null). A blind sleep raced the holder JVM's source-launch startup
        // under load and flipped this assertion (sweep-load flake, 2026-11-14)
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            try (BuildLock probe = BuildLock.acquire(tmp, Duration.ofMillis(200))) {
                if (probe == null)
                    break; // holder owns it now
            }
        }
        try (BuildLock l = BuildLock.acquire(tmp, Duration.ofMillis(500))) {
            assertNull(l, "nop-ib must not acquire while mvnq holds");
        }
        assertEquals(0, holder.waitFor(), "holder command should finish ok");
    }

    private static String lockHelper() {
        // ai-dev/tools/MvnqLock.java from the repo root (cwd of the surefire fork is
        // the module dir; walk up)
        Path dir = Path.of(System.getProperty("user.dir"));
        while (dir != null) {
            Path candidate = dir.resolve("ai-dev/tools/MvnqLock.java");
            if (candidate.toFile().exists())
                return candidate.toString();
            dir = dir.getParent();
        }
        throw new IllegalStateException("MvnqLock.java not found above " + System.getProperty("user.dir"));
    }
}
