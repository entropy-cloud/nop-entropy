package io.nop.ai.toolkit.fs;

import io.nop.ai.api.exceptions.NopAiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused value-level tests for the 9 bare {@code IllegalArgumentException}
 * throws in {@link LocalToolFileSystem} converted to {@link NopAiException}
 * (plan 2026-08-01-0936-3). Also verifies that the {@code isPathAllowed}
 * security decision logic itself is unchanged.
 */
public class TestLocalToolFileSystemErrors {

    @TempDir
    File tempDir;

    private LocalToolFileSystem newFs() {
        return new LocalToolFileSystem(tempDir);
    }

    @Test
    public void testPathNotAllowedCarriesVerbatimMessage() {
        LocalToolFileSystem fs = newFs();

        NopAiException ex = assertThrows(NopAiException.class, () -> fs.readText("../secret.txt", 100));
        assertTrue(ex.getMessage().contains("Path not allowed: ../secret.txt"),
                "path traversal must be rejected with the verbatim message");
    }

    @Test
    public void testReadTextFileNotFoundCarriesVerbatimMessage() {
        LocalToolFileSystem fs = newFs();

        NopAiException ex = assertThrows(NopAiException.class, () -> fs.readText("missing.txt", 100));
        assertTrue(ex.getMessage().contains("File not found: missing.txt"),
                "missing file must be reported with the verbatim message");
    }

    @Test
    public void testReadLinesFileNotFoundCarriesVerbatimMessage() {
        LocalToolFileSystem fs = newFs();

        NopAiException ex = assertThrows(NopAiException.class, () -> fs.readLines("missing.txt", 1, 10, 100));
        assertTrue(ex.getMessage().contains("File not found: missing.txt"),
                "missing file must be reported with the verbatim message");
    }

    @Test
    public void testDirectoryNotFoundCarriesVerbatimMessage() {
        LocalToolFileSystem fs = newFs();

        NopAiException ex = assertThrows(NopAiException.class, () -> fs.listDirectory("missing-dir", 0, 10));
        assertTrue(ex.getMessage().contains("Directory not found: missing-dir"),
                "missing directory must be reported with the verbatim message");
    }

    @Test
    public void testMoveSourceFileNotFoundCarriesVerbatimMessage() {
        LocalToolFileSystem fs = newFs();

        NopAiException ex = assertThrows(NopAiException.class, () -> fs.move("no-src.txt", "dst.txt", false));
        assertTrue(ex.getMessage().contains("File not found: no-src.txt"),
                "missing move source must be reported with the verbatim message");
    }

    @Test
    public void testMoveTargetAlreadyExistsCarriesVerbatimMessage() throws IOException {
        LocalToolFileSystem fs = newFs();
        Files.writeString(new File(tempDir, "src.txt").toPath(), "a", StandardCharsets.UTF_8);
        Files.writeString(new File(tempDir, "dst.txt").toPath(), "b", StandardCharsets.UTF_8);

        NopAiException ex = assertThrows(NopAiException.class, () -> fs.move("src.txt", "dst.txt", false));
        assertTrue(ex.getMessage().contains("Target file already exists: dst.txt"),
                "existing move target must be reported with the verbatim message");
    }

    @Test
    public void testCopySourceFileNotFoundCarriesVerbatimMessage() {
        LocalToolFileSystem fs = newFs();

        NopAiException ex = assertThrows(NopAiException.class, () -> fs.copy("no-src.txt", "dst.txt", false, false));
        assertTrue(ex.getMessage().contains("File not found: no-src.txt"),
                "missing copy source must be reported with the verbatim message");
    }

    @Test
    public void testCopyTargetAlreadyExistsCarriesVerbatimMessage() throws IOException {
        LocalToolFileSystem fs = newFs();
        Files.writeString(new File(tempDir, "src.txt").toPath(), "a", StandardCharsets.UTF_8);
        Files.writeString(new File(tempDir, "dst.txt").toPath(), "b", StandardCharsets.UTF_8);

        NopAiException ex = assertThrows(NopAiException.class, () -> fs.copy("src.txt", "dst.txt", false, false));
        assertTrue(ex.getMessage().contains("Target file already exists: dst.txt"),
                "existing copy target must be reported with the verbatim message");
    }

    @Test
    public void testIsPathAllowedSecurityDecisionUnchanged() {
        LocalToolFileSystem fs = newFs();

        assertTrue(fs.isPathAllowed("file.txt"), "in-workdir path must remain allowed");
        assertTrue(fs.isPathAllowed(tempDir.getAbsolutePath() + "/sub.txt"),
                "absolute path under workdir must remain allowed");
        assertFalse(fs.isPathAllowed("/sub.txt"), "absolute path outside workdir must remain rejected");
        assertFalse(fs.isPathAllowed("../secret.txt"), "traversal path must remain rejected");
        assertFalse(fs.isPathAllowed(null), "null path must remain rejected");
    }
}
