/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.source.SimpleVersionedSerializer;
import io.nop.stream.core.source.SourceReaderContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap item 10 (connectors audit) focused regression tests. Each test pins the
 * behavior introduced by one audit fix:
 * <ul>
 *   <li>CN-1 — reader cursor must ADVANCE from the restored base after recovery, never
 *       regress to startOffset on the first post-restore snapshot (kill-recover
 *       continuation semantics, source → collected sink).</li>
 *   <li>CN-2 — split / enumerator-state serializers fail fast on malformed payloads
 *       and on paths that cannot round-trip through the '|' / ',' / newline separators
 *       (no silent split loss / offset corruption).</li>
 *   <li>CN-4 — constructor null/empty guards throw the typed StreamException used by
 *       every other connector (taxonomy consistency).</li>
 * </ul>
 */
class TestFileSourceAuditFixes {

    @TempDir
    Path tempDir;

    // ====================== CN-1: post-restore cursor semantics ======================

    @Test
    void snapshotAfterFirstPostRestorePollAdvancesFromRestoredBase() throws Exception {
        Path dir = tempDir.resolve("cn1-cursor");
        createDir(dir);
        // 5 lines "L1".."L5", 3 bytes each ("L1" + LF)
        writeLines(dir, "seq.txt", Arrays.asList("L1", "L2", "L3", "L4", "L5"));

        // Simulate a checkpoint taken after consuming L1: cursor = 3
        FileSplit checkpointed = new FileSplit(dir.resolve("seq.txt").toString(),
                0L, dir.resolve("seq.txt").toFile().length(), 3L);

        FileSourceReader reader = new FileSourceReader(new SourceReaderContext(0, 1, null));
        reader.restoreState(Collections.singletonList(checkpointed));

        // Consume exactly ONE line (L2) post-restore, then checkpoint again
        Optional<String> line = reader.pollNext();
        assertEquals("L2", line.orElseThrow(), "restored reader must resume after the checkpointed cursor");

        List<FileSplit> snapshot = reader.snapshotState(2L);
        assertEquals(1, snapshot.size());
        assertEquals(6L, snapshot.get(0).getCurrentOffset(),
                "post-restore cursor must be restoredBase(3) + bytesRead(3) = 6; a value of 3 means "
                        + "the first snapshot after restore regressed the cursor to startOffset + bytes-since-open");
        reader.close();
    }

    @Test
    void killRecoverContinuationEmitsEachRecordExactlyOnce() throws Exception {
        Path dir = tempDir.resolve("cn1-kill-recover");
        createDir(dir);
        writeLines(dir, "seq.txt", Arrays.asList("L1", "L2", "L3", "L4", "L5"));

        // "Sink" = collected output list; the source drives records into it across a
        // simulated crash boundary (source reader → sink contract, kill-recover pattern
        // mirroring TestFileTwoPhaseCommitSink.testFileSinkKillRecoverExactlyOnce).
        List<String> sink = new ArrayList<>();

        // Phase 1: consume L1..L2, checkpoint (cursor = 8), "crash"
        FileSourceReader r1 = new FileSourceReader(new SourceReaderContext(0, 1, null));
        r1.start();
        r1.addSplits(Collections.singletonList(
                new FileSplit(dir.resolve("seq.txt").toString(),
                        0L, dir.resolve("seq.txt").toFile().length())));
        sink.add(r1.pollNext().orElseThrow()); // L1
        sink.add(r1.pollNext().orElseThrow()); // L2
        List<FileSplit> snap = r1.snapshotState(1L);
        r1.close();

        // Phase 2: new reader instance restores from the checkpoint and drains the rest
        FileSourceReader r2 = new FileSourceReader(new SourceReaderContext(0, 1, null));
        r2.restoreState(snap);
        Optional<String> next;
        while ((next = r2.pollNext()).isPresent()) {
            sink.add(next.get());
        }
        r2.pollNext(); // let the reader close out the active split
        r2.close();

        // Exactly-once continuation: all 5 records, no loss, no duplicate re-emission
        assertEquals(Arrays.asList("L1", "L2", "L3", "L4", "L5"), sink,
                "kill-recover continuation must emit every record exactly once across the crash boundary");
    }

