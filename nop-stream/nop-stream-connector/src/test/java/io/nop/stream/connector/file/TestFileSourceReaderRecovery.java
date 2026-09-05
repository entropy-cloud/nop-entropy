/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import io.nop.stream.core.source.SourceReaderContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-15 (plan 2026-09-04-1326-3): three recovery-cursor gaps in
 * {@link FileSourceReader}, one focused test each:
 * <ol>
 *   <li>partial skip on restore = typed IOException (never a silent read from the
 *       wrong position — truncated file after checkpoint);</li>
 *   <li>cursor advancement runs under the same monitor as {@code snapshotState}
 *       (concurrent snapshots never observe a torn cursor);</li>
 *   <li>reading is capped at {@code split.getEndOffset()} (a file that GREW after
 *       split enumeration never emits bytes beyond the split's byte range).</li>
 * </ol>
 */
class TestFileSourceReaderRecovery {

    @TempDir
    Path tempDir;

    private Path writeFile(String name, List<String> lines) throws IOException {
        Files.write(tempDir.resolve(name), (String.join("\n", lines) + "\n")
                .getBytes(StandardCharsets.UTF_8));
        return tempDir.resolve(name);
    }

    // ====================== gap 1: partial skip = typed failure ======================

    @Test
    void truncatedFileAfterCheckpointFailsTypedOnRestore() throws Exception {
        // Checkpoint taken after 4 lines (cursor = 12 bytes over "aa\n" lines)...
        Path file = writeFile("trunc.txt", List.of("aa", "aa", "aa", "aa", "aa"));
        long checkpointedCursor = 12;
        // ...then the file is truncated to 6 bytes (only the first 2 lines survive).
        try (var ch = Files.newByteChannel(file, java.nio.file.StandardOpenOption.WRITE)) {
            ch.truncate(6);
        }

        FileSplit stale = new FileSplit(file.toString(), 0L, file.toFile().length(),
                checkpointedCursor);
        FileSourceReader reader = new FileSourceReader(new SourceReaderContext(0, 1, null));
        reader.restoreState(Collections.singletonList(stale));

        IOException ex = assertThrows(IOException.class, reader::pollNext,
                "partial skip must fail typed instead of silently reading from a wrong position");
        assertTrue(ex.getMessage().contains("Failed to seek to cursor"),
                "failure names the unmet cursor: " + ex.getMessage());
        reader.close();
    }

    // ====================== gap 2: cursor advance under snapshotState's monitor ======================

    @Test
    void concurrentSnapshotsNeverObserveTornCursor() throws Exception {
        // 200 uniform 3-byte lines ("aa\n"): every legal cursor is a multiple of 3.
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            lines.add("aa");
        }
        Path file = writeFile("snap.txt", lines);
        long end = file.toFile().length();
        assertEquals(600L, end);

        FileSourceReader reader = new FileSourceReader(new SourceReaderContext(0, 1, null));
        reader.start();
        reader.addSplits(Collections.singletonList(new FileSplit(file.toString(), 0L, end)));

        Set<Long> observedCursors = ConcurrentHashMap.newKeySet();
        AtomicBoolean polling = new AtomicBoolean(true);
        CountDownLatch snapshotterDone = new CountDownLatch(1);
        // 确定性同步信号：快照线程首次观察到任一 cursor 即 countDown。
        // 主线程在耗尽数据行之前等待该信号，保证 observedCursors 非空不依赖
        // 线程调度时序/机器快慢（await 的长超时仅为死锁保护，正常路径必达）
        CountDownLatch firstObserved = new CountDownLatch(1);

        Thread snapshotter = new Thread(() -> {
            try {
                while (polling.get()) {
                    List<FileSplit> snap = reader.snapshotState(1L);
                    for (FileSplit split : snap) {
                        // Under the fixed monitor discipline every observed cursor is a
                        // line boundary (multiple of 3); a torn write could expose an
                        // arbitrary byte value (e.g. 1 or 2).
                        if (observedCursors.add(split.getCurrentOffset()))
                            firstObserved.countDown();
                    }
                    Thread.sleep(0, 200);
                }
                snapshotterDone.countDown();
            } catch (Exception e) {
                // surfaced by the poll thread's assertions below (snapshotter failure
                // would leave observedCursors empty and the latch uncounted)
            }
        });
        snapshotter.start();

        Optional<String> line;
        boolean waitedForFirstObservation = false;
        while ((line = reader.pollNext()).isPresent()) {
            assertEquals("aa", line.get());
            if (!waitedForFirstObservation) {
                assertTrue(firstObserved.await(30, java.util.concurrent.TimeUnit.SECONDS),
                        "snapshot thread must observe at least one cursor before data is drained");
                waitedForFirstObservation = true;
            }
        }
        reader.pollNext(); // let the reader close out the active split
        polling.set(false);
        assertTrue(snapshotterDone.await(30, java.util.concurrent.TimeUnit.SECONDS),
                "snapshot thread must terminate with the poll loop");
        reader.close();

        assertFalseEmpty(observedCursors);
        for (Long cursor : observedCursors) {
            assertEquals(0L, cursor % 3,
                    "cursor " + cursor + " is not a line boundary — torn cursor observed "
                            + "(snapshotState and cursor advancement are not mutually exclusive)");
        }
        // Final snapshot pins the completed cursor exactly at endOffset.
        assertEquals(Collections.emptyList(), reader.snapshotState(2L));
    }

    private static void assertFalseEmpty(Set<Long> set) {
        assertTrue(!set.isEmpty(), "snapshot thread must have observed at least one cursor");
    }

    // ====================== gap 3: endOffset cap ======================

    @Test
    void grownFileNeverEmitsBeyondEndOffset() throws Exception {
        // Split enumerated over 2 lines = 6 bytes...
        Path file = writeFile("grow.txt", List.of("aa", "bb"));
        long enumeratedSize = file.toFile().length();
        assertEquals(6L, enumeratedSize);

        // ...then the file grows (appended 2 more lines after enumeration).
        Files.write(file, "cc\ndd\n".getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.APPEND);
        assertTrue(file.toFile().length() > enumeratedSize, "file grew after enumeration");

        FileSourceReader reader = new FileSourceReader(new SourceReaderContext(0, 1, null));
        reader.start();
        // The split's contract is the byte range [0, 6) — the grown region belongs to a
        // FUTURE split, never to this one.
        reader.addSplits(Collections.singletonList(
                new FileSplit(file.toString(), 0L, enumeratedSize)));

        List<String> emitted = new ArrayList<>();
        Optional<String> line;
        while ((line = reader.pollNext()).isPresent()) {
            emitted.add(line.get());
        }
        reader.pollNext(); // close out the split
        reader.close();

        assertEquals(List.of("aa", "bb"), emitted,
                "only the split's byte range may be emitted — appended bytes beyond "
                        + "endOffset must not leak out");
        assertEquals(1, reader.getFinishedSplitCount());
    }
}
