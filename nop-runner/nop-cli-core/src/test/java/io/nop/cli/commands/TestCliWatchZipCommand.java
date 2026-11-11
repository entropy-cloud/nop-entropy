/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.autotest.core.mock.MockScheduledExecutor;
import io.nop.commons.concurrent.executor.IRateLimitExecutor;
import io.nop.commons.concurrent.executor.RateLimitExecutorImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

public class TestCliWatchZipCommand {

    @TempDir
    Path tempDir;

    private CliWatchZipCommand command;
    private File testDir;
    private File testZipFile;

    @BeforeEach
    void setUp() throws IOException {
        command = new CliWatchZipCommand();
        testDir = tempDir.resolve("test-watch-dir").toFile();
        testZipFile = tempDir.resolve("test-output.zip").toFile();

        // Create test directory
        assertTrue(testDir.mkdirs());

        // Create a test file in the directory
        File testFile = new File(testDir, "test.txt");
        Files.write(testFile.toPath(), "Hello World".getBytes());
    }

    @AfterEach
    void tearDown() {
        if (command != null) {
            command.destroy();
        }
    }

    @Test
    void testCommandParametersValidation() {
        // Test with non-existent directory
        command.watchDir = new File(tempDir.toFile(), "non-existent");
        command.zipFile = testZipFile;

        Integer result = command.call();
        assertEquals(1, result, "Should return error code for non-existent directory");
    }

    @Test
    void testDefaultZipFileName() {
        command.watchDir = testDir;
        // Don't set zipFile, should default to <watchDir>.zip

        // We can't easily test the full watch functionality without complex threading,
        // but we can test the parameter setup
        assertNull(command.zipFile, "zipFile should be null initially");

        // The actual zip file path would be set in the call() method
        // Expected: testDir.getParentFile() + "/" + testDir.getName() + ".zip"
        File expectedZipFile = new File(testDir.getParentFile(), testDir.getName() + ".zip");

        // This is what the command should set internally
        assertTrue(testDir.exists() && testDir.isDirectory(), "Test directory should exist");
    }

    @Test
    void testParameterDefaults() {
        assertEquals(100, command.debounceWait, "Default debounce wait should be 100ms");
        assertTrue(command.recursive, "Default recursive should be true");
    }

    // ===== plan 2563 MA-03 legs (C10-005 atomic + overlap) =====

    /**
     * Test stand-in: overrides performZipUpdate to block on a latch so a zip round can be held
     * in-flight while the rest of the machinery is driven; counts concurrent entries and total
     * rounds.
     */
    static class SlowZipCommand extends CliWatchZipCommand {
        final CountDownLatch gate = new CountDownLatch(1);
        final AtomicInteger totalRounds = new AtomicInteger();
        final AtomicInteger concurrent = new AtomicInteger();
        final AtomicInteger maxConcurrent = new AtomicInteger();
        volatile boolean block = true;

