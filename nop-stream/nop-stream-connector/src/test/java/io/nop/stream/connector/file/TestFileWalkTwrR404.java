/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.file;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.44 R4-04 (plan 2537) red⑨ acceptance pin — W-09 (finding 1.2-C6-011;
 * adjudication C.4 / seam S9): both bare {@code Files.walk} sites
 * ({@code FileSplitEnumerator.discoverSplits} + {@code FileSource.sample}) must
 * close the returned Stream on EVERY path — the exception path included. Pre-fix
 * a traversal that dies mid-walk (unreadable subdirectory → UncheckedIOException)
 * never executed the walker's registered onClose, and {@code UnixDirectoryStream}
 * has no finalizer/Cleaner (JDK 25 verified), so the directory fd leaked until
 * process exit.
 *
 * <p>Legs: (a) fd-delta leg (primary, the R07 form of plan 2522
 * {@code TestFileDownloadSinkClose} — shape reused, zero file copied):
 * {@link com.sun.management.UnixOperatingSystemMXBean#getOpenFileDescriptorCount()}
 * delta over N failure iterations must stay bounded (pre-fix it grows ≈1 fd per
 * iteration); (b) structure leg (dual-point source-text assertion, also the
 * documented degradation channel when the fd counters or permission injection
 * are unavailable); (c) happy-path regression: a fully readable tree still
 * discovers every file (TWR must not break the exhaustive self-close semantics).
 */
class TestFileWalkTwrR404 {

    private static boolean openFileDescriptorCountAvailable() {
        return ManagementFactory.getOperatingSystemMXBean()
                instanceof com.sun.management.UnixOperatingSystemMXBean;
    }

    @Test
    @EnabledIf("openFileDescriptorCountAvailable")
    void fdDeltaStaysBoundedAcrossWalkFailureIterationsR404() throws Exception {
        Path base = Files.createTempDirectory("r404-fd");
        Path dir = base.resolve("src");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("a.txt"), "data");
        Path closed = Files.createDirectories(dir.resolve("closed"));
        boolean permInjected;
        try {
            Files.setPosixFilePermissions(closed, PosixFilePermissions.fromString("---------"));
            permInjected = true;
        } catch (UnsupportedOperationException | IOException e) {
            permInjected = false;
        }
        if (!permInjected) {
            // documented degradation channel (adjudication C.4): permission injection
            // unavailable on this filesystem → the structure leg + the mutation probe
            // carry the pin (see ledger); the assumption marks this leg SKIPPED loudly
            // (visible in the signature, never a silent pass)
            org.junit.jupiter.api.Assumptions.assumeTrue(permInjected,
                    "degraded: perm injection unsupported on this filesystem — the "
                            + "structure leg + mutation probe carry red⑨ here");
            return;
        }
        com.sun.management.UnixOperatingSystemMXBean osBean =
                (com.sun.management.UnixOperatingSystemMXBean)
                        ManagementFactory.getOperatingSystemMXBean();
        try {
            // warmup: settle the fd baseline (JIT, logging, temp-dir machinery)
            for (int i = 0; i < 5; i++) {
                try {
                    new FileSplitEnumerator(dir.toString()).start(null);
                } catch (Exception expected) {
                    // UncheckedIOException on the unreadable subdir
                }
            }
            long before = osBean.getOpenFileDescriptorCount();
            int iterations = 40;
            int failures = 0;
            for (int i = 0; i < iterations; i++) {
                try {
                    new FileSplitEnumerator(dir.toString()).start(null);
                } catch (Exception expected) {
                    failures++;
                }
            }
            long delta = osBean.getOpenFileDescriptorCount() - before;
            assertTrue(failures >= iterations,
                    "every iteration must hit the failure path (got " + failures + ")");
            assertTrue(delta <= 5,
                    "the walk failure path must not leak directory fds: " + iterations
                            + " failing traversals leaked " + delta
                            + " fds (≈1 per bare Files.walk call — CRA 8.44 R4-04 W-09 red⑨; "
                            + "post-fix TWR closes the stream on every path)");
        } finally {
            Files.setPosixFilePermissions(closed, PosixFilePermissions.fromString("rwxr-xr-x"));
            cleanup(base);
        }
    }

    private static void cleanup(Path base) {
        try (var walk = Files.walk(base)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }

    // ==================== structure leg (dual-point, degradation channel) ====================

    private static String readSource(String relPath) throws IOException {
        Path source = Path.of(relPath);
        assertTrue(Files.exists(source),
                "source must be readable from the module working directory: "
                        + source.toAbsolutePath());
        return Files.readString(source);
    }

    @Test
    void bothWalkSitesUseTryWithResourcesR404() throws Exception {
        String enumerator = readSource(
                "src/main/java/io/nop/stream/connector/file/FileSplitEnumerator.java");
        String fileSource = readSource(
                "src/main/java/io/nop/stream/connector/file/FileSource.java");

        assertFalse(enumerator.contains("Files.walk(dir)\n"),
                "discoverSplits must not keep the bare Files.walk shape (the returned "
                        + "Stream must be TWR-owned — red⑨ structure leg, enumerator point)");
        assertTrue(enumerator.contains("try (Stream<Path>"),
                "discoverSplits must own the walk Stream via try-with-resources "
                        + "(SnapshotStrategy :162 alignment — red⑨ structure leg)");
        assertFalse(fileSource.contains("files = Files.walk(dir)"),
                "sample must not assign the bare Files.walk stream outside a TWR "
                        + "(red⑨ structure leg, sample point)");
        assertTrue(fileSource.contains("try (Stream<Path>"),
                "sample must own the walk Stream via try-with-resources (red⑨ structure leg)");
    }

    // ==================== happy-path regression ====================

    @Test
    void happyPathStillDiscoversEveryReadableFileR404() throws Exception {
        Path base = Files.createTempDirectory("r404-happy");
        try {
            Path dir = base.resolve("src");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("a.txt"), "1");
            Files.writeString(dir.resolve("b.txt"), "22");
            Files.createDirectories(dir.resolve("nested"));
            Files.writeString(dir.resolve("nested").resolve("c.txt"), "333");

            FileSplitEnumerator enumerator = new FileSplitEnumerator(dir.toString());
            enumerator.start(null);
            assertEquals(3, enumerator.getDiscoveredCount(),
                    "TWR must not change the exhaustive traversal on the happy path");
        } finally {
            cleanup(base);
        }
    }
}
