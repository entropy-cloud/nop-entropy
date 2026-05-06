package io.nop.code.core.incremental;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestPathFingerprintStore extends TestIFingerprintStore {

    @TempDir
    Path tempDir;

    private PathFingerprintStore store;

    @BeforeEach
    void setUp() {
        store = new PathFingerprintStore(tempDir);
    }

    @Override
    IFingerprintStore createStore() {
        return store;
    }

    @Test
    void testManifestFileCreatedOnDisk() throws IOException {
        store.saveFingerprints("my-index", List.of(
                new FileFingerprint("a.txt", "h1", 1L, 10L)));
        Path manifest = tempDir.resolve("my-index").resolve("manifest.json");
        assertTrue(Files.exists(manifest), "manifest.json should exist on disk");
        String content = Files.readString(manifest);
        assertTrue(content.contains("a.txt"));
    }

    @Test
    void testMultipleIndexesIsolated() throws IOException {
        store.saveFingerprints("index-a", List.of(
                new FileFingerprint("a.txt", "ha", 1L, 10L)));
        store.saveFingerprints("index-b", List.of(
                new FileFingerprint("b.txt", "hb", 2L, 20L)));

        List<FileFingerprint> loadedA = store.loadFingerprints("index-a");
        List<FileFingerprint> loadedB = store.loadFingerprints("index-b");

        assertEquals(1, loadedA.size());
        assertEquals("a.txt", loadedA.get(0).getFilePath());

        assertEquals(1, loadedB.size());
        assertEquals("b.txt", loadedB.get(0).getFilePath());
    }

    @Test
    void testOverwriteExistingManifest() throws IOException {
        store.saveFingerprints("idx", List.of(
                new FileFingerprint("old.txt", "old-hash", 1L, 10L)));
        store.saveFingerprints("idx", List.of(
                new FileFingerprint("new.txt", "new-hash", 2L, 20L),
                new FileFingerprint("new2.txt", "new2-hash", 3L, 30L)));

        List<FileFingerprint> loaded = store.loadFingerprints("idx");
        assertEquals(2, loaded.size());
        for (FileFingerprint fp : loaded) {
            assertNotEquals("old.txt", fp.getFilePath());
        }
    }
}
