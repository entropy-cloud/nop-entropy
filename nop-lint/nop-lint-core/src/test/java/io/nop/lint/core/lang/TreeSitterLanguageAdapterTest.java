package io.nop.lint.core.lang;

import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adapter focus tests: parse wiring onto the node facade, kind-name
 * resolution across the full symbol table (alias range, same-name collisions,
 * built-in recovery kinds), expando hook, and identity default.
 */
class TreeSitterLanguageAdapterTest {

    /**
     * Covers the three kind-resolution hazards: type_identifier (alias range),
     * a throws clause (named symbol colliding with the unnamed keyword token),
     * and ordinary declarations.
     */
    private static final String SNIPPET = """
            class Demo {
                java.util.List<String> names;
                void run(int x) throws Exception {
                    if (x > 0) {
                        throw new RuntimeException("boom");
                    }
                }
            }
            """;

    private static TreeSitterLanguageAdapter adapter;

    @BeforeAll
    static void loadLanguage() {
        Language java = Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");
        adapter = new TreeSitterLanguageAdapter("java", java, null);
    }

    @Test
    void parseProducesFacadeTree() {
        LintTree tree = adapter.parse(SNIPPET);
        assertEquals("program", tree.root().kind());
        assertEquals("java", adapter.id());
        assertNotNull(adapter.treeSitter());
    }

    @Test
    void kindIdResolvesKnownKinds() {
        assertTrue(adapter.kindId("method_declaration") > 0);
        assertTrue(adapter.kindId("type_identifier") > 0,
                "aliased kind must resolve beyond the non-alias symbol range");
        assertTrue(adapter.kindId("throws") > 0);
        assertTrue(adapter.kindId("block") > 0);
    }

    @Test
    void kindIdResolvesCollisionToNamedSymbol() {
        // The named `throws` clause node must win over the unnamed keyword token.
        LintNode method = findByKind(adapter.parse(SNIPPET).root(), "method_declaration");
        LintNode throwsClause = findDirectChildByKind(method, "throws");
        assertNotNull(throwsClause, "snippet method must declare a throws clause");
        assertEquals(adapter.kindId("throws"), throwsClause.kindId(),
                "kindId(\"throws\") must be the named clause id, not the keyword token id");
    }

    @Test
    void kindIdReturnsMinusOneForUnknownAndBuiltinError() {
        assertEquals(-1, adapter.kindId("not_a_kind"));
        assertEquals(-1, adapter.kindId("ERROR"),
                "built-in recovery kinds sit outside the name table by construction");
    }

    @Test
    void kindIdConsistentWithNodeKindAcrossNamedNodes() {
        LintTree tree = adapter.parse(SNIPPET);
        int namedChecked = 0;
        for (LintNode node : tree.root()) {
            if (!node.isNamed()) {
                continue;
            }
            // Built-in recovery nodes (ERROR/_ERROR) have ids beyond the name table.
            if (node.kindId() >= adapter.treeSitter().symbolCount() + adapter.treeSitter().aliasCount()) {
                continue;
            }
            assertEquals(node.kindId(), adapter.kindId(node.kind()),
                    "kind id must round-trip through the name table at " + node.kind());
            namedChecked++;
        }
        assertTrue(namedChecked > 20, "snippet must exercise enough named kinds, got " + namedChecked);
    }

    @Test
    void expandoHookTransformsAndIdentityByDefault() {
        UnaryOperator<String> underscore = pattern -> pattern.replace('$', '_');
        TreeSitterLanguageAdapter custom = new TreeSitterLanguageAdapter("java", adapter.treeSitter(), underscore);
        assertEquals("_VAR ___", custom.preprocessPattern("$VAR $$$"));

        assertEquals("$VAR $$$", adapter.preprocessPattern("$VAR $$$"),
                "default preprocessing is identity");
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> new TreeSitterLanguageAdapter(null, adapter.treeSitter(), null));
        assertThrows(NullPointerException.class, () -> new TreeSitterLanguageAdapter("java", null, null));
    }

    private LintNode findByKind(LintNode root, String kind) {
        for (LintNode node : root) {
            if (node.kind().equals(kind)) {
                return node;
            }
        }
        throw new AssertionError("no " + kind + " in tree");
    }

    private LintNode findDirectChildByKind(LintNode parent, String kind) {
        for (LintNode child : parent.children()) {
            if (child.kind().equals(kind)) {
                return child;
            }
        }
        return null;
    }
}
