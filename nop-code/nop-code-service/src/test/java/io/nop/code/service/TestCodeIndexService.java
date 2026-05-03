package io.nop.code.service;

import io.nop.code.core.model.*;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.api.dto.*;
import io.nop.code.service.impl.CodeIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestCodeIndexService {

    private ICodeIndexService service;
    private Path testProjectDir;

    @BeforeEach
    void setUp() {
        service = new CodeIndexService();
        testProjectDir = Paths.get("src/test/resources/test-project/src/main/java");
    }

    private void indexTestProject() {
        service.indexDirectory("test", testProjectDir, "**/*.java");
    }

    @Test
    void testIndexTestProject() {
        int count = service.indexDirectory("test", testProjectDir, "**/*.java");
        assertTrue(count >= 6, "Should index at least 6 files, got " + count);
    }

    @Test
    void testFindByQualifiedName() {
        indexTestProject();
        CodeSymbol user = service.findSymbolByQualifiedName("test", "com.example.domain.User");
        assertNotNull(user, "Should find User class");
        assertEquals(CodeSymbolKind.CLASS, user.getKind());
        assertEquals("User", user.getName());
    }

    @Test
    void testFindSymbolsByKind() {
        indexTestProject();
        List<CodeSymbol> classes = service.findSymbols("test", null,
                List.of(CodeSymbolKind.CLASS), null, 100);
        assertTrue(classes.stream().anyMatch(s -> "User".equals(s.getName())),
                "Should contain User class");
    }

    @Test
    void testGetFile() {
        indexTestProject();
        CodeFileAnalysisResult file = service.getFile("test", "com/example/domain/User.java");
        assertNotNull(file, "Should find User.java");
        assertEquals("com.example.domain", file.getPackageName());
    }

    @Test
    void testGetTypeHierarchy() {
        indexTestProject();
        TypeHierarchyDTO hierarchy = service.getTypeHierarchy("test",
                "com.example.domain.User", "super", 3);
        assertNotNull(hierarchy, "Should return hierarchy");
        assertNotNull(hierarchy.getSymbol(), "Should have root symbol");
        assertNotNull(hierarchy.getSuperTypes(), "Should have super types");
    }

    @Test
    void testGetCallHierarchy() {
        indexTestProject();
        CallHierarchyDTO callHierarchy = service.getCallHierarchy("test",
                "com.example.service.UserService.changeName", "outgoing", 2);
        assertNotNull(callHierarchy, "Should return call hierarchy");
        assertNotNull(callHierarchy.getSymbol(), "Should have root symbol");
    }

    @Test
    void testGetIndexStats() {
        indexTestProject();
        IndexStatsDTO stats = service.getIndexStats("test");
        assertNotNull(stats);
        assertTrue(stats.getFileCount() >= 6, "Should have at least 6 files");
        assertTrue(stats.getSymbolCount() > 0, "Should have symbols");
    }

    @Test
    void testGetTypeOutline() {
        indexTestProject();
        TypeOutlineDTO outline = service.getTypeOutline("test", "com.example.domain.User");
        assertNotNull(outline);
        assertEquals("User", outline.getName());
        assertNotNull(outline.getMethods());
        assertNotNull(outline.getFields());
    }

    @Test
    void testBatchGetTypeOutlines() {
        indexTestProject();
        List<TypeOutlineDTO> outlines = service.batchGetTypeOutlines("test",
                List.of("com.example.domain.User", "com.example.domain.Status"));
        assertEquals(2, outlines.size());
    }

    @Test
    void testFindSymbolsByPackage() {
        indexTestProject();
        List<CodeSymbol> symbols = service.findSymbols("test", null,
                null, "com.example.domain", 100);
        assertTrue(symbols.size() >= 3, "Domain package should have at least 3 symbols");
    }
}
