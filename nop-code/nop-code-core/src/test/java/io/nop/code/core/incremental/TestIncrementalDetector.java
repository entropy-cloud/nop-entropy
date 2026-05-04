package io.nop.code.core.incremental;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestIncrementalDetector {

    @TempDir
    Path tempDir;

    private IncrementalDetector detector;

    @BeforeEach
    void setUp() {
        detector = new IncrementalDetector();
    }

    // ---- FileFingerprint tests ----

    @Test
    void testFileFingerprintConstructor() {
        FileFingerprint fp = new FileFingerprint("/some/path.txt", "abc123", 1000L, 42L);
        assertEquals("/some/path.txt", fp.getFilePath());
        assertEquals("abc123", fp.getContentHash());
        assertEquals(1000L, fp.getLastModified());
        assertEquals(42L, fp.getFileSize());
    }

    @Test
    void testFileFingerprintDefaults() {
        FileFingerprint fp = new FileFingerprint();
        assertNull(fp.getFilePath());
        assertNull(fp.getContentHash());
        assertEquals(0L, fp.getLastModified());
        assertEquals(0L, fp.getFileSize());

        fp.setFilePath("/x.txt");
        fp.setContentHash("deadbeef");
        fp.setLastModified(999L);
        fp.setFileSize(7L);

        assertEquals("/x.txt", fp.getFilePath());
        assertEquals("deadbeef", fp.getContentHash());
        assertEquals(999L, fp.getLastModified());
        assertEquals(7L, fp.getFileSize());
    }

    // ---- computeFingerprint ----

    @Test
    void testComputeFingerprint() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello world");

        FileFingerprint fp = detector.computeFingerprint(file);

        assertEquals(file.toString(), fp.getFilePath());
        assertNotNull(fp.getContentHash());
        assertEquals(64, fp.getContentHash().length(), "SHA-256 hex string must be 64 chars");
        assertTrue(fp.getContentHash().matches("[0-9a-f]{64}"), "Hash must be lowercase hex");
        assertTrue(fp.getLastModified() > 0, "mtime must be positive");
        assertEquals(Files.size(file), fp.getFileSize());
    }

    @Test
    void testFingerprintConsistent() throws IOException {
        Path file = tempDir.resolve("same.txt");
        Files.writeString(file, "consistent content");

        FileFingerprint fp1 = detector.computeFingerprint(file);
        FileFingerprint fp2 = detector.computeFingerprint(file);

        assertEquals(fp1.getContentHash(), fp2.getContentHash());
    }

    @Test
    void testFingerprintDifferentContent() throws IOException {
        Path fileA = tempDir.resolve("a.txt");
        Path fileB = tempDir.resolve("b.txt");
        Files.writeString(fileA, "content A");
        Files.writeString(fileB, "content B");

        FileFingerprint fpA = detector.computeFingerprint(fileA);
        FileFingerprint fpB = detector.computeFingerprint(fileB);

        assertNotEquals(fpA.getContentHash(), fpB.getContentHash());
    }

    // ---- detectChanges ----

    @Test
    void testDetectAddedFiles() throws IOException {
        Path file1 = tempDir.resolve("new1.txt");
        Path file2 = tempDir.resolve("new2.txt");
        Files.writeString(file1, "aaa");
        Files.writeString(file2, "bbb");

        List<FileFingerprint> previous = Collections.emptyList();
        List<Path> currentFiles = List.of(file1, file2);

        ChangeSet cs = detector.detectChanges(previous, currentFiles);

        assertEquals(2, cs.getAddedFiles().size());
        assertTrue(cs.getAddedFiles().contains(file1));
        assertTrue(cs.getAddedFiles().contains(file2));
        assertTrue(cs.getModifiedFiles().isEmpty());
        assertTrue(cs.getDeletedFiles().isEmpty());
        assertTrue(cs.getUnchangedFiles().isEmpty());
    }

    @Test
    void testDetectDeletedFiles() throws IOException {
        Path file1 = tempDir.resolve("gone1.txt");
        Path file2 = tempDir.resolve("gone2.txt");
        Files.writeString(file1, "x");
        Files.writeString(file2, "y");

        List<FileFingerprint> previous = detector.computeFingerprints(List.of(file1, file2));
        List<Path> currentFiles = Collections.emptyList();

        ChangeSet cs = detector.detectChanges(previous, currentFiles);

        assertEquals(2, cs.getDeletedFiles().size());
        assertTrue(cs.getAddedFiles().isEmpty());
        assertTrue(cs.getModifiedFiles().isEmpty());
        assertTrue(cs.getUnchangedFiles().isEmpty());
    }

    @Test
    void testDetectUnchangedFiles() throws IOException {
        Path file = tempDir.resolve("stable.txt");
        Files.writeString(file, "unchanged");

        FileFingerprint prev = detector.computeFingerprint(file);
        List<FileFingerprint> previous = List.of(prev);

        // Same file, same mtime and size — should be detected as unchanged (fast path)
        List<Path> currentFiles = List.of(file);
        ChangeSet cs = detector.detectChanges(previous, currentFiles);

        assertEquals(1, cs.getUnchangedFiles().size());
        assertTrue(cs.getUnchangedFiles().contains(file));
        assertTrue(cs.getAddedFiles().isEmpty());
        assertTrue(cs.getModifiedFiles().isEmpty());
        assertTrue(cs.getDeletedFiles().isEmpty());
    }

    @Test
    void testDetectModifiedFiles() throws IOException {
        Path file = tempDir.resolve("mutable.txt");
        Files.writeString(file, "original");

        FileFingerprint prev = detector.computeFingerprint(file);
        List<FileFingerprint> previous = List.of(prev);

        // Modify file content (and thus mtime + size change)
        Files.writeString(file, "modified content", StandardOpenOption.TRUNCATE_EXISTING);
        // Ensure mtime changes by setting a future timestamp
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5000));

        List<Path> currentFiles = List.of(file);
        ChangeSet cs = detector.detectChanges(previous, currentFiles);

        assertEquals(1, cs.getModifiedFiles().size());
        assertTrue(cs.getModifiedFiles().contains(file));
        assertTrue(cs.getUnchangedFiles().isEmpty());
        assertTrue(cs.getAddedFiles().isEmpty());
        assertTrue(cs.getDeletedFiles().isEmpty());
    }

    @Test
    void testDetectTouchUnchanged() throws IOException {
        Path file = tempDir.resolve("touched.txt");
        Files.writeString(file, "stable content");

        FileFingerprint prev = detector.computeFingerprint(file);
        List<FileFingerprint> previous = List.of(prev);

        // Touch: change mtime but keep content identical
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 10000));

        List<Path> currentFiles = List.of(file);
        ChangeSet cs = detector.detectChanges(previous, currentFiles);

        // mtime differs but content hash matches → unchanged (slow path)
        assertEquals(1, cs.getUnchangedFiles().size());
        assertTrue(cs.getUnchangedFiles().contains(file));
        assertTrue(cs.getModifiedFiles().isEmpty());
    }

    // ---- computeFingerprints batch ----

    @Test
    void testComputeFingerprintsBatch() throws IOException {
        Path f1 = tempDir.resolve("a.txt");
        Path f2 = tempDir.resolve("b.txt");
        Path f3 = tempDir.resolve("c.txt");
        Files.writeString(f1, "alpha");
        Files.writeString(f2, "beta");
        Files.writeString(f3, "gamma");

        List<FileFingerprint> fps = detector.computeFingerprints(List.of(f1, f2, f3));

        assertEquals(3, fps.size());
        assertEquals(f1.toString(), fps.get(0).getFilePath());
        assertEquals(f2.toString(), fps.get(1).getFilePath());
        assertEquals(f3.toString(), fps.get(2).getFilePath());

        // Each fingerprint must have a valid hash
        for (FileFingerprint fp : fps) {
            assertNotNull(fp.getContentHash());
            assertEquals(64, fp.getContentHash().length());
            assertTrue(fp.getLastModified() > 0);
            assertTrue(fp.getFileSize() > 0);
        }
    }

    // ---- ChangeSet getAddedAndModified ----

    @Test
    void testChangeSetGetAddedAndModified() throws IOException {
        Path addedFile = tempDir.resolve("added.txt");
        Path existingFile = tempDir.resolve("existing.txt");
        Files.writeString(addedFile, "new stuff");
        Files.writeString(existingFile, "old stuff");

        // Build a scenario with one added file and one modified file
        FileFingerprint prevFp = detector.computeFingerprint(existingFile);
        // Modify existing file
        Files.writeString(existingFile, "changed stuff", StandardOpenOption.TRUNCATE_EXISTING);
        Files.setLastModifiedTime(existingFile, FileTime.fromMillis(System.currentTimeMillis() + 5000));

        List<FileFingerprint> previous = List.of(prevFp);
        List<Path> currentFiles = List.of(addedFile, existingFile);

        ChangeSet cs = detector.detectChanges(previous, currentFiles);

        List<Path> combined = cs.getAddedAndModified();
        assertEquals(2, combined.size());
        assertTrue(combined.contains(addedFile));
        assertTrue(combined.contains(existingFile));
    }

    // ---- ChangeSet list immutability ----

    @Test
    void testChangeSetListsAreUnmodifiable() {
        ChangeSet cs = new ChangeSet();
        assertThrows(UnsupportedOperationException.class, () -> cs.getAddedFiles().add(Path.of("x")));
        assertThrows(UnsupportedOperationException.class, () -> cs.getModifiedFiles().add(Path.of("x")));
        assertThrows(UnsupportedOperationException.class, () -> cs.getDeletedFiles().add(Path.of("x")));
        assertThrows(UnsupportedOperationException.class, () -> cs.getUnchangedFiles().add(Path.of("x")));
    }

    // ---- mixed scenario ----

    @Test
    void testDetectMixedChanges() throws IOException {
        // Setup: 3 files
        Path keepFile = tempDir.resolve("keep.txt");
        Path modifyFile = tempDir.resolve("modify.txt");
        Path deleteFile = tempDir.resolve("delete.txt");
        Files.writeString(keepFile, "keep");
        Files.writeString(modifyFile, "original");
        Files.writeString(deleteFile, "will be deleted");

        List<FileFingerprint> previous = detector.computeFingerprints(
                List.of(keepFile, modifyFile, deleteFile));

        // Modify one file
        Files.writeString(modifyFile, "modified!", StandardOpenOption.TRUNCATE_EXISTING);
        Files.setLastModifiedTime(modifyFile, FileTime.fromMillis(System.currentTimeMillis() + 5000));

        // New file added
        Path addedFile = tempDir.resolve("added.txt");
        Files.writeString(addedFile, "brand new");

        // Current state: keep + modify + added (deleteFile omitted)
        List<Path> currentFiles = List.of(keepFile, modifyFile, addedFile);

        ChangeSet cs = detector.detectChanges(previous, currentFiles);

        assertEquals(1, cs.getUnchangedFiles().size());
        assertTrue(cs.getUnchangedFiles().contains(keepFile));

        assertEquals(1, cs.getModifiedFiles().size());
        assertTrue(cs.getModifiedFiles().contains(modifyFile));

        assertEquals(1, cs.getDeletedFiles().size());
        assertTrue(cs.getDeletedFiles().contains(deleteFile));

        assertEquals(1, cs.getAddedFiles().size());
        assertTrue(cs.getAddedFiles().contains(addedFile));
    }
}
