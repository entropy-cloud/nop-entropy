package io.nop.code.core.incremental;

import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        IResource resource = toResource(file);
        FileFingerprint fp = detector.computeFingerprint(resource);

        assertEquals(resource.getPath(), fp.getFilePath());
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

        IResource resource = toResource(file);
        FileFingerprint fp1 = detector.computeFingerprint(resource);
        FileFingerprint fp2 = detector.computeFingerprint(resource);

        assertEquals(fp1.getContentHash(), fp2.getContentHash());
    }

    @Test
    void testFingerprintDifferentContent() throws IOException {
        Path fileA = tempDir.resolve("a.txt");
        Path fileB = tempDir.resolve("b.txt");
        Files.writeString(fileA, "content A");
        Files.writeString(fileB, "content B");

        FileFingerprint fpA = detector.computeFingerprint(toResource(fileA));
        FileFingerprint fpB = detector.computeFingerprint(toResource(fileB));

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
        List<IResource> currentResources = List.of(toResource(file1), toResource(file2));

        ChangeSet cs = detector.detectResourceChanges(previous, currentResources);

        assertEquals(2, cs.getAddedFiles().size());
        assertTrue(cs.getAddedFiles().contains(toResource(file1).getPath()));
        assertTrue(cs.getAddedFiles().contains(toResource(file2).getPath()));
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

        List<IResource> allResources = List.of(toResource(file1), toResource(file2));
        List<FileFingerprint> previous = detector.computeResourceFingerprints(allResources);
        List<IResource> currentResources = Collections.emptyList();

        ChangeSet cs = detector.detectResourceChanges(previous, currentResources);

        assertEquals(2, cs.getDeletedFiles().size());
        assertTrue(cs.getAddedFiles().isEmpty());
        assertTrue(cs.getModifiedFiles().isEmpty());
        assertTrue(cs.getUnchangedFiles().isEmpty());
    }

    @Test
    void testDetectUnchangedFiles() throws IOException {
        Path file = tempDir.resolve("stable.txt");
        Files.writeString(file, "unchanged");

        IResource resource = toResource(file);
        FileFingerprint prev = detector.computeFingerprint(resource);
        List<FileFingerprint> previous = List.of(prev);

        List<IResource> currentResources = List.of(resource);
        ChangeSet cs = detector.detectResourceChanges(previous, currentResources);

        assertEquals(1, cs.getUnchangedFiles().size());
        assertTrue(cs.getUnchangedFiles().contains(resource.getPath()));
        assertTrue(cs.getAddedFiles().isEmpty());
        assertTrue(cs.getModifiedFiles().isEmpty());
        assertTrue(cs.getDeletedFiles().isEmpty());
    }

    @Test
    void testDetectModifiedFiles() throws IOException {
        Path file = tempDir.resolve("mutable.txt");
        Files.writeString(file, "original");

        IResource resource = toResource(file);
        FileFingerprint prev = detector.computeFingerprint(resource);
        List<FileFingerprint> previous = List.of(prev);

        Files.writeString(file, "modified content", StandardOpenOption.TRUNCATE_EXISTING);
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5000));

        IResource modifiedResource = toResource(file);
        List<IResource> currentResources = List.of(modifiedResource);
        ChangeSet cs = detector.detectResourceChanges(previous, currentResources);

        assertEquals(1, cs.getModifiedFiles().size());
        assertTrue(cs.getUnchangedFiles().isEmpty());
        assertTrue(cs.getAddedFiles().isEmpty());
        assertTrue(cs.getDeletedFiles().isEmpty());
    }

    @Test
    void testDetectTouchUnchanged() throws IOException {
        Path file = tempDir.resolve("touched.txt");
        Files.writeString(file, "stable content");

        IResource resource = toResource(file);
        FileFingerprint prev = detector.computeFingerprint(resource);
        List<FileFingerprint> previous = List.of(prev);

        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 10000));

        IResource touchedResource = toResource(file);
        List<IResource> currentResources = List.of(touchedResource);
        ChangeSet cs = detector.detectResourceChanges(previous, currentResources);

        assertEquals(1, cs.getUnchangedFiles().size());
        assertTrue(cs.getModifiedFiles().isEmpty());
    }

    // ---- computeResourceFingerprints batch ----

    @Test
    void testComputeFingerprintsBatch() throws IOException {
        Path f1 = tempDir.resolve("a.txt");
        Path f2 = tempDir.resolve("b.txt");
        Path f3 = tempDir.resolve("c.txt");
        Files.writeString(f1, "alpha");
        Files.writeString(f2, "beta");
        Files.writeString(f3, "gamma");

        List<IResource> resources = List.of(toResource(f1), toResource(f2), toResource(f3));
        List<FileFingerprint> fps = detector.computeResourceFingerprints(resources);

        assertEquals(3, fps.size());

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

        IResource existingResource = toResource(existingFile);
        FileFingerprint prevFp = detector.computeFingerprint(existingResource);
        Files.writeString(existingFile, "changed stuff", StandardOpenOption.TRUNCATE_EXISTING);
        Files.setLastModifiedTime(existingFile, FileTime.fromMillis(System.currentTimeMillis() + 5000));

        List<FileFingerprint> previous = List.of(prevFp);
        List<IResource> currentResources = List.of(toResource(addedFile), toResource(existingFile));

        ChangeSet cs = detector.detectResourceChanges(previous, currentResources);

        List<String> combined = cs.getAddedAndModified();
        assertEquals(2, combined.size());
    }

    // ---- ChangeSet list immutability ----

    @Test
    void testChangeSetListsAreUnmodifiable() {
        ChangeSet cs = new ChangeSet();
        assertThrows(UnsupportedOperationException.class, () -> cs.getAddedFiles().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> cs.getModifiedFiles().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> cs.getDeletedFiles().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> cs.getUnchangedFiles().add("x"));
    }

    // ---- mixed scenario ----

    @Test
    void testDetectMixedChanges() throws IOException {
        Path keepFile = tempDir.resolve("keep.txt");
        Path modifyFile = tempDir.resolve("modify.txt");
        Path deleteFile = tempDir.resolve("delete.txt");
        Files.writeString(keepFile, "keep");
        Files.writeString(modifyFile, "original");
        Files.writeString(deleteFile, "will be deleted");

        List<IResource> allResources = List.of(toResource(keepFile), toResource(modifyFile), toResource(deleteFile));
        List<FileFingerprint> previous = detector.computeResourceFingerprints(allResources);

        Files.writeString(modifyFile, "modified!", StandardOpenOption.TRUNCATE_EXISTING);
        Files.setLastModifiedTime(modifyFile, FileTime.fromMillis(System.currentTimeMillis() + 5000));

        Path addedFile = tempDir.resolve("added.txt");
        Files.writeString(addedFile, "brand new");

        List<IResource> currentResources = List.of(toResource(keepFile), toResource(modifyFile), toResource(addedFile));

        ChangeSet cs = detector.detectResourceChanges(previous, currentResources);

        assertEquals(1, cs.getUnchangedFiles().size());
        assertTrue(cs.getUnchangedFiles().contains(toResource(keepFile).getPath()));

        assertEquals(1, cs.getModifiedFiles().size());
        assertTrue(cs.getModifiedFiles().contains(toResource(modifyFile).getPath()));

        assertEquals(1, cs.getDeletedFiles().size());
        assertTrue(cs.getDeletedFiles().contains(toResource(deleteFile).getPath()));

        assertEquals(1, cs.getAddedFiles().size());
        assertTrue(cs.getAddedFiles().contains(toResource(addedFile).getPath()));
    }

    // ---- IResource-based tests ----

    private IResource toResource(Path file) {
        return new FileResource(file.toFile());
    }

    @Test
    void testComputeFingerprintFromResource() throws IOException {
        Path file = tempDir.resolve("resource_hello.txt");
        Files.writeString(file, "hello from resource");

        IResource resource = toResource(file);
        FileFingerprint fp = detector.computeFingerprint(resource);

        assertEquals(resource.getPath(), fp.getFilePath());
        assertNotNull(fp.getContentHash());
        assertEquals(64, fp.getContentHash().length(), "SHA-256 hex string must be 64 chars");
        assertTrue(fp.getContentHash().matches("[0-9a-f]{64}"), "Hash must be lowercase hex");
        assertTrue(fp.getLastModified() > 0, "mtime must be positive");
        assertEquals(Files.size(file), fp.getFileSize());
    }

    @Test
    void testDetectChangesFromResources() throws IOException {
        Path file1 = tempDir.resolve("r_keep.txt");
        Path file2 = tempDir.resolve("r_modify.txt");
        Path file3 = tempDir.resolve("r_delete.txt");
        Files.writeString(file1, "keep");
        Files.writeString(file2, "original");
        Files.writeString(file3, "to be deleted");

        List<IResource> allResources = List.of(toResource(file1), toResource(file2), toResource(file3));
        List<FileFingerprint> previous = detector.computeResourceFingerprints(allResources);

        Files.writeString(file2, "modified!", StandardOpenOption.TRUNCATE_EXISTING);
        Files.setLastModifiedTime(file2, FileTime.fromMillis(System.currentTimeMillis() + 5000));

        Path addedFile = tempDir.resolve("r_added.txt");
        Files.writeString(addedFile, "new file");

        List<IResource> currentResources = List.of(toResource(file1), toResource(file2), toResource(addedFile));

        ChangeSet cs = detector.detectResourceChanges(previous, currentResources);

        assertEquals(1, cs.getUnchangedFiles().size());
        assertEquals(1, cs.getModifiedFiles().size());
        assertEquals(1, cs.getDeletedFiles().size());
        assertEquals(1, cs.getAddedFiles().size());
    }

    @Test
    void testComputeFingerprintsFromResources() throws IOException {
        Path f1 = tempDir.resolve("r_a.txt");
        Path f2 = tempDir.resolve("r_b.txt");
        Files.writeString(f1, "alpha");
        Files.writeString(f2, "beta");

        List<IResource> resources = List.of(toResource(f1), toResource(f2));
        List<FileFingerprint> fps = detector.computeResourceFingerprints(resources);

        assertEquals(2, fps.size());
        assertEquals(toResource(f1).getPath(), fps.get(0).getFilePath());
        assertEquals(toResource(f2).getPath(), fps.get(1).getFilePath());

        for (FileFingerprint fp : fps) {
            assertNotNull(fp.getContentHash());
            assertEquals(64, fp.getContentHash().length());
            assertTrue(fp.getLastModified() > 0);
            assertTrue(fp.getFileSize() > 0);
        }
    }

    @Test
    void testDetectChangesFromStore() throws IOException {
        IFingerprintStore store = new InMemoryFingerprintStore();

        Path file1 = tempDir.resolve("s_keep.txt");
        Path file2 = tempDir.resolve("s_modify.txt");
        Files.writeString(file1, "keep");
        Files.writeString(file2, "original");

        List<IResource> initialResources = List.of(toResource(file1), toResource(file2));
        detector.computeAndSaveFingerprints(store, "test-index", initialResources);

        Files.writeString(file2, "changed", StandardOpenOption.TRUNCATE_EXISTING);
        Files.setLastModifiedTime(file2, FileTime.fromMillis(System.currentTimeMillis() + 5000));

        Path addedFile = tempDir.resolve("s_added.txt");
        Files.writeString(addedFile, "brand new");

        List<IResource> currentResources = List.of(toResource(file1), toResource(file2), toResource(addedFile));
        ChangeSet cs = detector.detectChangesFromStore(store, "test-index", currentResources);

        assertEquals(1, cs.getUnchangedFiles().size());
        assertEquals(1, cs.getModifiedFiles().size());
        assertEquals(1, cs.getAddedFiles().size());
        assertTrue(cs.getDeletedFiles().isEmpty());
    }

    @Test
    void testComputeAndSaveFingerprints() throws IOException {
        IFingerprintStore store = new InMemoryFingerprintStore();

        Path f1 = tempDir.resolve("save_a.txt");
        Path f2 = tempDir.resolve("save_b.txt");
        Files.writeString(f1, "content a");
        Files.writeString(f2, "content b");

        List<IResource> resources = List.of(toResource(f1), toResource(f2));
        List<FileFingerprint> saved = detector.computeAndSaveFingerprints(store, "my-index", resources);

        assertEquals(2, saved.size());

        List<FileFingerprint> loaded = store.loadFingerprints("my-index");
        assertEquals(2, loaded.size());
        Map<String, FileFingerprint> loadedMap = new HashMap<>();
        for (FileFingerprint fp : loaded) {
            loadedMap.put(fp.getFilePath(), fp);
        }
        for (FileFingerprint fp : saved) {
            FileFingerprint loadedFp = loadedMap.get(fp.getFilePath());
            assertNotNull(loadedFp, "Missing fingerprint for " + fp.getFilePath());
            assertEquals(fp.getContentHash(), loadedFp.getContentHash());
        }
    }
}
