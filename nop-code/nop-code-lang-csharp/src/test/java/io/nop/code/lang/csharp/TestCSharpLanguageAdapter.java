package io.nop.code.lang.csharp;

import io.nop.code.core.model.CodeLanguage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCSharpLanguageAdapter {

    @Test
    void adapterMetadataConsistent() {
        CSharpLanguageAdapter adapter = new CSharpLanguageAdapter();
        assertEquals(CodeLanguage.CSHARP, adapter.getLanguage());
        List<String> extensions = adapter.getFileExtensions();
        assertTrue(extensions.contains(".cs"));
        assertInstanceOf(CSharpCodeFileAnalyzer.class, adapter.getFileAnalyzer());
    }

    @Test
    void excludePatternsCoverBinObjGit() {
        CSharpLanguageAdapter adapter = new CSharpLanguageAdapter();
        List<String> patterns = adapter.getExcludePatterns();
        assertTrue(patterns.contains("**/bin/**"));
        assertTrue(patterns.contains("**/obj/**"));
        assertTrue(patterns.contains("**/.git/**"));
    }
}
