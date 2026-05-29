package io.nop.code.service;

import io.nop.code.core.util.DigestHelper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TestDeterministicEntityIds {

    @Test
    void testSameInputProducesSameId() {
        String source = "src/Main.java";
        String target = "lib/Util.java";
        String stmt = "import lib.Util;";

        String id1 = DigestHelper.sha256Hex(
                ("idx1:" + source + ":" + target + ":" + stmt).getBytes(StandardCharsets.UTF_8)).substring(0, 36);
        String id2 = DigestHelper.sha256Hex(
                ("idx1:" + source + ":" + target + ":" + stmt).getBytes(StandardCharsets.UTF_8)).substring(0, 36);

        assertEquals(id1, id2, "Same input should produce same ID");
    }

    @Test
    void testDifferentInputProducesDifferentId() {
        String source = "src/Main.java";
        String target = "lib/Util.java";

        String id1 = DigestHelper.sha256Hex(
                ("idx1:" + source + ":" + target + ":import A").getBytes(StandardCharsets.UTF_8)).substring(0, 36);
        String id2 = DigestHelper.sha256Hex(
                ("idx1:" + source + ":" + target + ":import B").getBytes(StandardCharsets.UTF_8)).substring(0, 36);

        assertNotEquals(id1, id2, "Different inputs should produce different IDs");
    }

    @Test
    void testDeterministicPathBasedId() {
        String path1 = "/src/main/java/com/example/App.java";
        String path2 = "/src/main/java/com/example/Util.java";

        String id1 = "idx_" + DigestHelper.sha256Hex(path1.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
        String id2 = "idx_" + DigestHelper.sha256Hex(path2.getBytes(StandardCharsets.UTF_8)).substring(0, 16);

        assertNotEquals(id1, id2);
        assertEquals(id1, "idx_" + DigestHelper.sha256Hex(path1.getBytes(StandardCharsets.UTF_8)).substring(0, 16));
    }
}
