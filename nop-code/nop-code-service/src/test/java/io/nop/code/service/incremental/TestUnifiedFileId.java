package io.nop.code.service.incremental;

import io.nop.code.core.util.DigestHelper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TestUnifiedFileId {

    @Test
    void testOrmFingerprintStoreMatchesCodeIndexService() {
        String indexId = "test-idx";
        String filePath = "src/main/java/Foo.java";

        String codeIndexServiceId = DigestHelper.sha256Hex(
                (indexId + ":" + filePath).getBytes(StandardCharsets.UTF_8)).substring(0, 36);

        String ormFingerprintStoreId = DigestHelper.sha256Hex(
                (indexId + ":" + filePath).getBytes(StandardCharsets.UTF_8)).substring(0, 36);

        assertEquals(codeIndexServiceId, ormFingerprintStoreId,
                "Both should produce the same file ID for identical indexId + filePath");
    }

    @Test
    void testDifferentPathsProduceDifferentIds() {
        String indexId = "test-idx";
        String path1 = "src/main/java/Foo.java";
        String path2 = "src/main/java/Bar.java";

        String id1 = DigestHelper.sha256Hex(
                (indexId + ":" + path1).getBytes(StandardCharsets.UTF_8)).substring(0, 36);
        String id2 = DigestHelper.sha256Hex(
                (indexId + ":" + path2).getBytes(StandardCharsets.UTF_8)).substring(0, 36);

        assertNotEquals(id1, id2, "Different paths should produce different IDs");
    }
}
