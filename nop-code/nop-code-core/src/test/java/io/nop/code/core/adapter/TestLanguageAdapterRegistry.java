package io.nop.code.core.adapter;

import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.analyzer.ILanguageAdapter;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestLanguageAdapterRegistry {

    private LanguageAdapterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new LanguageAdapterRegistry();
    }

    private ILanguageAdapter createMockAdapter(CodeLanguage language, String... extensions) {
        return new ILanguageAdapter() {
            @Override
            public CodeLanguage getLanguage() {
                return language;
            }

            @Override
            public ICodeFileAnalyzer getFileAnalyzer() {
                return new ICodeFileAnalyzer() {
                    @Override
                    public CodeLanguage getLanguage() {
                        return language;
                    }

                    @Override
                    public CodeFileAnalysisResult analyze(String filePath, String sourceCode) {
                        return null;
                    }

                    @Override
                    public List<String> getFileExtensions() {
                        return Arrays.asList(extensions);
                    }
                };
            }

            @Override
            public List<String> getFileExtensions() {
                return Arrays.asList(extensions);
            }

            @Override
            public List<String> getExcludePatterns() {
                return Collections.emptyList();
            }
        };
    }

    @Test
    void testRegisterAdapterRegistersByLanguage() {
        registry.registerAdapter(createMockAdapter(CodeLanguage.JAVA, ".java"));
        assertNotNull(registry.getAdapter(CodeLanguage.JAVA));
    }

    @Test
    void testGetAdapterReturnsCorrectAdapter() {
        registry.registerAdapter(createMockAdapter(CodeLanguage.JAVA, ".java"));
        registry.registerAdapter(createMockAdapter(CodeLanguage.PYTHON, ".py"));
        assertEquals(CodeLanguage.JAVA, registry.getAdapter(CodeLanguage.JAVA).getLanguage());
        assertEquals(CodeLanguage.PYTHON, registry.getAdapter(CodeLanguage.PYTHON).getLanguage());
    }

    @Test
    void testGetAdapterUnsupportedReturnsNull() {
        assertNull(registry.getAdapter(CodeLanguage.JAVA));
    }

    @Test
    void testGetAnalyzerByFilePath() {
        registry.registerAdapter(createMockAdapter(CodeLanguage.JAVA, ".java"));
        assertNotNull(registry.getAnalyzer("Foo.java"));
        assertNull(registry.getAnalyzer("Foo.py"));
    }

    @Test
    void testGetSupportedLanguages() {
        registry.registerAdapter(createMockAdapter(CodeLanguage.JAVA, ".java"));
        registry.registerAdapter(createMockAdapter(CodeLanguage.PYTHON, ".py"));
        Set<CodeLanguage> langs = registry.getSupportedLanguages();
        assertEquals(2, langs.size());
        assertTrue(langs.contains(CodeLanguage.JAVA));
        assertTrue(langs.contains(CodeLanguage.PYTHON));
    }

    @Test
    void testRegisterAdapterDirectly() {
        registry.registerAdapter(createMockAdapter(CodeLanguage.TYPESCRIPT, ".ts"));
        assertNotNull(registry.getAdapter(CodeLanguage.TYPESCRIPT));
    }
}