    @Test
    void loneCrAndCrLfTerminatorsKeepCursorByteAccurate() throws Exception {
        Path dir = tempDir.resolve("cn1-terminators");
        createDir(dir);
        // Lone-CR terminated first line, CRLF second line, LF third line
        File f = new File(dir.toFile(), "mixed.txt");
        byte[] bytes = ("first\rsecond\r\nthird\n").getBytes(StandardCharsets.UTF_8);
        if (!f.getParentFile().mkdirs() && !f.getParentFile().isDirectory()) {
            throw new IOException("Failed to create dir");
        }
        java.nio.file.Files.write(f.toPath(), bytes);

        FileSourceReader reader = new FileSourceReader(new SourceReaderContext(0, 1, null));
        reader.start();
        reader.addSplits(Collections.singletonList(
                new FileSplit(f.toString(), 0L, bytes.length)));

        List<String> lines = new ArrayList<>();
        long cursorAfterSecondLine;
        Optional<String> next;
        int polls = 0;
        while ((next = reader.pollNext()).isPresent()) {
            lines.add(next.get());
            polls++;
            if (polls == 2) {
                // cursor after "first\r" (6) + "second\r\n" (8) = 14
                cursorAfterSecondLine = reader.snapshotState(1L).get(0).getCurrentOffset();
                assertEquals(14L, cursorAfterSecondLine,
                        "cursor must count terminator bytes exactly: lone CR=1, CRLF=2");
            }
        }
        reader.pollNext();
        reader.close();

        assertEquals(Arrays.asList("first", "second", "third"), lines,
                "all three terminator styles must split lines correctly");
    }

    // ====================== CN-2: serializer fail-fast ======================

    @Test
    void splitSerializerRejectsPayloadWithExtraPipeParts() {
        FileSource.FileSplitSerializer ser = new FileSource.FileSplitSerializer();
        byte[] corrupt = "/tmp/a|b.txt|0|100|50".getBytes(StandardCharsets.UTF_8);
        IOException ex = assertThrows(IOException.class, () -> ser.deserialize(ser.getVersion(), corrupt));
        assertTrue(ex.getMessage().contains("Malformed"),
                "payload whose path contains '|' must fail fast, not silently mis-parse");
    }

    @Test
    void splitSerializerRejectsNonNumericOffsets() {
        FileSource.FileSplitSerializer ser = new FileSource.FileSplitSerializer();
        byte[] corrupt = "/tmp/a.txt|zero|100|50".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> ser.deserialize(ser.getVersion(), corrupt));
    }

    @Test
    void splitSerializerRejectsPathWithReservedSeparators() {
        FileSource.FileSplitSerializer ser = new FileSource.FileSplitSerializer();
        FileSplit bad = new FileSplit("/tmp/a|b.txt", 0L, 100L);
        assertThrows(IOException.class, () -> ser.serialize(bad));
        FileSplit badNewline = new FileSplit("/tmp/a\nb.txt", 0L, 100L);
        assertThrows(IOException.class, () -> ser.serialize(badNewline));
    }

    @Test
    void enumeratorStateSerializerRejectsMalformedSplitLine() {
        FileSource.FileSplitEnumeratorStateSerializer ser =
                new FileSource.FileSplitEnumeratorStateSerializer();
        // 5-part split line = path containing '|'
        byte[] corrupt = "/tmp/dir\n0\n\n\n\n/tmp/a|b.txt|0|100|50\n".getBytes(StandardCharsets.UTF_8);
        IOException ex = assertThrows(IOException.class,
                () -> ser.deserialize(ser.getVersion(), corrupt));
        assertTrue(ex.getMessage().contains("Malformed split line"),
                "a corrupt split line must fail fast instead of being silently skipped (split loss on restore)");
    }

    @Test
    void enumeratorStateSerializerRejectsPathWithCommaInCsvSection() {
        FileSource.FileSplitEnumeratorStateSerializer ser =
                new FileSource.FileSplitEnumeratorStateSerializer();
        Set<String> discovered = new LinkedHashSet<>(Collections.singletonList("/tmp/a,b.txt"));
        Map<String, FileSplit> byId = new LinkedHashMap<>();
        FileSplitEnumeratorState state = new FileSplitEnumeratorState(
                "/tmp/dir", discovered, new LinkedHashSet<>(), new LinkedHashSet<>(), byId, 0);
        assertThrows(IOException.class, () -> ser.serialize(state),
                "a path containing ',' cannot round-trip the CSV section and must fail at serialize time");
    }

    // ====================== CN-4: constructor taxonomy ======================

    @Test
    void fileSourceConstructorRejectsNullAndEmptyWithTypedException() {
        assertThrows(StreamException.class, () -> new FileSource(null));
        assertThrows(StreamException.class, () -> new FileSource(""));
    }

    @Test
    void filePendingCommitConstructorRejectsNullWithTypedException() {
        assertThrows(StreamException.class, () -> new FilePendingCommit(null, 1));
    }

    // ====================== helpers ======================

    private static void createDir(Path dir) throws IOException {
        if (!dir.toFile().mkdirs() && !dir.toFile().isDirectory()) {
            throw new IOException("Failed to create directory: " + dir);
        }
    }

    private static void writeLines(Path dir, String fileName, List<String> lines) throws IOException {
        io.nop.commons.util.FileHelper.writeText(new File(dir.toFile(), fileName),
                String.join("\n", lines) + "\n", StandardCharsets.UTF_8.name());
    }
}
