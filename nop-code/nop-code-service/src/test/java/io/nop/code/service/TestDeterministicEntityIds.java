package io.nop.code.service;

import io.nop.code.core.util.DigestHelper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TestDeterministicEntityIds {

    private static final int ID_LENGTH = 36;

    private String generateFileId(String indexId, String filePath) {
        return DigestHelper.sha256Hex((indexId + ":" + filePath).getBytes(StandardCharsets.UTF_8)).substring(0, ID_LENGTH);
    }

    @Test
    void testSameInputsProduceSameId() {
        String id1 = generateFileId("idx1", "/src/main/java/App.java");
        String id2 = generateFileId("idx1", "/src/main/java/App.java");
        assertEquals(id1, id2, "Same indexId + filePath should produce same ID");
    }

    @Test
    void testDifferentIndexIdProducesDifferentId() {
        String id1 = generateFileId("idx1", "/src/main/java/App.java");
        String id2 = generateFileId("idx2", "/src/main/java/App.java");
        assertNotEquals(id1, id2, "Different indexId should produce different ID");
    }

    @Test
    void testDifferentFilePathProducesDifferentId() {
        String id1 = generateFileId("idx1", "/src/main/java/App.java");
        String id2 = generateFileId("idx1", "/src/main/java/Util.java");
        assertNotEquals(id1, id2, "Different filePath should produce different ID");
    }

    @Test
    void testIdFormat() {
        String id = generateFileId("idx1", "/src/main/java/App.java");
        assertEquals(ID_LENGTH, id.length(), "ID should be 36 characters (SHA-256 hex prefix)");
        assertTrue(id.matches("[0-9a-f]+"), "ID should be lowercase hex");
    }

    @Test
    void testIdStabilityAcrossMultipleCalls() {
        String input = "idx1:/src/App.java";
        String firstId = DigestHelper.sha256Hex(input.getBytes(StandardCharsets.UTF_8)).substring(0, ID_LENGTH);
        for (int i = 0; i < 10; i++) {
            String id = DigestHelper.sha256Hex(input.getBytes(StandardCharsets.UTF_8)).substring(0, ID_LENGTH);
            assertEquals(firstId, id, "Same input should always produce same ID");
        }
    }
}
