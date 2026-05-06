package io.nop.code.core.incremental;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

abstract class TestIFingerprintStore {

    abstract IFingerprintStore createStore();

    private FileFingerprint fp(String path, String hash, long modified, long size) {
        return new FileFingerprint(path, hash, modified, size);
    }

    @Test
    void testSaveAndLoadEmpty() throws IOException {
        IFingerprintStore store = createStore();
        store.saveFingerprints("idx", Collections.emptyList());
        List<FileFingerprint> loaded = store.loadFingerprints("idx");
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void testSaveAndLoadSingle() throws IOException {
        IFingerprintStore store = createStore();
        FileFingerprint expected = fp("a.txt", "hash1", 1000L, 100L);
        store.saveFingerprints("idx", List.of(expected));
        List<FileFingerprint> loaded = store.loadFingerprints("idx");
        assertEquals(1, loaded.size());
        assertEquals(expected.getFilePath(), loaded.get(0).getFilePath());
        assertEquals(expected.getContentHash(), loaded.get(0).getContentHash());
        assertEquals(expected.getLastModified(), loaded.get(0).getLastModified());
        assertEquals(expected.getFileSize(), loaded.get(0).getFileSize());
    }

    @Test
    void testSaveAndLoadBatch() throws IOException {
        IFingerprintStore store = createStore();
        List<FileFingerprint> fps = List.of(
                fp("a.txt", "h1", 1L, 10L),
                fp("b.txt", "h2", 2L, 20L),
                fp("c.txt", "h3", 3L, 30L));
        store.saveFingerprints("idx", fps);
        List<FileFingerprint> loaded = store.loadFingerprints("idx");
        assertEquals(3, loaded.size());
    }

    @Test
    void testSaveUpdatesExisting() throws IOException {
        IFingerprintStore store = createStore();
        store.saveFingerprints("idx", List.of(
                fp("a.txt", "old1", 1L, 10L),
                fp("b.txt", "old2", 2L, 20L)));
        store.saveFingerprints("idx", List.of(
                fp("a.txt", "new1", 11L, 110L)));
        List<FileFingerprint> loaded = store.loadFingerprints("idx");
        assertEquals(1, loaded.size());
        assertEquals("new1", loaded.get(0).getContentHash());
        assertEquals(110L, loaded.get(0).getFileSize());
    }

    @Test
    void testLoadNonExistentIndex() throws IOException {
        IFingerprintStore store = createStore();
        List<FileFingerprint> loaded = store.loadFingerprints("no-such-index");
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void testDeleteByPaths() throws IOException {
        IFingerprintStore store = createStore();
        store.saveFingerprints("idx", List.of(
                fp("a.txt", "h1", 1L, 10L),
                fp("b.txt", "h2", 2L, 20L),
                fp("c.txt", "h3", 3L, 30L)));
        store.deleteByPaths("idx", List.of("b.txt"));
        List<FileFingerprint> loaded = store.loadFingerprints("idx");
        assertEquals(2, loaded.size());
        for (FileFingerprint fp : loaded) {
            assertNotEquals("b.txt", fp.getFilePath());
        }
    }

    @Test
    void testDeleteByIndex() throws IOException {
        IFingerprintStore store = createStore();
        store.saveFingerprints("idx", List.of(
                fp("a.txt", "h1", 1L, 10L),
                fp("b.txt", "h2", 2L, 20L),
                fp("c.txt", "h3", 3L, 30L)));
        store.deleteByIndex("idx");
        List<FileFingerprint> loaded = store.loadFingerprints("idx");
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void testDeleteNonExistentPathsNoOp() throws IOException {
        IFingerprintStore store = createStore();
        store.saveFingerprints("idx", List.of(fp("a.txt", "h1", 1L, 10L)));
        assertDoesNotThrow(() -> store.deleteByPaths("idx", List.of("nonexistent.txt")));
        List<FileFingerprint> loaded = store.loadFingerprints("idx");
        assertEquals(1, loaded.size());
    }

    @Test
    void testLargeBatch() throws IOException {
        IFingerprintStore store = createStore();
        List<FileFingerprint> fps = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            fps.add(fp("file" + i + ".txt", "hash" + i, i * 100L, i * 10L));
        }
        store.saveFingerprints("idx", fps);
        List<FileFingerprint> loaded = store.loadFingerprints("idx");
        assertEquals(150, loaded.size());
    }

    @Test
    void testSpecialCharactersInPath() throws IOException {
        IFingerprintStore store = createStore();
        FileFingerprint fp = fp("path with spaces/中文路径/файл.java", "hash123", 42L, 999L);
        store.saveFingerprints("idx", List.of(fp));
        List<FileFingerprint> loaded = store.loadFingerprints("idx");
        assertEquals(1, loaded.size());
        assertEquals("path with spaces/中文路径/файл.java", loaded.get(0).getFilePath());
        assertEquals("hash123", loaded.get(0).getContentHash());
    }
}
