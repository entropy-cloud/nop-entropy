package io.nop.ai.shell.io;

import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.fs.LocalToolFileSystem;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2557 C8-010 (ruling I) + C8-012 (ruling J) carrier (C10-008 one guard
 * face over both):
 *
 * <ul>
 * <li>I: redirect output is flushed to disk in threshold chunks —
 * intermediate state is visible in the target file BEFORE close; the append
 * trap is handled (first flush truncates, subsequent flushes append); the
 * byte cap fails the command loud.</li>
 * <li>J: giant text chunks are split at the BlockingQueueShellOutput entry;
 * an over-cap readLine arrives as successive bounded splices whose
 * concatenation restores the full line; readAllText over the total cap throws
 * NopException (fail-loud, no silent truncation); FileShellInput splits giant
 * stored chunks.</li>
 * </ul>
 */
@Timeout(30)
class TestShellIoBounds {

    private static final Logger LOG = LoggerFactory.getLogger(TestShellIoBounds.class);

    private final List<Path> tempDirs = new ArrayList<>();

    private Path newTempDir() throws Exception {
        Path dir = Files.createTempDirectory("shell-io-bounds");
        tempDirs.add(dir);
        return dir;
    }

    @AfterEach
    void cleanupTempDirs() {
        for (Path dir : tempDirs) {
            try {
                if (Files.exists(dir)) {
                    // test tree only; recursion via walk
                    try (var walk = Files.walk(dir)) {
                        walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception e) {
                                LOG.warn("cleanup failed: {}", p, e);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                LOG.warn("cleanup failed for {}", dir, e);
            }
        }
        tempDirs.clear();
    }

    private String readTarget(Path dir, String relPath) throws Exception {
        return new String(Files.readAllBytes(dir.resolve(relPath)), StandardCharsets.UTF_8);
    }

    // ======================= ruling I: FileShellOutput =======================

    /**
     * Mutation-sensitive pin: chunked flush must be visible at the threshold,
     * BEFORE close. If the threshold flush is removed (mutation), the
     * intermediate-state assertion goes red.
     */
    @Test
    void testChunkedFlushVisibleAtThresholdBeforeClose() throws Exception {
        Path tempDir = newTempDir();
        IToolFileSystem fs = new LocalToolFileSystem(tempDir.toFile());
        String relPath = "redirect.txt";

        // tiny threshold so the test observes the flush trigger directly
        FileShellOutput output = new FileShellOutput(relPath, fs, false, 16,
                FileShellOutput.DEFAULT_MAX_TOTAL_BYTES);

        String firstBatch = repeat('a', 20); // >= threshold: write must flush by itself
        output.print(firstBatch);

        // intermediate state visible on disk before flush()/close()
        assertEquals(firstBatch, readTarget(tempDir, relPath),
                "reaching the flush threshold must persist the buffer before close");

        String secondBatch = repeat('b', 5); // below threshold: stays buffered
        output.print(secondBatch);
        assertEquals(firstBatch, readTarget(tempDir, relPath),
                "below-threshold tail must stay buffered");

        output.close();
        assertEquals(firstBatch + secondBatch, readTarget(tempDir, relPath),
                "final content must be the exact concatenation (no duplication, no loss)");
    }

    /**
     * Append trap: original append=false semantics — the FIRST flush writes
     * with append=false (truncate), subsequent flushes switch to append=true.
     */
    @Test
    void testOverwriteModeFirstFlushTruncatesThenAppends() throws Exception {
        Path tempDir = newTempDir();
        IToolFileSystem fs = new LocalToolFileSystem(tempDir.toFile());
        String relPath = "overwrite.txt";
        Files.writeString(tempDir.resolve(relPath), "old-content\n", StandardCharsets.UTF_8);

        FileShellOutput output = new FileShellOutput(relPath, fs, false, 8,
                FileShellOutput.DEFAULT_MAX_TOTAL_BYTES);
        String b1 = repeat('1', 10);
        String b2 = repeat('2', 10);
        String b3 = "tail";
        output.print(b1); // flush 1: truncate
        output.print(b2); // flush 2: append
        output.print(b3); // buffered
        output.close();   // final flush: append

        assertEquals(b1 + b2 + b3, readTarget(tempDir, relPath),
                "old content truncated by first flush; later flushes appended exactly once");
    }

    @Test
    void testAppendModeKeepsExistingContent() throws Exception {
        Path tempDir = newTempDir();
        IToolFileSystem fs = new LocalToolFileSystem(tempDir.toFile());
        String relPath = "append.txt";
        Files.writeString(tempDir.resolve(relPath), "existing\n", StandardCharsets.UTF_8);

        FileShellOutput output = new FileShellOutput(relPath, fs, true, 8,
                FileShellOutput.DEFAULT_MAX_TOTAL_BYTES);
        output.print(repeat('x', 10));
        output.close();

        String content = readTarget(tempDir, relPath);
        assertTrue(content.startsWith("existing\n"), "append mode must keep prior content");
        assertTrue(content.contains(repeat('x', 10)));
    }

    /** ruling I: over-cap redirect output FAILS the command loud. */
    @Test
    void testOutputByteCapFailsLoud() throws Exception {
        Path tempDir = newTempDir();
        IToolFileSystem fs = new LocalToolFileSystem(tempDir.toFile());
        String relPath = "capped.txt";

        FileShellOutput output = new FileShellOutput(relPath, fs, false, 4, 10);

        output.print(repeat('a', 8)); // buffered, under cap
        NopException ex = assertThrows(NopException.class, () -> output.print(repeat('b', 8)),
                "exceeding the byte cap must fail loud, not truncate silently");
        assertEquals(io.nop.ai.shell.NopAiShellErrors.ERR_AI_SHELL_OUTPUT_CAP_EXCEEDED.getErrorCode(), ex.getErrorCode());

        output.close();
        // everything accepted before the cap is on disk
        assertEquals(repeat('a', 8), readTarget(tempDir, relPath));
    }

    // ======================= ruling J: chunk caps =======================

    @Test
    void testBlockingQueueWriteSplitsGiantTextChunk() throws Exception {
        BlockingQueueShellOutput output = new BlockingQueueShellOutput(64);
        String giant = repeat('g', BlockingQueueShellOutput.MAX_CHUNK_CHARS + 100);
        output.write(ShellChunk.text(giant));
        output.close();

        List<String> texts = new ArrayList<>();
        IShellInput input = output.asInput();
        ShellChunk chunk;
        while ((chunk = input.read()) != null) {
            assertTrue(chunk.isText());
            assertTrue(chunk.asText().length() <= BlockingQueueShellOutput.MAX_CHUNK_CHARS,
                    "every queued chunk must be bounded");
            texts.add(chunk.asText());
        }
        assertTrue(texts.size() >= 2, "giant chunk must be split into multiple chunks");
        assertEquals(giant, String.join("", texts), "concatenation restores the original chunk");
    }

    /**
     * Over-cap readLine arrives as successive bounded SPLICES; the splice
     * concatenation restores the full line and following lines stay intact.
     */
    @Test
    void testReadLineSplicesOverCapLineConcatenationRestoresFullLine() throws Exception {
        BlockingQueueShellOutput output = new BlockingQueueShellOutput(64);
        String fullLine = repeat('L', AbstractShellInput.MAX_READ_LINE_CHARS * 2 + 5);
        output.println(fullLine);
        output.println("next");
        output.close();

        AbstractShellInput input = (AbstractShellInput) output.asInput();
        StringBuilder restored = new StringBuilder();
        String part;
        while ((part = input.readLine()) != null && !part.equals("next")) {
            assertTrue(part.length() <= AbstractShellInput.MAX_READ_LINE_CHARS,
                    "every splice must be bounded");
            restored.append(part);
            assertTrue(restored.length() <= fullLine.length(), "no spurious extra output");
        }
        assertEquals(fullLine, restored.toString(), "concatenation of splices restores the full line");
        assertEquals("next", part, "following lines must stay intact");
        assertNull(input.readLine());
    }

    /** ruling J: readAllText total cap is fail-loud with NopException. */
    @Test
    void testReadAllTextOverTotalCapThrowsNopException() throws Exception {
        String content = repeat('c', 100);
        FileShellInput input = new FileShellInput("capped.txt", content) {
            @Override
            protected int readAllTextMaxChars() {
                return 50;
            }
        };

        NopException ex = assertThrows(NopException.class, input::readAllText,
                "readAllText over the total cap must fail loud, never truncate silently");
        assertEquals(io.nop.ai.shell.NopAiShellErrors.ERR_AI_SHELL_INPUT_CAP_EXCEEDED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void testReadAllTextUnderCapStillWorks() throws Exception {
        FileShellInput input = new FileShellInput("ok.txt", "hello ") {
            @Override
            protected int readAllTextMaxChars() {
                return 50;
            }
        };
        assertEquals("hello ", input.readAllText());
    }

    @Test
    void testFileShellInputSplitsGiantStoredChunk() throws Exception {
        String giant = repeat('s', AbstractShellInput.MAX_READ_LINE_CHARS + 1);
        FileShellInput input = new FileShellInput("big.txt", giant);

        ShellChunk first = input.read();
        assertNotNull(first, "first chunk must exist");
        assertEquals(AbstractShellInput.MAX_READ_LINE_CHARS, first.asText().length(),
                "giant stored content must be split into bounded chunks");

        ShellChunk second = input.read();
        assertNotNull(second);
        assertEquals(1, second.asText().length());
        assertNull(input.read());

        assertEquals(giant, first.asText() + second.asText());
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }
}