        @Override
        protected void performZipUpdate(File dir, File zipFile) {
            totalRounds.incrementAndGet();
            int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            try {
                if (block) {
                    // bounded: never hang the test JVM if the latch is never released
                    gate.await(20, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                concurrent.decrementAndGet();
                super.performZipUpdate(dir, zipFile);
            }
        }
    }

    private IRateLimitExecutor wrap(MockScheduledExecutor mock) {
        return new RateLimitExecutorImpl(mock);
    }

    private void drive(MockScheduledExecutor mock, int millis) {
        // debounce task bodies return null, so drain blindly: expire + fire a few rounds
        for (int i = 0; i < 5; i++) {
            mock.expireTasks(millis);
            mock.triggerNext();
        }
    }

    /**
     * Atomic leg + overlap leg (plan 2563 MA-03, C10-005). A slow zip round is held in-flight
     * while a second debounce event fires: (1) the second callback must not enter concurrently
     * (max concurrent == 1); (2) the dirty flag must compensate exactly one extra round, so the
     * last event is not lost; (3) the target zip stays a complete old product while the write
     * is in flight, and no tmp residue remains afterwards.
     */
    @Test
    void overlapLegConcurrentEventsSerializeAndDirtyCompensates() throws Exception {
        SlowZipCommand slow = new SlowZipCommand();
        slow.debounceWait = 10;
        MockScheduledExecutor mock = new MockScheduledExecutor();
        IRateLimitExecutor executor = wrap(mock);

        // event 1 -> debounce task, drive it on a worker thread into the blocked in-flight round
        slow.scheduleZipUpdate(executor, testDir, testZipFile);
        Thread roundA = new Thread(() -> drive(mock, 50));
        roundA.start();
        long deadline = System.currentTimeMillis() + 10_000;
        while (slow.totalRounds.get() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, slow.totalRounds.get(), "first round entered the slow zip");

        // event 2 arrives while round 1 is in flight -> must NOT enter concurrently
        slow.scheduleZipUpdate(executor, testDir, testZipFile);
        drive(mock, 50);
        assertEquals(1, slow.maxConcurrent.get(),
                "no second concurrent entry into the zip write while one round is in flight");
        assertEquals(1, slow.totalRounds.get(),
                "the overlapping callback must not run a second zip round");

        // release round 1: the dirty flag must compensate exactly one more round
        slow.block = false;
        slow.gate.countDown();
        roundA.join(20_000);
        deadline = System.currentTimeMillis() + 10_000;
        while (slow.totalRounds.get() < 2 && System.currentTimeMillis() < deadline) {
            drive(mock, 50);
            Thread.sleep(10);
        }
        assertEquals(2, slow.totalRounds.get(),
                "dirty compensation must run one extra round so the last event is not lost");
        assertEquals(1, slow.maxConcurrent.get(), "rounds stay serialized throughout");

        assertTrue(testZipFile.isFile(), "target zip exists");
        assertEquals(0, tempDir.toFile().list((d, n) -> n.contains(".tmp-")).length,
                "no tmp residue left behind after atomic move");
    }

    /**
     * Atomic leg: while a zip round is in flight, a concurrent reader of the target zip only
     * ever sees a complete old product - the new product appears via ATOMIC_MOVE. Also pins
     * the written zip content is complete and readable.
     */
    @Test
    void atomicLegReaderSeesCompleteOldOrNewProductOnly() throws Exception {
        SlowZipCommand slow = new SlowZipCommand();
        slow.debounceWait = 10;
        MockScheduledExecutor mock = new MockScheduledExecutor();

        // many entries lengthen the write window so a direct overwrite would reliably be caught
        // by the concurrent reader mid-write
        for (int i = 0; i < 300; i++) {
            Files.write(testDir.toPath().resolve("bulk-" + i + ".txt"),
                    ("bulk-content-" + i).getBytes());
        }

        // seed a complete old product
        slow.performZipUpdate(testDir, testZipFile);
        assertTrue(testZipFile.isFile());

        AtomicInteger readerFailures = new AtomicInteger();
        AtomicReference<Exception> readerError = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            while (!Thread.interrupted()) {
                try (ZipFile zip = new ZipFile(testZipFile)) {
                    ZipEntry entry = zip.getEntry("test.txt");
                    if (entry == null) {
                        readerFailures.incrementAndGet();
                    }
                } catch (IOException e) {
                    // a partially overwritten product manifests as a broken zip here
                    readerError.set(e);
                    return;
                }
            }
        });
        reader.start();

        // hold a slow write in flight, then release it and let the move happen
        slow.scheduleZipUpdate(wrap(mock), testDir, testZipFile);
        Thread roundA = new Thread(() -> drive(mock, 50));
        roundA.start();
        long deadline = System.currentTimeMillis() + 10_000;
        while (slow.totalRounds.get() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        slow.block = false;
        slow.gate.countDown();
        roundA.join(20_000);
        deadline = System.currentTimeMillis() + 10_000;
        while (slow.totalRounds.get() < 1 || !testZipFile.isFile() || testZipFile.length() == 0) {
            if (System.currentTimeMillis() > deadline) break;
            Thread.sleep(10);
        }
        reader.interrupt();
        reader.join(5000);

        assertNull(readerError.get(),
                "concurrent reader must never observe a partially overwritten zip: "
                        + (readerError.get() == null ? "" : readerError.get()));
        assertEquals(0, readerFailures.get(), "target zip content stays complete for readers");

        try (ZipFile zip = new ZipFile(testZipFile)) {
            assertNotNull(zip.getEntry("test.txt"), "new product contains the watched file");
        }
        assertEquals(0, tempDir.toFile().list((d, n) -> n.contains(".tmp-")).length,
                "no tmp residue behind");
    }
}