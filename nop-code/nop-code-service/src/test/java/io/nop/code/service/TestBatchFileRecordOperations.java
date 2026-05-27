package io.nop.code.service;

import io.nop.code.core.incremental.FileFingerprint;
import io.nop.code.core.incremental.InMemoryFingerprintStore;
import io.nop.code.service.impl.CodeIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestBatchFileRecordOperations {

    private CodeIndexService service;
    private InMemoryFingerprintStore store;

    @BeforeEach
    void setUp() {
        service = new CodeIndexService();
        store = new InMemoryFingerprintStore();
        service.setFingerprintStore(store);
    }

    @Test
    void testFingerprintStore_saveAndLoadRoundTrip() throws IOException {
        List<FileFingerprint> fingerprints = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            fingerprints.add(new FileFingerprint("file" + i + ".java", "hash" + i, i * 100L, i * 10L));
        }

        store.saveFingerprints("test-index", fingerprints);
        List<FileFingerprint> loaded = store.loadFingerprints("test-index");

        assertEquals(10, loaded.size());
        FileFingerprint first = loaded.stream()
                .filter(f -> "file0.java".equals(f.getFilePath())).findFirst().orElseThrow();
        assertEquals("hash0", first.getContentHash());
        FileFingerprint last = loaded.stream()
                .filter(f -> "file9.java".equals(f.getFilePath())).findFirst().orElseThrow();
        assertEquals("hash9", last.getContentHash());
    }

    @Test
    void testFingerprintStore_deleteByPathsLeavesRemaining() throws IOException {
        List<FileFingerprint> fingerprints = List.of(
                new FileFingerprint("a.txt", "hash1", 100L, 10L),
                new FileFingerprint("b.txt", "hash2", 200L, 20L),
                new FileFingerprint("c.txt", "hash3", 300L, 30L));

        store.saveFingerprints("test-index", fingerprints);
        store.deleteByPaths("test-index", List.of("a.txt", "c.txt"));

        List<FileFingerprint> loaded = store.loadFingerprints("test-index");
        assertEquals(1, loaded.size());
        assertEquals("b.txt", loaded.get(0).getFilePath());
    }

    @Test
    void testService_batchLoadReturnsEmptyWhenNoDatabase() {
        List<FileFingerprint> result = service.batchLoadFileRecords("nonexistent");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testService_batchSaveDoesNotThrowWithoutDatabase() {
        List<FileFingerprint> fingerprints = List.of(
                new FileFingerprint("a.txt", "hash1", 100L, 10L));
        assertDoesNotThrow(() -> service.batchSaveFileRecords("test-index", fingerprints));
    }
}
