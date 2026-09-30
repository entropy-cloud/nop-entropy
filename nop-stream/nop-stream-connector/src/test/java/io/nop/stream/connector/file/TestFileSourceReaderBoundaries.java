/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5-CON-07 / R5-CON-10 focused tests for {@link FileSourceReader}:
 * <ol>
 *   <li>CON-07: a single line longer than {@code maxLineBytes} fails fast —
 *       the line-aggregation buffer is bounded, never heap-unbounded;</li>
 *   <li>CON-07 boundary: a line exactly at the limit still passes;</li>
 *   <li>CON-10: a split whose path escapes the source directory is rejected at
 *       openSplit (tampered legacy checkpoint payloads carry no checksum);</li>
 *   <li>CON-10 positive: splits inside the source directory read normally.</li>
 * </ol>
 */
class TestFileSourceReaderBoundaries {

    private static final long SMALL_LIMIT = 64L;

    @TempDir
    Path tempDir;

    private FileSourceReader readerFromSource(Path sourceDir, long maxLineBytes) {
        FileSource source = new FileSource(sourceDir.toString(), maxLineBytes);
        return (FileSourceReader) source.createReader(new SourceReaderContext(0, 1, null));
    }

    // ====================== R5-CON-07: line-length bound ======================

    @Test
    void overlongLineFailsFast() throws Exception {
        Path sourceDir = Files.createDirectories(tempDir.resolve("src07"));
        // 100 bytes with no line terminator, limit is 64 — must be rejected.
        byte[] payload = new byte[100];
        java.util.Arrays.fill(payload, (byte) 'x');
        Path file = Files.write(sourceDir.resolve("huge.txt"), payload);

        FileSourceReader reader = readerFromSource(sourceDir, SMALL_LIMIT);
        reader.addSplits(Collections.singletonList(
                new FileSplit(file.toString(), 0L, file.toFile().length())));

        IOException ex = assertThrows(IOException.class, reader::pollNext,
                "a line longer than maxLineBytes must fail fast, never buffer whole");
        assertTrue(ex.getMessage().contains("maxLineBytes=64"),
                "failure must name the bound: " + ex.getMessage());
        reader.close();
    }

    @Test
    void lineExactlyAtLimitStillPasses() throws Exception {
        Path sourceDir = Files.createDirectories(tempDir.resolve("src07b"));
        // Exactly 64 bytes then EOF: one line exactly at the limit is legal.
        byte[] payload = new byte[64];
        java.util.Arrays.fill(payload, (byte) 'y');
        Path file = Files.write(sourceDir.resolve("exact.txt"), payload);

        FileSourceReader reader = readerFromSource(sourceDir, SMALL_LIMIT);
        reader.addSplits(Collections.singletonList(
                new FileSplit(file.toString(), 0L, file.toFile().length())));

        Optional<String> line = reader.pollNext();
        assertTrue(line.isPresent(), "a line exactly at maxLineBytes must be emitted");
        assertEquals(64, line.get().length());
        reader.close();
    }

    // ====================== R5-CON-10: split-path containment ======================

    @Test
    void splitOutsideSourceDirectoryIsRejected() throws Exception {
        Path sourceDir = Files.createDirectories(tempDir.resolve("src10"));
        Files.write(sourceDir.resolve("in.txt"), "hello\n".getBytes(StandardCharsets.UTF_8));
        // The tampered-checkpoint threat: a file outside the source directory.
        Path outside = Files.write(tempDir.resolve("secret.txt"),
                "secret\n".getBytes(StandardCharsets.UTF_8));

        FileSourceReader reader = readerFromSource(sourceDir, FileSourceReader.DEFAULT_MAX_LINE_BYTES);
        reader.addSplits(Collections.singletonList(
                new FileSplit(outside.toString(), 0L, outside.toFile().length())));

        IOException ex = assertThrows(IOException.class, reader::pollNext,
                "a split path escaping the source directory must be rejected");
        assertTrue(ex.getMessage().contains("escapes the source directory"),
                "failure must name the containment violation: " + ex.getMessage());
        reader.close();
    }

    @Test
    void splitInsideSourceDirectoryReadsNormally() throws Exception {
        Path sourceDir = Files.createDirectories(tempDir.resolve("src10b"));
        Path inside = Files.write(sourceDir.resolve("ok.txt"),
                "line-1\nline-2\n".getBytes(StandardCharsets.UTF_8));

        FileSourceReader reader = readerFromSource(sourceDir, FileSourceReader.DEFAULT_MAX_LINE_BYTES);
        reader.addSplits(Collections.singletonList(
                new FileSplit(inside.toString(), 0L, inside.toFile().length())));

        assertEquals("line-1", reader.pollNext().orElse(null));
        assertEquals("line-2", reader.pollNext().orElse(null));
        reader.close();
    }

    @Test
    void traversalPathIsRejected() throws Exception {
        Path sourceDir = Files.createDirectories(tempDir.resolve("src10c"));
        // A ".." path that normalizes OUTSIDE the source directory (the file need
        // not exist — containment is checked before the existence probe).
        String traversal = sourceDir.toString() + java.io.File.separator
                + ".." + java.io.File.separator + "secret.txt";

        FileSourceReader reader = readerFromSource(sourceDir, FileSourceReader.DEFAULT_MAX_LINE_BYTES);
        reader.addSplits(Collections.singletonList(
                new FileSplit(traversal, 0L, 8L)));

        IOException ex = assertThrows(IOException.class, reader::pollNext,
                "a traversal path escaping the source directory must be rejected");
        assertTrue(ex.getMessage().contains("escapes the source directory"),
                "failure must name the containment violation: " + ex.getMessage());
        reader.close();
    }
}
