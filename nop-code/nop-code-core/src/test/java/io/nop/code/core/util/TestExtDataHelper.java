package io.nop.code.core.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestExtDataHelper {

    @Test
    void testSetFilePath_nullExtData() {
        String result = ExtDataHelper.setFilePath(null, "/src/main/Foo.java");
        assertNotNull(result);
        assertTrue(result.contains("/src/main/Foo.java"));
    }

    @Test
    void testSetFilePath_emptyExtData() {
        String result = ExtDataHelper.setFilePath("", "/src/main/Bar.java");
        assertNotNull(result);
        assertEquals("/src/main/Bar.java", ExtDataHelper.extractFilePath(result));
    }

    @Test
    void testSetFilePath_preservesAnnotations() {
        String withAnnotations = ExtDataHelper.setAnnotations(null, List.of("Bean", "Component"));
        String result = ExtDataHelper.setFilePath(withAnnotations, "/src/App.java");
        assertEquals("/src/App.java", ExtDataHelper.extractFilePath(result));
        assertEquals(List.of("Bean", "Component"), ExtDataHelper.getAnnotations(result));
    }

    @Test
    void testSetFilePath_overwritesExisting() {
        String ext = ExtDataHelper.setFilePath(null, "/old/path.java");
        String result = ExtDataHelper.setFilePath(ext, "/new/path.java");
        assertEquals("/new/path.java", ExtDataHelper.extractFilePath(result));
    }

    @Test
    void testSetFilePath_emptyPath_returnsUnchanged() {
        assertNull(ExtDataHelper.setFilePath(null, ""));
        assertEquals("existing", ExtDataHelper.setFilePath("existing", null));
    }

    @Test
    void testExtractFilePath_fromSetFilePath() {
        String ext = ExtDataHelper.setFilePath(null, "src/main/java/com/example/Service.java");
        assertEquals("src/main/java/com/example/Service.java", ExtDataHelper.extractFilePath(ext));
    }
}
