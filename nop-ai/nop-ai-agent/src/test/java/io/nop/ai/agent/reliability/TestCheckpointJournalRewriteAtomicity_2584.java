package io.nop.ai.agent.reliability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2584 Phase 1 (audit nop-ai Major 3) regression pins for the journal
 * rewrite that moved into {@link CheckpointJournalWriter} (shared ioLock +
 * temp-file + ATOMIC_MOVE publication):
 *
 * <ul>
 *   <li>a concurrent append during a rewrite is NEVER lost (the append
 *       serializes on the same ioLock; deterministic latch injection via the
 *       writer's in-lock test hook, no random concurrency);</li>
 *   <li>a rewrite failing mid-write leaves the previous journal untouched,
 *       readable and complete (no truncation);</li>
 *   <li>a successful rewrite leaves no temp files behind.</li>
 * </ul>
 */
public class TestCheckpointJournalRewriteAtomicity_2584 {

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("journal-rewrite-pin");
    }

    @AfterEach
    void tearDown() throws IOException {
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(tempDir)) {
            paths = walk.sorted(java.util.Comparator.reverseOrder()).toList();
        }
        for (Path p : paths) {
            Files.deleteIfExists(p);
        }
    }

    private Checkpoint cp(String watermark, int seq) {
        return Checkpoint.of("sess", watermark, seq, 1000L + seq,
                CheckpointType.TOOL_EXECUTION, "tool-" + seq, "call-" + seq,
                "in", "out", 1, 10);
    }

    private Path journalFile() {
        return tempDir.resolve("journal.md");
    }

    /**
     * Pin: a concurrent append during a rewrite is never lost. Deterministic
     * interleaving: thread A enters the rewrite (hook signals latch1 and
     * blocks on latch2 INSIDE the ioLock), thread B starts an append (blocks
     * on the same ioLock), then latch2 releases the rewrite. The final file
     * must contain BOTH the rewritten retained section and the appended
     * section — the pre-fix dual-lock shape (manager rotateLock vs writer
     * ioLock) could clobber B's append.
     */
    @Test
    void concurrentAppendDuringRewriteIsNeverLost() throws Exception {
        CheckpointJournalWriter writer = new CheckpointJournalWriter();
        Path file = journalFile();

        Checkpoint cp1 = cp("wm-1", 1);
        writer.appendCheckpoint(file, "sess", cp1);

        CountDownLatch rewriteInsideLock = new CountDownLatch(1);
        CountDownLatch releaseRewrite = new CountDownLatch(1);
        CountDownLatch appendDone = new CountDownLatch(1);
        AtomicReference<Throwable> appendError = new AtomicReference<>();

        writer.rewriteInsideLockHookForTest = () -> {
            rewriteInsideLock.countDown();
            try {
                releaseRewrite.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        List<Checkpoint> retained = java.util.Collections.singletonList(cp1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> writer.rewriteJournal(file, "sess", retained));
            assertTrue(rewriteInsideLock.await(10, TimeUnit.SECONDS),
                    "rewrite must be inside the ioLock before the append starts");

            Thread appender = new Thread(() -> {
                try {
                    writer.appendCheckpoint(file, "sess", cp("wm-2", 2));
                } catch (Throwable e) {
                    appendError.set(e);
                } finally {
                    appendDone.countDown();
                }
            });
            appender.start();
            // the append must block on the ioLock while the rewrite is in-flight
            releaseRewrite.countDown();

            assertTrue(appendDone.await(30, TimeUnit.SECONDS),
                    "the blocked append must complete after the rewrite");
            assertTrue(appendError.get() == null,
                    "append must not fail: " + appendError.get());

            CheckpointJournalReader reader = new CheckpointJournalReader();
            List<Checkpoint> readBack = reader.readAll(file);
            assertEquals(2, readBack.size(),
                    "the rewritten retained checkpoint AND the concurrent append must both survive");
            assertEquals("wm-1", readBack.get(0).getWatermark());
            assertEquals("wm-2", readBack.get(1).getWatermark());
        } finally {
            writer.rewriteInsideLockHookForTest = null;
            executor.shutdownNow();
        }
    }

    /**
     * Pin: a rewrite that fails mid-write (injected via the in-lock hook)
     * must leave the previous journal untouched — readable and complete, no
     * truncation, no temp leftovers.
     */
    @Test
    void failedRewriteLeavesJournalReadableAndComplete() throws Exception {
        CheckpointJournalWriter writer = new CheckpointJournalWriter();
        Path file = journalFile();

        writer.appendCheckpoint(file, "sess", cp("wm-1", 1));
        String before = Files.readString(file);

        writer.rewriteInsideLockHookForTest = () -> {
            throw new IllegalStateException("injected mid-rewrite failure (plan 2584 pin)");
        };
        try {
            // must NOT throw: a failed rotation rewrite never breaks the
            // checkpoint write path
            writer.rewriteJournal(file, "sess", java.util.Collections.singletonList(cp("wm-1", 1)));
        } finally {
            writer.rewriteInsideLockHookForTest = null;
        }

        assertEquals(before, Files.readString(file),
                "a failed rewrite must leave the previous journal byte-identical (no truncation)");
        CheckpointJournalReader reader = new CheckpointJournalReader();
        List<Checkpoint> readBack = reader.readAll(file);
        assertEquals(1, readBack.size(), "the journal must stay readable");
        assertEquals("wm-1", readBack.get(0).getWatermark());
        assertTrue(listTempFiles().isEmpty(), "no temp files may be left behind");
    }

    /** Pin: a successful rewrite publishes atomically and leaves no temp files. */
    @Test
    void successfulRewriteLeavesNoTempFiles() throws Exception {
        CheckpointJournalWriter writer = new CheckpointJournalWriter();
        Path file = journalFile();
        writer.appendCheckpoint(file, "sess", cp("wm-1", 1));

        writer.rewriteJournal(file, "sess", java.util.Collections.singletonList(cp("wm-2", 2)));

        CheckpointJournalReader reader = new CheckpointJournalReader();
        List<Checkpoint> readBack = reader.readAll(file);
        assertEquals(1, readBack.size());
        assertEquals("wm-2", readBack.get(0).getWatermark(),
                "the rewrite must publish only the retained set");
        assertTrue(listTempFiles().isEmpty(), "no temp files may remain after an atomic publish");
    }

    private List<Path> listTempFiles() throws IOException {
        try (Stream<Path> files = Files.list(tempDir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".tmp")).toList();
        }
    }
}
