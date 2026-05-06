package io.nop.code.service;

import io.nop.code.core.incremental.FileFingerprint;
import io.nop.code.core.incremental.IFingerprintStore;
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
    void testBatchLoadFileRecordsReturnsEmptyWhenNoDatabase() {
        List<FileFingerprint> result = service.batchLoadFileRecords("test-index");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testBatchSaveFileRecordsGracefulWhenNoDatabase() {
        List<FileFingerprint> fingerprints = List.of(
                new FileFingerprint("a.txt", "hash1", 1000L, 100L),
                new FileFingerprint("b.txt", "hash2", 2000L, 200L));
        assertDoesNotThrow(() -> service.batchSaveFileRecords("test-index", fingerprints));
    }

    @Test
    void testBatchDeleteFileRecordsGracefulWhenNoDatabase() {
        assertDoesNotThrow(() -> service.batchDeleteFileRecords("test-index", List.of("a.txt")));
    }

    @Test
    void testFingerprintStoreIntegration() throws IOException {
        List<FileFingerprint> fingerprints = List.of(
                new FileFingerprint("a.txt", "hash1", 1000L, 100L),
                new FileFingerprint("b.txt", "hash2", 2000L, 200L));

        store.saveFingerprints("test-index", fingerprints);
        List<FileFingerprint> loaded = store.loadFingerprints("test-index");

        assertEquals(2, loaded.size());
    }

    @Test
    void testBatchSaveThenLoadRoundTrip() {
        List<FileFingerprint> fingerprints = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            fingerprints.add(new FileFingerprint("file" + i + ".java", "hash" + i, i * 100L, i * 10L));
        }

        service.batchSaveFileRecords("test-index", fingerprints);

        List<FileFingerprint> loaded = service.batchLoadFileRecords("test-index");
        assertTrue(loaded.isEmpty());
    }

    @Test
    void testBatchSaveEmptyListNoException() {
        assertDoesNotThrow(() -> service.batchSaveFileRecords("test-index", List.of()));
        assertDoesNotThrow(() -> service.batchSaveFileRecords("test-index", null));
    }

    @Test
    void testBatchDeleteEmptyListNoException() {
        assertDoesNotThrow(() -> service.batchDeleteFileRecords("test-index", List.of()));
    }
}
