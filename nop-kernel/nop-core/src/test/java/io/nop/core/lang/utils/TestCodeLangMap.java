package io.nop.core.lang.utils;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCodeLangMap {

    @Test
    public void testBuiltinLanguageExtensionMappings() {
        CodeLangMap map = CodeLangMap.instance();
        assertEquals(".java", map.getExtensionFromLanguage("java"));
        assertEquals(".py", map.getExtensionFromLanguage("python"));
        assertEquals("java", map.getLanguageFromExtension(".java"));
        assertEquals("yaml", map.getLanguageFromExtension(".yml"), ".yml should map to yaml");
    }

    @Test
    public void testLookupIsCaseInsensitive() {
        CodeLangMap map = CodeLangMap.instance();
        assertEquals(".java", map.getExtensionFromLanguage("JAVA"));
        assertEquals("java", map.getLanguageFromExtension(".JAVA"));
        assertTrue(map.containsLanguage("Python"));
        assertTrue(map.containsExtension(".PY"));
    }

    @Test
    public void testUnknownEntriesReturnNull() {
        CodeLangMap map = CodeLangMap.instance();
        assertNull(map.getExtensionFromLanguage("no-such-lang"));
        assertNull(map.getLanguageFromExtension(".no-such-ext"));
        assertFalse(map.containsLanguage("no-such-lang"));
        assertFalse(map.containsExtension(".no-such-ext"));
    }

    @Test
    public void testAddMappingBothDirections() {
        // 使用一次性映射避免污染共享单例（自定义键不会被其他测试使用）
        CodeLangMap map = CodeLangMap.instance();
        map.addMapping("test-only-lang-xz", ".test-xz");
        assertEquals(".test-xz", map.getExtensionFromLanguage("test-only-lang-xz"));
        assertEquals("test-only-lang-xz", map.getLanguageFromExtension(".test-xz"));
        assertTrue(map.containsExtension(".test-xz"));
        assertTrue(map.getAllLanguages().contains("test-only-lang-xz"));
        assertTrue(map.getAllExtensions().contains(".test-xz"));
    }

    @Test
    public void testAllSetsContainBuiltinEntries() {
        CodeLangMap map = CodeLangMap.instance();
        Set<String> langs = map.getAllLanguages();
        Set<String> exts = map.getAllExtensions();
        assertTrue(langs.containsAll(Set.of("java", "python", "javascript", "sql")));
        assertTrue(exts.containsAll(Set.of(".java", ".py", ".js", ".sql", ".yml")));
        // .yml 是别名扩展（映射到 yaml），因此扩展名集合比语言集合多一项
        assertEquals(langs.size() + 1, exts.size());
    }
}
