package io.nop.code.service;

import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.analyzer.ProjectAnalysisResult;
import io.nop.code.core.analyzer.ProjectAnalyzer;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.lang.java.JavaLanguageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TestCodeIndexService {

    private ProjectAnalysisResult analysisResult;

    @BeforeEach
    void setUp() {
        LanguageAdapterRegistry registry = new LanguageAdapterRegistry();
        registry.registerAdapter(new JavaLanguageAdapter());
        ProjectAnalyzer analyzer = new ProjectAnalyzer(registry);

        Path projectRoot = new File("src/test/resources/test-project/src/main/java").toPath();
        analysisResult = analyzer.analyzeProject(projectRoot);
    }

    @Test
    void testAnalysisResultHasFiles() {
        assertTrue(analysisResult.getFileResults().size() >= 6,
                "Should analyze at least 6 files, got " + analysisResult.getFileResults().size());
    }

    @Test
    void testFindUserClass() {
        List<CodeSymbol> allSymbols = analysisResult.getFileResults().stream()
                .flatMap(f -> f.getSymbols().stream())
                .collect(Collectors.toList());

        CodeSymbol user = allSymbols.stream()
                .filter(s -> "com.example.domain.User".equals(s.getQualifiedName()))
                .findFirst()
                .orElse(null);

        assertNotNull(user, "Should find User class");
        assertEquals(CodeSymbolKind.CLASS, user.getKind());
        assertEquals("User", user.getName());
    }

    @Test
    void testFindClassesByKind() {
        List<CodeSymbol> classes = analysisResult.getFileResults().stream()
                .flatMap(f -> f.getSymbols().stream())
                .filter(s -> s.getKind() == CodeSymbolKind.CLASS)
                .collect(Collectors.toList());

        assertTrue(classes.stream().anyMatch(s -> "User".equals(s.getName())),
                "Should contain User class");
    }

    @Test
    void testFileAnalysisResult() {
        CodeFileAnalysisResult userFile = analysisResult.getFileResults().stream()
                .filter(f -> f.getFilePath().contains("User.java"))
                .findFirst()
                .orElse(null);
        assertNotNull(userFile, "Should find User.java analysis result");
        assertEquals("com.example.domain", userFile.getPackageName());
    }

    @Test
    void testTypeHierarchy() {
        List<CodeSymbol> allSymbols = analysisResult.getFileResults().stream()
                .flatMap(f -> f.getSymbols().stream())
                .collect(Collectors.toList());

        CodeSymbol user = allSymbols.stream()
                .filter(s -> "com.example.domain.User".equals(s.getQualifiedName()))
                .findFirst()
                .orElse(null);

        assertNotNull(user, "Should find User class");
        assertNotNull(user.getSuperClassName(), "User should have a super class");
    }

    @Test
    void testCallGraph() {
        assertNotNull(analysisResult.buildCallGraph(), "Should have call graph");
    }

    @Test
    void testSymbolCount() {
        long symbolCount = analysisResult.getFileResults().stream()
                .mapToLong(f -> f.getSymbols().size())
                .sum();
        assertTrue(symbolCount > 0, "Should have symbols, got " + symbolCount);
    }

    @Test
    void testTypeOutline() {
        CodeFileAnalysisResult userFile = analysisResult.getFileResults().stream()
                .filter(f -> f.getFilePath().contains("User.java"))
                .findFirst()
                .orElse(null);
        assertNotNull(userFile, "Should find User.java");
        assertNotNull(userFile.getSymbols(), "Should have symbols");

        CodeSymbol userSymbol = userFile.getSymbols().stream()
                .filter(s -> "User".equals(s.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(userSymbol, "Should find User symbol");
        assertEquals(CodeSymbolKind.CLASS, userSymbol.getKind());
    }

    @Test
    void testBatchTypeOutlines() {
        List<String> targetTypes = List.of("com.example.domain.User", "com.example.domain.Status");

        List<CodeFileAnalysisResult> matches = analysisResult.getFileResults().stream()
                .filter(f -> f.getSymbols().stream()
                        .anyMatch(s -> targetTypes.contains(s.getQualifiedName())))
                .collect(Collectors.toList());

        assertEquals(2, matches.size(), "Should find 2 matching types");
    }

    @Test
    void testSymbolsByPackage() {
        List<CodeSymbol> domainSymbols = analysisResult.getFileResults().stream()
                .filter(f -> f.getPackageName() != null && f.getPackageName().startsWith("com.example.domain"))
                .flatMap(f -> f.getSymbols().stream())
                .collect(Collectors.toList());

        assertTrue(domainSymbols.size() >= 3,
                "Domain package should have at least 3 symbols, got " + domainSymbols.size());
    }
}
