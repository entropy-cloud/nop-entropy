package io.nop.code.core.incremental;

import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestManifestStore {

    @TempDir
    Path tempDir;

    private ManifestStore store;
    private String manifestPath;

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        store = new ManifestStore();
        manifestPath = "file:" + tempDir.resolve("manifest.json").toAbsolutePath();
    }

    private FileFingerprint fp(String path, String hash, long modified, long size) {
        return new FileFingerprint(path, hash, modified, size);
    }

    private void assertFpEquals(FileFingerprint expected, FileFingerprint actual) {
        assertEquals(expected.getFilePath(), actual.getFilePath());
        assertEquals(expected.getContentHash(), actual.getContentHash());
        assertEquals(expected.getLastModified(), actual.getLastModified());
        assertEquals(expected.getFileSize(), actual.getFileSize());
    }

    @Test
    void testSaveAndLoad() throws IOException {
        List<FileFingerprint> fps = List.of(
                fp("a.txt", "hash1", 1000L, 100L),
                fp("b.txt", "hash2", 2000L, 200L));
        store.save(manifestPath, fps);
        List<FileFingerprint> loaded = store.load(manifestPath);
        assertEquals(2, loaded.size());
        assertFpEquals(fps.get(0), loaded.get(0));
        assertFpEquals(fps.get(1), loaded.get(1));
    }

    @Test
    void testLoadNonExistent() throws IOException {
        List<FileFingerprint> loaded = store.load("file:" + tempDir.resolve("no-such-file.json").toAbsolutePath());
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void testSaveEmptyList() throws IOException {
        store.save(manifestPath, Collections.emptyList());
        List<FileFingerprint> loaded = store.load(manifestPath);
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void testSaveSingleFingerprint() throws IOException {
        FileFingerprint expected = fp("single.txt", "abc123", 9999L, 42L);
        store.save(manifestPath, List.of(expected));
        List<FileFingerprint> loaded = store.load(manifestPath);
        assertEquals(1, loaded.size());
        assertFpEquals(expected, loaded.get(0));
    }

    @Test
    void testRoundTripPreservesData() throws IOException {
        FileFingerprint original = fp("deep/nested/path/File.java", "sha256:deadbeef", -1L, Long.MAX_VALUE);
        store.save(manifestPath, List.of(original));
        FileFingerprint loaded = store.load(manifestPath).get(0);
        assertEquals("deep/nested/path/File.java", loaded.getFilePath());
        assertEquals("sha256:deadbeef", loaded.getContentHash());
        assertEquals(-1L, loaded.getLastModified());
        assertEquals(Long.MAX_VALUE, loaded.getFileSize());
    }

    @Test
    void testFilePathWithSpecialChars() throws IOException {
        FileFingerprint original = fp("path with spaces\\and\\backslashes", "h", 1L, 2L);
        store.save(manifestPath, List.of(original));
        FileFingerprint loaded = store.load(manifestPath).get(0);
        assertEquals("path with spaces\\and\\backslashes", loaded.getFilePath());
    }

    @Test
    void testLoadMalformedJson() throws IOException {
        Path manifestFile = tempDir.resolve("manifest.json");
        Files.writeString(manifestFile, "this is not json!!!");
        List<FileFingerprint> loaded = store.load("file:" + manifestFile.toAbsolutePath());
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void testSaveCreatesParentDirectories() throws IOException {
        String nestedPath = "file:" + tempDir.resolve("a").resolve("b").resolve("c").resolve("manifest.json").toAbsolutePath();
        store.save(nestedPath, List.of(fp("x.txt", "h", 1L, 1L)));
        assertTrue(Files.exists(tempDir.resolve("a").resolve("b").resolve("c").resolve("manifest.json")));
        assertEquals(1, store.load(nestedPath).size());
    }

    @Test
    void testLargeFingerprintList() throws IOException {
        List<FileFingerprint> fps = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            fps.add(fp("file" + i + ".txt", "hash" + i, i * 100L, i * 10L));
        }
        store.save(manifestPath, fps);
        List<FileFingerprint> loaded = store.load(manifestPath);
        assertEquals(100, loaded.size());
        assertFpEquals(fps.get(0), loaded.get(0));
        assertFpEquals(fps.get(99), loaded.get(99));
    }

    @Test
    void testOverwriteExisting() throws IOException {
        store.save(manifestPath, List.of(fp("old.txt", "old", 1L, 1L)));
        store.save(manifestPath, List.of(fp("new.txt", "new", 2L, 2L), fp("new2.txt", "new2", 3L, 3L)));
        List<FileFingerprint> loaded = store.load(manifestPath);
        assertEquals(2, loaded.size());
        assertEquals("new.txt", loaded.get(0).getFilePath());
        assertEquals("new2.txt", loaded.get(1).getFilePath());
    }
}
