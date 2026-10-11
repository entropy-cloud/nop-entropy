package io.nop.lint.core.pattern;

import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintNode;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The multi-capture snapshot contract the xscript binding layer relies on
 * (plan 08 Phase 3): one copy, fully immutable — map-level writes throw,
 * value-list writes throw, and captures inserted after the snapshot never
 * bleed into an already-returned map.
 */
class TestMetaVarEnvSnapshot {

    private static io.nop.lint.core.lang.LintLanguage java;

    @BeforeAll
    static void loadLanguage() {
        java = new TreeSitterLanguageAdapter("java",
                Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);
    }

    private static MetaVarEnv envWithMultiCapture() {
        LintNode root = java.parse("class A { void f() { g(1); } }").root();
        MetaVarEnv env = new MetaVarEnv();
        env.insertMulti("args", List.of(root));
        return env;
    }

    @Test
    void mapLevelWritesThrow() {
        Map<String, List<LintNode>> snapshot = envWithMultiCapture().multiCaptures();
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.put("x", List.of()));
    }

    @Test
    void valueListWritesThrow() {
        Map<String, List<LintNode>> snapshot = envWithMultiCapture().multiCaptures();
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.get("args").add(snapshot.get("args").get(0)),
                "the value list is a copied immutable sequence, not the live capture");
    }

    @Test
    void laterInsertsDoNotBleedIntoTheReturnedSnapshot() {
        MetaVarEnv env = envWithMultiCapture();
        LintNode root = java.parse("class B { }").root();
        Map<String, List<LintNode>> snapshot = env.multiCaptures();

        env.insertMulti("args", List.of(root));
        env.insertMulti("later", List.of(root));

        assertEquals(1, snapshot.size(), "only the captures at snapshot time");
        assertEquals(1, snapshot.get("args").size(), "later appends never bleed in");
        assertTrue(!snapshot.containsKey("later"));
    }
}
