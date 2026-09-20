package io.nop.lint.core;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Module bootstrap proof: the Java grammar blob loads from the nop-treesitter
 * jar, a Java snippet parses, and the tree is walkable through TSNode — the
 * full chain every later lint component stands on.
 */
class CoreBootstrapTest {

    private static final String SNIPPET = """
            class Demo {
                void run(int x) {
                    if (x > 0) {
                        throw new RuntimeException("boom");
                    }
                }
            }
            """;

    @Test
    void javaBlobParsesAndTreeWalks() {
        Language java = Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");
        assertNotNull(java, "java grammar blob must load from the classpath");

        TSTree tree = TSParser.parse(java, SNIPPET);
        TSNode root = tree.rootNode();
        assertNotNull(root, "parse must produce a root node");

        List<String> kinds = collectKinds(root);
        assertTrue(kinds.contains("method_declaration"),
                "snippet declares a method, kinds saw: " + kinds);
        assertTrue(kinds.contains("throw_statement"),
                "snippet throws, kinds saw: " + kinds);
        assertFalse(kinds.contains("ERROR"), "snippet must parse without errors");
    }

    private List<String> collectKinds(TSNode node) {
        List<String> kinds = new ArrayList<>();
        collectKinds(node, kinds);
        return kinds;
    }

    private void collectKinds(TSNode node, List<String> kinds) {
        kinds.add(node.type());
        for (int i = 0; i < node.childCount(); i++) {
            TSNode child = node.child(i);
            if (child != null) {
                collectKinds(child, kinds);
            }
        }
    }
}
