package io.nop.ai.toolkit.fs;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2026-09-14-1937-1 Phase 2: {@link LocalToolFileSystem#writeText} atomic
 * replace semantics (temp + rename; failure never destroys the previous
 * target content) and fail-fast {@code mkdirs}/{@code delete} (no more silent
 * success when the operation fails).
 */
public class TestLocalToolFileSystemAtomicWrite {

    @TempDir
    File tempDir;

    private LocalToolFileSystem newFs() {
        return new LocalToolFileSystem(tempDir);
    }

    private String readText(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void testWriteTextReplacesContentAndLeavesNoTempFiles() throws Exception {
        LocalToolFileSystem fs = newFs();
        File target = new File(tempDir, "a.txt");
        Files.writeString(target.toPath(), "old", StandardCharsets.UTF_8);

        fs.writeText("a.txt", "new content", false);

        assertEquals("new content", readText(target));
        assertEquals(1, Objects.requireNonNull(tempDir.listFiles()).length,
                "no temp files may be left behind");
    }

    @Test
    public void testWriteTextCreatesMissingParentDirectories() throws Exception {
        LocalToolFileSystem fs = newFs();

        fs.writeText("sub/dir/b.txt", "content", false);

        assertTrue(new File(tempDir, "sub/dir/b.txt").exists());
        assertEquals("content", readText(new File(tempDir, "sub/dir/b.txt")));
    }

    @Test
    public void testWriteTextFailurePreservesOldContentWhenTargetNotReplaceable() throws Exception {
        // "target in non-replaceable form": the target path is an existing
        // non-empty directory, so the final move must fail and the directory
        // (the previous "content") must survive untouched.
        LocalToolFileSystem fs = newFs();
        File dirTarget = new File(tempDir, "t.txt");
        assertTrue(dirTarget.mkdir());
        File child = new File(dirTarget, "keep.txt");
        Files.writeString(child.toPath(), "keep", StandardCharsets.UTF_8);

        assertThrows(NopException.class, () -> fs.writeText("t.txt", "x", false));

        assertTrue(dirTarget.isDirectory(), "old directory target must survive the failed write");
        assertEquals("keep", readText(child));
    }

    @Test
    public void testWriteTextFailurePreservesOldContentOnReadOnlyParent() throws Exception {
        // POSIX-only failure injection: creating the temp file fails when the
        // parent directory loses write permission; the previous target content
        // must survive.
        Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));

        LocalToolFileSystem fs = newFs();
        File target = new File(tempDir, "ro.txt");
        Files.writeString(target.toPath(), "old content", StandardCharsets.UTF_8);

        assertTrue(tempDir.setWritable(false, false));
        Assumptions.assumeFalse(tempDir.canWrite(), "test requires a non-writable parent dir");
        try {
            assertThrows(NopException.class, () -> fs.writeText("ro.txt", "new", false));
            assertEquals("old content", readText(target),
                    "previous content must survive a failed atomic write");
        } finally {
            assertTrue(tempDir.setWritable(true, false));
        }
    }

    @Test
    public void testAppendKeepsAppendSemantics() throws Exception {
        LocalToolFileSystem fs = newFs();

        fs.writeText("log.txt", "line1\n", false);
        fs.writeText("log.txt", "line2\n", true);

        assertEquals("line1\nline2\n", readText(new File(tempDir, "log.txt")));
    }

    @Test
    public void testMkdirsFailureThrowsWhenParentIsFile() throws Exception {
        LocalToolFileSystem fs = newFs();
        File blocker = new File(tempDir, "block");
        Files.writeString(blocker.toPath(), "x", StandardCharsets.UTF_8);

        NopException ex = assertThrows(NopException.class, () -> fs.mkdirs("block/sub"));

        assertTrue(ex.getMessage().contains("Failed to create directory: block/sub"),
                "mkdirs failure must carry the failing path");
        assertTrue(blocker.isFile(), "blocker file must be untouched");
    }

    @Test
    public void testMkdirsOnExistingDirectoryIsNoOp() throws Exception {
        LocalToolFileSystem fs = newFs();
        assertTrue(new File(tempDir, "x").mkdir());

        fs.mkdirs("x");

        assertTrue(new File(tempDir, "x").isDirectory());
    }

    @Test
    public void testDeleteFailureThrowsWhenDirectoryNotEmptyWithoutRecursive() throws Exception {
        LocalToolFileSystem fs = newFs();
        File dir = new File(tempDir, "d");
        assertTrue(dir.mkdir());
        Files.writeString(new File(dir, "keep.txt").toPath(), "keep", StandardCharsets.UTF_8);

        NopException ex = assertThrows(NopException.class, () -> fs.delete("d", false, false));

        assertTrue(ex.getMessage().contains("Failed to delete: d"),
                "delete failure must carry the failing path");
        assertTrue(dir.isDirectory(), "non-empty directory must survive a non-recursive delete");

        fs.delete("d", true, false);
        assertFalse(dir.exists(), "recursive delete must succeed");
    }

    @Test
    public void testDeleteMissingFileIsNoOp() {
        LocalToolFileSystem fs = newFs();

        fs.delete("missing.txt", false, false);

        assertFalse(new File(tempDir, "missing.txt").exists());
    }
}